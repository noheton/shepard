package de.dlr.shepard.v2.admin.io;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Response body for {@code POST /v2/admin/bootstrap}. */
@Data
@AllArgsConstructor
@Schema(name = "BootstrapResponse")
public class BootstrapResponseIO {

  @Schema(description = "Username that was granted the instance-admin role.")
  private String username;

  @Schema(description = "Role granted to the user.", example = "instance-admin")
  private String role;
}
