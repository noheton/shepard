package de.dlr.shepard.plugin;

import io.quarkus.logging.Log;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * PM1a — central registry for {@link PluginManifest} instances
 * discovered at startup.
 *
 * <p>Discovery shape (ADR-0023):
 * <ol>
 *   <li><strong>Build-classpath manifests.</strong> Any
 *       {@code PluginManifest} on the backend's own classloader is
 *       picked up via {@code ServiceLoader.load(PluginManifest.class)}
 *       — this is the Phase 1 shape for CDI-heavy plugins (UH1a;
 *       see ADR-0024).</li>
 *   <li><strong>Drop-in JARs.</strong> The directory
 *       {@code shepard.plugins.dir} (default {@code backend/plugins})
 *       is walked for {@code *.jar} files; each gets a child
 *       {@link URLClassLoader} with the backend's loader as parent
 *       (so plugin classes can see core classes but two plugins'
 *       transitive deps don't collide). Each child loader is
 *       ServiceLoader-scanned for {@code PluginManifest}.</li>
 * </ol>
 *
 * <p>Per-plugin {@code shepard.plugins.<id>.enabled} (default
 * {@code true}) gates whether {@link PluginManifest#onRegister} is
 * invoked. Disabled plugins still appear in {@link #list()} — operators
 * see them in {@code GET /v2/admin/plugins} so they can flip the
 * toggle at runtime.
 *
 * <p>Fail-soft per plugin: any exception from {@code onRegister} is
 * caught, the plugin transitions to {@link PluginState#FAILED}, and
 * the registry continues with the next plugin. The startup itself
 * doesn't fail.
 *
 * <p>Bootstrap ordering: this registry observes
 * {@link StartupEvent}. {@code ShepardMain.init()} runs the
 * {@code MigrationsRunner.apply()} from {@code @Startup} (an Arc
 * lifecycle event that fires before the CDI container's
 * {@code StartupEvent}), so plugin {@code onRegister} runs strictly
 * <em>after</em> migrations — matching ADR-0023.
 *
 * <p>{@code @SuppressFBWarnings} rationale: the URLClassLoader
 * intentionally loads from the configured plugin directory; path
 * traversal is prevented by canonicalising the resolved JAR against
 * the configured plugin dir's real path (see
 * {@link #discoverJarPlugins(Path)}). Signature verification is
 * out of scope for PM1a and flagged as PM1b follow-up work.
 *
 * <p>PM1e — runtime overrides are persisted via
 * {@link PluginRuntimeOverrideDAO} (Neo4j {@code :PluginRuntimeOverride}
 * label, V32 uniqueness constraint). On startup we seed the
 * in-memory cache from the DAO so a {@code disable} survives a
 * restart; on {@link #setEnabled(String, boolean)} we write
 * through the DAO synchronously (when overriding the default) or
 * delete the row (when resetting to the default), keeping the
 * table sparse. The deploy-time
 * {@code shepard.plugins.<id>.enabled} key in
 * {@code application.properties} stays the install default — the
 * persisted row wins forever after, matching the A3b / N1c2 / UH1a
 * "operator-knob" idiom.
 */
@ApplicationScoped
public class PluginRegistry {

  /** Default plugin directory, relative to the JVM working dir. */
  public static final String DEFAULT_PLUGIN_DIR = "backend/plugins";

  /** Config key for the plugin directory (deploy-time only). */
  public static final String CONFIG_PLUGINS_DIR = "shepard.plugins.dir";

  /** Per-plugin runtime-toggle key template — fed plugin id. */
  public static final String CONFIG_PLUGIN_ENABLED_TPL = "shepard.plugins.%s.enabled";

  /**
   * Opt-out for the build-classpath ServiceLoader pass — set to
   * {@code false} to scan only the drop-in JAR directory. Mainly
   * useful in unit tests where the test classpath may carry plugin
   * manifests (e.g. UH1a as a build dependency) that the test
   * doesn't want to discover. Default {@code true} (scan).
   */
  public static final String CONFIG_CLASSPATH_SCAN_ENABLED = "shepard.plugins.classpath-scan.enabled";

  @Inject
  BeanManager beanManager;

  /**
   * PM1e — DAO for {@link PluginRuntimeOverride} rows. Resolved
   * lazily via {@link Instance} so tests can construct a
   * {@link PluginRegistry} reflectively without a CDI container
   * (matching the {@link PluginRegistryTest} fixture's posture);
   * when the DAO isn't available (i.e. {@link #overrideDao} resolves
   * to unsatisfied), the registry falls back to the in-memory-only
   * behaviour PM1a-c shipped.
   */
  @Inject
  Instance<PluginRuntimeOverrideDAO> overrideDao;

  @ConfigProperty(name = CONFIG_PLUGINS_DIR, defaultValue = DEFAULT_PLUGIN_DIR)
  String pluginsDir;

  @ConfigProperty(name = CONFIG_CLASSPATH_SCAN_ENABLED, defaultValue = "true")
  boolean classpathScanEnabled;

  /**
   * Insertion-ordered map keyed by plugin id. Reads happen on the
   * caller's thread; writes happen only from {@link #onStart} +
   * {@link #onShutdown} on the Quarkus startup / shutdown thread.
   * Marked volatile-by-LinkedHashMap-replacement on startup, then
   * frozen as an unmodifiable view.
   *
   * <p>Package-private so {@link PluginRegistryDependencyTest} can
   * stage entries directly and exercise
   * {@link #validateAndOrderDependencies()} without booting the full
   * JAR-discovery path.
   */
  final Map<String, PluginEntry> entries = new LinkedHashMap<>();

  /**
   * Runtime override of the per-plugin enabled flag — keyed by
   * plugin id. Absent entry = no override, fall through to the
   * config-property value. Volatile read; mutations happen only via
   * {@link #setEnabled(String, boolean)} (the admin REST / CLI
   * surface).
   *
   * <p>PM1e — this map mirrors the persistent
   * {@code :PluginRuntimeOverride} table; it is seeded from the DAO
   * on startup (see {@link #seedOverridesFromDao()}) and kept in
   * step with each {@code setEnabled} write. Reads stay on the
   * in-memory cache so hot paths don't hit the DB.
   */
  private final Map<String, Boolean> runtimeOverrides = new java.util.concurrent.ConcurrentHashMap<>();

  /**
   * Child class loaders the registry created — held so they can be
   * closed in {@link #onShutdown}. SpotBugs notes that holding the
   * loader keeps the JAR file handle open; that's intentional for
   * the lifetime of the JVM (the plugin's classes need to remain
   * resolvable). Closing happens explicitly on shutdown.
   */
  private final List<URLClassLoader> childLoaders = new ArrayList<>();

  @PostConstruct
  void init() {
    // Deliberately empty — discovery runs from StartupEvent so the
    // ordering with ShepardMain.init()'s migrations is well-defined.
    // The PostConstruct exists so @ApplicationScoped lazy-init still
    // wires beanManager early enough for tests that bypass startup.
  }

  /**
   * Discover and register all plugins. Runs on Quarkus's startup
   * sequence — strictly after {@code ShepardMain.init()} (which
   * runs in {@code @Startup}, an Arc-lifecycle hook that fires
   * before the CDI {@code StartupEvent}).
   */
  void onStart(@Observes StartupEvent event) {
    discover();
  }

  /**
   * Drive the discovery + lifecycle. Package-private for direct
   * test invocation without booting Quarkus.
   *
   * <p>Discovery sequence (PM1c):
   * <ol>
   *   <li>Classpath ServiceLoader scan (collects manifests but
   *       defers lifecycle).</li>
   *   <li>JAR-walk scan (same — collects only).</li>
   *   <li>{@link #validateAndOrderDependencies()} (PM1c) — checks
   *       each non-failed entry's
   *       {@link PluginManifest#dependencies()} against the
   *       discovery set; marks FAILED on missing dependency, version
   *       mismatch, or cycle; reorders the entries map by
   *       topological order so dependencies register first.</li>
   *   <li>{@link #invokeLifecycle()} — calls {@code onRegister} per
   *       non-failed entry in topo-sorted order.</li>
   * </ol>
   */
  void discover() {
    Log.info("PM1a: plugin discovery starting");
    // PM1e — load persisted runtime overrides BEFORE the lifecycle
    // pass so the seeded values are visible to isEnabled() during
    // invokeLifecycle(). Failing to seed (e.g. Neo4j not reachable)
    // is logged + treated as "no overrides" — the registry then
    // behaves exactly as it did pre-PM1e.
    seedOverridesFromDao();
    if (classpathScanEnabled) {
      discoverClasspathPlugins();
    } else {
      Log.debug("PM1a: classpath scan disabled via shepard.plugins.classpath-scan.enabled=false");
    }
    Path resolved = resolvePluginDir();
    if (resolved != null) {
      discoverJarPlugins(resolved);
    }
    validateAndOrderDependencies();
    invokeLifecycle();
    Log.infof(
      "PM1a: plugin discovery complete — %d discovered, %d enabled, %d disabled, %d failed",
      entries.size(),
      countByState(PluginState.ENABLED),
      countByState(PluginState.DISABLED),
      countByState(PluginState.FAILED)
    );
  }

  /**
   * PM1e — seed the in-memory {@link #runtimeOverrides} map from the
   * persisted {@link PluginRuntimeOverride} rows. Idempotent
   * (replaces any in-memory state with the DB truth). When the DAO
   * isn't injected (test path) or is unreachable (DB down at
   * startup), logs and continues — the registry then falls back to
   * the deploy-time defaults exactly as PM1a-c did.
   */
  void seedOverridesFromDao() {
    if (overrideDao == null || overrideDao.isUnsatisfied()) {
      Log.debug("PM1e: PluginRuntimeOverrideDAO not injected — skipping seed (in-memory-only mode)");
      return;
    }
    try {
      List<PluginRuntimeOverride> rows = overrideDao.get().findAllOverrides();
      runtimeOverrides.clear();
      for (PluginRuntimeOverride row : rows) {
        if (row.getPluginId() != null && !row.getPluginId().isBlank()) {
          runtimeOverrides.put(row.getPluginId(), row.isEnabled());
        }
      }
      Log.infof("PM1e: loaded %d persistent runtime override(s)", rows.size());
    } catch (RuntimeException ex) {
      // Fail-soft — a DB blip at startup must not kneecap plugin
      // discovery; the registry continues with the deploy-time
      // defaults. The blip is loud in the log so an operator notices.
      Log.warnf(ex, "PM1e: could not seed runtime overrides from DAO — falling back to deploy-time defaults");
    }
  }

  /**
   * Phase 1 build-classpath path: any {@link PluginManifest} on the
   * backend's classloader (e.g. UH1a's manifest, shipped as a
   * Maven {@code <dependency>} in the backend pom) is picked up
   * here. The Quarkus build-time CDI scanner discovers the plugin's
   * {@code @ApplicationScoped} beans separately; this registry's
   * job is to log + track + lifecycle-hook them.
   */
  private void discoverClasspathPlugins() {
    try {
      ServiceLoader<PluginManifest> loader = ServiceLoader.load(
        PluginManifest.class,
        Thread.currentThread().getContextClassLoader()
      );
      for (PluginManifest manifest : loader) {
        register(new PluginEntry(manifest, null));
      }
    } catch (ServiceConfigurationError e) {
      // A malformed META-INF/services file on the backend classpath
      // is a build-time defect, not a runtime concern — log loudly
      // and move on. plugin.discovery.failed in spirit.
      Log.warnf(e, "PM1a: classpath ServiceLoader failed for PluginManifest — check META-INF/services entries");
    }
  }

  /**
   * Phase 1 drop-in path: walk {@code shepard.plugins.dir} for
   * {@code *.jar} files; each gets a child {@link URLClassLoader}
   * and its ServiceLoader is scanned.
   *
   * <p>Security note (PM1a scope): we resolve {@code pluginsDir} to
   * its real path and only accept JARs whose canonical path lies
   * within that real path. JARs outside the directory (e.g. via
   * symlinks pointing elsewhere) are rejected with a WARN. JAR
   * signature verification is PM1b follow-up work — flagged in
   * {@code aidocs/16}.
   */
  private void discoverJarPlugins(Path pluginDir) {
    Path realDir;
    try {
      realDir = pluginDir.toRealPath();
    } catch (IOException e) {
      Log.warnf(e, "PM1a: plugin dir %s could not be resolved — skipping JAR discovery", pluginDir);
      return;
    }
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(realDir, "*.jar")) {
      for (Path jar : stream) {
        loadJarPlugin(jar, realDir);
      }
    } catch (IOException e) {
      Log.warnf(e, "PM1a: error walking plugin dir %s — JAR discovery incomplete", realDir);
    }
  }

  private void loadJarPlugin(Path jar, Path expectedParent) {
    Path realJar;
    try {
      realJar = jar.toRealPath();
    } catch (IOException e) {
      Log.warnf(e, "PM1a: could not resolve %s — skipping", jar);
      return;
    }
    if (!realJar.startsWith(expectedParent)) {
      // Defence in depth — DirectoryStream already constrains the
      // walk, but a malicious symlink within the dir could point at
      // an arbitrary JAR. Reject the off-tree case.
      Log.warnf("PM1a: plugin JAR %s resolves outside %s — skipping for safety", jar, expectedParent);
      return;
    }
    URL jarUrl;
    try {
      jarUrl = realJar.toUri().toURL();
    } catch (MalformedURLException e) {
      Log.warnf(e, "PM1a: %s cannot be expressed as a URL — skipping", realJar);
      return;
    }
    URLClassLoader childLoader = new URLClassLoader(
      "shepard-plugin:" + realJar.getFileName(),
      new URL[] { jarUrl },
      Thread.currentThread().getContextClassLoader()
    );
    childLoaders.add(childLoader);
    // ServiceLoader.load(class, loader) walks the loader AND its
    // parents for `META-INF/services/...` entries — so if the
    // backend's own classloader already carries
    // `META-INF/services/PluginManifest` (e.g. UH1a as a build
    // dependency), the child-loader pass would re-discover the
    // backend-classpath service and double-register it. To keep
    // the JAR-walk strictly about the JAR itself, we read the
    // service-file entries directly from the JAR and resolve
    // each implementation class via the child loader.
    List<String> implClasses = readServiceImplsFromJar(realJar);
    if (implClasses.isEmpty()) {
      Log.warnf("PM1a: %s carries no META-INF/services/PluginManifest — ignored", realJar.getFileName());
      return;
    }
    for (String implClass : implClasses) {
      try {
        Class<?> cls = Class.forName(implClass, true, childLoader);
        if (!PluginManifest.class.isAssignableFrom(cls)) {
          Log.warnf(
            "PM1a: %s names %s in META-INF/services/PluginManifest but it's not a PluginManifest — skipping",
            realJar.getFileName(),
            implClass
          );
          continue;
        }
        PluginManifest manifest = (PluginManifest) cls.getDeclaredConstructor().newInstance();
        register(new PluginEntry(manifest, realJar));
      } catch (ReflectiveOperationException | LinkageError e) {
        // plugin.discovery.failed — record but continue.
        Log.warnf(e, "PM1a: failed to instantiate %s from %s — plugin.discovery.failed", implClass, realJar.getFileName());
      }
    }
  }

  /**
   * Read implementation-class names from the JAR's own
   * {@code META-INF/services/de.dlr.shepard.plugin.PluginManifest}
   * entry — without using {@link ServiceLoader#load}, because that
   * would also walk the parent classloader and return entries
   * already discovered there.
   */
  private List<String> readServiceImplsFromJar(Path jar) {
    List<String> result = new ArrayList<>();
    try (java.util.jar.JarFile jf = new java.util.jar.JarFile(jar.toFile())) {
      java.util.jar.JarEntry entry = jf.getJarEntry("META-INF/services/" + PluginManifest.class.getName());
      if (entry == null) {
        return result;
      }
      try (java.io.BufferedReader r = new java.io.BufferedReader(
          new java.io.InputStreamReader(jf.getInputStream(entry), java.nio.charset.StandardCharsets.UTF_8))) {
        String line;
        while ((line = r.readLine()) != null) {
          // ServiceLoader file syntax: ignore lines past `#`, trim,
          // skip blanks.
          int hash = line.indexOf('#');
          if (hash >= 0) {
            line = line.substring(0, hash);
          }
          line = line.trim();
          if (!line.isEmpty()) {
            result.add(line);
          }
        }
      }
    } catch (IOException e) {
      Log.warnf(e, "PM1a: error reading service file from %s", jar);
    }
    return result;
  }

  /**
   * Insert a plugin entry. Rejects duplicate ids (the second
   * occurrence wins {@code FAILED} state) so two JARs claiming the
   * same plugin id don't silently shadow each other.
   */
  private void register(PluginEntry entry) {
    String id = entry.id();
    if (id == null || id.isBlank()) {
      Log.warnf("PM1a: plugin manifest %s has blank id — plugin.manifest.invalid", entry.manifest().getClass().getName());
      return;
    }
    if (entries.containsKey(id)) {
      PluginEntry existing = entries.get(id);
      // PM1a phase 3: a plugin that lives on the build classpath
      // (e.g. UH1a as a Maven dependency of the backend, per
      // ADR-0023) is *also* commonly present as a JAR in
      // `backend/plugins/` for operator visibility (`ls` shows it).
      // The classpath ServiceLoader pass runs first; when the same
      // id then shows up from a JAR walk we treat the duplicate as
      // benign and skip the second occurrence — the classpath
      // version is already wired through Quarkus's build-time CDI
      // scan and is the active runtime path.
      boolean firstIsClasspath = existing.jarPath() == null;
      boolean secondIsJar = entry.jarPath() != null;
      if (firstIsClasspath && secondIsJar) {
        Log.infof(
          "PM1a: plugin '%s' already discovered on the build classpath — JAR at %s shadowed (benign; the classpath bean wiring wins)",
          id,
          entry.jarPath()
        );
        return;
      }
      entry.markFailed("duplicate plugin id (already loaded from " + existing.jarPath() + ")");
      Log.warnf(
        "PM1a: duplicate plugin id '%s' — second occurrence from %s rejected (already loaded from %s)",
        id,
        entry.jarPath(),
        existing.jarPath()
      );
      // Track the failed duplicate under a synthetic key so
      // operators see why it didn't load.
      entries.put(id + "#duplicate-" + System.nanoTime(), entry);
      return;
    }
    entries.put(id, entry);
    Log.infof(
      "PM1a: plugin '%s' v%s discovered (from %s)",
      id,
      entry.version(),
      entry.jarPath() == null ? "build classpath" : entry.jarPath()
    );
  }

  /**
   * PM1c — resolve {@link PluginManifest#dependencies()} into a
   * topological order; mark plugins FAILED that depend on a missing
   * sibling, a version-incompatible sibling, or participate in a
   * cycle. Non-failing plugins are reordered (in the {@link #entries}
   * map) so dependencies register before dependents.
   *
   * <p>Failure modes:
   * <ul>
   *   <li>{@code plugin.dependency.missing} — declared dependency id
   *       not present in the discovery set.</li>
   *   <li>{@code plugin.dependency.version-mismatch} — sibling
   *       discovered but its {@code version()} doesn't satisfy the
   *       declared {@link PluginDependency#versionConstraint()}.</li>
   *   <li>{@code plugin.dependency.cycle} — every plugin on a cycle
   *       fails; the cycle is logged once.</li>
   * </ul>
   *
   * <p>Algorithm: Kahn's topological sort over the non-FAILED entries.
   * After dependency-validation, any plugin still in DISCOVERED state
   * is eligible for the sort; the sort's output ordering is appended
   * after all FAILED entries (which keep their insertion-order slot
   * for operator readability). A separate FAILED-then-sorted ordering
   * keeps {@link #list()} stable across restart for the failed rows
   * (which an operator typically wants to fix first).
   */
  void validateAndOrderDependencies() {
    // Step 1 — index the discovery set by id, but only the entries
    // that aren't already FAILED (a duplicate-id row from PM1a's
    // register() phase carries a synthetic key — we never want to
    // consider it a dep target).
    Map<String, PluginEntry> byId = new HashMap<>();
    for (PluginEntry entry : entries.values()) {
      if (entry.state() != PluginState.FAILED) {
        byId.put(entry.id(), entry);
      }
    }

    // Step 2 — validate each entry's dependencies. A failure here
    // marks the entry FAILED and removes it from the graph for the
    // topo-sort step. We walk a stable copy of the values so the
    // iteration isn't disturbed by failures (no map mutation).
    List<PluginEntry> candidates = new ArrayList<>(byId.values());
    for (PluginEntry entry : candidates) {
      List<PluginDependency> deps;
      try {
        deps = entry.dependencies();
      } catch (RuntimeException ex) {
        entry.markFailed(
          "plugin.dependency.read-failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage()
        );
        Log.warnf(ex, "PM1c: plugin '%s' dependencies() threw — marking FAILED", entry.id());
        byId.remove(entry.id());
        continue;
      }
      if (deps == null || deps.isEmpty()) {
        continue;
      }
      for (PluginDependency dep : deps) {
        PluginEntry target = byId.get(dep.pluginId());
        if (target == null) {
          entry.markFailed(
            "plugin.dependency.missing: required plugin '" + dep.pluginId() + "' not discovered"
          );
          Log.warnf(
            "PM1c: plugin '%s' depends on missing plugin '%s' — plugin.dependency.missing",
            entry.id(),
            dep.pluginId()
          );
          byId.remove(entry.id());
          break;
        }
        VersionRange range;
        try {
          range = VersionRange.parse(dep.versionConstraint());
        } catch (IllegalArgumentException ex) {
          entry.markFailed(
            "plugin.dependency.version-mismatch: unparseable range '" + dep.versionConstraint() +
            "' for '" + dep.pluginId() + "' (" + ex.getMessage() + ")"
          );
          Log.warnf(
            "PM1c: plugin '%s' declares unparseable version range for '%s' — plugin.dependency.version-mismatch",
            entry.id(),
            dep.pluginId()
          );
          byId.remove(entry.id());
          break;
        }
        if (!range.accepts(target.version())) {
          entry.markFailed(
            "plugin.dependency.version-mismatch: '" + dep.pluginId() + "' v" + target.version() +
            " does not satisfy '" + range.raw() + "'"
          );
          Log.warnf(
            "PM1c: plugin '%s' requires %s %s but discovered v%s — plugin.dependency.version-mismatch",
            entry.id(),
            dep.pluginId(),
            range.raw(),
            target.version()
          );
          byId.remove(entry.id());
          break;
        }
      }
    }

    // Step 3 — Kahn's topo-sort over what's left.
    // Edges go from dependency → dependent (dependency must be
    // registered first).
    Map<String, Set<String>> dependents = new HashMap<>();
    Map<String, Integer> incoming = new HashMap<>();
    for (PluginEntry entry : byId.values()) {
      incoming.putIfAbsent(entry.id(), 0);
      dependents.putIfAbsent(entry.id(), new HashSet<>());
    }
    for (PluginEntry entry : byId.values()) {
      for (PluginDependency dep : entry.dependencies()) {
        // A dep that drops out of byId via failure-pruning means the
        // dependent was already marked FAILED in step 2 — skip.
        if (!byId.containsKey(dep.pluginId())) {
          continue;
        }
        Set<String> children = dependents.get(dep.pluginId());
        if (children.add(entry.id())) {
          incoming.merge(entry.id(), 1, Integer::sum);
        }
      }
    }

    Deque<String> queue = new ArrayDeque<>();
    for (Map.Entry<String, Integer> e : incoming.entrySet()) {
      if (e.getValue() == 0) {
        queue.add(e.getKey());
      }
    }
    List<String> sorted = new ArrayList<>(byId.size());
    while (!queue.isEmpty()) {
      String id = queue.poll();
      sorted.add(id);
      for (String child : dependents.get(id)) {
        int n = incoming.merge(child, -1, Integer::sum);
        if (n == 0) {
          queue.add(child);
        }
      }
    }

    if (sorted.size() < byId.size()) {
      // Cycle — every node with incoming > 0 is on a cycle.
      List<String> onCycle = new ArrayList<>();
      for (Map.Entry<String, Integer> e : incoming.entrySet()) {
        if (e.getValue() > 0) {
          onCycle.add(e.getKey());
        }
      }
      Collections.sort(onCycle);
      Log.warnf(
        "PM1c: plugin dependency cycle detected across %s — every plugin on the cycle marked FAILED",
        onCycle
      );
      for (String id : onCycle) {
        PluginEntry e = byId.remove(id);
        if (e != null) {
          e.markFailed("plugin.dependency.cycle: participates in cycle " + onCycle);
        }
      }
    }

    // Step 4 — rebuild the entries map: previously-FAILED rows keep
    // their original insertion-order position; the survivors are
    // re-inserted in topo order. The original "#duplicate-..." rows
    // (from PM1a duplicate-id detection) live in entries but not in
    // byId — they stay put.
    LinkedHashMap<String, PluginEntry> rebuilt = new LinkedHashMap<>();
    // First pass: preserve all entries that aren't in byId at their
    // original position (these are FAILED rows, duplicates,
    // dep-failures we just marked).
    Set<String> surviving = byId.keySet();
    for (Map.Entry<String, PluginEntry> e : entries.entrySet()) {
      if (!surviving.contains(e.getKey())) {
        rebuilt.put(e.getKey(), e.getValue());
      }
    }
    // Second pass: append survivors in topological order.
    for (String id : sorted) {
      if (surviving.contains(id)) {
        rebuilt.put(id, byId.get(id));
      }
    }
    entries.clear();
    entries.putAll(rebuilt);
  }

  /** Drive {@code onRegister} for the enabled plugins. */
  private void invokeLifecycle() {
    PluginContext ctx;
    for (PluginEntry entry : entries.values()) {
      if (entry.state() == PluginState.FAILED) {
        // Already failed (e.g. duplicate-id) — skip lifecycle.
        continue;
      }
      boolean enabled = isEnabled(entry.id());
      if (!enabled) {
        entry.markDisabled();
        Log.infof("PM1a: plugin '%s' DISABLED (shepard.plugins.%s.enabled=false)", entry.id(), entry.id());
        continue;
      }
      ctx = new DefaultPluginContext(entry, beanManager);
      try {
        entry.manifest().onRegister(ctx);
        entry.markEnabled();
        Log.infof("PM1a: plugin '%s' v%s ENABLED", entry.id(), entry.version());
      } catch (RuntimeException ex) {
        entry.markFailed(ex.getClass().getSimpleName() + ": " + ex.getMessage());
        Log.warnf(ex, "PM1a: plugin '%s' onRegister threw — plugin.discovery.failed; continuing", entry.id());
      }
    }
  }

  /** Invoked on shutdown. Drives {@code onUnregister} in reverse order. */
  void onShutdown(@Observes ShutdownEvent event) {
    List<PluginEntry> reversed = new ArrayList<>(entries.values());
    Collections.reverse(reversed);
    for (PluginEntry entry : reversed) {
      if (entry.state() != PluginState.ENABLED) {
        continue;
      }
      try {
        entry.manifest().onUnregister(new DefaultPluginContext(entry, beanManager));
      } catch (RuntimeException ex) {
        Log.warnf(ex, "PM1a: plugin '%s' onUnregister threw — ignored", entry.id());
      }
    }
    for (URLClassLoader loader : childLoaders) {
      try {
        loader.close();
      } catch (IOException e) {
        Log.debugf(e, "PM1a: error closing %s", loader.getName());
      }
    }
    childLoaders.clear();
  }

  // -----------------------------------------------------------------
  // Public read API — consumed by the admin REST + CLI.
  // -----------------------------------------------------------------

  /** Snapshot list of all discovered plugins, in insertion order. */
  public List<PluginEntry> list() {
    return List.copyOf(entries.values());
  }

  /** Look up a plugin by its declared id. */
  public Optional<PluginEntry> get(String id) {
    return Optional.ofNullable(entries.get(id));
  }

  /**
   * Whether the named plugin is currently enabled. Reads the runtime
   * override if any; otherwise falls through to
   * {@code shepard.plugins.<id>.enabled} from MicroProfile config
   * (defaulting to {@code true} if neither is set).
   */
  public boolean isEnabled(String id) {
    Boolean override = runtimeOverrides.get(id);
    if (override != null) {
      return override;
    }
    String key = String.format(CONFIG_PLUGIN_ENABLED_TPL, id);
    return ConfigProvider.getConfig()
      .getOptionalValue(key, Boolean.class)
      .orElse(Boolean.TRUE);
  }

  /**
   * Flip the runtime override for a plugin. Returns the new effective
   * value.
   *
   * <p>PM1e — the flip is persisted to the
   * {@code :PluginRuntimeOverride} table synchronously within the
   * caller's request so a {@code disable} survives a restart. The
   * write is sparse: if the new value matches the deploy-time
   * default ({@code shepard.plugins.<id>.enabled}, default
   * {@code true}), the row is DELETEd rather than updated to match
   * — the table only ever carries rows that differ from the
   * install default.
   *
   * <p>Does <em>not</em> re-invoke {@code onRegister} on a plugin
   * that was {@code DISABLED} at startup — hot-toggle is PM1b2
   * follow-up work. Flipping to {@code false} also does not
   * invoke {@code onUnregister}; the plugin stays {@code ENABLED}
   * with the override read by callers (e.g. UH1a's own
   * {@code shepard.unhide.enabled} gates the feed at request time).
   *
   * @param id      the plugin id (must be registered)
   * @param enabled the target enabled value
   * @return the new effective value (matches {@code enabled})
   */
  public boolean setEnabled(String id, boolean enabled) {
    return setEnabled(id, enabled, null);
  }

  /**
   * PM1e — overload carrying the actor identifier ({@code sub}
   * claim, or the sentinel {@code "anonymous"}) for the audit-trail
   * mirror on the persisted row. The two-argument
   * {@link #setEnabled(String, boolean)} delegates here with a
   * {@code null} actor so existing call-sites stay source-compatible.
   *
   * @param id        the plugin id (must be registered)
   * @param enabled   the target enabled value
   * @param actorSub  the admin's subject identifier, or {@code null}
   *                  if the caller cannot resolve it (e.g. CDI-less
   *                  test path); persisted to
   *                  {@link PluginRuntimeOverride#getUpdatedBy()} for
   *                  fast "who flipped this most recently" queries.
   * @return the new effective value
   */
  public boolean setEnabled(String id, boolean enabled, String actorSub) {
    if (!entries.containsKey(id)) {
      throw new IllegalArgumentException("No plugin registered with id '" + id + "'");
    }
    runtimeOverrides.put(id, enabled);
    persistOverride(id, enabled, actorSub);
    Log.infof("PM1e: plugin '%s' runtime override persisted with enabled=%s (survives restart)", id, enabled);
    return enabled;
  }

  /**
   * PM1e — write-through helper. If {@code enabled} matches the
   * deploy-time default, deletes the row (keeps the table sparse);
   * otherwise upserts the override.
   *
   * <p>Fail-soft on DAO errors — the in-memory cache has already
   * been mutated, so the override is still in effect for the
   * current JVM; a WARN-level log records the persistence failure.
   * (Tests that don't wire a DAO take the same path.)
   */
  private void persistOverride(String id, boolean enabled, String actorSub) {
    if (overrideDao == null || overrideDao.isUnsatisfied()) {
      Log.debugf("PM1e: PluginRuntimeOverrideDAO not injected — runtime override for '%s' is in-memory only", id);
      return;
    }
    boolean deployDefault = readDeployTimeDefault(id);
    try {
      PluginRuntimeOverrideDAO dao = overrideDao.get();
      if (enabled == deployDefault) {
        // Reset to default → delete the row, free the in-memory
        // entry from the override map so isEnabled() falls through
        // to the deploy-time default again.
        boolean removed = dao.deleteByPluginId(id);
        runtimeOverrides.remove(id);
        if (removed) {
          Log.infof("PM1e: plugin '%s' override reset to deploy-time default (enabled=%s) — row deleted", id, enabled);
        } else {
          Log.debugf("PM1e: plugin '%s' already at deploy-time default — no-op delete", id);
        }
        return;
      }
      Optional<PluginRuntimeOverride> existing = dao.findByPluginId(id);
      PluginRuntimeOverride row = existing.orElseGet(PluginRuntimeOverride::new);
      row.setPluginId(id);
      row.setEnabled(enabled);
      row.setUpdatedAt(java.time.Instant.now());
      row.setUpdatedBy(actorSub == null || actorSub.isBlank() ? "anonymous" : actorSub);
      dao.save(row);
    } catch (RuntimeException ex) {
      // In-memory cache is already mutated; the override is in
      // effect for the lifetime of the JVM. Log loudly so the
      // operator can re-flip after the DB recovers.
      Log.warnf(
        ex,
        "PM1e: could not persist runtime override for '%s' (enabled=%s) — in-memory state is current; row will not survive restart until next successful PATCH",
        id,
        enabled
      );
    }
  }

  /**
   * Read the deploy-time default for a plugin's enabled flag —
   * the {@code shepard.plugins.<id>.enabled} key, defaulting to
   * {@code true} when absent (the PM1a posture).
   */
  private boolean readDeployTimeDefault(String id) {
    String key = String.format(CONFIG_PLUGIN_ENABLED_TPL, id);
    return ConfigProvider.getConfig()
      .getOptionalValue(key, Boolean.class)
      .orElse(Boolean.TRUE);
  }

  // -----------------------------------------------------------------
  // Internals.
  // -----------------------------------------------------------------

  private Path resolvePluginDir() {
    Path p = Paths.get(pluginsDir);
    if (!Files.exists(p)) {
      Log.infof("PM1a: plugin dir '%s' does not exist — skipping JAR discovery (set shepard.plugins.dir to override)", p);
      return null;
    }
    if (!Files.isDirectory(p)) {
      Log.warnf("PM1a: plugin dir '%s' is not a directory — skipping", p);
      return null;
    }
    return p;
  }

  private long countByState(PluginState state) {
    return entries.values().stream().filter(e -> e.state() == state).count();
  }

  /** Default {@link PluginContext} bound to a specific entry. */
  private static final class DefaultPluginContext implements PluginContext {

    private final PluginEntry entry;
    private final BeanManager beanManager;

    DefaultPluginContext(PluginEntry entry, BeanManager beanManager) {
      this.entry = entry;
      this.beanManager = beanManager;
    }

    @Override
    public String pluginId() {
      return entry.id();
    }

    @Override
    public String pluginVersion() {
      return entry.version();
    }

    @Override
    public BeanManager beanManager() {
      return beanManager;
    }
  }
}
