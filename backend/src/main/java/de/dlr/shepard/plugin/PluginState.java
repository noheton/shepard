package de.dlr.shepard.plugin;

/**
 * PM1a — lifecycle states a {@link PluginManifest}-equipped plugin
 * passes through.
 *
 * <pre>
 *           +-------------+
 *           |  DISCOVERED |  manifest loaded, toggle not yet checked
 *           +------+------+
 *                  |
 *      toggle=true | toggle=false
 *           +------+------+--------------+--------------+
 *           |             |              |              |
 *      +----v----+   +----v----+    +----v----+   +----v----+
 *      | ENABLED |   | DISABLED|    |  FAILED |   |DEGRADED |
 *      +---------+   +---------+    +---------+   +---------+
 *      onRegister    onRegister     onRegister    runtime-only
 *      succeeded      skipped       threw         JAR without CDI
 * </pre>
 *
 * <p>The lifecycle is one-way in Phase 1 — flipping the toggle at
 * runtime via {@code PATCH /v2/admin/plugins/{id}} updates the
 * runtime override but does not re-invoke {@code onRegister} on a
 * plugin that was {@code DISABLED} at startup. PM1b adds hot-toggle
 * support.
 *
 * <p>PM1b2 adds {@link #DEGRADED} — surfaces the case where a plugin
 * JAR is dropped into {@code /deployments/plugins/} at runtime but
 * isn't on the backend's compile-time classpath, so Quarkus's
 * build-time CDI scanner never indexed its beans. The plugin's
 * {@link PluginManifest} lifecycle hooks still run, but its
 * {@code @Path} resources / {@code @ApplicationScoped} beans aren't
 * wired into Quarkus. This is a "detected and reported"
 * intermediate state — operators upgrade the JAR onto the build
 * classpath (per the {@code with-plugins} Maven profile) to lift it
 * to {@link #ENABLED}. The PM1b3 design doc
 * ({@code aidocs/69-runtime-plugin-cdi.md}) tracks the path to true
 * runtime drop-in CDI integration.
 */
public enum PluginState {
  /** Manifest read from the JAR but the toggle hasn't been consulted. */
  DISCOVERED,
  /** Toggle was true at startup and {@code onRegister} succeeded. */
  ENABLED,
  /** Toggle was false at startup; manifest is loaded but inert. */
  DISABLED,
  /** {@code onRegister} threw; the plugin is loaded but inert. */
  FAILED,
  /**
   * Runtime-discovered JAR without build-time CDI indexing — the
   * manifest is loaded and its lifecycle hooks ran, but the plugin's
   * REST resources / CDI beans aren't active. Operators see the row
   * in {@code GET /v2/admin/plugins} with this state so they know
   * the plugin needs a build-classpath rebuild (PM1b2 stop-gap;
   * PM1b3 will lift this to {@link #ENABLED} via runtime CDI
   * integration).
   */
  DEGRADED,
}
