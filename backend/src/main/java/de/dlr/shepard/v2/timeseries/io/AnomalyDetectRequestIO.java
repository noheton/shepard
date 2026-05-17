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

  // ── effective defaults ───────────────────────────────────────────────────

  /** Returns the configured window, defaulting to 51. */
  public int effectiveWindow() {
    return window != null ? window : 51;
  }

  /** Returns the configured threshold k, defaulting to 6.0. */
  public double effectiveK() {
    return k != null ? k : 6.0;
  }
}
