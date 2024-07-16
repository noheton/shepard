package de.dlr.shepard.neo4Core.io;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Schema(name = "Roles")
@NoArgsConstructor
@AllArgsConstructor
public class RolesIO {

  @Schema(accessMode = AccessMode.READ_ONLY)
  private boolean owner;

  @Schema(accessMode = AccessMode.READ_ONLY)
  private boolean manager;

  @Schema(accessMode = AccessMode.READ_ONLY)
  private boolean writer;

  @Schema(accessMode = AccessMode.READ_ONLY)
  private boolean reader;
}
