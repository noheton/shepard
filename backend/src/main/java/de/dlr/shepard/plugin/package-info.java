/**
 * PM1a — plugin SPI baseline.
 *
 * <p>This package is the structural seam separating shepard's core
 * from the {@code shepard-plugin-*} ecosystem (ADR-0023 / aidocs/47 §2.5).
 * Every plugin compiles against {@link de.dlr.shepard.plugin.PluginManifest};
 * {@link de.dlr.shepard.plugin.PluginRegistry} discovers them at
 * startup and drives the {@code onRegister} / {@code onUnregister}
 * lifecycle.
 *
 * <p>Operator-facing surfaces (admin REST + CLI) live in
 * {@code de.dlr.shepard.v2.admin.resources} and the {@code cli/}
 * module respectively; both consume {@link PluginRegistry#list()}
 * and {@link PluginRegistry#setEnabled(String, boolean)}.
 *
 * @see <a href="https://github.com/noheton/shepard/blob/main/aidocs/63-architecture-decision-log.md">ADR-0023</a>
 * @see <a href="https://github.com/noheton/shepard/blob/main/aidocs/47-dev-experience-and-plugin-system.md">aidocs/47 §2.5</a>
 */
package de.dlr.shepard.plugin;
