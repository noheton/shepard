package de.dlr.shepard.v2.admin.ror.entities;

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
 * ROR1 — runtime-mutable Research Organization Registry config singleton.
 *
 * <p>Single-instance Neo4j node mirroring the A3b / N1c2 / UH1a
 * pattern (CLAUDE.md "Always: surface operator knobs in the admin
 * config"). One {@code :InstanceRorConfig} node is seeded on first
 * startup; subsequent runtime PATCHes against
 * {@code GET/PATCH /v2/admin/instance/ror} mutate this node in place.
 *
 * <p>Field set is the runtime-mutable subset of the feature's knobs:
 *
 * <ul>
 *   <li>{@link #rorId} — the ROR identifier suffix (e.g. {@code 04cvxnb49}
 *       for DLR). Not validated beyond 1-9 alphanumeric chars (deliberately
 *       lenient — real ROR IDs are 9-char Crockford base32, but canonicality
 *       checking is deferred to a future tightening pass). {@code null} means
 *       "not configured"; the full URL is computed as
 *       {@code https://ror.org/<rorId>} by the IO layer.</li>
 *   <li>{@link #organizationName} — human-readable name for display
 *       (e.g. {@code "DLR e.V."}). Free-form string. {@code null} when
 *       not set.</li>
 * </ul>
 *
 * <p><b>Precedence.</b> Runtime field values win; no deploy-time
 * defaults are seeded (the singleton starts blank; operators populate
 * it via {@code PATCH /v2/admin/instance/ror}).
 *
 * <p><b>Constraint.</b> {@code V42__Add_appId_constraint_InstanceRorConfig.cypher}
 * adds {@code REQUIRE n.appId IS UNIQUE} on {@code :InstanceRorConfig}.
 */
@NodeEntity
@Getter
@Setter
@NoArgsConstructor
@ToString
public class InstanceRorConfig implements HasAppId {

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
   * ROR identifier suffix (e.g. {@code 04cvxnb49} for DLR). The
   * full URL is {@code https://ror.org/<rorId>} — computed by the
   * IO layer, never stored here. {@code null} when not configured.
   *
   * <p>Validation at the REST layer: must be 1-9 alphanumeric chars
   * or absent/null (which clears the field). The lenient pattern
   * ({@code [A-Za-z0-9]{1,9}}) is intentional — real ROR IDs are
   * 9-char Crockford base32 with a checksum but canonicality
   * enforcement is deferred to a future tightening pass.
   */
  @Property("rorId")
  private String rorId;

  /**
   * Human-readable organization name (e.g. {@code "DLR e.V."}).
   * Surfaced in admin GET + eventually embedded in RO-Crate and
   * KIP provenance records. Free-form; not validated. {@code null}
   * when not configured.
   */
  @Property("organizationName")
  private String organizationName;

  /** For testing purposes only. */
  public InstanceRorConfig(long id) {
    this.id = id;
  }

  @Override
  public int hashCode() {
    return Objects.hash(appId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof InstanceRorConfig other)) return false;
    return Objects.equals(appId, other.appId);
  }
}
