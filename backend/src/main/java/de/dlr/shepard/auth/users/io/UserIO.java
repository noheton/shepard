package de.dlr.shepard.auth.users.io;

import de.dlr.shepard.auth.apikey.entities.ApiKey;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.DisplayNameResolver;
import de.dlr.shepard.common.subscription.entities.Subscription;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@Schema(name = "User")
public class UserIO {

  @Schema(readOnly = true, required = true)
  private String username;

  private String firstName;

  private String lastName;

  private String email;

  /**
   * ORCID identifier (`https://orcid.org/<orcid>`), per `aidocs/16 U1a`.
   * Nullable; null until the user sets one via `PATCH /v2/users/me`.
   * Format: 16 digits split into 4 groups of 4 separated by hyphens;
   * final character may be `X` for the check digit (ISO 7064 mod 11-2).
   */
  @Schema(nullable = true, example = "0000-0002-1825-0097")
  private String orcid;

  /**
   * User-chosen display-name override, per `aidocs/16 U1b`. Nullable;
   * unset until set via `PATCH /v2/users/me`. Distinguishable from
   * `effectiveDisplayName` (read-only computed) so clients can tell
   * "the user picked this" from "the system derived this".
   */
  @Schema(nullable = true, description = "User-chosen display-name override.")
  private String displayName;

  /**
   * Computed at read time per `aidocs/16 U1b`: the rendered name the
   * UI / audit-trail / 'Created by' lines should display. Falls back
   * through (1) `displayName`, (2) `firstName lastName`, (3)
   * trailing-segment redaction of `username` for the cryptic-IdP-id
   * case. Read-only.
   */
  @Schema(readOnly = true, required = true, description = "Effective rendered display name, with fallbacks applied.")
  private String effectiveDisplayName;

  @Schema(readOnly = true, required = true)
  private Long[] subscriptionIds;

  @Schema(readOnly = true, required = true)
  private UUID[] apiKeyIds;

  public UserIO(User user) {
    this.username = user.getUsername();
    this.firstName = user.getFirstName();
    this.lastName = user.getLastName();
    this.email = user.getEmail();
    this.orcid = user.getOrcid();
    this.displayName = user.getDisplayName();
    this.effectiveDisplayName = DisplayNameResolver.effectiveDisplayName(user);
    this.subscriptionIds = user.getSubscriptions().stream().map(Subscription::getId).toArray(Long[]::new);
    this.apiKeyIds = user.getApiKeys().stream().map(ApiKey::getUid).toArray(UUID[]::new);
  }
}
