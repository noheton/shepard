package de.dlr.shepard.v2.users.io;

import com.fasterxml.jackson.annotation.JsonFormat;
import de.dlr.shepard.auth.users.entities.GitCredential;
import java.util.Date;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Read-only view of a {@link GitCredential}. The PAT is intentionally
 * absent — write-only field, never returned on the wire.
 */
@Data
@NoArgsConstructor
@Schema(name = "GitCredential")
public class GitCredentialIO {

  @Schema(readOnly = true, required = true)
  private String appId;

  @Schema(description = "Git host, no scheme. Example: gitlab.com")
  private String host;

  @Schema(nullable = true, description = "Human-readable label for this credential.")
  private String displayName;

  @Schema(description = "Git username this credential is valid for.")
  private String username;

  @JsonFormat(shape = JsonFormat.Shape.STRING)
  @Schema(readOnly = true, required = true, format = "date-time", example = "2026-05-12T10:00:00.000+00:00")
  private Date createdAt;

  public GitCredentialIO(GitCredential cred) {
    this.appId = cred.getAppId();
    this.host = cred.getHost();
    this.displayName = cred.getDisplayName();
    this.username = cred.getUsername();
    this.createdAt = cred.getCreatedAt();
  }
}
