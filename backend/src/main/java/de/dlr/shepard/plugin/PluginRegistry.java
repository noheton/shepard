package de.dlr.shepard.plugin;

import io.quarkus.logging.Log;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
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
 */
@ApplicationScoped
public class PluginRegistry {

  /** Default plugin directory, relative to the JVM working dir. */
  public static final String DEFAULT_PLUGIN_DIR = "backend/plugins";

  /** Config key for the plugin directory (deploy-time only). */
  public static final String CONFIG_PLUGINS_DIR = "shepard.plugins.dir";

  /** Per-plugin runtime-toggle key template — fed plugin id. */
  public static final String CONFIG_PLUGIN_ENABLED_TPL = "shepard.plugins.%s.enabled";

  @Inject
  BeanManager beanManager;

  @ConfigProperty(name = CONFIG_PLUGINS_DIR, defaultValue = DEFAULT_PLUGIN_DIR)
  String pluginsDir;

  /**
   * Insertion-ordered map keyed by plugin id. Reads happen on the
   * caller's thread; writes happen only from {@link #onStart} +
   * {@link #onShutdown} on the Quarkus startup / shutdown thread.
   * Marked volatile-by-LinkedHashMap-replacement on startup, then
   * frozen as an unmodifiable view.
   */
  private final Map<String, PluginEntry> entries = new LinkedHashMap<>();

  /**
   * Runtime override of the per-plugin enabled flag — keyed by
   * plugin id. {@code Optional.empty()} = no override, fall through
   * to the config-property value. Volatile read; mutations happen
   * only via {@link #setEnabled(String, boolean)} (the admin REST /
   * CLI surface).
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
   */
  void discover() {
    Log.info("PM1a: plugin discovery starting");
    discoverClasspathPlugins();
    Path resolved = resolvePluginDir();
    if (resolved != null) {
      discoverJarPlugins(resolved);
    }
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
    try {
      ServiceLoader<PluginManifest> loader = ServiceLoader.load(PluginManifest.class, childLoader);
      boolean any = false;
      for (PluginManifest manifest : loader) {
        any = true;
        register(new PluginEntry(manifest, realJar));
      }
      if (!any) {
        Log.warnf("PM1a: %s carries no META-INF/services/PluginManifest — ignored", realJar.getFileName());
      }
    } catch (ServiceConfigurationError e) {
      // plugin.discovery.failed — record but continue with other JARs.
      Log.warnf(e, "PM1a: ServiceLoader failed for %s — plugin.discovery.failed", realJar.getFileName());
    }
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
   * value. The flip is in-memory only — surviving across restart
   * requires either editing {@code application.properties} or
   * persisting the override (PM1c).
   *
   * <p>Does <em>not</em> re-invoke {@code onRegister} on a plugin
   * that was {@code DISABLED} at startup — hot-toggle is PM1b
   * follow-up work. Flipping to {@code false} also does not
   * invoke {@code onUnregister}; the plugin stays {@code ENABLED}
   * with the override read by callers (e.g. UH1a's own
   * {@code shepard.unhide.enabled} gates the feed at request time).
   */
  public boolean setEnabled(String id, boolean enabled) {
    if (!entries.containsKey(id)) {
      throw new IllegalArgumentException("No plugin registered with id '" + id + "'");
    }
    runtimeOverrides.put(id, enabled);
    Log.infof("PM1a: plugin '%s' runtime override set to enabled=%s (no restart performed)", id, enabled);
    return enabled;
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
