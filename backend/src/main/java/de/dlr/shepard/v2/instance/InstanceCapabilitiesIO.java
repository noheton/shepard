package de.dlr.shepard.v2.instance;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Wire shape for {@code GET /v2/instance/capabilities}.
 *
 * <p>Carries the set of plugins whose runtime state is {@code ENABLED}
 * on this instance, with enough metadata (id, version, display title)
 * for the frontend to gate plugin-specific UI surfaces AND show an
 * "Active plugins" list on the About → Version page.
 *
 * <p>Does not expose the full admin-level registry (disabled/failed
 * plugins, JAR paths, failure messages) — that stays on
 * {@code GET /v2/admin/plugins}.
 */
public record InstanceCapabilitiesIO(
  @Schema(description = "Plugins whose state is ENABLED on this instance.")
  List<PluginInfo> plugins
) {

  /** Minimal public descriptor for one enabled plugin. */
  public record PluginInfo(
    @Schema(description = "Plugin id (e.g. \"video\", \"git\").")
    String id,
    @Schema(description = "Plugin version string (e.g. \"1.0.0-SNAPSHOT\").")
    String version,
    @Schema(description = "Human-readable display name; falls back to id when blank.")
    String title
  ) {}
}
