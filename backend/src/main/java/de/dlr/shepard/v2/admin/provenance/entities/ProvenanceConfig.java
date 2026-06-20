package de.dlr.shepard.v2.admin.provenance.entities;

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
 * FTOGGLE-PROV-1 — runtime-mutable provenance config singleton.
 *
 * <p>Single Neo4j node seeded on first startup from deploy-time defaults
 * ({@code shepard.provenance.*}); runtime PATCHes via
 * {@code PATCH /v2/admin/config/provenance} mutate this node in place.
 *
 * <p>Field set:
 * <ul>
 *   <li>{@link #enabled} — master switch; {@code null} → default {@code true}.</li>
 *   <li>{@link #captureReads} — capture GET requests too; {@code null} → default {@code false}.</li>
 *   <li>{@link #retentionDays} — how long to keep Activity nodes; {@code null} → default {@code 730}.</li>
 * </ul>
 *
 * <p>Constraint: {@code V116__Add_appId_constraint_ProvenanceConfig.cypher}
 * adds {@code REQUIRE n.appId IS UNIQUE}.
 */
@NodeEntity
@Getter
@Setter
@NoArgsConstructor
@ToString
public class ProvenanceConfig implements HasAppId {

  @Id
  @GeneratedValue
  private Long id;

  @Property("appId")
  private String appId;

  /** Master provenance-capture switch. {@code null} → deploy-time default ({@code true}). */
  @Property("enabled")
  private Boolean enabled;

  /**
   * Capture read (GET) requests in addition to mutations.
   * {@code null} → deploy-time default ({@code false}).
   */
  @Property("captureReads")
  private Boolean captureReads;

  /**
   * Retention window in days for {@code :Activity} nodes.
   * {@code null} → deploy-time default ({@code 730}).
   */
  @Property("retentionDays")
  private Long retentionDays;

  @Override
  public int hashCode() {
    return Objects.hash(appId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof ProvenanceConfig other)) return false;
    return Objects.equals(appId, other.appId);
  }
}
