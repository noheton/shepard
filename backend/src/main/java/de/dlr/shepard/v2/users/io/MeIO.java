package de.dlr.shepard.v2.users.io;

import de.dlr.shepard.auth.users.io.UserIO;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * CW1 — Response shape for {@code GET /v2/users/me}.
 *
 * <p>Extends the upstream {@link UserIO} fields with v2-only additions:
 * <ul>
 *   <li>{@code watchedCollectionCount} — number of collections the caller is
 *       currently watching (CW1, populated from {@code :CollectionWatcher} nodes).
 * </ul>
 *
 * <p>The upstream {@code GET /shepard/api/users} endpoint is unchanged; this
 * is the v2-surface projection that can grow independently.
 */
@Schema(name = "MeIO")
public record MeIO(
  @Schema(readOnly = true, required = true)
  String username,

  @Schema(readOnly = true, required = true)
  String appId,

  @Schema(nullable = true)
  String firstName,

  @Schema(nullable = true)
  String lastName,

  @Schema(nullable = true)
  String email,

  @Schema(nullable = true)
  String orcid,

  @Schema(nullable = true)
  String displayName,

  @Schema(readOnly = true, required = true)
  String effectiveDisplayName,

  @Schema(readOnly = true, required = true,
    description = "Number of collections the caller is currently watching (CW1).")
  int watchedCollectionCount
) {
  public static MeIO from(UserIO user, int watchedCollectionCount) {
    return new MeIO(
      user.getUsername(),
      user.getAppId(),
      user.getFirstName(),
      user.getLastName(),
      user.getEmail(),
      user.getOrcid(),
      user.getDisplayName(),
      user.getEffectiveDisplayName(),
      watchedCollectionCount
    );
  }
}
