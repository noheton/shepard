package de.dlr.shepard.v2.admin.io;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request body for {@code POST /v2/admin/instance-admins} — grants
 * the {@code instance-admin} role to the named user.
 */
@Data
@NoArgsConstructor
@Schema(name = "GrantInstanceAdmin")
public class GrantInstanceAdminIO {

  @NotBlank
  @Schema(required = true, description = "Username of the user to grant the role to.")
  private String username;
}
