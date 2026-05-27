package de.dlr.shepard.data.timeseries.repositories;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the Postgres {@code Bind} parameter-count guard in
 * {@link TimeseriesDataPointRepository}.
 *
 * <p>The static initializer checks that
 * {@code INSERT_BATCH_SIZE × PARAMS_PER_ROW_LOWER_BOUND + FIXED_PARAMS_PER_BATCH < PG_BIND_PARAM_LIMIT (32 767)}.
 * These tests verify the boundary arithmetic using the same formula,
 * without touching the actual static constants (which are already validated at
 * class-load time).
 *
 * <p>See TS-AUDIT-2026-05-24-011 in {@code aidocs/16-dispatcher-backlog.md}.
 */
public class TimeseriesDataPointRepositoryBatchGuardTest {

  /** Replicate the guard formula so tests are independent of constants changing. */
  private static int estimatedParams(int batchSize) {
    return batchSize * TimeseriesDataPointRepository.PARAMS_PER_ROW_LOWER_BOUND
      + TimeseriesDataPointRepository.FIXED_PARAMS_PER_BATCH;
  }

  private static void assertGuardThrowsForBatchSize(int batchSize) {
    int estimated = estimatedParams(batchSize);
    assertTrue(
      estimated >= TimeseriesDataPointRepository.PG_BIND_PARAM_LIMIT,
      "Expected estimated param count " + estimated + " >= " + TimeseriesDataPointRepository.PG_BIND_PARAM_LIMIT
    );
  }

  private static void assertGuardPassesForBatchSize(int batchSize) {
    int estimated = estimatedParams(batchSize);
    assertTrue(
      estimated < TimeseriesDataPointRepository.PG_BIND_PARAM_LIMIT,
      "Expected estimated param count " + estimated + " < " + TimeseriesDataPointRepository.PG_BIND_PARAM_LIMIT
    );
  }

  // ── current INSERT_BATCH_SIZE is safely under the limit ──────────────────

  @Test
  void currentBatchSize_isUnderPgLimit() {
    // The static initializer on TimeseriesDataPointRepository already enforces this
    // at class-load time; this test makes the constraint explicit and human-readable.
    assertGuardPassesForBatchSize(TimeseriesDataPointRepository.INSERT_BATCH_SIZE);
  }

  // ── boundary: last safe value before the limit ────────────────────────────

  @Test
  void batchSizeJustUnderLimit_passes() {
    // PG_BIND_PARAM_LIMIT - FIXED_PARAMS_PER_BATCH - 1 is the last safe batch size.
    int lastSafe = (TimeseriesDataPointRepository.PG_BIND_PARAM_LIMIT
      - TimeseriesDataPointRepository.FIXED_PARAMS_PER_BATCH - 1)
      / TimeseriesDataPointRepository.PARAMS_PER_ROW_LOWER_BOUND;
    assertGuardPassesForBatchSize(lastSafe);
  }

  // ── boundary: first value that would breach the limit ────────────────────

  @Test
  void batchSizeAtLimit_breachesGuard() {
    // PG_BIND_PARAM_LIMIT - FIXED_PARAMS_PER_BATCH is the smallest batch size where
    // estimatedParams == PG_BIND_PARAM_LIMIT (i.e., >= limit).
    int firstUnsafe = (TimeseriesDataPointRepository.PG_BIND_PARAM_LIMIT
      - TimeseriesDataPointRepository.FIXED_PARAMS_PER_BATCH)
      / TimeseriesDataPointRepository.PARAMS_PER_ROW_LOWER_BOUND;
    assertGuardThrowsForBatchSize(firstUnsafe);
  }

  @Test
  void batchSizeWellOverLimit_breachesGuard() {
    // Doubling the current batch size should clearly breach the limit.
    int doubled = TimeseriesDataPointRepository.INSERT_BATCH_SIZE * 2;
    assertGuardThrowsForBatchSize(doubled);
  }

  // ── the static initializer itself fires at class load ────────────────────

  @Test
  void classLoads_withoutThrowingForCurrentConstants() {
    // If the static initializer fires, class loading throws ExceptionInInitializerError.
    // Referencing a static constant forces class initialization; assertDoesNotThrow
    // confirms the guard did not fire at the current INSERT_BATCH_SIZE value.
    assertDoesNotThrow(() -> {
      @SuppressWarnings("unused")
      int batchSize = TimeseriesDataPointRepository.INSERT_BATCH_SIZE;
    });
  }
}
