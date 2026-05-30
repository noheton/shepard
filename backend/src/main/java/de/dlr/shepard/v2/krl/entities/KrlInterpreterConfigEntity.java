package de.dlr.shepard.v2.krl.entities;

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
 * <p>Single-instance Neo4j node following the A3b / J1e / N1c2 / UH1a
 * pattern (CLAUDE.md "Always: surface operator knobs in the admin
 * config"). One {@code :KrlInterpreterConfigEntity} node is seeded on
 * first startup from the deploy-time defaults in
 * {@code application.properties} ({@code shepard.krl.sidecar.url},
 * {@code shepard.krl.sidecar.timeout-seconds},
 * {@code shepard.krl.sidecar.max-body-size-mb}); subsequent runtime
 * PATCHes against
 * {@code GET/PATCH /v2/admin/plugins/krl/config} mutate this node in
 * place.
 *
 * <p>All three fields are nullable. A {@code null} runtime value means
 * "revert to the deploy-time default" — the service layer resolves the
 * effective value at read time. This follows RFC 7396 "clear" semantics
 * handled at the service layer.
 *
 * <p>The simple class name uses the {@code Entity} suffix to avoid a
 * name collision with the pre-existing CDI bean
 * {@code de.dlr.shepard.v2.krl.config.KrlInterpreterConfig} (the
 * deploy-time tier-1 source). Both coexist; the CDI bean is the seed
 * source; this entity carries the runtime-mutable tier-2 overrides.
 *
 * <p><b>Constraint.</b>
 * {@code V96__Add_KrlInterpreterConfig_singleton.cypher} adds
 * {@code REQUIRE n.appId IS UNIQUE} on
 * {@code :KrlInterpreterConfigEntity}.
 */
@NodeEntity
@Getter
@Setter
@NoArgsConstructor
@ToString
public class KrlInterpreterConfigEntity implements HasAppId {

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
   * Runtime override for the sidecar base URL. When {@code null}, the
   * effective value is the deploy-time default
   * ({@code shepard.krl.sidecar.url},
   * default {@code http://krl-interpreter-sidecar:8000}).
   */
  @Property("sidecarUrl")
  private String sidecarUrl;

  /**
   * Runtime override for the per-call request timeout in seconds.
   * When {@code null}, the effective value is the deploy-time default
   * ({@code shepard.krl.sidecar.timeout-seconds}, default {@code 120}).
   */
  @Property("timeoutSeconds")
  private Integer timeoutSeconds;

  /**
   * Runtime override for the maximum request body size in megabytes.
   * When {@code null}, the effective value is the deploy-time default
   * ({@code shepard.krl.sidecar.max-body-size-mb}, default {@code 16}).
   */
  @Property("maxBodySizeMb")
  private Integer maxBodySizeMb;

  /** For testing purposes only. */
  public KrlInterpreterConfigEntity(long id) {
    this.id = id;
  }

  @Override
  public int hashCode() {
    return Objects.hash(appId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof KrlInterpreterConfigEntity other)) return false;
    return Objects.equals(appId, other.appId);
  }
}
