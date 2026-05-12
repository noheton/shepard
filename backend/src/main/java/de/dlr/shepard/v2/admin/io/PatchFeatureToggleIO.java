package de.dlr.shepard.v2.admin.io;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@Schema(name = "PatchFeatureToggle")
public class PatchFeatureToggleIO {

  @NotNull
  @Schema(required = true, description = "Desired enabled state for the feature toggle.")
  private Boolean enabled;
}
