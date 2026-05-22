package de.dlr.shepard.plugins.analyticsts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.dlr.shepard.spi.analytics.AnomalyDetectionResult;
import de.dlr.shepard.spi.analytics.AnomalyInterval;
import de.dlr.shepard.spi.analytics.ExecutionMode;
import de.dlr.shepard.spi.analytics.TimeseriesAnalyticsInput;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * AT1 — unit tests for {@link MADDetector}: identity / mode declaration,
 * parameter parsing, edge cases, deterministic algorithm output.
 */
class MADDetectorTest {

  // ── identity ─────────────────────────────────────────────────────────────

  @Test
  void detector_id_is_mad_v1() {
    assertThat(new MADDetector().id()).isEqualTo("mad-v1");
  }

  @Test
  void detector_executes_in_process() {
    assertThat(new MADDetector().executionMode()).isEqualTo(ExecutionMode.IN_PROCESS);
  }

  @Test
  void detector_has_a_human_readable_title() {
    assertThat(new MADDetector().title()).contains("MAD").contains("mad-v1");
  }

  // ── median primitive (verbatim from in-tree median()) ───────────────────

  @Test
  void median_of_odd_length_returns_middle_element() {
    assertThat(MADDetector.median(new double[] { 1.0, 5.0, 3.0 })).isEqualTo(3.0);
  }

  @Test
  void median_of_even_length_returns_average_of_two_middles() {
    assertThat(MADDetector.median(new double[] { 1.0, 2.0, 3.0, 4.0 })).isEqualTo(2.5);
  }

  @Test
  void median_of_single_element_returns_that_element() {
    assertThat(MADDetector.median(new double[] { 42.0 })).isEqualTo(42.0);
  }

  // ── effective window ────────────────────────────────────────────────────

  @Test
  void effective_window_forces_odd() {
    assertThat(MADDetector.effectiveWindow(50, 1000)).isEqualTo(51);
  }

  @Test
  void effective_window_keeps_odd_unchanged() {
    assertThat(MADDetector.effectiveWindow(51, 1000)).isEqualTo(51);
  }

  @Test
  void effective_window_clamps_to_series_length_when_too_large() {
    // 100-point series, requested window 201 → clamped to 99 (largest odd ≤ 100)
    assertThat(MADDetector.effectiveWindow(201, 100)).isEqualTo(99);
  }

  @Test
  void effective_window_clamps_to_odd_series_length_unchanged() {
    assertThat(MADDetector.effectiveWindow(99, 99)).isEqualTo(99);
  }

  // ── parameter validation ────────────────────────────────────────────────

  @Test
  void detect_rejects_window_less_than_three() {
    var d = new MADDetector();
    var input = input(new long[] { 0, 1, 2 }, new double[] { 1.0, 2.0, 3.0 }, Map.of("window", 2));
    assertThatThrownBy(() -> d.detect(input))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("window must be ≥ 3");
  }

  @Test
  void detect_rejects_non_positive_k() {
    var d = new MADDetector();
    var input = input(new long[] { 0, 1, 2 }, new double[] { 1.0, 2.0, 3.0 }, Map.of("k", 0.0));
    assertThatThrownBy(() -> d.detect(input))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("k must be > 0");
  }

  @Test
  void detect_rejects_non_numeric_window_parameter() {
    var d = new MADDetector();
    var input = input(new long[] { 0, 1, 2 }, new double[] { 1.0, 2.0, 3.0 }, Map.of("window", "fifty"));
    assertThatThrownBy(() -> d.detect(input))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("window parameter must be a number");
  }

  // ── empty-series behaviour ──────────────────────────────────────────────

  @Test
  void detect_on_empty_series_returns_zero_total_points_and_no_intervals() {
    var d = new MADDetector();
    var input = input(new long[] {}, new double[] {}, Map.of());
    AnomalyDetectionResult result = d.detect(input);
    assertThat(result.totalPoints()).isZero();
    assertThat(result.intervals()).isEmpty();
    assertThat(result.k()).isEqualTo(MADDetector.DEFAULT_K);
  }

  // ── algorithmic correctness on a synthetic spike ────────────────────────

  @Test
  void detect_flags_synthetic_spike_in_otherwise_flat_series() {
    // 41 points: 20 at 1.0, one 1000.0 spike at index 20, 20 at 1.0.
    int n = 41;
    long[] ts = new long[n];
    double[] v = new double[n];
    for (int i = 0; i < n; i++) {
      ts[i] = (long) i * 1_000_000L; // 1 ms cadence in ns
      v[i] = (i == 20) ? 1000.0 : 1.0;
    }
    var d = new MADDetector();
    var input = input(ts, v, Map.of("window", 11, "k", 6.0));
    AnomalyDetectionResult result = d.detect(input);

    assertThat(result.intervals()).isNotEmpty();
    // The spike must be in at least one detected interval.
    boolean spikeInside = result.intervals().stream()
      .anyMatch(iv -> iv.startNs() <= 20_000_000L && iv.endNs() >= 20_000_000L);
    assertThat(spikeInside).as("spike at index 20 must fall inside a detected interval").isTrue();
  }

  @Test
  void detect_does_not_flag_flat_series_with_no_outliers() {
    int n = 41;
    long[] ts = new long[n];
    double[] v = new double[n];
    for (int i = 0; i < n; i++) {
      ts[i] = (long) i;
      v[i] = 7.0;
    }
    var d = new MADDetector();
    AnomalyDetectionResult result = d.detect(input(ts, v, Map.of("window", 11, "k", 6.0)));

    assertThat(result.intervals()).isEmpty();
    assertThat(result.totalPoints()).isEqualTo(n);
  }

  @Test
  void detect_collects_contiguous_anomalous_run_as_single_interval() {
    // Spike run from index 20..23 inclusive (4 anomalous points).
    int n = 51;
    long[] ts = new long[n];
    double[] v = new double[n];
    for (int i = 0; i < n; i++) {
      ts[i] = (long) i * 1_000L;
      v[i] = (i >= 20 && i <= 23) ? 1000.0 : 1.0;
    }
    AnomalyDetectionResult result = new MADDetector().detect(input(ts, v, Map.of("window", 11, "k", 6.0)));

    assertThat(result.intervals()).hasSize(1);
    AnomalyInterval iv = result.intervals().get(0);
    assertThat(iv.startNs()).isEqualTo(20_000L);
    assertThat(iv.endNs()).isEqualTo(23_000L);
    assertThat(iv.peakValue()).isEqualTo(1000.0);
  }

  // ── helper ──────────────────────────────────────────────────────────────

  private static TimeseriesAnalyticsInput input(long[] ts, double[] v, Map<String, Object> params) {
    Map<String, Object> mutable = new HashMap<>(params);
    return new TimeseriesAnalyticsInput(ts, v, mutable, "ref-test");
  }
}
