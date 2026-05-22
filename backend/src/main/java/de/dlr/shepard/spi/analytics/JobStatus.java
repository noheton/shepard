package de.dlr.shepard.spi.analytics;

/**
 * AT1 — lifecycle state of a {@link JobHandle} returned by
 * {@link RemoteTimeseriesAnalytics#submitJob} and polled via
 * {@link RemoteTimeseriesAnalytics#pollJobStatus(String)}.
 *
 * <p>Linear progression:
 * {@code SUBMITTED → QUEUED → RUNNING → (SUCCEEDED | FAILED | CANCELLED)}.
 * Implementations are not required to surface every intermediate state —
 * an orchestrator that goes directly from {@code SUBMITTED} to
 * {@code RUNNING} is conformant. The terminal states are mutually
 * exclusive.
 */
public enum JobStatus {
  /** Accepted by the orchestrator; not yet scheduled. */
  SUBMITTED,

  /** Scheduled, awaiting executor capacity. */
  QUEUED,

  /** Executor has the job and is processing it. */
  RUNNING,

  /** Terminal — completed without error; result is fetchable. */
  SUCCEEDED,

  /** Terminal — failed; cause should be in the orchestrator's logs. */
  FAILED,

  /** Terminal — cancelled via {@link RemoteTimeseriesAnalytics#cancelJob}. */
  CANCELLED,
}
