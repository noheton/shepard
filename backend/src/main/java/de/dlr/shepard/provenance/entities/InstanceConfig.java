package de.dlr.shepard.provenance.entities;

import de.dlr.shepard.common.identifier.HasAppId;
import de.dlr.shepard.common.util.HasId;
import java.util.Objects;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;

/**
 * Singleton holding the per-instance {@code instance_secret} used
 * by {@code de.dlr.shepard.provenance.services.HmacChainService}
 * to sign the {@link Activity} chain.
 *
 * <p>Follows the A3b / N1c2 / UH1a {@code :*Config} pattern: exactly
 * one row in the database, seeded on first start from
 * {@code ${SHEPARD_INSTANCE_SECRET}} (env) or from a fresh
 * {@code SecureRandom}-derived value if the env var is absent.
 * Service-layer reads/writes go through
 * {@code InstanceConfigService.current()} so "exactly one" is
 * enforced at the service boundary (matching the SemanticConfig
 * precedent — see {@code aidocs/semantics/48 §3}).
 *
 * <p><b>Why a separate entity from {@link Activity}.</b> The secret
 * outlives any single Activity row; it survives provenance-retention
 * pruning ({@code aidocs/52}); it has its own rotation lifecycle. A
 * mixed-into-Activity field would be lost the first time a
 * retention job pruned the row that carried it.
 *
 * <p><b>Rotation contract.</b>
 * <ul>
 *   <li>{@link #secretVersion} starts at 1 on first start.</li>
 *   <li>A rotation mints a new {@code instanceSecret} value and
 *       increments {@code secretVersion}.</li>
 *   <li>Old {@link Activity} rows retain their {@code secretVersion}
 *       value and stay verifiable against the old key, which the
 *       operator keeps in their key-rotation runbook (see
 *       {@code docs/reference/audit-trail.md}).</li>
 *   <li>The verifier walks the chain detecting
 *       {@code secretVersion} transitions and picks the right key
 *       per segment.</li>
 * </ul>
 *
 * <p><b>What this entity does NOT carry.</b> Cluster identity
 * ({@code shepard.instance.id}), instance URL, OIDC issuer — those
 * are deploy-time-only per the CLAUDE.md "knobs that stay
 * deploy-time-only" exception list. {@code :InstanceConfig} is the
 * audit-secret only.
 */
@NodeEntity
@Data
@NoArgsConstructor
public class InstanceConfig implements HasId, HasAppId {

  @Id
  @GeneratedValue
  private Long id;

  /**
   * Application-level identifier (UUID v7). Minted on save by
   * {@code GenericDAO#createOrUpdate} per the L2a seam.
   */
  @Property("appId")
  private String appId;

  /**
   * The current HMAC signing key — base64-encoded random bytes, 32
   * bytes (256 bits) before encoding to keep parity with the SHA-256
   * block. Never logged. Never serialised to the wire.
   *
   * <p>On rotation, the new value is written to this field and
   * {@link #secretVersion} is incremented. The previous value is
   * NOT retained on this entity — the operator's runbook is
   * responsible for archiving prior keys (otherwise a rotation
   * makes the secret too convenient an attack target).
   */
  @Property("instanceSecret")
  private String instanceSecret;

  /**
   * Monotonic counter — starts at 1, increments on rotation. Every
   * Activity carries the {@code secretVersion} that signed it so the
   * verifier can pick the right key per chain segment.
   */
  @Property("secretVersion")
  private Integer secretVersion = 1;

  /** Millis since epoch when the singleton was first persisted. */
  @Property("createdAtMillis")
  private Long createdAtMillis;

  /** Millis since epoch when the row was last mutated (rotation). */
  @Property("lastRotatedAtMillis")
  private Long lastRotatedAtMillis;

  @Override
  public String getUniqueId() {
    return appId;
  }

  @Override
  public int hashCode() {
    return Objects.hash(appId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof InstanceConfig other)) return false;
    return Objects.equals(appId, other.appId);
  }
}
