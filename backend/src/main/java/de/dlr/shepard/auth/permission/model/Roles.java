package de.dlr.shepard.auth.permission.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@Schema(name = "Roles")
@NoArgsConstructor
@AllArgsConstructor
public class Roles {

  @Schema(readOnly = true, required = true)
  private boolean owner;

  @Schema(readOnly = true, required = true)
  private boolean manager;

  @Schema(readOnly = true, required = true)
  private boolean writer;

  @Schema(readOnly = true, required = true)
  private boolean reader;
}
