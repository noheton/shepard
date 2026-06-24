package de.dlr.shepard.v2.thermography.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * MFFD-NDT-QUALITY-1 — response shape for
 * {@code POST /v2/thermography/analyze}.
 *
 * <p>Summary view, returned synchronously. The heavy frame-stat payload
 * lives on the {@code :SemanticAnnotation} graph (one row per frame's
 * peak-delta-c) and on the plate-heatmap endpoint; this body answers
 * the "did it work + how bad was the worst frame" question for the UI
 * to render the quality chip immediately.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Thermography analysis summary. Frame-level stats are "
  + "persisted as SemanticAnnotations; the plate heatmap is fetchable via "
  + "GET /v2/thermography/{imageBundleAppId}/plate-heatmap.")
public class AnalyzeResultIO {

  @Schema(description = "FileBundleReference appId that was analyzed.")
  private String imageBundleAppId;

  @Schema(description = "Number of TIFF frames successfully decoded + scored.")
  private int framesAnalyzed;

  @Schema(description = "Number of TIFF frames skipped (decode error, "
    + "non-TIFF MIME, zero-pixel frame, …). Skips are logged as WARN.")
  private int framesSkipped;

  @Schema(description = "Maximum peak-delta-c across all frames, degrees C.")
  private double maxPeakDeltaC;

  @Schema(description = "Mean of mean-delta-c across all frames, degrees C.")
  private double meanOfMeanDeltaC;

  @Schema(description = "Absolute maximum temperature observed, degrees C.")
  private double maxC;

  @Schema(description = "Threshold-C used for the quality-score denominator.")
  private double thresholdC;

  @Schema(description = "Bundle-level quality score in [0, 1]. "
    + "1 = perfectly uniform; 0 = worst frame met or exceeded the threshold.")
  private double qualityScore;

  @Schema(description = "Bundle-level hot-spot centroid x-coordinate in "
    + "frame pixel coordinates (weighted average), -1 when no frames.")
  private double hotspotCentroidX;

  @Schema(description = "Bundle-level hot-spot centroid y-coordinate, parallel.")
  private double hotspotCentroidY;

  @Schema(description = "Number of SemanticAnnotation rows written by this "
    + "analysis (bundle-level + DO-level scores).")
  private int annotationsWritten;
}
