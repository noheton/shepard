package de.dlr.shepard.v2.admin.io;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request body for {@code POST /v2/admin/instance/nuke}.
 *
 * <p>The caller must supply the exact confirmation phrase to prove intent.
 * The phrase is intentionally awkward so it cannot be sent by accident.
 */
@Data
@NoArgsConstructor
@Schema(name = "NukeRequest")
public class NukeRequestIO {

  @NotBlank
  @Schema(
    required = true,
    description = "Must be exactly \"yes drop everything\" to confirm the destructive reset.",
    example = "yes drop everything"
  )
  private String confirmPhrase;
}
