package de.dlr.shepard.v2.instance;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Wire shape for {@code GET /v2/instance/capabilities}.
 *
 * <p>Carries the set of plugin IDs whose runtime state is
 * {@code ENABLED} on this instance. The frontend uses this to
 * gate plugin-specific UI surfaces (e.g. the Helmholtz Unhide
 * "Publishing" panel) without exposing the full admin-level
 * plugin registry.
 */
public record InstanceCapabilitiesIO(
  @Schema(description = "Plugin IDs whose state is ENABLED on this instance.")
  List<String> enabledPlugins
) {}
