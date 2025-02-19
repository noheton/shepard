package de.dlr.shepard.common.search.io;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@AllArgsConstructor
@NoArgsConstructor
public abstract class ASearchParams {

  @Valid
  @NotBlank
  @Schema(example = "{\"property\":\"name\",\"value\":\"\",\"operator\":\"contains\"}")
  private String query;
}
