package de.dlr.shepard.plugins.unhide.entities;

import de.dlr.shepard.common.identifier.HasAppId;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;

/**
 * UH1a — runtime-mutable Unhide-integration config singleton.
 *
 * <p>Single-instance Neo4j node — mirrors the A3b feature-toggle and
 * (forthcoming) N1c2 `:SemanticConfig` shapes per the
 * {@code CLAUDE.md} "Always: surface operator knobs in the admin
 * config" rule. One {@code :UnhideConfig} node is seeded on first
 * startup from the {@code shepard.unhide.*} install-time defaults;
 * subsequent runtime PATCHes against
 * {@code /v2/admin/unhide/config} mutate this node in place.
 *
 * <p>Field set is the runtime-mutable subset of the feature's knobs
 * per {@code aidocs/67 §5.1}:
 *
 * <ul>
 *   <li>{@link #enabled} — master toggle. When {@code false},
 *       {@code /v2/unhide/feed.jsonld} returns 503 (RFC 7807
 *       {@code unhide.feed.disabled}).</li>
 *   <li>{@link #feedPublic} — when {@code true}, the feed is
 *       unauthenticated like {@code /versionz}; when {@code false}
 *       (default), the feed requires {@code X-API-KEY} with hash
 *       matching {@link #harvestApiKeyHash} OR an
 *       {@code instance-admin}-roled caller.</li>
 *   <li>{@link #contactEmail} — advertised in the feed metadata
 *       block so Unhide's harvester knows whom to ping.</li>
 *   <li>{@link #harvestApiKeyHash} — SHA-256 hex of the
 *       mint-once-clipboard harvest key. <b>Never store
 *       the plaintext</b>; the plaintext is returned exactly once
 *       at mint time via the rotate endpoint and immediately
 *       discarded server-side.</li>
 *   <li>{@link #harvestApiKeyLastRotatedAt} — epoch millis of the
 *       most recent rotate or revoke; {@code null} until first
 *       mint.</li>
 * </ul>
 *
 * <p><b>Precedence.</b> Runtime field values win for all four;
 * deploy-time {@code shepard.unhide.*} properties are install
 * defaults that seed the singleton on first start. See
 * {@code aidocs/67 §5.2} + the {@code CLAUDE.md} admin-config rule.
 */
@NodeEntity
@Getter
@Setter
@NoArgsConstructor
@ToString
public class UnhideConfig implements HasAppId {

  @Id
  @GeneratedValue
  private Long id;

  /**
   * Application-level identifier (UUID v7). Minted on save by
   * {@code GenericDAO.createOrUpdate}.
   */
  @Property("appId")
  private String appId;

  /**
   * Master runtime toggle. When {@code false}, the feed endpoint
   * returns 503 with the {@code unhide.feed.disabled} problem type.
   * Default {@code false} so a fresh install never accidentally
   * exposes a feed an operator hasn't reviewed.
   */
  @Property("enabled")
  private boolean enabled = false;

  /**
   * Feed visibility flag. When {@code true}, the feed is
   * unauthenticated (like {@code /versionz} / {@code /healthz}).
   * When {@code false} (default), the feed requires either
   * {@code X-API-KEY} with hash matching {@link #harvestApiKeyHash}
   * OR an authenticated caller carrying the {@code instance-admin}
   * role.
   */
  @Property("feedPublic")
  private boolean feedPublic = false;

  /**
   * Contact email surfaced in the feed's {@code _meta} block so
   * Unhide's data-provider liaison can reach the operating
   * institution. Free-form; not validated past basic non-empty.
   */
  @Property("contactEmail")
  private String contactEmail;

  /**
   * SHA-256 hex digest of the current harvest API key. The
   * plaintext is never stored — it is returned exactly once at
   * mint time and the caller is responsible for saving it. A
   * {@code null} value means no harvest key has been minted (or
   * the key has been revoked); when {@link #feedPublic} is also
   * {@code false}, the feed is reachable only by
   * {@code instance-admin}-roled callers in that state.
   */
  @Property("harvestApiKeyHash")
  private String harvestApiKeyHash;

  /**
   * Epoch millis of the most recent harvest-key mint or revoke;
   * {@code null} until first mint. Surfaced on the
   * {@code GET /v2/admin/unhide/config} read so operators can
   * see how stale the key is without exposing the hash itself.
   */
  @Property("harvestApiKeyLastRotatedAt")
  private Long harvestApiKeyLastRotatedAt;

  /** For testing purposes only. */
  public UnhideConfig(long id) {
    this.id = id;
  }

  @Override
  public int hashCode() {
    return Objects.hash(appId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof UnhideConfig other)) return false;
    return Objects.equals(appId, other.appId);
  }
}
