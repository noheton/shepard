package de.dlr.shepard.v2.timeseries.io;

import de.dlr.shepard.v2.timeseries.model.TimeseriesAnnotation;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
public class TimeseriesAnnotationIO {

  @Schema(readOnly = true)
  private String appId;

  @Schema(description = "Start of the annotated interval in nanoseconds since Unix epoch.", required = true)
  private Long startNs;

  @Schema(description = "End of the annotated interval in nanoseconds since Unix epoch. Null for point annotations.")
  private Long endNs;

  @Schema(description = "Annotation label / tag, e.g. 'anomaly', 'event-start'.", required = true)
  private String label;

  @Schema(description = "Optional longer description.")
  private String description;

  @Schema(description = "True when created by the anomaly-detection pipeline, false for user-created annotations.")
  private boolean aiGenerated;

  @Schema(description = "Confidence score [0.0, 1.0] from anomaly detection. Null for human-created annotations.")
  private Double confidence;

  public TimeseriesAnnotationIO(TimeseriesAnnotation a) {
    this.appId = a.getAppId();
    this.startNs = a.getStartNs();
    this.endNs = a.getEndNs();
    this.label = a.getLabel();
    this.description = a.getDescription();
    this.aiGenerated = a.isAiGenerated();
    this.confidence = a.getConfidence();
  }
}
