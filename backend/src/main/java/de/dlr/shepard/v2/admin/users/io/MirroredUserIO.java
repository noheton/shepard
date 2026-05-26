package de.dlr.shepard.v2.admin.users.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.auth.users.entities.MirroredUser;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * PROV-USER-MIRROR-ENDPOINT — response body for
 * {@code POST /v2/admin/users/mirror}.
 *
 * <p>Returns the stable {@code appId} plus the natural key fields so the
 * caller can cache the mapping for later PROV-O attribution.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MirroredUserIO(

  @Schema(description = "Stable application-level identifier (UUID v7) for this mirror node.")
  String appId,

  @Schema(description = "Base URL of the source Shepard instance.")
  String sourceInstance,

  @Schema(description = "Username as known on the source instance.")
  String sourceUsername,

  @Schema(description = "Human-readable display name from the source side. Null when not provided.")
  String sourceDisplayName,

  @Schema(description = "Email address from the source side. Null when not provided.")
  String sourceEmail
) {

  /**
   * Project a {@link MirroredUser} entity onto this IO shape.
   */
  public static MirroredUserIO from(MirroredUser entity) {
    return new MirroredUserIO(
      entity.getAppId(),
      entity.getSourceInstance(),
      entity.getSourceUsername(),
      entity.getSourceDisplayName(),
      entity.getSourceEmail()
    );
  }
}
