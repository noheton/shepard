package de.dlr.shepard.v2.admin.io;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "FeatureToggle")
public class FeatureToggleIO {

  @Schema(required = true, description = "Stable identifier for the feature toggle.")
  private String name;

  @Schema(required = true, description = "Whether the feature is currently enabled.")
  private boolean enabled;

  @Schema(required = true, description = "Human-readable description of what the toggle controls.")
  private String description;

  /**
   * DX7 — origin of the toggle's current value.
   * One of: {@code "runtime"} (set via PATCH admin endpoint this JVM
   * lifetime, reverts on restart), {@code "config"} (value comes from
   * {@code application.properties} / env), {@code "default"} (config
   * key absent; hardcoded default active).
   */
  @Schema(
    required = true,
    description = "Origin of the current value: 'runtime' (PATCH-overridden, reverts on restart), " +
    "'config' (from application.properties / env), or 'default' (hardcoded default, no explicit config key)."
  )
  private String source;
}
