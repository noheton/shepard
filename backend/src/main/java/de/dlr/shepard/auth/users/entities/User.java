package de.dlr.shepard.auth.users.entities;

import de.dlr.shepard.auth.apikey.entities.ApiKey;
import de.dlr.shepard.common.identifier.HasAppId;
import de.dlr.shepard.common.subscription.entities.Subscription;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.HasId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.Relationship;
import org.neo4j.ogm.annotation.Relationship.Direction;
import org.neo4j.ogm.annotation.typeconversion.DateLong;

@NodeEntity
@Data
@NoArgsConstructor
public class User implements HasId, HasAppId {

  @Id
  private String username;

  /**
   * Application-level identifier (UUID v7) — additive in L2a.
   */
  @Property("appId")
  private String appId;

  private String firstName;

  private String lastName;

  private String email;

  /**
   * ORCID identifier (16-digit ISO 7064 mod 11-2 checked), per
   * {@code aidocs/16 U1a}. Nullable; set via
   * {@code PATCH /v2/users/me}. RO-Crate export
   * ({@code aidocs/31}) picks this up automatically once present.
   */
  private String orcid;

  /**
   * User-chosen override for how their name renders across the UI
   * (audit trails, headers, "Created by ..." lines) — per
   * {@code aidocs/16 U1b}. Nullable; when null,
   * {@link de.dlr.shepard.auth.users.services.DisplayNameResolver}
   * derives the rendered name from {@code firstName}/{@code lastName}
   * with the cryptic-Keycloak-username redaction as fallback.
   */
  private String displayName;

  /**
   * Per-user UI preferences stored as a JSON object — per
   * {@code aidocs/16 U1d}. Nullable; when null the
   * {@code GET /v2/users/me/preferences} endpoint returns an empty map.
   * Keys are open-world strings (theme, language, timeZone, dateFormat,
   * defaultPageSize, defaultLandingPage, …); values are always strings.
   * Serialized by {@code UserService.patchPreferences} using Jackson's
   * {@code ObjectMapper}.
   */
  @Property("preferencesJson")
  private String preferencesJson;

  /**
   * PROV1l — GDPR consent surface. When {@code true}, the
   * {@link de.dlr.shepard.provenance.filters.ProvenanceCaptureFilter}
   * omits the caller's identity ({@code agentUsername}) from captured
   * {@code :Activity} nodes. The activity is still recorded (for audit
   * volume / rate metrics) but without the personal identifier.
   *
   * <p>Defaults to {@code false} (identity included, matching the
   * existing behaviour). A user opts in via
   * {@code PATCH /v2/users/me} with {@code {"anonymizeInProvenance": true}}.
   *
   * <p>Neo4j schemaless — no migration needed. Old nodes simply lack
   * the property and OGM returns the Java default ({@code false}).
   */
  @Property("anonymizeInProvenance")
  private boolean anonymizeInProvenance = false;

  /**
   * ROLE-GRANT-STALE-SESSION-02 — millis-since-epoch of the most recent
   * mutation to this user's role set (grant or revoke). Stamped by
   * {@link de.dlr.shepard.v2.admin.services.InstanceAdminService} on every
   * role mutation; consulted by
   * {@link de.dlr.shepard.auth.security.JwtTokenAuthService} per request.
   *
   * <p>If a presented JWT's {@code iat} (issued-at) is earlier than this
   * timestamp, the token is rejected with HTTP 401 + body
   * {@code {"error":"role_changed", ...}} so the user is forced through a
   * sign-out + re-auth that picks up the new role set.
   *
   * <p>Nullable per the additive-schema rule — pre-feature {@code :User}
   * rows simply lack the property and OGM returns {@code null}. A null
   * value means "no recorded role change for this user" and the gate
   * passes through (matching the existing behaviour).
   *
   * <p>See V98 migration + {@code aidocs/16} {@code ROLE-GRANT-STALE-SESSION-02}.
   */
  @DateLong
  @Property("roleChangedAt")
  private Date roleChangedAt;

  @ToString.Exclude
  @Relationship(type = Constants.SUBSCRIBED_BY, direction = Direction.INCOMING)
  private List<Subscription> subscriptions = new ArrayList<>();

  @ToString.Exclude
  @Relationship(type = Constants.BELONGS_TO, direction = Direction.INCOMING)
  private List<ApiKey> apiKeys = new ArrayList<>();

  @ToString.Exclude
  @Relationship(type = "OWNS_CREDENTIAL", direction = Direction.OUTGOING)
  private List<GitCredential> gitCredentials = new ArrayList<>();

  /**
   * For testing purposes only
   *
   * @param username identifies the user
   */
  public User(String username) {
    this.username = username;
    this.firstName = "";
    this.lastName = "";
    this.email = "";
  }

  /**
   * Simple constructor
   *
   * @param username  Username
   * @param firstName First name
   * @param lastName  Last name
   * @param email     E-Mail Address
   */
  public User(String username, String firstName, String lastName, String email) {
    this.username = username;
    this.firstName = firstName;
    this.lastName = lastName;
    this.email = email;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result =
      prime *
      result +
      Objects.hash(
        anonymizeInProvenance,
        displayName,
        email,
        firstName,
        lastName,
        orcid,
        preferencesJson,
        roleChangedAt,
        username
      );
    result = prime * result + HasId.hashcodeHelper(apiKeys);
    result = prime * result + HasId.hashcodeHelper(subscriptions);
    result = prime * result + HasId.hashcodeHelper(gitCredentials);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof User)) return false;
    User other = (User) obj;
    return (
      HasId.areEqualSetsByUniqueId(apiKeys, other.apiKeys) &&
      HasId.areEqualSetsByUniqueId(subscriptions, other.subscriptions) &&
      HasId.areEqualSetsByUniqueId(gitCredentials, other.gitCredentials) &&
      anonymizeInProvenance == other.anonymizeInProvenance &&
      Objects.equals(displayName, other.displayName) &&
      Objects.equals(email, other.email) &&
      Objects.equals(firstName, other.firstName) &&
      Objects.equals(lastName, other.lastName) &&
      Objects.equals(orcid, other.orcid) &&
      Objects.equals(preferencesJson, other.preferencesJson) &&
      Objects.equals(roleChangedAt, other.roleChangedAt) &&
      Objects.equals(username, other.username)
    );
  }

  @Override
  public String getUniqueId() {
    return username;
  }
}
