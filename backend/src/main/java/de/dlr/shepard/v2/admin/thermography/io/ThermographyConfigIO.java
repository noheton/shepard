package de.dlr.shepard.v2.admin.thermography.io;

import de.dlr.shepard.v2.admin.thermography.entities.ThermographyConfig;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * MFFD-NDT-ADMIN-CONFIG-1 — JSON shape returned by
 * {@code GET/PATCH /v2/admin/thermography/config}.
 *
 * <p>All three numeric fields are resolved (never null on the wire) —
 * the factory method applies deploy-time defaults when the singleton
 * carries {@code null} for a field. This simplifies callers: they always
 * get an effective value, never a nullable that requires further fallback
 * logic.
 *
 * <p>Wire names:
 * <ul>
 *   <li>{@link #appId} — the singleton node's UUID v7 identifier</li>
 *   <li>{@link #thresholdC} — effective quality-score threshold in °C</li>
 *   <li>{@link #gridWidth} — effective plate-grid column count</li>
 *   <li>{@link #gridHeight} — effective plate-grid row count</li>
 * </ul>
 */
@Schema(description = "Runtime configuration for the MFFD thermography NDT analysis; returned by GET/PATCH /v2/admin/thermography/config.")
public record ThermographyConfigIO(
  String appId,
  double thresholdC,
  int gridWidth,
  int gridHeight
) {

  /**
   * Project a {@link ThermographyConfig} entity onto the IO record,
   * resolving {@code null} fields against the deploy-time defaults.
   *
   * @param cfg               the singleton entity (never null)
   * @param defaultThresholdC deploy-time default threshold in °C
   * @param defaultGridWidth  deploy-time default grid column count
   * @param defaultGridHeight deploy-time default grid row count
   * @return a fully-resolved IO record with no null fields
   */
  public static ThermographyConfigIO from(
    ThermographyConfig cfg,
    double defaultThresholdC,
    int defaultGridWidth,
    int defaultGridHeight
  ) {
    double threshold = cfg.getThresholdC() != null ? cfg.getThresholdC() : defaultThresholdC;
    int width = cfg.getGridWidth() != null ? cfg.getGridWidth() : defaultGridWidth;
    int height = cfg.getGridHeight() != null ? cfg.getGridHeight() : defaultGridHeight;
    return new ThermographyConfigIO(cfg.getAppId(), threshold, width, height);
  }
}
