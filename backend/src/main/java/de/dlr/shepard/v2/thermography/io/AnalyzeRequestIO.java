package de.dlr.shepard.v2.thermography.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * MFFD-NDT-QUALITY-1 — request body for
 * {@code POST /v2/thermography/analyze}.
 *
 * <p>The required field is the FileBundleReference {@code appId}; everything
 * else has admin-tunable defaults (mirror of the
 * {@code shepard.v2.thermography.*} application.properties knobs, runtime-
 * overridable per call so a caller can re-analyze a region with a different
 * NDT threshold without an admin redeploy).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Thermography re-analyze request — kicks off a fresh "
  + "metric computation against the FileBundleReference identified by "
  + "imageBundleAppId. Companion threshold + grid knobs override the "
  + "shepard.v2.thermography defaults for this run only.")
public class AnalyzeRequestIO {

  @Schema(description = "FileBundleReference appId carrying the TIFF frames.",
    required = true)
  private String imageBundleAppId;

  @Schema(description = "Hot-spot threshold in degrees Celsius for the "
    + "quality-score denominator. When absent, the deploy default "
    + "(shepard.v2.thermography.threshold-c, default 80) is used.")
  private Double thresholdC;

  @Schema(description = "Grid width of the plate heatmap (default 64).")
  private Integer gridWidth;

  @Schema(description = "Grid height of the plate heatmap (default 64).")
  private Integer gridHeight;
}
