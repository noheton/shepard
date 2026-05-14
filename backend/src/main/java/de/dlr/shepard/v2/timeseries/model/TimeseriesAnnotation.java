package de.dlr.shepard.v2.timeseries.model;

import de.dlr.shepard.common.neo4j.entities.AbstractEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.neo4j.ogm.annotation.NodeEntity;

/**
 * TA1a — a labelled time-range (or point) annotation on a
 * {@link de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference}.
 *
 * <p>Timestamps use nanoseconds since Unix epoch, matching
 * {@code TimeseriesDataPoint.timestamp}. When {@code endNs} is {@code null}
 * the annotation is a single-point marker; when both are set it spans an
 * interval. {@code aiGenerated=true} flags annotations produced by anomaly
 * detection rather than a human user.
 */
@NodeEntity
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
public class TimeseriesAnnotation extends AbstractEntity {

  /** Start of the annotated interval in nanoseconds since Unix epoch. */
  private long startNs;

  /**
   * End of the annotated interval in nanoseconds since Unix epoch.
   * {@code null} means a point annotation (no interval end).
   */
  private Long endNs;

  /** Human-readable label / tag, e.g. "anomaly", "event-start", "calibration". */
  private String label;

  /** Optional longer description. */
  private String description;

  /** {@code true} when created by the anomaly-detection pipeline rather than a user. */
  private boolean aiGenerated = false;

  /**
   * Confidence score in {@code [0.0, 1.0]} emitted by the anomaly-detection
   * pipeline. {@code null} for human-created annotations.
   */
  private Double confidence;

  public TimeseriesAnnotation(long id) {
    super(id);
  }
}
