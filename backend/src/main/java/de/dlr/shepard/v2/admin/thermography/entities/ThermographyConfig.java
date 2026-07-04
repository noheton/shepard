package de.dlr.shepard.v2.admin.thermography.entities;

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
 * MFFD-NDT-ADMIN-CONFIG-1 — runtime-mutable thermography analysis config singleton.
 *
 * <p>Single-instance Neo4j node following the A3b / N1c2 / UH1a / J1e
 * pattern (CLAUDE.md "Always: surface operator knobs in the admin
 * config"). One {@code :ThermographyConfig} node is seeded on first
 * startup from the deploy-time defaults in {@code application.properties}
 * ({@code shepard.v2.thermography.threshold-c} default {@code 80.0},
 * {@code shepard.v2.thermography.grid-width} default {@code 64},
 * {@code shepard.v2.thermography.grid-height} default {@code 64});
 * subsequent runtime PATCHes against
 * {@code GET/PATCH /v2/admin/thermography/config} mutate this node in place.
 *
 * <p>Field set is the runtime-mutable subset of the analysis knobs:
 *
 * <ul>
 *   <li>{@link #thresholdC} — quality-score denominator (degrees Celsius).
 *       Hot-spot deltas above this threshold score toward 0; below it toward 1.
 *       {@code null} → fall back to the deploy-time default (80.0 °C).</li>
 *   <li>{@link #gridWidth} — number of plate-grid columns for the heatmap
 *       aggregation. {@code null} → deploy-time default (64).</li>
 *   <li>{@link #gridHeight} — number of plate-grid rows.
 *       {@code null} → deploy-time default (64).</li>
 * </ul>
 *
 * <p><b>Precedence.</b> Non-null runtime values win over deploy-time
 * defaults; per-request overrides in {@code AnalyzeRequestIO} win over
 * the runtime singleton. The deploy-time keys stay valid so an operator
 * can ship baked-in defaults in their IaC, but they don't override a
 * runtime flip.
 *
 * <p><b>Constraint.</b> {@code V111__Add_appId_constraint_ThermographyConfig.cypher}
 * adds {@code REQUIRE n.appId IS UNIQUE} on {@code :ThermographyConfig}.
 */
@NodeEntity
@Getter
@Setter
@NoArgsConstructor
@ToString
public class ThermographyConfig implements HasAppId {

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
   * Quality-score denominator (degrees Celsius). Hot-spot deltas above
   * this threshold push the score toward 0; below it toward 1.
   * {@code null} means "use the deploy-time default (80.0 °C)".
   */
  @Property("thresholdC")
  private Double thresholdC;

  /**
   * Number of plate-grid columns for the heatmap accumulation.
   * {@code null} means "use the deploy-time default (64)".
   */
  @Property("gridWidth")
  private Integer gridWidth;

  /**
   * Number of plate-grid rows for the heatmap accumulation.
   * {@code null} means "use the deploy-time default (64)".
   */
  @Property("gridHeight")
  private Integer gridHeight;

  /** For testing purposes only. */
  public ThermographyConfig(long id) {
    this.id = id;
  }

  @Override
  public int hashCode() {
    return Objects.hash(appId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof ThermographyConfig other)) return false;
    return Objects.equals(appId, other.appId);
  }
}
