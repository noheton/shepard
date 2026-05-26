package de.dlr.shepard.v2.admin.users.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * PROV-USER-MIRROR-ENDPOINT — request body for
 * {@code POST /v2/admin/users/mirror}.
 *
 * <p>Required fields: {@link #sourceInstance}, {@link #sourceUsername}.
 * Optional fields: {@link #sourceDisplayName}, {@link #sourceEmail}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MirroredUserCreateIO(

  @Schema(
    required = true,
    description = "Base URL of the source Shepard instance " +
      "(e.g. 'https://cube3.dlr.de/shepard'). " +
      "Part of the composite uniqueness key with sourceUsername."
  )
  String sourceInstance,

  @Schema(
    required = true,
    description = "Username as known on the source instance " +
      "(Keycloak preferred_username or sub claim). " +
      "Part of the composite uniqueness key with sourceInstance."
  )
  String sourceUsername,

  @Schema(
    description = "Human-readable display name from the source side. Nullable."
  )
  String sourceDisplayName,

  @Schema(
    description = "Email address from the source side. Nullable."
  )
  String sourceEmail
) {}
