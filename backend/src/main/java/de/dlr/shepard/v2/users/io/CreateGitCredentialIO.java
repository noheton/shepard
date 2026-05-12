package de.dlr.shepard.v2.users.io;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request body for {@code POST /v2/me/git-credentials}. The {@code pat}
 * field is write-only — it is encrypted before storage and never returned
 * in any response.
 */
@Data
@NoArgsConstructor
@Schema(name = "CreateGitCredential")
public class CreateGitCredentialIO {

  @Schema(required = true, description = "Git host, no scheme or trailing slash. Example: gitlab.com")
  private String host;

  @Schema(nullable = true, description = "Human-readable label. Example: DLR GitLab")
  private String displayName;

  @Schema(required = true, description = "Git username.")
  private String username;

  @Schema(required = true, writeOnly = true, description = "Personal access token. Write-only — never returned.")
  private String pat;
}
