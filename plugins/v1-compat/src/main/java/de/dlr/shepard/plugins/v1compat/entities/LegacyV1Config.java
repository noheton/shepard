package de.dlr.shepard.plugins.v1compat.entities;

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
 * V1COMPAT.0 — runtime-mutable config singleton for the v1 compat
 * marker plugin (per {@code aidocs/platform/103a §2 row 1}).
 *
 * <p>Single-instance Neo4j node — mirrors the A3b feature-toggle and
 * {@code :UnhideConfig} / {@code :SemanticConfig} shapes per the
 * {@code CLAUDE.md} "Always: surface operator knobs in the admin
 * config" rule. One {@code :LegacyV1Config} node is seeded on first
 * startup by the V63 Cypher migration; subsequent runtime PATCHes
 * against {@code /v2/admin/legacy/v1/config} mutate this node in
 * place.
 *
 * <p>Field set is the <b>minimal</b> Phase 1 shape per
 * {@code aidocs/platform/103a} clarification 2 lean A
 * — just {@code enabled}. Per-endpoint disable, log-level enums,
 * suppress-deprecation-headers, etc. are deferred to Phase 2.
 *
 * <ul>
 *   <li>{@link #enabled} — master toggle. When {@code true} (the
 *       default + shipping state), every {@code /shepard/api/...}
 *       request flows through the existing v1 resources, and the
 *       response bytes are byte-identical to upstream shepard 5.2.0,
 *       plus three additive RFC-standard deprecation headers
 *       ({@code Deprecation}, {@code Link}, {@code X-Shepard-Legacy}).
 *       When {@code false}, the {@code LegacyV1GateFilter} short-
 *       circuits every {@code /shepard/api/...} request with HTTP 410
 *       Gone + an RFC 7807 problem-detail body
 *       ({@code v1-disabled}).</li>
 * </ul>
 *
 * <p><b>Precedence.</b> Runtime field value wins; deploy-time
 * {@code shepard.legacy.v1.enabled} is the install default that
 * seeds the singleton on first start. See {@code CLAUDE.md}
 * admin-config rule.
 *
 * @see de.dlr.shepard.plugins.v1compat.services.LegacyV1ConfigService
 */
@NodeEntity
@Getter
@Setter
@NoArgsConstructor
@ToString
public class LegacyV1Config implements HasAppId {

  /** Internal Neo4j node id (legacy long ID). */
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
   * Master runtime toggle. When {@code true} (default + shipping
   * state), {@code /shepard/api/...} flows through the existing v1
   * resources byte-identically. When {@code false}, the
   * {@code LegacyV1GateFilter} returns 410 Gone + RFC 7807 problem-
   * detail body for every {@code /shepard/api/...} request.
   *
   * <p>Default {@code true} so a fresh install never surprise-breaks
   * a downstream tool still hitting the upstream v1 surface — the
   * v1 sunset philosophy is "no fork-imposed timeline; operator
   * decides when to flip".
   */
  @Property("enabled")
  private boolean enabled = true;

  /** Millis since epoch when the row was first persisted. */
  @Property("createdAt")
  private Long createdAt;

  /** Millis since epoch when {@link #enabled} was last touched. */
  @Property("updatedAt")
  private Long updatedAt;

  /** Username of the admin who last modified the config. */
  @Property("updatedBy")
  private String updatedBy;

  /** For testing purposes only. */
  public LegacyV1Config(long id) {
    this.id = id;
  }

  @Override
  public int hashCode() {
    return Objects.hash(appId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof LegacyV1Config other)) return false;
    return Objects.equals(appId, other.appId);
  }
}
