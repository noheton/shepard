package de.dlr.shepard.plugins.minter.epic.entities;

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
 * KIP1c — runtime-mutable ePIC-minter config singleton.
 *
 * <p>Single-instance Neo4j node mirroring the A3b / N1c2 / UH1a
 * pattern (CLAUDE.md "Always: surface operator knobs in the admin
 * config"). One {@code :EpicMinterConfig} node is seeded on
 * first startup from the {@code shepard.minters.epic.*}
 * install-time defaults; subsequent runtime PATCHes against
 * {@code /v2/admin/minters/epic/config} mutate this node in place.
 *
 * <p>Field set is the runtime-mutable subset of the feature's knobs:
 *
 * <ul>
 *   <li>{@link #enabled} — master toggle. When {@code false},
 *       {@code EpicMinter.mint()} throws {@code MinterException}
 *       ("ePIC minter disabled"). Defaults to {@code false} so a
 *       fresh install never accidentally hits the ePIC service without
 *       an operator's review.</li>
 *   <li>{@link #apiBaseUrl} — base URL of the ePIC Handle Service
 *       REST API (B2HANDLE-compatible endpoint).</li>
 *   <li>{@link #handlePrefix} — ePIC handle prefix allocated by the
 *       ePIC consortium (e.g. {@code 21.T11148} for Helmholtz test,
 *       or a member-specific prefix in production). Required for mint
 *       to succeed.</li>
 *   <li>{@link #credentialKey} — encrypted credential string. Stores
 *       the reversibly-encrypted value of the credential used for
 *       HTTP Basic auth. AES-GCM keyed off {@code shepard.instance.id}.
 *       NEVER returned through the admin REST; only the
 *       {@link #credentialHash} fingerprint is surfaced.</li>
 *   <li>{@link #credentialHash} — SHA-256 hex of the plaintext
 *       credential. Surfaced only via its first-8-hex fingerprint on
 *       the admin GET so an operator can confirm "yes that's the
 *       credential I set".</li>
 *   <li>{@link #updatedAt}, {@link #updatedBy} — audit shape.</li>
 * </ul>
 *
 * <p><b>Precedence.</b> Runtime field values win; deploy-time
 * {@code shepard.minters.epic.*} properties are install defaults
 * that seed the singleton on first start. The credential is never a
 * deploy-time key — must always be set via the
 * {@code POST /v2/admin/minters/epic/credential} endpoint
 * (security posture; gitleaks would otherwise flag operator
 * application.properties as a credential leak).
 */
@NodeEntity
@Getter
@Setter
@NoArgsConstructor
@ToString
public class EpicMinterConfig implements HasAppId {

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
   * Master runtime toggle. When {@code false}, the
   * {@code EpicMinter.mint()} call throws {@code MinterException}
   * before any HTTP call to the ePIC service is attempted. Default
   * {@code false}: an operator must explicitly enable after
   * configuring credentials + prefix.
   */
  @Property("enabled")
  private boolean enabled = false;

  /**
   * ePIC Handle Service REST API base URL. Example:
   * {@code https://handle.argo.grnet.gr/api}. The minter appends
   * {@code /handles/<prefix>/<suffix>} for each mint call.
   */
  @Property("apiBaseUrl")
  private String apiBaseUrl;

  /**
   * ePIC-allocated handle prefix (e.g. {@code 21.T11148}).
   * Required; {@code EpicMinter.mint()} throws when blank.
   */
  @Property("handlePrefix")
  private String handlePrefix;

  /**
   * Encrypted credential string — AES-GCM keyed off the
   * instance id. Stores the base credential used for HTTP Basic
   * auth (value is base64-encoded at request time). NEVER
   * returned through admin REST; only the {@link #credentialHash}
   * fingerprint is surfaced.
   */
  @Property("credentialKey")
  private String credentialKey;

  /**
   * SHA-256 hex of the plaintext credential. Surfaced only via its
   * first-8-hex fingerprint on {@code GET .../config}. Recomputed
   * on each credential-set call; cleared when the credential is
   * deleted.
   */
  @Property("credentialHash")
  private String credentialHash;

  /** Epoch millis of the most recent config mutation. */
  @Property("updatedAt")
  private Long updatedAt;

  /**
   * Username of the operator who last patched / set credentials.
   * Surfaced on the admin GET. Not used for authz — just an audit
   * convenience read.
   */
  @Property("updatedBy")
  private String updatedBy;

  /** For testing purposes only. */
  public EpicMinterConfig(long id) {
    this.id = id;
  }

  @Override
  public int hashCode() {
    return Objects.hash(appId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof EpicMinterConfig other)) return false;
    return Objects.equals(appId, other.appId);
  }
}
