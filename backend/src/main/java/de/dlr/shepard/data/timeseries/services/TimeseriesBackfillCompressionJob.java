package de.dlr.shepard.data.timeseries.services;

import de.dlr.shepard.data.timeseries.repositories.TimeseriesDataPointRepository;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

/**
 * TS-AUDIT-2026-05-24-008 — fortnightly maintenance job that compresses any
 * {@code timeseries_data_points} chunks that are older than 8 days and still
 * uncompressed.
 *
 * <p>TimescaleDB's automatic compression policy fires once per day, so a backfill
 * chunk ingested at 12:26 (when the last policy run was 11:56) sits uncompressed
 * for up to 23 hours.  During MFFD-scale ingest this creates multiple such chunks
 * per hour → temporary disk inflation of 5–10×.
 *
 * <p>This job closes the gap on a fortnightly cadence (configurable via
 * {@code shepard.timeseries.compression-backfill.interval}, default {@code P14D}).
 * A second call site exists in the importer post-phase
 * ({@link de.dlr.shepard.v2.importer.resources.ImportJobsV2Rest}) which compresses
 * immediately after each import run, so large single-session backfills are handled
 * without waiting for the fortnightly tick.
 *
 * <p>The job delegates to
 * {@link TimeseriesDataPointRepository#compressBackfilledChunks()}, which is
 * idempotent and logs each compressed chunk at INFO level.
 *
 * <p>{@code @ActivateRequestContext} is required because
 * {@link TimeseriesDataPointRepository} is {@code @RequestScoped} and the
 * scheduler fires outside of a JAX-RS request scope.
 */
@ApplicationScoped
public class TimeseriesBackfillCompressionJob {

  @Inject
  TimeseriesDataPointRepository timeseriesDataPointRepository;

  /**
   * Fortnightly run.  The interval is resolved from the config property at startup;
   * changing it at runtime requires a restart.  The default {@code P14D} means every
   * 14 days from the first tick (Quarkus Scheduler {@code every} with ISO-8601 duration).
   *
   * <p>The job is best-effort: any exception from the repository method is caught and
   * logged at WARN level so that a transient TimescaleDB error does not restart the
   * scheduler or produce an unhandled exception.
   */
  @Scheduled(every = "{shepard.timeseries.compression-backfill.interval}")
  @ActivateRequestContext
  public void runFortnightly() {
    Log.info("TS-AUDIT-008: fortnightly backfill compression job starting");
    try {
      timeseriesDataPointRepository.compressBackfilledChunks();
    } catch (RuntimeException e) {
      Log.warnf(e, "TS-AUDIT-008: fortnightly backfill compression job failed");
    }
    Log.debug("TS-AUDIT-008: fortnightly backfill compression job finished");
  }
}
