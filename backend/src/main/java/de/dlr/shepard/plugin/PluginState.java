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
 *           +------+------+--------------+
 *           |             |              |
 *      +----v----+   +----v----+    +----v----+
 *      | ENABLED |   | DISABLED|    |  FAILED |
 *      +---------+   +---------+    +---------+
 *      onRegister    onRegister     onRegister
 *      succeeded      skipped       threw
 * </pre>
 *
 * <p>The lifecycle is one-way in Phase 1 — flipping the toggle at
 * runtime via {@code PATCH /v2/admin/plugins/{id}} updates the
 * runtime override but does not re-invoke {@code onRegister} on a
 * plugin that was {@code DISABLED} at startup. PM1b adds hot-toggle
 * support.
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
}
