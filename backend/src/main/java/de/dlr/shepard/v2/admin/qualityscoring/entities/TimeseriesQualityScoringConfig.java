package de.dlr.shepard.v2.admin.qualityscoring.entities;

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
 * FTOGGLE-QS-1 — runtime-mutable config singleton for the AI1c
 * quality-scoring background job.
 *
 * <p>Single-instance Neo4j node following the A3b / N1c2 / UH1a /
 * MFFD-NDT-ADMIN-CONFIG-1 pattern. One {@code :TimeseriesQualityScoringConfig}
 * node is seeded on first startup from the deploy-time defaults in
 * {@code application.properties}; subsequent PATCH calls via
 * {@code PATCH /v2/admin/config/timeseries-quality-scoring} mutate this
 * node in place.
 *
 * <p>Runtime-mutable fields:
 * <ul>
 *   <li>{@link #enabled} — whether the scoring job fires. {@code null} →
 *       fall back to the deploy-time default ({@code false}).</li>
 *   <li>{@link #batchSize} — max references scored per tick. {@code null} →
 *       fall back to the deploy-time default (100). Clamped to [1, 10 000]
 *       by the job itself.</li>
 * </ul>
 *
 * <p>The scheduling interval ({@code shepard.timeseries.quality-scoring.interval})
 * is Quarkus-evaluated at startup and stays deploy-time-only per the
 * CLAUDE.md "Pre-startup ordering invariants" exception.
 *
 * <p><b>Constraint.</b> V115 migration adds
 * {@code REQUIRE n.appId IS UNIQUE} on {@code :TimeseriesQualityScoringConfig}.
 */
@NodeEntity
@Getter
@Setter
@NoArgsConstructor
@ToString
public class TimeseriesQualityScoringConfig implements HasAppId {

  @Id
  @GeneratedValue
  private Long id;

  @Property("appId")
  private String appId;

  /**
   * Whether the quality-scoring job is active. {@code null} means
   * "use the deploy-time default (false)".
   */
  @Property("enabled")
  private Boolean enabled;

  /**
   * Max references scored per tick. {@code null} means "use the
   * deploy-time default (100)". The job clamps to [1, 10 000].
   */
  @Property("batchSize")
  private Integer batchSize;

  public TimeseriesQualityScoringConfig(long id) {
    this.id = id;
  }

  @Override
  public int hashCode() {
    return Objects.hash(appId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof TimeseriesQualityScoringConfig other)) return false;
    return Objects.equals(appId, other.appId);
  }
}
