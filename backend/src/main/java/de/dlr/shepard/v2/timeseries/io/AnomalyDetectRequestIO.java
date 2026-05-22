package de.dlr.shepard.v2.timeseries.io;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * AI1b — request body for
 * {@code POST /v2/timeseries-references/{refAppId}/detect-anomalies}.
 *
 * <p>Series selection: if the referenced TimeseriesReference holds exactly
 * one series the filter fields may be omitted and that series is used
 * automatically. When the reference holds multiple series, supply any
 * non-null subset of the five filter fields (measurement / device /
 * location / symbolicName / field); only the non-null fields are applied
 * as an exact-match filter. The request is rejected with {@code 400} if
 * the filter resolves to zero or more than one series.
 *
 * <p>All numeric fields have defaults so the caller may send an empty body
 * ({@code {}}) to run with defaults against a single-series reference.
 */
@Data
@NoArgsConstructor
@Schema(description = "Request parameters for the rolling-median MAD anomaly detector.")
public class AnomalyDetectRequestIO {

  /**
   * Rolling window size (number of data points). Must be ≥ 3.
   * Forced odd: if even, incremented by 1 before use.
   * If larger than the series length, the effective window is clamped
   * to the series length (also forced odd). The actual window size
   * used is reflected in the response's {@code windowSize} field.
   * Default: 51.
   */
  @Schema(description = "Rolling window size (data points). Must be ≥ 3. Forced odd. Clamped to series length if too large. Default: 51.", defaultValue = "51")
  private Integer window;

  /**
   * Anomaly threshold: a point is anomalous when |Z-score| > k,
   * where Z = (v - median) / (1.4826 * MAD). Must be > 0.
   * Default: 6.0.
   */
  @Schema(description = "Anomaly threshold on the Z-score (|z| > k is flagged). Must be > 0. Default: 6.0.", defaultValue = "6.0")
  private Double k;

  /**
   * When {@code true}, each detected contiguous anomaly interval is
   * persisted as a {@link de.dlr.shepard.v2.timeseries.model.TimeseriesAnnotation}
   * with label {@code "anomaly"}, {@code aiGenerated=true}, and
   * confidence = min(1.0, maxZScore / (2 * k)). Requires Write
   * permission on the parent DataObject (the same permission gate as
   * {@link de.dlr.shepard.v2.timeseries.resources.TimeseriesAnnotationRest#create}).
   * Default: false.
   */
  @Schema(description = "If true, persist a TimeseriesAnnotation per detected interval (label 'anomaly', aiGenerated=true). Requires Write permission. Default: false.", defaultValue = "false")
  private boolean createAnnotations = false;

  // ── series selector ──────────────────────────────────────────────────────

  @Schema(description = "Measurement tag — selects which series in the reference to score. Omit when the reference contains exactly one series.")
  private String measurement;

  @Schema(description = "Device tag — selects which series in the reference to score.")
  private String device;

  @Schema(description = "Location tag — selects which series in the reference to score.")
  private String location;

  @Schema(description = "SymbolicName tag — selects which series in the reference to score.")
  private String symbolicName;

  @Schema(description = "Field tag — selects which series in the reference to score.")
  private String field;

  // ── detector selector (AT1) ─────────────────────────────────────────────

  /**
   * AT1 — optional detector identifier. Resolves to a registered
   * {@code de.dlr.shepard.spi.analytics.TimeseriesAnalytics} bean via
   * {@code AnalyticsRegistry}. When null or omitted, the request runs
   * against the default detector {@code "mad-v1"} (the rolling-median
   * MAD detector that historically backed this endpoint as the only
   * implementation).
   *
   * <p>Adding new detectors is additive — they drop into the registry
   * via {@code shepard-plugin-analytics-ts} (or a sibling plugin) and
   * become reachable through this field without breaking existing
   * callers.
   *
   * <p>Wire-stability invariant: a request that omits this field is
   * byte-identical (in response shape and content) to one before the
   * AT1 extraction — see {@code AnomalyDetectionWireStabilityTest}
   * for the recorded-fixture proof.
   */
  @Schema(
    description = "Detector identifier (AT1). Default 'mad-v1' (the rolling-median MAD detector). " +
      "Future detectors register via shepard-plugin-analytics-ts and become reachable through this field.",
    defaultValue = "mad-v1"
  )
  private String detectorId;

  // ── effective defaults ───────────────────────────────────────────────────

  /** Returns the configured window, defaulting to 51. */
  public int effectiveWindow() {
    return window != null ? window : 51;
  }

  /** Returns the configured threshold k, defaulting to 6.0. */
  public double effectiveK() {
    return k != null ? k : 6.0;
  }

  /**
   * AT1 — resolves the detector id, defaulting to {@code "mad-v1"} when
   * omitted. Used by the dispatcher to look up via
   * {@code AnalyticsRegistry}.
   */
  public String effectiveDetectorId() {
    return (detectorId == null || detectorId.isBlank()) ? "mad-v1" : detectorId;
  }
}
