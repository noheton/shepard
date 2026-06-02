package de.dlr.shepard.v2.thermography.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * MFFD-NDT-QUALITY-1 — response shape for
 * {@code GET /v2/thermography/{imageBundleAppId}/plate-heatmap}.
 *
 * <p>The {@code cells} matrix is row-major: outer index = y (height),
 * inner index = x (width). Values are degrees Celsius; cells with no
 * coverage are clamped to {@code minTemp} (cold-not-NaN, so the
 * frontend Canvas pipeline can map them through the same colormap
 * branch as covered cells).
 *
 * <p>When the bundle has not been analyzed yet (no plate-grid
 * annotation present), the endpoint returns 404 — the frontend
 * surfaces the "Re-analyze" button in that state.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Plate heatmap — a single composite max-temperature "
  + "grid across all frames in the bundle. Row-major, [height][width].")
public class PlateHeatmapIO {

  @Schema(description = "FileBundleReference appId this heatmap describes.")
  private String imageBundleAppId;

  @Schema(description = "Grid width in cells.")
  private int width;

  @Schema(description = "Grid height in cells.")
  private int height;

  @Schema(description = "Row-major cell matrix, [height][width], degrees C.")
  private float[][] cells;

  @Schema(description = "Bundle-wide minimum temperature, degrees C.")
  private double minTemp;

  @Schema(description = "Bundle-wide maximum temperature, degrees C.")
  private double maxTemp;

  @Schema(description = "Threshold-C used at analysis time — drives the "
    + "amber/red colour bands on the frontend renderer.")
  private double thresholdTemp;

  @Schema(description = "Number of frames the heatmap summarises.")
  private int frameCount;
}
