package de.dlr.shepard.v2.admin.sqltimeseries.io;

import de.dlr.shepard.v2.admin.sqltimeseries.entities.SqlTimeseriesConfig;

/**
 * P10c — JSON shape returned by {@code GET/PATCH /v2/admin/sql-timeseries/config}.
 *
 * <p>Both fields are always present (non-null) in the response — the
 * effective values are resolved by falling back to deploy-time defaults
 * when the singleton's fields are {@code null}. This means consumers
 * always see a fully-resolved config snapshot, never a sparse document.
 *
 * <p>Wire names:
 * <ul>
 *   <li>{@link #maxRows} — serialises as {@code "maxRows"}</li>
 *   <li>{@link #maxDuration} — serialises as {@code "maxDuration"} (ISO-8601 string;
 *       stored as {@code maxDurationIso} in the entity to avoid confusion)</li>
 * </ul>
 */
public record SqlTimeseriesConfigIO(
  long maxRows,
  String maxDuration
) {

  /**
   * Project a {@link SqlTimeseriesConfig} entity onto the IO, resolving
   * {@code null} fields to their deploy-time defaults.
   *
   * @param cfg               the singleton entity (never null)
   * @param defaultMaxRows    deploy-time default for max rows
   * @param defaultMaxDuration deploy-time default for max duration (ISO-8601)
   * @return a fully-resolved IO record
   */
  public static SqlTimeseriesConfigIO from(
      SqlTimeseriesConfig cfg,
      long defaultMaxRows,
      String defaultMaxDuration) {
    long rows = cfg.getMaxRows() != null ? cfg.getMaxRows() : defaultMaxRows;
    String dur = cfg.getMaxDurationIso() != null ? cfg.getMaxDurationIso() : defaultMaxDuration;
    return new SqlTimeseriesConfigIO(rows, dur);
  }
}
