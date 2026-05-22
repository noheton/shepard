package de.dlr.shepard.plugins.analyticsts;

import de.dlr.shepard.spi.analytics.AnomalyDetectionResult;
import de.dlr.shepard.spi.analytics.AnomalyInterval;
import de.dlr.shepard.spi.analytics.ExecutionMode;
import de.dlr.shepard.spi.analytics.TimeseriesAnalytics;
import de.dlr.shepard.spi.analytics.TimeseriesAnalyticsInput;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * AT1 — rolling-median MAD anomaly detector, extracted from the
 * in-tree AI1b {@code AnomalyDetectionService}. Implements the
 * {@code TimeseriesAnalytics} SPI at the {@link ExecutionMode#IN_PROCESS}
 * tier; registered as detector id {@code "mad-v1"}.
 *
 * <h2>Algorithm</h2>
 * <p>Pure-Java translation of the reference Python:
 * <pre>{@code
 * def rolling_mad_detect(t, v, window=51, k=6.0):
 *     if window % 2 == 0:
 *         window += 1
 *     half = window // 2
 *     pad_v = np.pad(v, (half, half), mode='edge')
 *     med = [median(pad_v[i:i+window]) for i in range(len(v))]
 *     mad = [median(|pad_v[i:i+window] - med[i]|) for i in range(len(v))]
 *     mad = max(mad, 1e-3)     # floor to avoid div-by-zero
 *     z   = (v - med) / (1.4826 * mad)
 *     return z, |z| > k
 * }</pre>
 *
 * <h2>Behavioural equivalence</h2>
 * <p>The {@link #median(double[])} primitive is identical to the
 * in-tree version (JDK {@code Arrays.sort} + index arithmetic). This
 * matters because the entire detector is downstream of {@code median}
 * — substituting a library that handles even-length-window ties
 * differently would silently shift every Z-score. The recorded-fixture
 * test {@code MADDetectorBehaviouralEquivalenceTest} proves
 * byte-for-byte identical output across the test battery.
 *
 * <h2>Parameters</h2>
 * <p>Read from {@link TimeseriesAnalyticsInput#parameters()}:
 * <ul>
 *   <li>{@code window} (Integer; default 51) — rolling window. Forced
 *       odd; clamped to series length.</li>
 *   <li>{@code k} (Double; default 6.0) — threshold on |Z-score|.</li>
 * </ul>
 * Both have backstop defaults so a caller may send an empty parameter
 * map.
 */
@ApplicationScoped
public class MADDetector implements TimeseriesAnalytics {

  /** Stable detector id. Matches {@code AnalyticsRegistry.DEFAULT_DETECTOR_ID}. */
  public static final String ID = "mad-v1";

  /** MAD → σ scale factor for the Gaussian approximation. */
  static final double CONSISTENCY_FACTOR = 1.4826;

  /** MAD floor to avoid division by zero. Verbatim from in-tree AI1b. */
  static final double MAD_FLOOR = 1e-3;

  /** Default rolling-window size when caller doesn't specify. */
  static final int DEFAULT_WINDOW = 51;

  /** Default |Z|-threshold when caller doesn't specify. */
  static final double DEFAULT_K = 6.0;

  @Override
  public String id() {
    return ID;
  }

  @Override
  public String title() {
    return "Rolling-median MAD anomaly detector (mad-v1)";
  }

  @Override
  public ExecutionMode executionMode() {
    return ExecutionMode.IN_PROCESS;
  }

  @Override
  public AnomalyDetectionResult detect(TimeseriesAnalyticsInput input) {
    int rawWindow = readWindow(input);
    double k = readK(input);
    if (rawWindow < 3) {
      throw new IllegalArgumentException("window must be ≥ 3, got " + rawWindow);
    }
    if (k <= 0) {
      throw new IllegalArgumentException("k must be > 0, got " + k);
    }

    double[] v = input.values();
    long[] ts = input.timestamps();
    int n = v.length;
    if (n == 0) {
      return new AnomalyDetectionResult(List.of(), 0, k, 0);
    }

    int window = effectiveWindow(rawWindow, n);
    boolean[] flags = rollingMadDetect(v, window, k);
    double[] z = computeZScores(v, window);
    List<AnomalyInterval> intervals = collectIntervals(ts, v, z, flags);

    return new AnomalyDetectionResult(intervals, window, k, n);
  }

  // ── parameter readers ─────────────────────────────────────────────────────

  public static int readWindow(TimeseriesAnalyticsInput input) {
    Object raw = input.parameters().get("window");
    if (raw == null) return DEFAULT_WINDOW;
    if (raw instanceof Number n) return n.intValue();
    throw new IllegalArgumentException("window parameter must be a number, got " + raw.getClass().getName());
  }

  public static double readK(TimeseriesAnalyticsInput input) {
    Object raw = input.parameters().get("k");
    if (raw == null) return DEFAULT_K;
    if (raw instanceof Number n) return n.doubleValue();
    throw new IllegalArgumentException("k parameter must be a number, got " + raw.getClass().getName());
  }

  // ── algorithm (extracted verbatim from AI1b AnomalyDetectionService) ────

  /**
   * Force window odd, then clamp to series length (also odd).
   * Minimum effective window is 1 (single-element degenerate case).
   *
   * <p>Identical to {@code AnomalyDetectionService.effectiveWindow}.
   */
  public static int effectiveWindow(int rawWindow, int seriesLength) {
    int w = (rawWindow % 2 == 0) ? rawWindow + 1 : rawWindow;
    if (w > seriesLength) {
      w = seriesLength;
      if (w % 2 == 0) w = Math.max(1, w - 1);
    }
    return w;
  }

  /**
   * Compute per-point Z-scores using rolling-median MAD.
   * Result array is parallel to {@code v}.
   *
   * <p>Identical to {@code AnomalyDetectionService.computeZScores}.
   */
  public static double[] computeZScores(double[] v, int window) {
    int n = v.length;
    int half = window / 2;
    double[] padded = new double[n + 2 * half];
    for (int i = 0; i < half; i++) padded[i] = v[0];
    System.arraycopy(v, 0, padded, half, n);
    for (int i = half + n; i < padded.length; i++) padded[i] = v[n - 1];

    double[] med = new double[n];
    double[] mad = new double[n];
    double[] windowBuf = new double[window];

    for (int i = 0; i < n; i++) {
      System.arraycopy(padded, i, windowBuf, 0, window);
      double m = median(windowBuf);
      med[i] = m;

      double[] absDevs = new double[window];
      for (int j = 0; j < window; j++) absDevs[j] = Math.abs(windowBuf[j] - m);
      double madVal = median(absDevs);
      mad[i] = Math.max(madVal, MAD_FLOOR);
    }

    double[] z = new double[n];
    for (int i = 0; i < n; i++) {
      z[i] = (v[i] - med[i]) / (CONSISTENCY_FACTOR * mad[i]);
    }
    return z;
  }

  /**
   * Run the full rolling-MAD detect: returns a boolean array where
   * {@code true} indicates an anomalous point (|z| > k).
   *
   * <p>Identical to {@code AnomalyDetectionService.rollingMadDetect}.
   */
  public static boolean[] rollingMadDetect(double[] v, int window, double k) {
    double[] z = computeZScores(v, window);
    boolean[] flags = new boolean[v.length];
    for (int i = 0; i < v.length; i++) {
      flags[i] = Math.abs(z[i]) > k;
    }
    return flags;
  }

  /**
   * Collect contiguous runs of {@code true} in {@code anomalyFlags}
   * into {@link AnomalyInterval} records, tracking per-interval peak
   * value and max |Z-score|.
   *
   * <p>Mirrors {@code AnomalyDetectionService.collectIntervals} but
   * targets the SPI {@link AnomalyInterval} record (structurally
   * identical to the in-tree {@code AnomalyIntervalIO} wire-shape;
   * mapping is one-to-one).
   */
  public static List<AnomalyInterval> collectIntervals(long[] timestamps, double[] v, double[] zScores, boolean[] anomalyFlags) {
    List<AnomalyInterval> out = new ArrayList<>();
    int n = anomalyFlags.length;
    int i = 0;
    while (i < n) {
      if (anomalyFlags[i]) {
        int start = i;
        double maxZ = Math.abs(zScores[i]);
        double peakV = v[i];
        while (i < n && anomalyFlags[i]) {
          double absZ = Math.abs(zScores[i]);
          if (absZ > maxZ) {
            maxZ = absZ;
            peakV = v[i];
          }
          i++;
        }
        int end = i - 1;
        out.add(new AnomalyInterval(timestamps[start], timestamps[end], peakV, maxZ));
      } else {
        i++;
      }
    }
    return out;
  }

  /**
   * Compute the median of an array <em>in-place</em> (sorts a copy).
   * Undefined for empty arrays (caller guarantees window ≥ 1).
   *
   * <p><strong>Verbatim copy</strong> of {@code AnomalyDetectionService.median}.
   * Keeping JDK {@code Arrays.sort} + index arithmetic is intentional:
   * substituting a library that handles even-length-window ties
   * differently would silently shift every Z-score. The
   * behavioural-equivalence test enforces this invariant.
   */
  public static double median(double[] a) {
    double[] copy = Arrays.copyOf(a, a.length);
    Arrays.sort(copy);
    int mid = copy.length / 2;
    if (copy.length % 2 == 0) {
      return (copy[mid - 1] + copy[mid]) / 2.0;
    }
    return copy[mid];
  }
}
