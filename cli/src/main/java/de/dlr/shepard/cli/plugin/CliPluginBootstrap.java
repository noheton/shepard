package de.dlr.shepard.cli.plugin;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.ServiceLoader;
import picocli.CommandLine;

/**
 * PM1d — discovers {@link AdminCliCommandProvider} implementations
 * at CLI startup and registers each one's {@code @Command} class as
 * a top-level subcommand on the {@code shepard-admin} root.
 *
 * <p>Mirrors the shape of the backend's
 * {@code de.dlr.shepard.plugin.PluginRegistry} (PM1a) so an operator
 * with a {@code shepard-plugin-<feature>.jar} drop-in gets both
 * sides — backend wiring AND CLI verbs — from the same artefact
 * dropped into the corresponding plugin directory.
 *
 * <p>Discovery sources (both run on every CLI invocation):
 *
 * <ol>
 *   <li><b>Build classpath.</b> {@link ServiceLoader#load(Class)} over
 *       the current thread's context classloader. Catches plugins
 *       that ship the CLI module as a Maven {@code <dependency>} of
 *       the CLI assembly (uncommon in production — the shaded
 *       {@code shepard-admin.jar} is the typical distribution — but
 *       the path is useful for local development and integration
 *       tests where the plugin's classes are already on the
 *       classpath).</li>
 *   <li><b>Drop-in JARs.</b> Walks the configured plugin directory
 *       for {@code *.jar} files. Each JAR gets a child
 *       {@link URLClassLoader} parented to the CLI's classloader so
 *       plugin classes can see the CLI's SPI types and Picocli, while
 *       a plugin's transitive deps stay isolated from each other.
 *       Each child loader is {@code ServiceLoader}-scanned for
 *       {@link AdminCliCommandProvider}.</li>
 * </ol>
 *
 * <p>Configuration:
 *
 * <ul>
 *   <li>{@link #SYS_PROP_PLUGINS_DIR} ({@value #SYS_PROP_PLUGINS_DIR})
 *       — JVM system property that overrides the plugin directory.
 *       Wins over the env var.</li>
 *   <li>{@link #ENV_PLUGINS_DIR} ({@value #ENV_PLUGINS_DIR})
 *       — environment variable equivalent. Same key as the backend's
 *       {@code SHEPARD_PLUGINS_DIR} so operators with custom plugin
 *       installs can point both sides at the same directory.</li>
 *   <li>Default: {@value #DEFAULT_PLUGINS_DIR} (matches the
 *       container image's {@code /deployments/plugins} layout from
 *       PM1a phase 2). If the directory doesn't exist, the JAR walk
 *       silently no-ops — local development against a fresh checkout
 *       just relies on the classpath path.</li>
 * </ul>
 *
 * <p>Hardening (mirrors PM1a):
 *
 * <ul>
 *   <li>Each JAR's real path must lie inside the resolved plugin
 *       directory's real path. Symlinks pointing outside the
 *       directory get rejected with a WARN and skipped.</li>
 *   <li>Per-provider {@code addSubcommand} call is wrapped in
 *       try/catch — a plugin JAR with a broken provider class logs
 *       to stderr but doesn't crash the CLI. An operator can still
 *       run {@code shepard-admin features list} when one plugin's
 *       CLI is broken.</li>
 *   <li>{@link ServiceConfigurationError} from one malformed service
 *       file does not stop discovery of other plugins.</li>
 * </ul>
 *
 * <p>Stderr logging shape: this class is package-private from the
 * operator's perspective — production-mode output stays clean.
 * Discovery warnings go to stderr with a {@code shepard-admin:}
 * prefix so they don't pollute machine-readable stdout pipes.
 */
public final class CliPluginBootstrap {

  /** System property name that overrides the plugin directory. */
  public static final String SYS_PROP_PLUGINS_DIR = "shepard.plugins.dir";

  /** Environment variable name that overrides the plugin directory. */
  public static final String ENV_PLUGINS_DIR = "SHEPARD_PLUGINS_DIR";

  /** Default plugin directory if neither override is set. */
  public static final String DEFAULT_PLUGINS_DIR = "cli/plugins";

  /**
   * Container-image default — set by the Dockerfile alongside
   * {@code /deployments/plugins} so the CLI shipped inside the
   * shepard image discovers the same JAR directory the backend uses.
   * Surfaced here as a documented constant so the Dockerfile's
   * env-var pin and the CLI's classpath default stay in sync.
   */
  public static final String CONTAINER_PLUGINS_DIR = "/deployments/plugins";

  /** Held so the CLI can close them on JVM shutdown if desired. */
  private final List<URLClassLoader> childLoaders = new ArrayList<>();

  /** Per-invocation list of warnings — surfaced by {@link #warnings()}. */
  private final List<String> warnings = new ArrayList<>();

  /** Resolved plugin directory the bootstrap walked (null if none). */
  private Path resolvedDir;

  /**
   * Discover and register every {@link AdminCliCommandProvider} on
   * the classpath and in the configured plugin directory. Idempotent:
   * if {@code commandLine} already has a subcommand with the
   * candidate name, the duplicate is logged + skipped (defends
   * against a third-party plugin colliding with a core verb).
   *
   * @param commandLine the {@code shepard-admin} root {@link CommandLine}
   * @param env         env-var lookup function — {@code System::getenv}
   *                    in production, a fixture in tests
   * @return the same {@code commandLine} for chaining
   */
  public CommandLine discoverInto(CommandLine commandLine, java.util.function.Function<String, String> env) {
    if (commandLine == null) {
      throw new IllegalArgumentException("commandLine must not be null");
    }
    java.util.function.Function<String, String> envFn = env == null ? System::getenv : env;

    // Pass 1: classpath ServiceLoader (uncommon in production but
    // the cheap path for local-dev + integration tests).
    discoverClasspath(commandLine);

    // Pass 2: JAR walk over the configured plugin directory.
    Path dir = resolvePluginDir(envFn);
    if (dir != null) {
      resolvedDir = dir;
      discoverJars(commandLine, dir);
    }
    return commandLine;
  }

  /**
   * Convenience overload — uses {@link System#getenv(String)} for the
   * environment lookup.
   */
  public CommandLine discoverInto(CommandLine commandLine) {
    return discoverInto(commandLine, System::getenv);
  }

  /**
   * Warnings emitted during the most recent
   * {@link #discoverInto(CommandLine, java.util.function.Function)}
   * call. Empty when discovery was clean. Tests assert on this
   * collection to verify the fail-soft paths.
   */
  public List<String> warnings() {
    return Collections.unmodifiableList(warnings);
  }

  /**
   * The plugin directory the last discovery resolved to, or
   * {@code null} if none was found (default missing + no override
   * set, or override pointed at a non-existent path).
   */
  public Path resolvedDir() {
    return resolvedDir;
  }

  /**
   * Child class loaders the bootstrap opened. Held so they can be
   * closed on JVM shutdown if a caller wants. The CLI's main path
   * doesn't bother closing them — the JVM exit cleans up the file
   * handles — but tests may close to release the JAR locks.
   */
  public List<URLClassLoader> childLoaders() {
    return Collections.unmodifiableList(childLoaders);
  }

  // -----------------------------------------------------------------
  // Internals.
  // -----------------------------------------------------------------

  private void discoverClasspath(CommandLine commandLine) {
    try {
      ServiceLoader<AdminCliCommandProvider> loader = ServiceLoader.load(
        AdminCliCommandProvider.class,
        Thread.currentThread().getContextClassLoader()
      );
      for (AdminCliCommandProvider provider : loader) {
        registerProvider(commandLine, provider, "build classpath");
      }
    } catch (java.util.ServiceConfigurationError e) {
      warnf("classpath ServiceLoader failed for AdminCliCommandProvider: %s", e.getMessage());
    }
  }

  private void discoverJars(CommandLine commandLine, Path pluginDir) {
    Path realDir;
    try {
      realDir = pluginDir.toRealPath();
    } catch (IOException e) {
      warnf("plugin dir %s could not be resolved (%s) — skipping JAR discovery", pluginDir, e.getMessage());
      return;
    }
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(realDir, "*.jar")) {
      // Sort by filename so discovery order is deterministic across
      // runs — tests that assert on the order of subcommands need
      // this; operators get a stable {@code shepard-admin --help}
      // listing.
      List<Path> jars = new ArrayList<>();
      stream.forEach(jars::add);
      jars.sort((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()));
      for (Path jar : jars) {
        loadJar(commandLine, jar, realDir);
      }
    } catch (IOException e) {
      warnf("error walking plugin dir %s: %s — JAR discovery incomplete", realDir, e.getMessage());
    }
  }

  private void loadJar(CommandLine commandLine, Path jar, Path expectedParent) {
    Path realJar;
    try {
      realJar = jar.toRealPath();
    } catch (IOException e) {
      warnf("could not resolve %s (%s) — skipping", jar, e.getMessage());
      return;
    }
    if (!realJar.startsWith(expectedParent)) {
      // Defence in depth — DirectoryStream already constrained the
      // walk, but a malicious symlink within the dir could point at
      // an arbitrary JAR. Reject the off-tree case.
      warnf("plugin JAR %s resolves outside %s — skipping for safety", jar, expectedParent);
      return;
    }
    URL jarUrl;
    try {
      jarUrl = realJar.toUri().toURL();
    } catch (MalformedURLException e) {
      warnf("%s cannot be expressed as a URL (%s) — skipping", realJar, e.getMessage());
      return;
    }
    URLClassLoader child = new URLClassLoader(
      "shepard-admin-plugin:" + realJar.getFileName(),
      new URL[] { jarUrl },
      Thread.currentThread().getContextClassLoader()
    );
    childLoaders.add(child);

    List<String> implClasses = readServiceImplsFromJar(realJar);
    if (implClasses.isEmpty()) {
      // Some JARs carry only the backend manifest (no CLI side) —
      // that's fine; not every plugin has CLI verbs. Don't warn.
      return;
    }
    for (String impl : implClasses) {
      try {
        Class<?> cls = Class.forName(impl, true, child);
        if (!AdminCliCommandProvider.class.isAssignableFrom(cls)) {
          warnf("%s names %s in META-INF/services/AdminCliCommandProvider but it's not a provider — skipping",
            realJar.getFileName(), impl);
          continue;
        }
        AdminCliCommandProvider provider = (AdminCliCommandProvider) cls.getDeclaredConstructor().newInstance();
        registerProvider(commandLine, provider, realJar.getFileName().toString());
      } catch (ReflectiveOperationException | LinkageError e) {
        warnf("failed to instantiate %s from %s (%s) — plugin CLI discovery failed for that provider",
          impl, realJar.getFileName(), e.getMessage());
      }
    }
  }

  /**
   * Read service implementation classnames directly from the JAR
   * (not via {@link ServiceLoader}, because that would also walk the
   * parent classloader and re-discover classpath entries that the
   * Phase 1 pass already handled).
   */
  static List<String> readServiceImplsFromJar(Path jar) {
    List<String> result = new ArrayList<>();
    try (java.util.jar.JarFile jf = new java.util.jar.JarFile(jar.toFile())) {
      java.util.jar.JarEntry entry = jf.getJarEntry(
        "META-INF/services/" + AdminCliCommandProvider.class.getName()
      );
      if (entry == null) {
        return result;
      }
      try (
        java.io.BufferedReader r = new java.io.BufferedReader(
          new java.io.InputStreamReader(jf.getInputStream(entry), java.nio.charset.StandardCharsets.UTF_8)
        )
      ) {
        String line;
        while ((line = r.readLine()) != null) {
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
      // Fall through — caller treats empty result as "no contribution".
    }
    return result;
  }

  /**
   * Wire {@code provider}'s command class into {@code commandLine}.
   * Catches everything: a third-party plugin's broken constructor /
   * missing {@code @Command} annotation must not crash the CLI.
   */
  private void registerProvider(CommandLine commandLine, AdminCliCommandProvider provider, String source) {
    Class<?> cls;
    try {
      cls = provider.commandClass();
    } catch (RuntimeException e) {
      warnf("provider %s threw from commandClass() (%s) — skipping", provider.getClass().getName(), e.getMessage());
      return;
    }
    if (cls == null) {
      warnf("provider %s returned null from commandClass() — skipping", provider.getClass().getName());
      return;
    }
    Object instance;
    try {
      instance = cls.getDeclaredConstructor().newInstance();
    } catch (ReflectiveOperationException e) {
      warnf("provider %s's command class %s does not have a usable no-arg constructor (%s) — skipping",
        provider.getClass().getName(), cls.getName(), e.getMessage());
      return;
    }
    // Discover the @Command name so a duplicate against an existing
    // subcommand is handled with a clear warning rather than a
    // Picocli exception.
    String name = extractCommandName(cls);
    if (name != null && commandLine.getSubcommands().containsKey(name)) {
      warnf("subcommand '%s' from %s shadows an existing subcommand — skipping (source=%s)",
        name, provider.getClass().getName(), source);
      return;
    }
    try {
      commandLine.addSubcommand(instance);
    } catch (RuntimeException e) {
      warnf("could not register subcommand from %s (%s) — skipping (source=%s)",
        provider.getClass().getName(), e.getMessage(), source);
    }
  }

  /**
   * Picocli's {@code @Command(name = "<default>")} sentinel — the
   * value Picocli substitutes when {@code name} is omitted on the
   * annotation. We treat this as "no name" so an anonymous parent
   * command class isn't mistaken for a real subcommand name.
   */
  private static final String PICOCLI_DEFAULT_COMMAND_NAME = "<main class>";

  private static String extractCommandName(Class<?> cls) {
    picocli.CommandLine.Command annotation = cls.getAnnotation(picocli.CommandLine.Command.class);
    if (annotation == null) {
      return null;
    }
    String name = annotation.name();
    if (name == null || name.isBlank() || PICOCLI_DEFAULT_COMMAND_NAME.equals(name)) {
      return null;
    }
    return name;
  }

  private Path resolvePluginDir(java.util.function.Function<String, String> envFn) {
    // System property wins, then env var, then default.
    String fromSysProp = System.getProperty(SYS_PROP_PLUGINS_DIR);
    if (fromSysProp != null && !fromSysProp.isBlank()) {
      return checkDir(Paths.get(fromSysProp));
    }
    String fromEnv = envFn.apply(ENV_PLUGINS_DIR);
    if (fromEnv != null && !fromEnv.isBlank()) {
      return checkDir(Paths.get(fromEnv));
    }
    // No override — try the container image's default first (so the
    // shaded CLI inside a shepard image finds its drop-ins), then
    // the local-dev default.
    Path container = Paths.get(CONTAINER_PLUGINS_DIR);
    if (Files.isDirectory(container)) {
      return container;
    }
    Path local = Paths.get(DEFAULT_PLUGINS_DIR);
    if (Files.isDirectory(local)) {
      return local;
    }
    return null;
  }

  private Path checkDir(Path p) {
    if (!Files.exists(p)) {
      warnf("plugin dir %s does not exist — skipping JAR discovery", p);
      return null;
    }
    if (!Files.isDirectory(p)) {
      warnf("plugin dir %s is not a directory — skipping", p);
      return null;
    }
    return p;
  }

  private void warnf(String fmt, Object... args) {
    String msg = String.format(Locale.ROOT, fmt, args);
    warnings.add(msg);
    System.err.println("shepard-admin: " + msg);
  }

  /**
   * Collected for assertions — the {@link Set} of subcommand names
   * the discovery added on top of whatever {@code commandLine}
   * already had.
   */
  static Set<String> subcommandNames(CommandLine commandLine) {
    return new LinkedHashSet<>(commandLine.getSubcommands().keySet());
  }
}
