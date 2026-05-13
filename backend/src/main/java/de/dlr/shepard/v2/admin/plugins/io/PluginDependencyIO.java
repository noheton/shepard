package de.dlr.shepard.v2.admin.plugins.io;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * PM1c — wire shape for a single declared plugin dependency,
 * surfaced inside {@link PluginEntryIO#dependencies()}.
 *
 * <p>Mirrors {@link de.dlr.shepard.plugin.PluginDependency} (the SPI
 * record) at the JSON boundary so the admin REST schema doesn't
 * leak the internal type's package. Two scalar strings — operator
 * reading {@code GET /v2/admin/plugins} sees exactly what the
 * dependent plugin declared.
 */
@Schema(name = "PluginDependency", description = "A declared plugin dependency (pluginId + versionConstraint).")
public record PluginDependencyIO(
  @Schema(required = true, description = "Sibling plugin id this plugin depends on.") String pluginId,
  @Schema(description = "Maven-style version range (e.g. [1.0,2.0), [1.5,), 1.0.0).") String versionConstraint
) {}
