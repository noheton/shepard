package de.dlr.shepard.data.timeseries.repositories;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the static helper {@link TimeseriesDataPointRepository#choosePreaggBucketNs}.
 * No DB required — the method is a pure arithmetic function.
 */
public class TimeseriesDataPointRepositoryPreaggTest {

  private static final long ONE_MS = 1_000_000L;

  // ── degenerate / guard-rail inputs ─────────────────────────────────────────

  @Test
  void returnsZero_whenTargetIsZero() {
    assertEquals(0L, TimeseriesDataPointRepository.choosePreaggBucketNs(60_000_000_000L, 0));
  }

  @Test
  void returnsZero_whenTargetIsNegative() {
    assertEquals(0L, TimeseriesDataPointRepository.choosePreaggBucketNs(60_000_000_000L, -1));
  }

  @Test
  void returnsZero_whenWindowIsZero() {
    assertEquals(0L, TimeseriesDataPointRepository.choosePreaggBucketNs(0L, 200));
  }

  @Test
  void returnsZero_whenWindowIsNegative() {
    assertEquals(0L, TimeseriesDataPointRepository.choosePreaggBucketNs(-1L, 200));
  }

  // ── window too small to benefit ────────────────────────────────────────────

  @Test
  void returnsZero_whenBucketBelowOneMs() {
    // window=500ms, target=200 → bucket = 500ms / (200*5) = 0.5ms < 1ms
    long windowNs = 500 * ONE_MS;
    assertEquals(0L, TimeseriesDataPointRepository.choosePreaggBucketNs(windowNs, 200));
  }

  @Test
  void returnsZero_whenBucketExactlyBelowOneMs() {
    // window = 999ms, target=200 → bucket = 999ms / 1000 = 0.999ms < 1ms
    long windowNs = 999 * ONE_MS;
    assertEquals(0L, TimeseriesDataPointRepository.choosePreaggBucketNs(windowNs, 200));
  }

  // ── exactly at the 1 ms threshold ──────────────────────────────────────────

  @Test
  void returnsOneMs_whenBucketIsExactlyOneMs() {
    // window = 1000ms, target=200 → bucket = 1000ms / 1000 = 1ms exactly
    long windowNs = 1_000 * ONE_MS;
    assertEquals(ONE_MS, TimeseriesDataPointRepository.choosePreaggBucketNs(windowNs, 200));
  }

  // ── typical production workloads ───────────────────────────────────────────

  @Test
  void correctBucket_oneHour100Hz_target200() {
    // 1 hour @ 100 Hz: window = 3600s = 3_600_000_000_000 ns, target=200
    // bucket = 3_600_000_000_000 / (200*5) = 3_600_000_000_000 / 1000 = 3_600_000_000 ns = 3.6 s
    long windowNs = 3_600L * 1_000_000_000L;
    long expected = windowNs / 1000;
    assertEquals(expected, TimeseriesDataPointRepository.choosePreaggBucketNs(windowNs, 200));
  }

  @Test
  void correctBucket_tenMinutes1kHz_target500() {
    // 10 min @ 1 kHz: window = 600s = 600_000_000_000 ns, target=500
    // bucket = 600_000_000_000 / (500*5) = 600_000_000_000 / 2500 = 240_000_000 ns = 240 ms
    long windowNs = 600L * 1_000_000_000L;
    long expected = windowNs / 2500;
    assertEquals(expected, TimeseriesDataPointRepository.choosePreaggBucketNs(windowNs, 500));
  }

  @Test
  void correctBucket_oneDay100Hz_target200() {
    // 24 h window, target=200: bucket = 24*3600*1e9 / 1000 = 86_400_000_000 ns = 86.4 s
    long windowNs = 24L * 3_600L * 1_000_000_000L;
    long expected = windowNs / 1000;
    assertEquals(expected, TimeseriesDataPointRepository.choosePreaggBucketNs(windowNs, 200));
  }

  // ── reduction factor calculation ───────────────────────────────────────────

  @Test
  void bucketAlwaysProducesAtMostTarget5Buckets() {
    // choosing bucketNs = windowNs/(target*5) gives exactly target*5 equal buckets
    int target = 200;
    long windowNs = 3_600_000_000_000L; // 1 hour
    long bucketNs = TimeseriesDataPointRepository.choosePreaggBucketNs(windowNs, target);
    long numBuckets = windowNs / bucketNs;
    assertEquals((long) target * 5, numBuckets);
  }
}
