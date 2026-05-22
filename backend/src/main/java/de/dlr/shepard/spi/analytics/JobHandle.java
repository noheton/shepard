package de.dlr.shepard.spi.analytics;

import java.time.Instant;
import java.util.Optional;

/**
 * AT1 — value class returned from
 * {@link RemoteTimeseriesAnalytics#submitJob} and refreshed by
 * {@link RemoteTimeseriesAnalytics#pollJobStatus(String)}.
 *
 * <p>Carries the orchestrator-assigned {@code jobId} (opaque to the
 * dispatching layer), the most recent {@link JobStatus}, and an optional
 * orchestrator-side updated-at timestamp. Implementations may extend the
 * shape via subclasses or composition; the SPI uses this minimal record
 * to keep the {@code shepard-plugin-mlops} adapter surface small.
 *
 * @param jobId          opaque orchestrator-side identifier (never null)
 * @param status         most recent observed status (never null)
 * @param updatedAt      orchestrator-side last-update timestamp;
 *                       {@link Optional#empty()} when the orchestrator
 *                       doesn't report this
 * @param resultLocation orchestrator-side URI / pointer to the result
 *                       payload once {@link JobStatus#SUCCEEDED};
 *                       {@link Optional#empty()} until terminal
 */
public record JobHandle(
  String jobId,
  JobStatus status,
  Optional<Instant> updatedAt,
  Optional<String> resultLocation
) {
  /** Convenience factory for the initial submit (no resultLocation yet). */
  public static JobHandle submitted(String jobId) {
    return new JobHandle(jobId, JobStatus.SUBMITTED, Optional.empty(), Optional.empty());
  }
}
