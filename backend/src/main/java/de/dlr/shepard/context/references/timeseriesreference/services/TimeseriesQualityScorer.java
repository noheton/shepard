package de.dlr.shepard.context.references.timeseriesreference.services;

import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;

/**
 * AI1c — pure-heuristic channel-quality scorer for
 * {@code TimeseriesReference}. Combines three orthogonal signals into a
 * single {@code [0.0, 1.0]} quality score:
 *
 * <ol>
 *   <li><b>Completeness</b> — {@code 1 - missing_value_ratio}. A data
 *       point is "missing" if its value is {@code null}.</li>
 *   <li><b>Coverage</b> — {@code points / expected_points_in_window}
 *       where {@code expected = (lastTimestamp - firstTimestamp) /
 *       median_dt}. Clamped to {@code [0, 1]}.</li>
 *   <li><b>Stability</b> — {@code 1 - clamp(stddev / |mean|, 0, 1)} for
 *       numeric series with {@code mean != 0}. A proxy for "is this
 *       a signal or pure noise?". Skipped when the mean is zero or
 *       the values aren't numeric.</li>
 * </ol>
 *
 * <p>The combined score is the simple average of whichever signals
 * could be computed for the sample. Series shorter than
 * {@link #MIN_POINTS_FOR_SCORING} return {@link Optional#empty()} —
 * the caller leaves {@code qualityScore = null} so an insufficient
 * sample is distinguishable from a deliberately-low score.
 *
 * <p>Pure heuristics — no LLM calls. Independent of AI1a. Design in
 * {@code aidocs/43 §3.2}.
 *
 * <p>SpotBugs: this class deliberately scores arbitrary user-supplied
 * data shapes. The numeric coercion path is null-safe and tolerant of
 * non-Number objects (string-typed timeseries, etc.) — they simply
 * cause the stability signal to be skipped.
 */
@ApplicationScoped
public class TimeseriesQualityScorer {

  /**
   * Minimum sample size for a meaningful score. Below this, the
   * coverage + stability signals are too noisy to be useful and we
   * return {@link Optional#empty()}.
   */
  public static final int MIN_POINTS_FOR_SCORING = 10;

  /**
   * Maximum number of data points the job feeds the scorer (the
   * "last N" tail sample). This keeps the scoring job's I/O budget
   * predictable on long-lived references with millions of points.
   * Public so the scheduling job can ask the scorer for its
   * preferred sample size.
   */
  public static final int RECOMMENDED_SAMPLE_SIZE = 1_000;

  /**
   * Score the given data points. Returns {@link Optional#empty()}
   * when the sample is below {@link #MIN_POINTS_FOR_SCORING} or
   * every signal had to be skipped (e.g. a single-value series).
   *
   * @param dataPoints the sample to score; null + empty handled
   * @return a quality score in {@code [0.0, 1.0]}, or empty
   */
  public Optional<Double> score(List<TimeseriesDataPoint> dataPoints) {
    if (dataPoints == null || dataPoints.size() < MIN_POINTS_FOR_SCORING) {
      return Optional.empty();
    }

    int n = dataPoints.size();

    // --- 1. Completeness: fraction of non-null values.
    long present = dataPoints.stream().filter(p -> p != null && p.getValue() != null).count();
    double completenessScore = (double) present / (double) n;

    // --- 2. Coverage: ratio of observed points to "expected" given
    // the median sampling interval. Anything > 1 is clamped to 1
    // (denser-than-expected is "good", not "more than perfect").
    Double coverageScore = computeCoverageScore(dataPoints);

    // --- 3. Stability: 1 - cv (coefficient of variation), clamped.
    // Only defined for numeric values with a non-zero mean.
    Double stabilityScore = computeStabilityScore(dataPoints);

    double sum = completenessScore;
    int count = 1;
    if (coverageScore != null) {
      sum += coverageScore;
      count++;
    }
    if (stabilityScore != null) {
      sum += stabilityScore;
      count++;
    }
    // Defensive: if somehow no signal was usable, return empty.
    if (count == 0) return Optional.empty();
    double combined = sum / count;
    // Final clamp — guards against any future signal that could
    // produce a marginally-out-of-range value.
    return Optional.of(clamp01(combined));
  }

  /**
   * Coverage signal — {@code points / expected_points}. Returns
   * {@code null} when the median sampling interval isn't usable
   * (e.g. all timestamps equal).
   */
  Double computeCoverageScore(List<TimeseriesDataPoint> dataPoints) {
    int n = dataPoints.size();
    long firstTs = dataPoints.getFirst().getTimestamp();
    long lastTs = dataPoints.getLast().getTimestamp();
    long span = lastTs - firstTs;
    if (span <= 0) {
      // Degenerate — same-instant points. Skip coverage rather than
      // misreport a 0 or infinity.
      return null;
    }
    long medianDt = medianDelta(dataPoints);
    if (medianDt <= 0) {
      return null;
    }
    double expected = (double) span / (double) medianDt + 1.0;
    if (expected <= 0) return null;
    double ratio = (double) n / expected;
    return clamp01(ratio);
  }

  /**
   * Stability signal — {@code 1 - clamp(stddev / |mean|, 0, 1)}.
   * Returns {@code null} for non-numeric series, mean ≈ 0, or
   * fewer than two numeric samples (stddev undefined).
   */
  Double computeStabilityScore(List<TimeseriesDataPoint> dataPoints) {
    double sum = 0;
    int numericCount = 0;
    for (TimeseriesDataPoint p : dataPoints) {
      Double v = toDouble(p);
      if (v != null) {
        sum += v;
        numericCount++;
      }
    }
    if (numericCount < 2) return null;
    double mean = sum / numericCount;
    if (Math.abs(mean) < 1e-12) {
      // Mean ≈ 0 — coefficient of variation is undefined. Don't
      // pretend; skip the signal.
      return null;
    }
    double sqSum = 0;
    for (TimeseriesDataPoint p : dataPoints) {
      Double v = toDouble(p);
      if (v != null) {
        double d = v - mean;
        sqSum += d * d;
      }
    }
    double variance = sqSum / numericCount;
    double stddev = Math.sqrt(variance);
    double cv = stddev / Math.abs(mean);
    return 1.0 - clamp01(cv);
  }

  /**
   * Coerce a {@link TimeseriesDataPoint}'s value to {@code Double}.
   * Booleans collapse to 0.0 / 1.0; strings and {@code null} return
   * {@code null}. Numeric values use their {@code .doubleValue()}.
   */
  static Double toDouble(TimeseriesDataPoint p) {
    if (p == null || p.getValue() == null) return null;
    Object v = p.getValue();
    if (v instanceof Number n) return n.doubleValue();
    if (v instanceof Boolean b) return b ? 1.0 : 0.0;
    return null;
  }

  /**
   * Median of adjacent timestamp deltas. Robust to outliers (a single
   * 24-hour gap in an otherwise-1-second series doesn't blow the
   * coverage estimate up). Returns 0 if every delta was 0.
   */
  static long medianDelta(List<TimeseriesDataPoint> dataPoints) {
    int n = dataPoints.size();
    if (n < 2) return 0L;
    long[] deltas = new long[n - 1];
    for (int i = 1; i < n; i++) {
      long d = dataPoints.get(i).getTimestamp() - dataPoints.get(i - 1).getTimestamp();
      // Negative deltas are physically meaningless (timestamps should
      // be monotonic) — take absolute value so a single mis-ordered
      // pair doesn't poison the median.
      deltas[i - 1] = Math.abs(d);
    }
    java.util.Arrays.sort(deltas);
    return deltas[deltas.length / 2];
  }

  /** Clamp {@code x} to {@code [0, 1]}. */
  static double clamp01(double x) {
    if (Double.isNaN(x)) return 0.0;
    if (x < 0.0) return 0.0;
    if (x > 1.0) return 1.0;
    return x;
  }
}
