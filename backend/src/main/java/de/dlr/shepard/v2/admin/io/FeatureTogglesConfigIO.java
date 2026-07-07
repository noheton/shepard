package de.dlr.shepard.v2.admin.io;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * APISIMP-FEATURE-TOGGLE-CONFIG-UNIFY — read projection returned by
 * {@code GET /v2/admin/config/feature-toggles} (via the generic
 * {@code AdminConfigRest} + {@code FeatureTogglesConfigDescriptor}).
 *
 * <p>Wraps the same {@link FeatureToggleIO} rows previously served by
 * {@code GET /v2/admin/runtime-toggles}, now unified under the config surface.
 */
@Schema(name = "FeatureTogglesConfig", description = "Current state of all runtime feature toggles.")
public record FeatureTogglesConfigIO(
  @Schema(
    required = true,
    description = "All registered feature toggles with their current enabled state and source."
  )
  List<FeatureToggleIO> toggles
) {}
