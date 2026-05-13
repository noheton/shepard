package de.dlr.shepard.plugin;

/**
 * PM1a — the SPI contract every shepard plugin implements.
 *
 * <p>Each plugin JAR dropped into {@code backend/plugins/} (or
 * declared as a Maven dependency in Phase 1, see ADR-0024) ships a
 * {@code META-INF/services/de.dlr.shepard.plugin.PluginManifest}
 * file naming its single implementation class.
 *
 * <p>Discovery shape (see {@link PluginRegistry}):
 * <ol>
 *   <li>At startup, after {@code MigrationsRunner.apply()} and before
 *       the REST endpoints come up, the backend walks
 *       {@code shepard.plugins.dir} (default {@code backend/plugins})
 *       for {@code *.jar} files.</li>
 *   <li>Each JAR gets a child {@link java.net.URLClassLoader} whose
 *       parent is the backend's classloader — isolation per ADR-0023
 *       ("two plugins with conflicting transitive deps").</li>
 *   <li>{@link java.util.ServiceLoader#load(Class, ClassLoader)}
 *       discovers each plugin's {@code PluginManifest}.</li>
 *   <li>If the runtime toggle {@code shepard.plugins.<id>.enabled} is
 *       true (default), {@link #onRegister(PluginContext)} is invoked.
 *       Otherwise the plugin stays in the {@link PluginState#DISABLED}
 *       state — discovered but inert.</li>
 *   <li>On JVM shutdown, {@link #onUnregister(PluginContext)} is
 *       invoked in reverse-registration order.</li>
 * </ol>
 *
 * <p>An implementation throwing during {@link #onRegister} is
 * <strong>fail-soft</strong>: the plugin is marked
 * {@link PluginState#FAILED}, the cause is logged at WARN, and other
 * plugins continue. A failed plugin's REST resources / CDI beans do
 * not become active.
 *
 * <p>ADR-0023 — JAR drop-in via {@code ServiceLoader}.
 * ADR-0024 — Quarkus build-time CDI scanning constraint (Phase 1
 * plugins with CDI beans ship as build-classpath dependencies in
 * addition to the JAR drop-in; the manifest itself is always
 * discovered through {@code ServiceLoader}).
 */
public interface PluginManifest {
  /**
   * Stable plugin identifier — lowercase, hyphen-separated. Matches
   * the {@code shepard.plugins.<id>.enabled} config key and the
   * {@code shepard-plugin-<id>} Maven artefact id. Example:
   * {@code "unhide"}, {@code "hdf-hsds"}, {@code "video"}.
   *
   * <p>Two plugins claiming the same id collide; the second is
   * rejected with {@link PluginState#FAILED} and a
   * {@code plugin.discovery.failed} log entry.
   */
  String id();

  /**
   * Plugin version, semver. Plugin-side — independent of the shepard
   * core version. Surfaced in {@code GET /v2/admin/plugins} and in
   * the startup INFO log line.
   */
  String version();

  /**
   * Semver range of the shepard core this plugin is known compatible
   * with. Example: {@code ">=5.2.0,<6"} reads as "needs at least
   * 5.2.0, breaks at 6.0.0". The registry does not yet enforce the
   * range in Phase 1 — it only surfaces the declared value to the
   * admin REST. PM1b adds the actual compatibility check.
   */
  String shepardCompatibility();

  /**
   * Lifecycle hook invoked after the JAR's classes are loaded and
   * the plugin's {@code shepard.plugins.<id>.enabled} toggle has been
   * read (and is {@code true}). The plugin uses {@code ctx} to wire
   * itself into registries owned by core — e.g. a future
   * {@code PayloadKindRegistry}, the existing {@code FeatureToggleRegistry},
   * any CDI bean lookups it needs.
   *
   * <p>Implementations <strong>must</strong> be idempotent — the
   * registry guarantees a single {@code onRegister} per JVM, but a
   * future hot-reload story (PM1c) will re-invoke it.
   *
   * <p>An exception escaping this method is caught by the registry,
   * the plugin is marked failed, and other plugins continue.
   *
   * @param ctx read-only context handle into core services.
   */
  default void onRegister(PluginContext ctx) {
    // no-op default — plugins with only metadata / classpath-side
    // CDI bean discovery (the UH1a Phase-1 shape per ADR-0024) need
    // no active onRegister wiring.
  }

  /**
   * Lifecycle hook invoked on JVM shutdown in reverse-registration
   * order. Plugins that bind external resources (sockets, schedulers,
   * file handles) should release them here.
   *
   * <p>Phase 1 plugins backed by Quarkus CDI shutdown hooks
   * (e.g. {@code @Observes ShutdownEvent}) need no explicit
   * {@code onUnregister} — the default no-op suffices.
   */
  default void onUnregister(PluginContext ctx) {
    // no-op default — see onRegister javadoc.
  }
}
