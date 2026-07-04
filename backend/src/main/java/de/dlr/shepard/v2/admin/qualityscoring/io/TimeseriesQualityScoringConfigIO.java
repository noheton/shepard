package de.dlr.shepard.v2.admin.qualityscoring.io;

import de.dlr.shepard.v2.admin.qualityscoring.entities.TimeseriesQualityScoringConfig;

/**
 * FTOGGLE-QS-1 — JSON shape for
 * {@code GET/PATCH /v2/admin/config/timeseries-quality-scoring}.
 *
 * <p>All fields are resolved (never null on the wire) — the factory
 * method applies deploy-time defaults when the singleton carries null.
 *
 * <p>Wire names:
 * <ul>
 *   <li>{@link #enabled} — whether the AI1c scoring job fires</li>
 *   <li>{@link #batchSize} — max references scored per tick</li>
 * </ul>
 *
 * <p>Note: {@code interval} (the Quarkus {@code @Scheduled.every} cadence)
 * is deploy-time-only and is not exposed here — changing it requires a
 * restart; see CLAUDE.md "Pre-startup ordering invariants" exception.
 */
public record TimeseriesQualityScoringConfigIO(
  boolean enabled,
  int batchSize
) {

  /**
   * Project a {@link TimeseriesQualityScoringConfig} onto the IO record,
   * resolving {@code null} fields against the deploy-time defaults.
   */
  public static TimeseriesQualityScoringConfigIO from(
    TimeseriesQualityScoringConfig cfg,
    boolean defaultEnabled,
    int defaultBatchSize
  ) {
    boolean eff = cfg.getEnabled() != null ? cfg.getEnabled() : defaultEnabled;
    int bs = cfg.getBatchSize() != null ? cfg.getBatchSize() : defaultBatchSize;
    return new TimeseriesQualityScoringConfigIO(eff, bs);
  }
}
