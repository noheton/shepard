package de.dlr.shepard.v2.admin.io;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request body for {@code POST /v2/admin/bootstrap} — consumes the
 * one-shot bootstrap token written to {@code shepard.bootstrap.token-path}
 * by {@link de.dlr.shepard.auth.bootstrap.BootstrapTokenInitializer}.
 */
@Data
@NoArgsConstructor
@Schema(name = "BootstrapRequest")
public class BootstrapRequestIO {

  @NotBlank
  @Schema(required = true, description = "The bootstrap token from /opt/shepard/.bootstrap-token (or shepard.bootstrap.token-path).")
  private String token;

  @NotBlank
  @Schema(required = true, description = "Username to grant the instance-admin role to.")
  private String username;
}
