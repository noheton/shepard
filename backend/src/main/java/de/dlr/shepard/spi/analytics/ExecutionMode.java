package de.dlr.shepard.spi.analytics;

/**
 * AT1 — execution mode for a {@link TimeseriesAnalytics} implementation.
 *
 * <p>Two-tier model:
 *
 * <ul>
 *   <li>{@link #IN_PROCESS} — light detectors (statistical, threshold-based,
 *       MAD, rule-based). Runs in the backend JVM. Returns synchronously
 *       from {@link TimeseriesAnalytics#detect(TimeseriesAnalyticsInput)}.
 *       The current MAD detector ({@code mad-v1}) ships at this tier.</li>
 *   <li>{@link #VIA_ORCHESTRATOR} — heavy detectors (ML models at corpus
 *       scale, Transformer-based, multi-channel correlation). Future home
 *       of {@code shepard-plugin-mlops} — submits a job to an external
 *       orchestrator (Airflow / REBAR / SAIA), polls for status, returns
 *       a {@link JobHandle}. Implementations of this tier extend
 *       {@link RemoteTimeseriesAnalytics}. Stub at the SPI level until the
 *       mlops plugin lands; the {@link AnalyticsRegistry} routes by mode
 *       but only the in-process branch is exercised today.</li>
 * </ul>
 *
 * <p>The mode is declared by the detector via {@link
 * TimeseriesAnalytics#executionMode()} (defaulting to {@code IN_PROCESS}).
 * The dispatching layer (in-tree {@code AnomalyDetectionService} →
 * {@link AnalyticsRegistry}) uses this signal to pick the
 * synchronous-vs-async response shape for the REST endpoint.
 */
public enum ExecutionMode {
  /**
   * Detector runs in the backend JVM and returns its result
   * synchronously. The historical (and currently only live) tier.
   */
  IN_PROCESS,

  /**
   * Detector submits work to an external orchestrator and returns a
   * job handle. Caller polls for status, eventually fetches the result.
   * Future home of {@code shepard-plugin-mlops}; not yet wired in core.
   */
  VIA_ORCHESTRATOR,
}
