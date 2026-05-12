package de.dlr.shepard.context.references.timeseriesreference.model;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.HasId;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;

@NodeEntity
@Data
@NoArgsConstructor
public class TimeseriesReference extends BasicReference {

  private long start;

  private long end;

  /**
   * AI1c — channel-quality score in {@code [0.0, 1.0]} emitted by the
   * background {@code TimeseriesQualityScoringJob}. {@code null} means
   * "not yet scored" (newly-created references; refs the heuristic
   * skipped for lack of data; or any ref pre-dating AI1c).
   *
   * <p>The score is pure-heuristic — completeness, coverage, stability
   * averaged — no LLM call. See
   * {@code TimeseriesQualityScorer} for the formula and
   * {@code aidocs/43 §3.2} for the design.
   *
   * <p>Searchable as a regular Neo4j property — the existing
   * {@code Neo4jQueryBuilder.primitiveClauseWithNeo4jId} accepts
   * camelCase property names through {@code SAFE_PROPERTY_NAME}.
   */
  private Double qualityScore;

  /**
   * AI1c — millisecond epoch at which the background job last computed
   * (or attempted to compute) {@link #qualityScore}. {@code null}
   * means "never scored". Used by the job to skip references that
   * were re-scored within the rescoring window.
   */
  private Long lastScoredAt;

  @Relationship(type = Constants.HAS_PAYLOAD)
  private List<ReferencedTimeseriesNodeEntity> referencedTimeseriesList = new ArrayList<>();

  @ToString.Exclude
  @Relationship(type = Constants.IS_IN_CONTAINER)
  private TimeseriesContainer timeseriesContainer;

  /**
   * For testing purposes only
   *
   * @param id identifies the entity
   */
  public TimeseriesReference(long id) {
    super(id);
  }

  public void addTimeseries(ReferencedTimeseriesNodeEntity timeseries) {
    this.referencedTimeseriesList.add(timeseries);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hash(end, start, referencedTimeseriesList, qualityScore, lastScoredAt);
    result = prime * result + HasId.hashcodeHelper(timeseriesContainer);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    if (!(obj instanceof TimeseriesReference)) return false;
    TimeseriesReference other = (TimeseriesReference) obj;
    return (
      end == other.end &&
      start == other.start &&
      Objects.equals(qualityScore, other.qualityScore) &&
      Objects.equals(lastScoredAt, other.lastScoredAt) &&
      Objects.equals(referencedTimeseriesList, other.referencedTimeseriesList) &&
      HasId.equalsHelper(timeseriesContainer, other.timeseriesContainer)
    );
  }
}
