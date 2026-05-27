package de.dlr.shepard.data.timeseries.repositories;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for TS-AUDIT-2026-05-24-011:
 * the Postgres Bind parameter-count guard in
 * {@link TimeseriesDataPointRepository#buildInsertQueryObject}.
 *
 * No DB required — exercises pure arithmetic helpers only.
 */
public class TimeseriesDataPointRepositoryParamGuardTest {

  // ── insertParamCount arithmetic ────────────────────────────────────────────

  @Test
  void paramCountForZeroRows_isOne() {
    assertEquals(1, TimeseriesDataPointRepository.insertParamCount(0));
  }

  @Test
  void paramCountForOneRow_isThree() {
    // 1 shared :timeseriesid + 2 per row (:time0, :value0)
    assertEquals(3, TimeseriesDataPointRepository.insertParamCount(1));
  }

  @Test
  void paramCountFormula_matchesExpectedPattern() {
    for (int rows = 0; rows <= 100; rows++) {
      assertEquals(1 + rows * 2, TimeseriesDataPointRepository.insertParamCount(rows));
    }
  }

  // ── default INSERT_BATCH_SIZE stays within the limit ─────────────────────

  @Test
  void defaultBatchSize_isWithinPgBindLimit() {
    int paramCount = TimeseriesDataPointRepository.insertParamCount(
      TimeseriesDataPointRepository.INSERT_BATCH_SIZE
    );
    assertTrue(
      paramCount <= TimeseriesDataPointRepository.PG_BIND_PARAM_LIMIT,
      "Default INSERT_BATCH_SIZE (" + TimeseriesDataPointRepository.INSERT_BATCH_SIZE +
      ") produces " + paramCount + " parameters, exceeding PG_BIND_PARAM_LIMIT (" +
      TimeseriesDataPointRepository.PG_BIND_PARAM_LIMIT + ")"
    );
  }

  // ── max safe batch size ───────────────────────────────────────────────────

  @Test
  void maxSafeBatchSize_isExactlyAtLimit() {
    int maxSafe = (TimeseriesDataPointRepository.PG_BIND_PARAM_LIMIT - 1) / 2;
    int paramCount = TimeseriesDataPointRepository.insertParamCount(maxSafe);
    assertTrue(paramCount <= TimeseriesDataPointRepository.PG_BIND_PARAM_LIMIT);
  }

  @Test
  void oneBeyondMaxSafeBatchSize_exceedsLimit() {
    int overLimit = (TimeseriesDataPointRepository.PG_BIND_PARAM_LIMIT - 1) / 2 + 1;
    int paramCount = TimeseriesDataPointRepository.insertParamCount(overLimit);
    assertTrue(paramCount > TimeseriesDataPointRepository.PG_BIND_PARAM_LIMIT);
  }
}
