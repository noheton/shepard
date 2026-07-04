package de.dlr.shepard.v2.admin.config.resources;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * V2CONV-A4 — listing entry returned by {@code GET /v2/admin/config}. One row
 * per registered {@link de.dlr.shepard.v2.admin.config.spi.ConfigDescriptor},
 * so an operator (or the admin UI) can discover the available
 * {@code /v2/admin/config/{feature}} keys without hard-coding them.
 */
@Schema(name = "ConfigFeature", description = "A runtime-configurable feature exposed under /v2/admin/config/{feature}.")
public record ConfigFeatureIO(
  @Schema(description = "The {feature} path segment, e.g. 'semantic'.") String feature,
  @Schema(description = "Human-readable description of what this config controls.") String description
) {}
