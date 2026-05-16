package de.dlr.shepard.context.references.timeseriesreference.model;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.HasId;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.v2.timeseries.model.TimeseriesAnnotation;
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

  /**
   * TM1 — how timestamps in the referenced timeseries relate to wall clock.
   * "WALL_CLOCK" (default): stored sample timestamps are already UTC nanoseconds.
   * "EXPERIMENT_RELATIVE": sample timestamps are relative to t=0 of the experiment;
   * wall_clock_ns(sample) = wallClockOffset + sample_t_ns.
   * Null on pre-TM1a rows — treat as "WALL_CLOCK".
   */
  private String timeReference;

  /**
   * TM1 — nanoseconds epoch (UTC) of the DAQ's t=0, i.e. the wall-clock time
   * that corresponds to sample_t=0 in the stored data.
   * Only meaningful when timeReference == "EXPERIMENT_RELATIVE".
   * Mutable: can be corrected any time a better anchor is discovered.
   */
  private Long wallClockOffset;

  /**
   * TM1 — free-text provenance tag for how wallClockOffset was determined.
   * Suggested values: "manual", "ffprobe", "SA_sync", "NTP_marker".
   */
  private String wallClockOffsetSource;

  @Relationship(type = Constants.HAS_PAYLOAD)
  private List<ReferencedTimeseriesNodeEntity> referencedTimeseriesList = new ArrayList<>();

  @ToString.Exclude
  @Relationship(type = Constants.IS_IN_CONTAINER)
  private TimeseriesContainer timeseriesContainer;

  @Relationship(type = Constants.HAS_TIMESERIES_ANNOTATION)
  private List<TimeseriesAnnotation> timeseriesAnnotations = new ArrayList<>();

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
    result = prime * result + Objects.hash(end, start, referencedTimeseriesList, qualityScore, lastScoredAt, timeseriesAnnotations, timeReference, wallClockOffset, wallClockOffsetSource);
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
      Objects.equals(timeReference, other.timeReference) &&
      Objects.equals(wallClockOffset, other.wallClockOffset) &&
      Objects.equals(wallClockOffsetSource, other.wallClockOffsetSource) &&
      Objects.equals(referencedTimeseriesList, other.referencedTimeseriesList) &&
      Objects.equals(timeseriesAnnotations, other.timeseriesAnnotations) &&
      HasId.equalsHelper(timeseriesContainer, other.timeseriesContainer)
    );
  }
}
