package de.dlr.shepard.spi.analytics;

/**
 * AT1 — SPI for pluggable timeseries-analytics detectors (anomaly,
 * quality, novelty, classification). The original (and currently only
 * canonical) member of this family is the rolling-median MAD anomaly
 * detector that landed in-tree as AI1b — this SPI is the seam that
 * extracts it into {@code shepard-plugin-analytics-ts}.
 *
 * <h2>Implementation contract</h2>
 *
 * <p>An implementation:
 * <ul>
 *   <li>Declares a stable {@link #id()} (lowercase + hyphen; e.g.
 *       {@code "mad-v1"}). The id is the runtime selector exposed via
 *       the optional {@code detectorId} request field — new detectors
 *       drop into the registry without breaking the v2 wire shape.</li>
 *   <li>Declares its {@link #executionMode()} — {@link
 *       ExecutionMode#IN_PROCESS} (default) for light detectors that
 *       run synchronously inside the backend JVM, or
 *       {@link ExecutionMode#VIA_ORCHESTRATOR} for heavy detectors that
 *       submit jobs externally (see {@link RemoteTimeseriesAnalytics}).</li>
 *   <li>For {@code IN_PROCESS} detectors: implements
 *       {@link #detect(TimeseriesAnalyticsInput)} which runs to
 *       completion and returns a non-null
 *       {@link AnomalyDetectionResult}. Throws
 *       {@link IllegalArgumentException} for bad parameters.</li>
 * </ul>
 *
 * <h2>Discovery + registration</h2>
 *
 * <p>Discovery is via CDI. The {@link AnalyticsRegistry} bean collects
 * every {@code @ApplicationScoped TimeseriesAnalytics} on the
 * classpath at startup and indexes them by {@link #id()}. Plugins
 * register their implementation as a CDI bean (their JAR is on the
 * backend's build classpath via the {@code with-plugins} Maven profile
 * + Quarkus {@code quarkus.index-dependency} declaration — the
 * established shepard plugin pattern).
 *
 * <h2>Wire-stability</h2>
 *
 * <p>This SPI lives in the v2 development surface. The currently shipped
 * REST endpoint {@code POST /v2/timeseries-references/{refAppId}/detect-anomalies}
 * is fork-added (not upstream v5.2.0). PR-4 of the AT1 extraction
 * preserves byte-stable output for clients omitting the new
 * {@code detectorId} field (recorded-fixture proof in
 * {@code AnomalyDetectionWireStabilityTest}).
 *
 * @see RemoteTimeseriesAnalytics
 * @see ExecutionMode
 * @see AnalyticsRegistry
 */
public interface TimeseriesAnalytics {
  /**
   * Stable detector identifier; selector for the optional
   * {@code detectorId} request field. Lowercase, hyphen-separated.
   * Two detectors claiming the same id collide on startup and the
   * second registration is rejected ({@code analytics.discovery.failed}
   * log entry).
   *
   * <p>Examples: {@code "mad-v1"}, {@code "stl-residual-v1"},
   * {@code "iforest-v2"}.
   */
  String id();

  /**
   * Human-readable display name for admin UI / logs. Defaults to
   * {@link #id()} when not overridden.
   */
  default String title() {
    return id();
  }

  /**
   * Execution-mode tier. Defaults to {@link ExecutionMode#IN_PROCESS}
   * — the historical (and currently only live) tier. Heavy detectors
   * override to {@link ExecutionMode#VIA_ORCHESTRATOR} and implement
   * {@link RemoteTimeseriesAnalytics}.
   *
   * <p>The dispatching layer in the backend uses this to pick the
   * REST response shape (synchronous result vs. job handle).
   */
  default ExecutionMode executionMode() {
    return ExecutionMode.IN_PROCESS;
  }

  /**
   * Run anomaly detection on the given input. Implementations of the
   * {@link ExecutionMode#IN_PROCESS} tier must implement this method
   * substantively. Implementations of {@link
   * ExecutionMode#VIA_ORCHESTRATOR} should throw
   * {@link UnsupportedOperationException} — callers select the async
   * path via {@link RemoteTimeseriesAnalytics#submitJob}.
   *
   * @param input non-null parallel-array data + detector parameters
   * @return non-null result (empty intervals list when nothing flagged)
   * @throws IllegalArgumentException for invalid parameters (e.g.
   *         {@code window < 3}, {@code k <= 0})
   * @throws UnsupportedOperationException if this detector's
   *         {@link #executionMode()} is {@link ExecutionMode#VIA_ORCHESTRATOR}
   */
  AnomalyDetectionResult detect(TimeseriesAnalyticsInput input);
}
