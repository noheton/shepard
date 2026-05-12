package de.dlr.shepard.v2.users.io;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request body for {@code PATCH /v2/me/git-credentials/{appId}}. All
 * fields are optional; absent fields leave the credential unchanged.
 * The {@code pat} field is write-only — it is encrypted before storage
 * and never returned in any response.
 */
@Data
@NoArgsConstructor
@Schema(name = "PatchGitCredential")
public class PatchGitCredentialIO {

  @Schema(nullable = true, description = "Updated human-readable label. Null = leave unchanged.")
  private String displayName;

  @Schema(nullable = true, description = "Updated git username. Null = leave unchanged.")
  private String username;

  @Schema(nullable = true, writeOnly = true, description = "Updated PAT. Write-only — never returned. Null = leave unchanged.")
  private String pat;
}
