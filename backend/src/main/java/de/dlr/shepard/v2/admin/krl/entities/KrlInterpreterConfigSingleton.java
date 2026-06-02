package de.dlr.shepard.v2.admin.krl.entities;

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
 * KRL-CONFIG-1 — runtime-mutable KRL interpreter config singleton.
 *
 * <p>Single-instance Neo4j node following the A3b / N1c2 / UH1a / J1e
 * pattern (CLAUDE.md "Always: surface operator knobs in the admin
 * config"). One {@code :KrlInterpreterConfigSingleton} node is seeded
 * on first startup from the deploy-time defaults in
 * {@code application.properties}; subsequent runtime PATCHes against
 * {@code GET/PATCH /v2/admin/krl/config} mutate this node in place.
 *
 * <p>Field set is the runtime-mutable subset of the KRL interpreter
 * configuration knobs:
 *
 * <ul>
 *   <li>{@link #enabled} — master switch for the KRL interpreter
 *       sidecar. When {@code false}, {@code POST /v2/krl/interpret}
 *       returns HTTP 503. Default {@code true} (the sidecar is
 *       operator opt-in via compose profile, but the feature flag is
 *       ON once the compose profile is loaded).</li>
 *   <li>{@link #sidecarUrl} — sidecar base URL (e.g.
 *       {@code http://krl-interpreter-sidecar:8000}). Overrides the
 *       deploy-time default ({@code shepard.krl.sidecar.url}). Set to
 *       {@code null} to revert to the deploy-time default.</li>
 *   <li>{@link #timeoutSeconds} — per-call request timeout in seconds.
 *       Zero or negative reverts to the deploy-time default
 *       ({@code shepard.krl.sidecar.timeout-seconds}).</li>
 *   <li>{@link #maxBodySizeMb} — maximum summed file-payload size in
 *       megabytes. Zero or negative reverts to the deploy-time default
 *       ({@code shepard.krl.sidecar.max-body-size-mb}).</li>
 * </ul>
 *
 * <p><b>Precedence.</b> Non-null / positive runtime values win over
 * deploy-time defaults; setting a field to null / zero via PATCH
 * reverts to the deploy-time default (RFC 7396 "clear" semantics
 * handled at the service layer).
 *
 * <p><b>Constraint.</b> {@code V99__Add_appId_constraint_KrlInterpreterConfig.cypher}
 * adds {@code REQUIRE n.appId IS UNIQUE} on
 * {@code :KrlInterpreterConfigSingleton}.
 *
 * <p><b>Note on naming.</b> The class is named
 * {@code KrlInterpreterConfigSingleton} to avoid colliding with the
 * existing deploy-time-only CDI bean
 * {@link de.dlr.shepard.v2.krl.config.KrlInterpreterConfig} that
 * currently wires the sidecar client. The two coexist during the
 * KRL-CONFIG-1 integration window; the deploy-time bean becomes a
 * thin wrapper reading from this singleton in
 * {@code KRL-CONFIG-1b} (tracked in {@code aidocs/16}).
 */
@NodeEntity
@Getter
@Setter
@NoArgsConstructor
@ToString
public class KrlInterpreterConfigSingleton implements HasAppId {

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
   * Master switch for the KRL interpreter feature. When {@code false},
   * the {@code POST /v2/krl/interpret} endpoint returns HTTP 503.
   * Default: {@code true} (opt-out posture — the operator brings the
   * sidecar up as a deliberate action; once it is up, the feature is on).
   */
  @Property("enabled")
  private boolean enabled;

  /**
   * Sidecar base URL, e.g. {@code http://krl-interpreter-sidecar:8000}.
   * {@code null} → use the deploy-time default
   * ({@code shepard.krl.sidecar.url}). Overriding this at runtime
   * lets an operator redirect the backend to a different sidecar
   * instance (e.g. a higher-capacity node) without a restart.
   */
  @Property("sidecarUrl")
  private String sidecarUrl;

  /**
   * Per-call request timeout in seconds.
   * {@code 0} → use the deploy-time default
   * ({@code shepard.krl.sidecar.timeout-seconds}, default 120).
   * Positive values override the deploy-time default at runtime.
   */
  @Property("timeoutSeconds")
  private int timeoutSeconds;

  /**
   * Maximum summed file-payload size in megabytes. The backend rejects
   * requests whose combined srcFile + urdfFile + datFiles exceed this.
   * {@code 0} → use the deploy-time default
   * ({@code shepard.krl.sidecar.max-body-size-mb}, default 16).
   */
  @Property("maxBodySizeMb")
  private int maxBodySizeMb;

  /** For testing purposes only. */
  public KrlInterpreterConfigSingleton(long id) {
    this.id = id;
  }

  @Override
  public int hashCode() {
    return Objects.hash(appId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof KrlInterpreterConfigSingleton other)) return false;
    return Objects.equals(appId, other.appId);
  }
}
