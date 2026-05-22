package de.dlr.shepard.spi.analytics;

/**
 * AT1 — orchestrator-tier extension of {@link TimeseriesAnalytics} for
 * heavy detectors (ML at corpus scale, Transformer-based, multi-channel
 * correlation). Future home of {@code shepard-plugin-mlops} adapters
 * (Airflow / REBAR / SAIA / GWDG).
 *
 * <p><strong>Not yet implemented.</strong> No production caller exists in
 * the backend today. The SPI shape lives here so PR-1 of the AT1
 * extraction ships a complete tier seam — when the mlops plugin lands,
 * it implements this interface and the {@link AnalyticsRegistry} routes
 * to it via {@link #executionMode()}. PR-1..PR-7 only exercise the
 * {@link ExecutionMode#IN_PROCESS} branch.
 *
 * <p>Conformant implementations:
 * <ul>
 *   <li>Override {@link #executionMode()} to return
 *       {@link ExecutionMode#VIA_ORCHESTRATOR}.</li>
 *   <li>Override {@link #detect} to throw
 *       {@link UnsupportedOperationException} (the synchronous path is
 *       not meaningful for orchestrator-tier detectors).</li>
 *   <li>Implement {@link #submitJob}, {@link #pollJobStatus}, and
 *       {@link #cancelJob}.</li>
 * </ul>
 *
 * @see TimeseriesAnalytics
 * @see ExecutionMode#VIA_ORCHESTRATOR
 */
public interface RemoteTimeseriesAnalytics extends TimeseriesAnalytics {
  /**
   * Detectors of this tier always run via the orchestrator.
   */
  @Override
  default ExecutionMode executionMode() {
    return ExecutionMode.VIA_ORCHESTRATOR;
  }

  /**
   * Synchronous detection is not meaningful for this tier — implementations
   * must throw {@link UnsupportedOperationException}. The default
   * does exactly that.
   */
  @Override
  default AnomalyDetectionResult detect(TimeseriesAnalyticsInput input) {
    throw new UnsupportedOperationException(
      "Detector " + id() + " is VIA_ORCHESTRATOR; use submitJob / pollJobStatus instead"
    );
  }

  /**
   * Submit a detection job to the orchestrator. Returns a {@link
   * JobHandle} carrying the orchestrator-assigned id, initial status
   * (typically {@link JobStatus#SUBMITTED} or {@link JobStatus#QUEUED}),
   * and the timestamp of the orchestrator's acknowledgement.
   *
   * <p>Implementations <strong>not yet shipped.</strong> The mlops
   * plugin will provide the concrete Airflow / REBAR / SAIA adapter.
   *
   * @param input detector parameters + data pointer; null-checked by
   *              the dispatcher before reaching the implementation
   * @return a non-null job handle
   */
  JobHandle submitJob(TimeseriesAnalyticsInput input);

  /**
   * Refresh a previously-submitted job's status. The returned handle's
   * {@code jobId} equals the input {@code jobId}; the {@code status},
   * {@code updatedAt}, and {@code resultLocation} reflect the latest
   * orchestrator-side state.
   *
   * @param jobId opaque orchestrator-side identifier (from a prior
   *              {@link #submitJob} result)
   * @return refreshed job handle
   */
  JobHandle pollJobStatus(String jobId);

  /**
   * Request cancellation of a previously-submitted job. Idempotent —
   * cancelling an already-terminal job is a no-op. Implementations
   * surface the cancellation via {@link JobStatus#CANCELLED} on the
   * next {@link #pollJobStatus(String)}.
   *
   * @param jobId opaque orchestrator-side identifier
   */
  void cancelJob(String jobId);
}
