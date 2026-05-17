package de.dlr.shepard.v2.admin.sqltimeseries.entities;

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
 * P10c — runtime-mutable SQL timeseries config singleton.
 *
 * <p>Single-instance Neo4j node following the A3b / N1c2 / UH1a / ROR1
 * pattern (CLAUDE.md "Always: surface operator knobs in the admin
 * config"). One {@code :SqlTimeseriesConfig} node is seeded on first
 * startup from the deploy-time defaults in {@code application.properties};
 * subsequent runtime PATCHes against
 * {@code GET/PATCH /v2/admin/sql-timeseries/config} mutate this node in
 * place.
 *
 * <p>Field set is the runtime-mutable subset of the feature's knobs:
 *
 * <ul>
 *   <li>{@link #maxRows} — hard row cap for {@code POST /v2/sql/timeseries}.
 *       {@code null} means "use the deploy-time default" ({@code 1000000}).
 *       When non-null, must be {@literal >} 0 (validated at the REST layer).
 *       The effective value is computed by
 *       {@code SqlTimeseriesConfigService.effectiveMaxRows()}.</li>
 *   <li>{@link #maxDurationIso} — hard query duration cap as an ISO-8601
 *       duration string (e.g. {@code "PT60S"}). {@code null} means "use the
 *       deploy-time default" ({@code "PT60S"}). When non-null, must be
 *       parseable by {@code Duration.parse()} (validated at the REST layer).
 *       Wire name is {@code maxDuration} (suffix stripped by the IO layer).
 *       The effective value is computed by
 *       {@code SqlTimeseriesConfigService.effectiveMaxDurationIso()}.</li>
 * </ul>
 *
 * <p><b>Precedence.</b> Non-null runtime values win over the deploy-time
 * defaults; setting a field to {@code null} via PATCH reverts it to the
 * deploy-time default (RFC 7396 "clear" semantics handled at the service
 * layer).
 *
 * <p><b>Constraint.</b> {@code V43__Add_appId_constraint_SqlTimeseriesConfig.cypher}
 * adds {@code REQUIRE n.appId IS UNIQUE} on {@code :SqlTimeseriesConfig}.
 */
@NodeEntity
@Getter
@Setter
@NoArgsConstructor
@ToString
public class SqlTimeseriesConfig implements HasAppId {

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
   * Hard row cap override for {@code POST /v2/sql/timeseries}.
   * {@code null} → use the deploy-time default ({@code shepard.timeseries.sql.max-rows}).
   * Must be {@literal >} 0 when non-null (enforced at the REST layer).
   */
  @Property("maxRows")
  private Long maxRows;

  /**
   * Hard query duration cap as an ISO-8601 duration string (e.g. {@code "PT60S"}).
   * {@code null} → use the deploy-time default ({@code shepard.timeseries.sql.max-duration}).
   * Must be parseable by {@link java.time.Duration#parse} when non-null (enforced at the REST layer).
   * Wire name: {@code maxDuration} (suffix stripped by the IO layer).
   */
  @Property("maxDurationIso")
  private String maxDurationIso;

  /** For testing purposes only. */
  public SqlTimeseriesConfig(long id) {
    this.id = id;
  }

  @Override
  public int hashCode() {
    return Objects.hash(appId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof SqlTimeseriesConfig other)) return false;
    return Objects.equals(appId, other.appId);
  }
}
