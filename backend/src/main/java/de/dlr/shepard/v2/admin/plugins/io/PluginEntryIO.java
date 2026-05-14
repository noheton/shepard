package de.dlr.shepard.v2.admin.plugins.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.plugin.PluginDependency;
import de.dlr.shepard.plugin.PluginEntry;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * PM1b — wire shape for a single plugin row returned by
 * {@code GET /v2/admin/plugins} and {@code PATCH /v2/admin/plugins/{id}}.
 *
 * <p>Deliberately strips internals from the underlying
 * {@link de.dlr.shepard.plugin.PluginEntry} — no
 * {@link java.net.URLClassLoader} reference, no live
 * {@link de.dlr.shepard.plugin.PluginManifest} pointer. The operator
 * sees stable scalar fields:
 *
 * <ul>
 *   <li>{@code id} — the plugin's declared identifier (matches the
 *       {@code shepard.plugins.<id>.enabled} config key).</li>
 *   <li>{@code version} — plugin-side semver, independent of shepard
 *       core.</li>
 *   <li>{@code shepardCompatibility} — the semver range the plugin
 *       claims compatibility with.</li>
 *   <li>{@code state} — {@code DISCOVERED} / {@code ENABLED} /
 *       {@code DISABLED} / {@code FAILED} (the lifecycle outcome).</li>
 *   <li>{@code enabled} — the current effective toggle (runtime
 *       override wins, falling through to
 *       {@code shepard.plugins.<id>.enabled} from config). May differ
 *       from {@code state} immediately after a PATCH — see PM1a's
 *       runbook on hot-toggle vs. on-startup wiring.</li>
 *   <li>{@code sourcePath} — file path of the JAR the plugin loaded
 *       from, or {@code null} for build-classpath plugins (ADR-0024
 *       Phase 1 shape).</li>
 *   <li>{@code registeredAt} — wall-clock instant the registry first
 *       observed the plugin.</li>
 *   <li>{@code failureMessage} — non-null only when {@code state ==
 *       FAILED}; carries the exception summary that caused the
 *       plugin to fail discovery / lifecycle.</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "PluginEntry", description = "Registry row for a single discovered plugin.")
public record PluginEntryIO(
  @Schema(required = true, description = "Stable plugin identifier (matches shepard.plugins.<id>.enabled).") String id,
  @Schema(required = true, description = "Plugin-side semver version.") String version,
  @Schema(description = "Semver range the plugin claims compatibility with.") String shepardCompatibility,
  @Schema(required = true, description = "Lifecycle state: DISCOVERED / ENABLED / DISABLED / FAILED / DEGRADED (PM1b2).") String state,
  @Schema(required = true, description = "Effective enabled toggle (runtime override or config fallback).") boolean enabled,
  @Schema(description = "Path of the source JAR (null for build-classpath plugins).") String sourcePath,
  @Schema(required = true, description = "Wall-clock instant the registry first observed the plugin.") Date registeredAt,
  @Schema(description = "Failure reason — populated only when state == FAILED.") String failureMessage,
  @Schema(description = "PM1c — human-readable display name (default: id).") String title,
  @Schema(description = "PM1c — one-paragraph operator-facing description.") String description,
  @Schema(description = "PM1c — plugin homepage URL.") String homepageUrl,
  @Schema(description = "PM1c — plugin source-code repository URL.") String repositoryUrl,
  @Schema(description = "PM1c — SPDX licence identifier.") String licence,
  @Schema(description = "PM1c — required sibling plugins.") List<PluginDependencyIO> dependencies
) {
  /**
   * Project a {@link PluginEntry} onto the wire shape. {@code enabled}
   * is captured from the registry's effective view so the response
   * reflects the runtime override that PM1b's PATCH may have set.
   */
  public static PluginEntryIO from(PluginEntry entry, boolean enabled) {
    List<PluginDependencyIO> depIO = new ArrayList<>();
    for (PluginDependency dep : entry.dependencies()) {
      depIO.add(new PluginDependencyIO(dep.pluginId(), dep.versionConstraint()));
    }
    return new PluginEntryIO(
      entry.id(),
      entry.version(),
      entry.shepardCompatibility(),
      entry.state().name(),
      enabled,
      entry.jarPath() == null ? null : entry.jarPath().toString(),
      entry.registeredAt() == null ? null : Date.from(entry.registeredAt()),
      entry.failureMessage(),
      nullIfBlank(entry.title()),
      nullIfBlank(entry.description()),
      entry.homepageUrl().map(uri -> uri.toString()).orElse(null),
      entry.repositoryUrl().map(uri -> uri.toString()).orElse(null),
      nullIfBlank(entry.licence()),
      depIO
    );
  }

  /**
   * Collapses empty strings to {@code null} so the
   * {@link JsonInclude.Include#NON_NULL} default omits them from
   * the wire shape — clients that don't know about the new fields
   * stay agnostic, and operators see real values instead of empty
   * keys.
   */
  private static String nullIfBlank(String s) {
    return s == null || s.isBlank() ? null : s;
  }
}
