package de.dlr.shepard.v2.timeseries.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.v2.dataobject.io.DataObjectListItemV2IO;
import de.dlr.shepard.v2.timeseries.model.TimeseriesAnnotation;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
public class TimeseriesAnnotationIO {

  @Schema(readOnly = true)
  private String appId;

  @Schema(description = "Start of the annotated interval as ISO 8601 UTC with nanosecond precision, e.g. '2024-06-01T12:00:00.000000001Z'.", required = true)
  private String start;

  @Schema(description = "End of the annotated interval as ISO 8601 UTC with nanosecond precision. Null for point annotations.")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String end;

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
    this.start = DataObjectListItemV2IO.toIsoNs(a.getStartNs());
    this.end = a.getEndNs() != null ? DataObjectListItemV2IO.toIsoNs(a.getEndNs()) : null;
    this.label = a.getLabel();
    this.description = a.getDescription();
    this.aiGenerated = a.isAiGenerated();
    this.confidence = a.getConfidence();
  }
}
