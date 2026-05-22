package de.dlr.shepard.context.references.timeseriesreference.services;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.plugins.analyticsts.MADDetector;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * AT1 — behavioural-equivalence test: proves the extracted
 * {@link MADDetector} math produces output identical, bit-for-bit,
 * to the in-tree {@link AnomalyDetectionService} math across a
 * representative input battery.
 *
 * <p>This test lives in {@code de.dlr.shepard.context.references.timeseriesreference.services}
 * (the in-tree package) so it can reach the package-private static
 * methods {@code AnomalyDetectionService.median /
 * computeZScores / rollingMadDetect}. Crossing the visibility seam
 * inside a test module is the standard idiom for byte-stability
 * proofs.
 *
 * <p>If this test ever fails, the extraction has introduced a
 * mathematical regression. Per the AT1 design (and
 * {@code CLAUDE.md feedback_reuse_trusted_code.md}), the medianing
 * primitive must stay {@code Arrays.sort} + index arithmetic. A
 * library substitution that changes even-length-window tie-breaking
 * would silently shift every Z-score — the algorithm is downstream
 * of {@code median} both for the rolling baseline and for the MAD
 * itself.
 */
class MADDetectorBehaviouralEquivalenceTest {

  // ── median primitive ─────────────────────────────────────────────────────

  @Test
  void median_matches_in_tree_for_odd_lengths() {
    double[] a = { 1.0, 5.0, 3.0 };
    assertThat(MADDetector.median(a)).isEqualTo(AnomalyDetectionService.median(a));
  }

  @Test
  void median_matches_in_tree_for_even_lengths() {
    double[] a = { 1.0, 2.0, 3.0, 4.0 };
    assertThat(MADDetector.median(a)).isEqualTo(AnomalyDetectionService.median(a));
  }

  @Test
  void median_matches_in_tree_for_window_of_one() {
    double[] a = { 42.0 };
    assertThat(MADDetector.median(a)).isEqualTo(AnomalyDetectionService.median(a));
  }

  @Test
  void median_matches_in_tree_for_random_inputs() {
    Random rng = new Random(0xC0FFEEL);
    for (int trial = 0; trial < 100; trial++) {
      int len = 1 + rng.nextInt(64);
      double[] a = new double[len];
      for (int i = 0; i < len; i++) a[i] = rng.nextGaussian() * 100.0;
      double extracted = MADDetector.median(a);
      double inTree = AnomalyDetectionService.median(a);
      assertThat(extracted)
        .as("len=%d trial=%d", len, trial)
        .isEqualTo(inTree);
    }
  }

  // ── computeZScores ───────────────────────────────────────────────────────

  @Test
  void zscores_match_in_tree_on_flat_series() {
    double[] v = new double[51];
    java.util.Arrays.fill(v, 7.0);
    assertSameDoubles(MADDetector.computeZScores(v, 11), AnomalyDetectionService.computeZScores(v, 11));
  }

  @Test
  void zscores_match_in_tree_on_spike_series() {
    int n = 51;
    double[] v = new double[n];
    java.util.Arrays.fill(v, 1.0);
    v[25] = 1000.0;
    assertSameDoubles(MADDetector.computeZScores(v, 11), AnomalyDetectionService.computeZScores(v, 11));
  }

  @Test
  void zscores_match_in_tree_on_random_gaussian_with_outliers() {
    Random rng = new Random(0xDEADBEEFL);
    int n = 200;
    double[] v = new double[n];
    for (int i = 0; i < n; i++) v[i] = rng.nextGaussian();
    // Inject 5 outliers.
    v[42] = 50.0;
    v[100] = -47.0;
    v[101] = 60.0;
    v[150] = 30.0;
    v[199] = -33.0;
    assertSameDoubles(MADDetector.computeZScores(v, 21), AnomalyDetectionService.computeZScores(v, 21));
  }

  @Test
  void zscores_match_in_tree_for_various_window_sizes() {
    Random rng = new Random(0xFEEDBEEFL);
    int n = 100;
    double[] v = new double[n];
    for (int i = 0; i < n; i++) v[i] = Math.sin(i / 7.0) + rng.nextGaussian() * 0.1;
    for (int window : new int[] { 5, 11, 21, 51, 99 }) {
      assertSameDoubles(MADDetector.computeZScores(v, window), AnomalyDetectionService.computeZScores(v, window));
    }
  }

  // ── rollingMadDetect ────────────────────────────────────────────────────

  @Test
  void detect_flags_match_in_tree() {
    int n = 51;
    double[] v = new double[n];
    java.util.Arrays.fill(v, 1.0);
    v[25] = 1000.0;
    v[26] = 1000.0;
    boolean[] extracted = MADDetector.rollingMadDetect(v, 11, 6.0);
    boolean[] inTree = AnomalyDetectionService.rollingMadDetect(v, 11, 6.0);
    assertThat(extracted).isEqualTo(inTree);
  }

  @Test
  void detect_flags_match_in_tree_for_random_series() {
    Random rng = new Random(0xBADBEEFL);
    int n = 256;
    double[] v = new double[n];
    for (int i = 0; i < n; i++) v[i] = rng.nextGaussian();
    v[64] = 25.0; // outlier
    v[128] = -25.0; // outlier
    for (double k : new double[] { 3.0, 6.0, 9.0 }) {
      boolean[] extracted = MADDetector.rollingMadDetect(v, 21, k);
      boolean[] inTree = AnomalyDetectionService.rollingMadDetect(v, 21, k);
      assertThat(extracted)
        .as("k=%s", k)
        .isEqualTo(inTree);
    }
  }

  // ── effective-window primitive ──────────────────────────────────────────

  @Test
  void effective_window_matches_in_tree() {
    int[][] cases = {
      { 50, 1000 }, // → 51
      { 51, 1000 }, // → 51
      { 201, 100 }, // → 99 (clamp + odd)
      { 99, 99 }, // → 99
      { 3, 5 }, // → 3
      { 4, 10 }, // → 5
    };
    for (int[] c : cases) {
      assertThat(MADDetector.effectiveWindow(c[0], c[1]))
        .as("raw=%d seriesLength=%d", c[0], c[1])
        .isEqualTo(AnomalyDetectionService.effectiveWindow(c[0], c[1]));
    }
  }

  // ── helper ──────────────────────────────────────────────────────────────

  /**
   * Strict double-for-double equality (no epsilon). The whole point of
   * this test is that the IEEE 754 result is identical when the
   * algorithm is unchanged.
   */
  private static void assertSameDoubles(double[] extracted, double[] inTree) {
    assertThat(extracted.length).isEqualTo(inTree.length);
    for (int i = 0; i < extracted.length; i++) {
      // Distinguish 0.0 / -0.0 (Double.compare) AND treat NaN==NaN as equal:
      // both implementations produce the same NaN pattern when MAD floor kicks in
      // on a flat zero-variance window.
      assertThat(Double.doubleToRawLongBits(extracted[i]))
        .as("index=%d extracted=%s inTree=%s", i, extracted[i], inTree[i])
        .isEqualTo(Double.doubleToRawLongBits(inTree[i]));
    }
  }
}
