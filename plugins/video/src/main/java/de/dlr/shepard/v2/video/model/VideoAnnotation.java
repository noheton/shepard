package de.dlr.shepard.v2.video.model;

import de.dlr.shepard.common.neo4j.entities.AbstractEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.neo4j.ogm.annotation.NodeEntity;

/**
 * VID1b-annotation — a labelled time-range (or point) annotation on a
 * VideoStreamReference. Timestamps are in seconds (double), matching
 * VideoStreamReference.durationSeconds.
 */
@NodeEntity
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
public class VideoAnnotation extends AbstractEntity {

  /** Start of the annotated interval in seconds from the beginning of the video. */
  private double startSeconds;

  /**
   * End of the annotated interval in seconds. null means a point annotation.
   */
  private Double endSeconds;

  /** Human-readable label, e.g. "ignition", "burn", "cooldown". */
  private String label;

  /** Optional longer description. */
  private String description;

  /** true when created by an automated detector rather than a human. */
  private boolean aiGenerated = false;

  /** Confidence score [0.0, 1.0] from AI detection. null for human-created. */
  private Double confidence;

  public VideoAnnotation(long id) {
    super(id);
  }
}
