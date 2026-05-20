package de.dlr.shepard.v2.video.io;

import de.dlr.shepard.v2.video.model.VideoAnnotation;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
public class VideoAnnotationIO {

  @Schema(readOnly = true)
  private String appId;

  @Schema(description = "Start of the annotated interval in seconds from the start of the video.", required = true)
  private Double startSeconds;

  @Schema(description = "End of the annotated interval in seconds. Null for point annotations.")
  private Double endSeconds;

  @Schema(description = "Annotation label, e.g. 'ignition', 'burn', 'cooldown'.", required = true)
  private String label;

  @Schema(description = "Optional longer description.")
  private String description;

  @Schema(description = "True when created by an automated detector.")
  private boolean aiGenerated;

  @Schema(description = "Confidence score [0.0, 1.0] from AI detection. Null for human-created annotations.")
  private Double confidence;

  public VideoAnnotationIO(VideoAnnotation a) {
    this.appId = a.getAppId();
    this.startSeconds = a.getStartSeconds();
    this.endSeconds = a.getEndSeconds();
    this.label = a.getLabel();
    this.description = a.getDescription();
    this.aiGenerated = a.isAiGenerated();
    this.confidence = a.getConfidence();
  }
}
