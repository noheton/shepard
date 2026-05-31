package de.dlr.shepard.auth.users.entities;

import de.dlr.shepard.common.identifier.HasAppId;
import de.dlr.shepard.common.util.HasId;
import java.util.Date;
import java.util.Objects;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.typeconversion.DateLong;

@NodeEntity
@Data
@NoArgsConstructor
public class GitCredential implements HasId, HasAppId {

  @Id
  @Property("appId")
  private String appId;

  /**
   * Hostname only, no scheme, no trailing slash.
   * Examples: {@code gitlab.com}, {@code github.com}.
   */
  private String host;

  /** Human-readable label chosen by the user. */
  private String displayName;

  /** The git username this credential is valid for. */
  private String username;

  /**
   * AES-GCM encrypted PAT stored as base64(IV ‖ ciphertext).
   * Never returned on the wire — omitted from every IO type.
   */
  @Schema(hidden = true)
  private String encryptedPat;

  @DateLong
  private Date createdAt;

  /**
   * ADM-USR-GIT-BACKEND-1 — Epoch-stamped on every create and on every
   * explicit rotate via {@code POST /v2/admin/users/{username}/git-credentials/
   * {appId}/rotate}. Nullable per the additive-schema rule: rows persisted
   * before this field existed return {@code null} until the credential is
   * next rotated. Exposed (read-only) via the admin list endpoint so an
   * operator can spot stale credentials at a glance.
   */
  @DateLong
  private Date lastRotatedAt;

  @Override
  public String getUniqueId() {
    return appId;
  }

  @Override
  public int hashCode() {
    return Objects.hash(appId, host, displayName, username, createdAt, lastRotatedAt);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof GitCredential other)) return false;
    return (
      Objects.equals(appId, other.appId) &&
      Objects.equals(host, other.host) &&
      Objects.equals(displayName, other.displayName) &&
      Objects.equals(username, other.username) &&
      Objects.equals(createdAt, other.createdAt) &&
      Objects.equals(lastRotatedAt, other.lastRotatedAt)
    );
  }
}
