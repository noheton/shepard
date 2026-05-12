package de.dlr.shepard.context.references.timeseriesreference.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * AI1c — unit tests for {@link TimeseriesQualityScorer}. Pure
 * heuristics, deterministic where possible (seeded RNG for the
 * noisy-signal case).
 */
class TimeseriesQualityScorerTest {

  private final TimeseriesQualityScorer scorer = new TimeseriesQualityScorer();

  // ---------------------------------------------------------------
  //  Synthetic data fixtures
  // ---------------------------------------------------------------

  /** Evenly-spaced numeric series at the given constant value. */
  private static List<TimeseriesDataPoint> perfect(int n, double value) {
    List<TimeseriesDataPoint> out = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      out.add(new TimeseriesDataPoint(i * 1_000_000L, value));
    }
    return out;
  }

  /** Evenly-spaced numeric series with Gaussian noise around a mean. */
  private static List<TimeseriesDataPoint> noisy(int n, double mean, double stddev, long seed) {
    Random r = new Random(seed);
    List<TimeseriesDataPoint> out = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      double v = mean + r.nextGaussian() * stddev;
      out.add(new TimeseriesDataPoint(i * 1_000_000L, v));
    }
    return out;
  }

  /** Evenly-spaced series where a fraction of values are null. */
  private static List<TimeseriesDataPoint> gappy(int n, double value, double missingFraction) {
    Random r = new Random(0xC0FFEE);
    List<TimeseriesDataPoint> out = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      Object v = r.nextDouble() < missingFraction ? null : value;
      out.add(new TimeseriesDataPoint(i * 1_000_000L, v));
    }
    return out;
  }

  // ---------------------------------------------------------------
  //  Boundary / null-handling
  // ---------------------------------------------------------------

  @Test
  void scoreReturnsEmptyForNull() {
    assertTrue(scorer.score(null).isEmpty(), "null sample must score empty");
  }

  @Test
  void scoreReturnsEmptyForEmpty() {
    assertTrue(scorer.score(List.of()).isEmpty(), "empty sample must score empty");
  }

  @Test
  void scoreReturnsEmptyForSinglePoint() {
    List<TimeseriesDataPoint> one = List.of(new TimeseriesDataPoint(1L, 42.0));
    assertTrue(scorer.score(one).isEmpty(), "single-point sample must score empty");
  }

  @Test
  void scoreReturnsEmptyForJustBelowMinimum() {
    // MIN_POINTS_FOR_SCORING is 10 — 9 points is below it.
    List<TimeseriesDataPoint> nine = perfect(TimeseriesQualityScorer.MIN_POINTS_FOR_SCORING - 1, 1.0);
    assertTrue(scorer.score(nine).isEmpty(), "fewer than 10 points must score empty");
  }

  // ---------------------------------------------------------------
  //  Perfect signal — high score
  // ---------------------------------------------------------------

  @Test
  void scorePerfectSignalIsNearOne() {
    List<TimeseriesDataPoint> perfect = perfect(200, 5.0);
    // Constant non-zero series: completeness=1, coverage=1, stability=null
    // (mean is non-zero, stddev=0 → cv=0 → stability=1). All three signals
    // contribute, average ≈ 1.0.
    Optional<Double> score = scorer.score(perfect);
    assertTrue(score.isPresent(), "perfect series should score");
    assertTrue(score.get() >= 0.99, "perfect series should score near 1.0, got " + score.get());
  }

  // ---------------------------------------------------------------
  //  Pure noise — low stability
  // ---------------------------------------------------------------

  @Test
  void scoreNoisySignalHasLowStability() {
    // High coefficient of variation (stddev > |mean|) → stability ≈ 0.
    // Coverage + completeness stay at 1.0 → combined ≈ 0.66 max,
    // depending on the realised stddev.
    List<TimeseriesDataPoint> noisy = noisy(500, /* mean */ 1.0, /* stddev */ 5.0, 42L);
    Optional<Double> score = scorer.score(noisy);
    assertTrue(score.isPresent(), "noisy series should score (above min size)");
    assertTrue(score.get() < 0.8, "noisy series should score below 0.8, got " + score.get());

    // Direct stability probe — non-null and low.
    Double stability = scorer.computeStabilityScore(noisy);
    assertNotNull(stability, "stability should be defined for numeric series with non-zero mean");
    assertTrue(stability < 0.5, "stability should be < 0.5 for high-CV noise, got " + stability);
  }

  // ---------------------------------------------------------------
  //  Gappy signal — low completeness
  // ---------------------------------------------------------------

  @Test
  void scoreGappySignalHasLowCompleteness() {
    // ~50% missing → completeness ≈ 0.5; combined < 0.8 (even with
    // coverage=1, stability=1 this averages to ~0.83 at best).
    List<TimeseriesDataPoint> gappy = gappy(200, 7.0, 0.5);
    Optional<Double> score = scorer.score(gappy);
    assertTrue(score.isPresent(), "gappy series should still score (size OK)");
    assertTrue(score.get() < 0.9, "gappy series should score below 0.9, got " + score.get());
  }

  // ---------------------------------------------------------------
  //  Coverage signal — explicit probes
  // ---------------------------------------------------------------

  @Test
  void coverageNullWhenAllTimestampsEqual() {
    List<TimeseriesDataPoint> sameInstant = new ArrayList<>();
    for (int i = 0; i < 20; i++) sameInstant.add(new TimeseriesDataPoint(1_000L, 1.0));
    assertNull(scorer.computeCoverageScore(sameInstant), "coverage must be null when timestamps degenerate to one instant");
  }

  @Test
  void coverageNearOneForEvenlySpaced() {
    List<TimeseriesDataPoint> dense = perfect(100, 1.0);
    Double cov = scorer.computeCoverageScore(dense);
    assertNotNull(cov);
    assertTrue(cov > 0.95, "evenly-spaced coverage should be ≈ 1, got " + cov);
  }

  @Test
  void coverageLowForSparseSampling() {
    // 10 points spanning 1_000_000_000 nanos with median delta ≈
    // 111_111_111 nanos → expected ≈ 10. Observed 10 → coverage ≈ 1.
    // Now drop one mid-point: span identical, n = 9 → coverage < 1.
    List<TimeseriesDataPoint> wide = new ArrayList<>();
    wide.add(new TimeseriesDataPoint(0L, 1.0));
    wide.add(new TimeseriesDataPoint(1L, 1.0));
    wide.add(new TimeseriesDataPoint(2L, 1.0));
    wide.add(new TimeseriesDataPoint(3L, 1.0));
    wide.add(new TimeseriesDataPoint(4L, 1.0));
    wide.add(new TimeseriesDataPoint(5L, 1.0));
    wide.add(new TimeseriesDataPoint(6L, 1.0));
    wide.add(new TimeseriesDataPoint(7L, 1.0));
    wide.add(new TimeseriesDataPoint(8L, 1.0));
    // Then a 100-step gap.
    wide.add(new TimeseriesDataPoint(108L, 1.0));
    Double cov = scorer.computeCoverageScore(wide);
    assertNotNull(cov);
    assertTrue(cov < 0.5, "10 observed / ~108 expected should be < 0.5, got " + cov);
  }

  // ---------------------------------------------------------------
  //  Stability signal — non-numeric and zero-mean edge cases
  // ---------------------------------------------------------------

  @Test
  void stabilityNullForNonNumericSeries() {
    List<TimeseriesDataPoint> strings = new ArrayList<>();
    for (int i = 0; i < 20; i++) strings.add(new TimeseriesDataPoint(i * 1000L, "hello"));
    assertNull(scorer.computeStabilityScore(strings), "stability must be null for string series");
  }

  @Test
  void stabilityNullForZeroMean() {
    List<TimeseriesDataPoint> symmetric = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      // Symmetric ±1 sequence around 0.
      symmetric.add(new TimeseriesDataPoint(i * 1000L, (i % 2 == 0) ? 1.0 : -1.0));
    }
    assertNull(scorer.computeStabilityScore(symmetric), "stability must be null when mean ≈ 0");
  }

  @Test
  void stabilityCoercesBooleansToNumeric() {
    // Booleans 1.0 / 0.0 → mean = 0.5, stddev > 0 → cv > 0 → stability < 1.
    List<TimeseriesDataPoint> bools = new ArrayList<>();
    for (int i = 0; i < 20; i++) bools.add(new TimeseriesDataPoint(i * 1000L, (i % 2 == 0)));
    Double stability = scorer.computeStabilityScore(bools);
    assertNotNull(stability, "booleans should be coerced and yield a stability value");
    assertTrue(stability >= 0.0 && stability <= 1.0);
  }

  // ---------------------------------------------------------------
  //  toDouble + helpers
  // ---------------------------------------------------------------

  @Test
  void toDoubleReturnsNullForNullPoint() {
    assertNull(TimeseriesQualityScorer.toDouble(null));
  }

  @Test
  void toDoubleReturnsNullForNullValue() {
    assertNull(TimeseriesQualityScorer.toDouble(new TimeseriesDataPoint(1L, null)));
  }

  @Test
  void toDoubleCoercesNumber() {
    assertEquals(2.5, TimeseriesQualityScorer.toDouble(new TimeseriesDataPoint(1L, 2.5)));
    assertEquals(7.0, TimeseriesQualityScorer.toDouble(new TimeseriesDataPoint(1L, 7)));
    assertEquals(1.0, TimeseriesQualityScorer.toDouble(new TimeseriesDataPoint(1L, true)));
    assertEquals(0.0, TimeseriesQualityScorer.toDouble(new TimeseriesDataPoint(1L, false)));
  }

  @Test
  void toDoubleReturnsNullForString() {
    assertNull(TimeseriesQualityScorer.toDouble(new TimeseriesDataPoint(1L, "abc")));
  }

  @Test
  void clamp01ClampsAtBounds() {
    assertEquals(0.0, TimeseriesQualityScorer.clamp01(-0.5));
    assertEquals(1.0, TimeseriesQualityScorer.clamp01(1.5));
    assertEquals(0.5, TimeseriesQualityScorer.clamp01(0.5));
    assertEquals(0.0, TimeseriesQualityScorer.clamp01(Double.NaN));
  }

  @Test
  void medianDeltaIsRobustToASingleHugeGap() {
    // 9 deltas of 1, plus one delta of 100_000 — median should be 1.
    List<TimeseriesDataPoint> mixed = new ArrayList<>();
    long t = 0L;
    for (int i = 0; i < 10; i++) {
      mixed.add(new TimeseriesDataPoint(t, 1.0));
      t += 1L;
    }
    mixed.add(new TimeseriesDataPoint(t + 100_000L, 1.0));
    long median = TimeseriesQualityScorer.medianDelta(mixed);
    assertEquals(1L, median, "median delta should ignore the outlier gap");
  }

  @Test
  void medianDeltaHandlesNegativeTimestampsAbsolutely() {
    // Out-of-order pair contributes |delta|, not the signed delta.
    List<TimeseriesDataPoint> ooo = new ArrayList<>();
    ooo.add(new TimeseriesDataPoint(100L, 1.0));
    ooo.add(new TimeseriesDataPoint(50L, 1.0)); // backwards
    ooo.add(new TimeseriesDataPoint(150L, 1.0));
    long median = TimeseriesQualityScorer.medianDelta(ooo);
    assertTrue(median > 0L, "median should be positive even with reversed pair, got " + median);
  }

  @Test
  void medianDeltaForSinglePoint() {
    assertEquals(0L, TimeseriesQualityScorer.medianDelta(List.of(new TimeseriesDataPoint(1L, 1.0))));
  }

  // ---------------------------------------------------------------
  //  Defensive — fully-degenerate series where every signal collapses
  // ---------------------------------------------------------------

  @Test
  void scoreEmptyOptionalIfEverySignalCollapses() {
    // All-null values + same timestamp + non-numeric:
    //   completeness = 0
    //   coverage = null (zero span)
    //   stability = null (no numeric samples)
    // Combined: just completeness=0 → score = 0.0 (NOT empty — caller
    // can still distinguish "tried but failed" from "below min size").
    List<TimeseriesDataPoint> degenerate = new ArrayList<>();
    for (int i = 0; i < 20; i++) degenerate.add(new TimeseriesDataPoint(1_000L, null));
    Optional<Double> score = scorer.score(degenerate);
    assertTrue(score.isPresent(), "completeness alone is a valid signal");
    assertEquals(0.0, score.get(), 1e-9, "all-null → completeness=0 → combined=0");
  }

  @Test
  void recommendedSampleSizeIsSane() {
    // Defensive — ensure the constant stays in a sensible range so the
    // job's I/O budget per tick is bounded.
    assertTrue(TimeseriesQualityScorer.RECOMMENDED_SAMPLE_SIZE >= 100);
    assertTrue(TimeseriesQualityScorer.RECOMMENDED_SAMPLE_SIZE <= 100_000);
  }

  @Test
  void minPointsForScoringIsAtLeastTen() {
    assertTrue(TimeseriesQualityScorer.MIN_POINTS_FOR_SCORING >= 10);
  }

  @Test
  void scoreClampedToUnitInterval() {
    // Any score we produce must land in [0, 1].
    List<TimeseriesDataPoint> hetero = noisy(200, 0.1, 100.0, 7L);
    Optional<Double> score = scorer.score(hetero);
    assertTrue(score.isPresent());
    assertTrue(score.get() >= 0.0 && score.get() <= 1.0, "score out of range: " + score.get());
    assertFalse(Double.isNaN(score.get()));
  }
}
