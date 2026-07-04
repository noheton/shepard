package de.dlr.shepard.v2.users.io;

import com.fasterxml.jackson.annotation.JsonFormat;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.entities.UserGroup;
import de.dlr.shepard.auth.users.services.DisplayNameResolver;
import jakarta.validation.constraints.NotBlank;
import java.util.Date;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * V2-SWEEP-002 — wire shape for {@code GET|POST|PATCH|DELETE /v2/user-groups}.
 *
 * <p>Uses {@code appId} (UUID v7) as the primary identifier; no numeric Neo4j id
 * is exposed (that is an implementation detail of the v1 surface).
 *
 * <p>Create body: {@code name} + optional {@code usernames}.
 * PATCH body: only present fields are applied (RFC 7396 merge-patch).
 */
@Data
@NoArgsConstructor
@Schema(name = "UserGroupV2")
public class UserGroupV2IO {

  @Schema(readOnly = true, description = "Application identifier (UUID v7).")
  private String appId;

  @NotBlank
  @Schema(required = true, example = "LUMEN engineers")
  private String name;

  @Schema(description = "Usernames of group members.")
  private List<String> usernames;

  @JsonFormat(shape = JsonFormat.Shape.STRING)
  @Schema(readOnly = true, format = "date-time", example = "2024-08-15T11:18:44.632+00:00")
  private Date createdAt;

  @Schema(readOnly = true)
  private String createdBy;

  @JsonFormat(shape = JsonFormat.Shape.STRING)
  @Schema(readOnly = true, nullable = true, format = "date-time", example = "2024-08-15T11:18:44.632+00:00")
  private Date updatedAt;

  @Schema(readOnly = true, nullable = true)
  private String updatedBy;

  public UserGroupV2IO(UserGroup group) {
    this.appId = group.getAppId();
    this.name = group.getName();
    this.usernames = group.getUsers().stream().map(User::getUsername).toList();
    this.createdAt = group.getCreatedAt();
    this.createdBy = group.getCreatedBy() != null
      ? DisplayNameResolver.effectiveDisplayName(group.getCreatedBy())
      : null;
    this.updatedAt = group.getUpdatedAt();
    this.updatedBy = group.getUpdatedBy() != null
      ? DisplayNameResolver.effectiveDisplayName(group.getUpdatedBy())
      : null;
  }
}
