package de.dlr.shepard.context.references.timeseriesreference.services;

import de.dlr.shepard.context.references.timeseriesreference.daos.TimeseriesReferenceDAO;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPointsQueryParams;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import de.dlr.shepard.v2.timeseries.daos.TimeseriesAnnotationDAO;
import de.dlr.shepard.v2.timeseries.io.AnomalyDetectRequestIO;
import de.dlr.shepard.v2.timeseries.io.AnomalyDetectResultIO;
import de.dlr.shepard.v2.timeseries.io.AnomalyIntervalIO;
import de.dlr.shepard.v2.timeseries.model.TimeseriesAnnotation;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * AI1b — rolling-median MAD anomaly detector for timeseries data.
 *
 * <h2>Algorithm</h2>
 * <p>Implements a pure-Java translation of the reference Python:
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
 * <h2>Series selection</h2>
 * <p>The reference may hold one or more referenced series. The request
 * filter fields (measurement / device / location / symbolicName / field)
 * are applied to select exactly one series:
 * <ul>
 *   <li>If all five are null and the reference holds exactly one series,
 *       that series is used automatically.</li>
 *   <li>Otherwise all five must match exactly one series; a request
 *       that resolves to zero or multiple series is rejected by the
 *       REST layer (not this service).</li>
 * </ul>
 *
 * <h2>Non-numeric values</h2>
 * <p>Data points with {@code null} or non-numeric values are <em>skipped</em>
 * during algorithm input extraction. If no numeric points remain the
 * result is an empty anomaly list with {@code totalPoints = 0}.
 *
 * <h2>Confidence scoring</h2>
 * <p>Annotation confidence = {@code min(1.0, maxZScore / (2 * k))}. A
 * point at exactly {@code k + ε} scores ≈ 0.5; a point at {@code 2k}
 * scores 1.0. This is monotonically increasing and interpretable:
 * "how extreme relative to the threshold" — unlike the naive
 * {@code maxZ / k} which is always ≥ 1.0 and thus always clips.
 */
@RequestScoped
public class AnomalyDetectionService {

  /** MAD → σ scale factor for the Gaussian approximation. */
  static final double CONSISTENCY_FACTOR = 1.4826;

  /** MAD floor to avoid division by zero. */
  static final double MAD_FLOOR = 1e-3;

  @Inject
  TimeseriesReferenceDAO timeseriesReferenceDAO;

  @Inject
  TimeseriesService timeseriesService;

  @Inject
  TimeseriesAnnotationDAO annotationDAO;

  // ── public API ────────────────────────────────────────────────────────────

  /**
   * Resolve the reference, select the series, fetch data, run the
   * rolling-MAD detector, optionally create annotations, and return
   * the result.
   *
   * <p>The caller is responsible for:
   * <ul>
   *   <li>Auth gate (Read for detection; Write when createAnnotations=true)</li>
   *   <li>Resolving the {@link TimeseriesReference} and passing it in</li>
   *   <li>Selecting the single {@link Timeseries} from the reference</li>
   * </ul>
   *
   * @param ref     resolved, non-null TimeseriesReference
   * @param series  the single series to score
   * @param request detection parameters
   * @return detection result
   */
  public AnomalyDetectResultIO detect(
    TimeseriesReference ref,
    Timeseries series,
    AnomalyDetectRequestIO request
  ) {
    // 1. Validate parameters
    int rawWindow = request.effectiveWindow();
    double k = request.effectiveK();
    if (rawWindow < 3) {
      throw new IllegalArgumentException("window must be ≥ 3, got " + rawWindow);
    }
    if (k <= 0) {
      throw new IllegalArgumentException("k must be > 0, got " + k);
    }

    // 2. Fetch data points
    var containerId = ref.getTimeseriesContainer().getId();
    var queryParams = new TimeseriesDataPointsQueryParams(ref.getStart(), ref.getEnd(), null, null, null);
    List<TimeseriesDataPoint> raw = timeseriesService.getDataPointsByTimeseries(containerId, series, queryParams);

    // 3. Extract numeric values and timestamps (skip null / non-numeric)
    List<Long> timestamps = new ArrayList<>();
    List<Double> values = new ArrayList<>();
    for (TimeseriesDataPoint dp : raw) {
      Double v = TimeseriesQualityScorer.toDouble(dp);
      if (v != null) {
        timestamps.add(dp.getTimestamp());
        values.add(v);
      }
    }

    if (values.isEmpty()) {
      Log.debugf("AI1b: ref=%s series=%s — no numeric points; returning empty result", ref.getAppId(), series);
      return new AnomalyDetectResultIO(List.of(), 0, k, 0, 0);
    }

    // 4. Compute effective window (odd, clamped to series length)
    int n = values.size();
    int window = effectiveWindow(rawWindow, n);

    // 5. Run rolling-MAD
    double[] v = values.stream().mapToDouble(Double::doubleValue).toArray();
    boolean[] anomalyFlags = rollingMadDetect(v, window, k);
    double[] zScores = computeZScores(v, window);

    // 6. Collect contiguous intervals
    List<AnomalyIntervalIO> intervals = collectIntervals(timestamps, v, zScores, anomalyFlags);

    // 7. Optionally persist annotations
    int annotationsCreated = 0;
    if (request.isCreateAnnotations()) {
      for (AnomalyIntervalIO interval : intervals) {
        TimeseriesAnnotation ann = new TimeseriesAnnotation();
        ann.setStartNs(interval.startNs());
        ann.setEndNs(interval.endNs() == interval.startNs() ? null : interval.endNs());
        ann.setLabel("anomaly");
        ann.setAiGenerated(true);
        ann.setConfidence(Math.min(1.0, interval.maxZScore() / (2.0 * k)));
        annotationDAO.createOrUpdate(ann);
        annotationDAO.linkToReference(ref.getAppId(), ann.getAppId());
        annotationsCreated++;
      }
    }

    return new AnomalyDetectResultIO(intervals, window, k, n, annotationsCreated);
  }

  // ── algorithm ─────────────────────────────────────────────────────────────

  /**
   * Force window odd, then clamp to series length (also odd).
   * Minimum effective window is 1 (single-element degenerate case).
   */
  static int effectiveWindow(int rawWindow, int seriesLength) {
    // Force odd
    int w = (rawWindow % 2 == 0) ? rawWindow + 1 : rawWindow;
    // Clamp to series length (also forced odd when clamped)
    if (w > seriesLength) {
      w = seriesLength;
      if (w % 2 == 0) w = Math.max(1, w - 1);
    }
    return w;
  }

  /**
   * Compute per-point Z-scores using rolling-median MAD.
   * Result array is parallel to {@code v}.
   */
  static double[] computeZScores(double[] v, int window) {
    int n = v.length;
    int half = window / 2;
    // Edge-pad
    double[] padded = new double[n + 2 * half];
    // left edge: repeat v[0]
    for (int i = 0; i < half; i++) padded[i] = v[0];
    System.arraycopy(v, 0, padded, half, n);
    // right edge: repeat v[n-1]
    for (int i = half + n; i < padded.length; i++) padded[i] = v[n - 1];

    double[] med = new double[n];
    double[] mad = new double[n];
    double[] windowBuf = new double[window];

    for (int i = 0; i < n; i++) {
      // Extract window slice
      System.arraycopy(padded, i, windowBuf, 0, window);
      double m = median(windowBuf);
      med[i] = m;

      // Compute absolute deviations
      double[] absDevs = new double[window];
      for (int j = 0; j < window; j++) absDevs[j] = Math.abs(windowBuf[j] - m);
      double madVal = median(absDevs);
      mad[i] = Math.max(madVal, MAD_FLOOR);
    }

    // Z-scores
    double[] z = new double[n];
    for (int i = 0; i < n; i++) {
      z[i] = (v[i] - med[i]) / (CONSISTENCY_FACTOR * mad[i]);
    }
    return z;
  }

  /**
   * Run the full rolling-MAD detect: returns a boolean array where
   * {@code true} indicates an anomalous point (|z| > k).
   */
  static boolean[] rollingMadDetect(double[] v, int window, double k) {
    double[] z = computeZScores(v, window);
    boolean[] flags = new boolean[v.length];
    for (int i = 0; i < v.length; i++) {
      flags[i] = Math.abs(z[i]) > k;
    }
    return flags;
  }

  /**
   * Collect contiguous runs of {@code true} in {@code anomalyFlags} into
   * {@link AnomalyIntervalIO} records, tracking per-interval peak value
   * and max |Z-score|.
   */
  static List<AnomalyIntervalIO> collectIntervals(
    List<Long> timestamps,
    double[] v,
    double[] zScores,
    boolean[] anomalyFlags
  ) {
    List<AnomalyIntervalIO> out = new ArrayList<>();
    int n = anomalyFlags.length;
    int i = 0;
    while (i < n) {
      if (anomalyFlags[i]) {
        int start = i;
        double maxZ = Math.abs(zScores[i]);
        double peakV = v[i];
        // Extend run
        while (i < n && anomalyFlags[i]) {
          double absZ = Math.abs(zScores[i]);
          if (absZ > maxZ) {
            maxZ = absZ;
            peakV = v[i];
          }
          i++;
        }
        int end = i - 1;
        out.add(new AnomalyIntervalIO(timestamps.get(start), timestamps.get(end), peakV, maxZ));
      } else {
        i++;
      }
    }
    return out;
  }

  /**
   * Compute the median of an array <em>in-place</em> (sorts a copy).
   * Undefined for empty arrays (caller guarantees window ≥ 1).
   */
  static double median(double[] a) {
    double[] copy = Arrays.copyOf(a, a.length);
    Arrays.sort(copy);
    int mid = copy.length / 2;
    if (copy.length % 2 == 0) {
      return (copy[mid - 1] + copy[mid]) / 2.0;
    }
    return copy[mid];
  }
}
