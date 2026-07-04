package de.dlr.shepard.cli.io;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * MFFD-NDT-ADMIN-CONFIG-1 — CLI-side mirror of {@code ThermographyConfigIO}
 * from {@code GET/PATCH /v2/admin/thermography/config}.
 *
 * <p>{@code thresholdC} is the quality-score denominator in degrees Celsius;
 * {@code gridWidth} and {@code gridHeight} are the plate-heatmap grid dimensions.
 * All three fields are resolved (never null) by the server — the effective value
 * (singleton overriding deploy-time default) is always returned.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ThermographyConfig {

  private final String appId;
  private final double thresholdC;
  private final int gridWidth;
  private final int gridHeight;

  public ThermographyConfig(
    @JsonProperty("appId") String appId,
    @JsonProperty("thresholdC") double thresholdC,
    @JsonProperty("gridWidth") int gridWidth,
    @JsonProperty("gridHeight") int gridHeight
  ) {
    this.appId = appId;
    this.thresholdC = thresholdC;
    this.gridWidth = gridWidth;
    this.gridHeight = gridHeight;
  }

  public String getAppId() {
    return appId;
  }

  public double getThresholdC() {
    return thresholdC;
  }

  public int getGridWidth() {
    return gridWidth;
  }

  public int getGridHeight() {
    return gridHeight;
  }
}
