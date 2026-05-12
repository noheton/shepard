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
}
