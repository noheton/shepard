package de.dlr.shepard.data.timeseries.repositories;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TimeseriesDataPointRepository#compressBackfilledChunks()}.
 *
 * <p>No live database required — the {@link EntityManager} is mocked so the tests
 * verify the method's control-flow and SQL construction without a TimescaleDB fixture.
 *
 * <p>TS-AUDIT-2026-05-24-008.
 */
public class TimeseriesDataPointRepositoryBackfillCompressionTest {

  private TimeseriesDataPointRepository repository;
  private EntityManager entityManager;

  @BeforeEach
  void setUp() {
    entityManager = mock(EntityManager.class);
    repository = new TimeseriesDataPointRepository();
    repository.entityManager = entityManager;
  }

  /**
   * Helper: builds a {@code List<Object[]>} that contains one row with two String columns.
   *
   * <p>Using {@code List.of(new Object[]{"a","b"})} triggers Java's varargs sugar and
   * creates {@code List<Object>} containing two Strings rather than a
   * {@code List<Object[]>} containing one two-element array.  The {@link ArrayList}
   * constructor sidesteps that ambiguity.
   */
  private static List<Object[]> rowList(Object[]... rows) {
    List<Object[]> result = new ArrayList<>();
    for (Object[] row : rows) {
      result.add(row);
    }
    return result;
  }

  // ── no stale chunks — nothing to compress ─────────────────────────────────

  @Test
  void noStaleChunks_noCompressCallIssued() {
    Query selectQuery = mock(Query.class);
    when(entityManager.createNativeQuery(contains("timescaledb_information.chunks")))
      .thenReturn(selectQuery);
    when(selectQuery.setParameter(anyString(), any())).thenReturn(selectQuery);
    when(selectQuery.getResultList()).thenReturn(List.of());

    assertDoesNotThrow(() -> repository.compressBackfilledChunks());

    // compress_chunk should never have been called.
    verify(entityManager, never()).createNativeQuery(contains("compress_chunk"));
  }

  // ── one stale uncompressed chunk — compress_chunk issued once ─────────────

  @Test
  void oneStaleChunk_compressChunkIssuedOnce() {
    Query selectQuery = mock(Query.class);
    Query compressQuery = mock(Query.class);

    when(entityManager.createNativeQuery(contains("timescaledb_information.chunks")))
      .thenReturn(selectQuery);
    when(selectQuery.setParameter(anyString(), any())).thenReturn(selectQuery);
    when(selectQuery.getResultList())
      .thenReturn(rowList(new Object[] { "_timescaledb_internal", "_hyper_1_1_chunk" }));

    when(entityManager.createNativeQuery(contains("compress_chunk")))
      .thenReturn(compressQuery);
    when(compressQuery.getResultList()).thenReturn(List.of());

    assertDoesNotThrow(() -> repository.compressBackfilledChunks());

    verify(entityManager, times(1)).createNativeQuery(contains("compress_chunk"));
    verify(compressQuery, times(1)).getResultList();
  }

  // ── two stale chunks — compress_chunk issued twice ─────────────────────────

  @Test
  void twoStaleChunks_compressChunkIssuedTwice() {
    Query selectQuery = mock(Query.class);
    Query compressQuery = mock(Query.class);

    when(entityManager.createNativeQuery(contains("timescaledb_information.chunks")))
      .thenReturn(selectQuery);
    when(selectQuery.setParameter(anyString(), any())).thenReturn(selectQuery);
    when(selectQuery.getResultList())
      .thenReturn(rowList(
        new Object[] { "_timescaledb_internal", "_hyper_1_1_chunk" },
        new Object[] { "_timescaledb_internal", "_hyper_1_2_chunk" }
      ));

    when(entityManager.createNativeQuery(contains("compress_chunk")))
      .thenReturn(compressQuery);
    when(compressQuery.getResultList()).thenReturn(List.of());

    assertDoesNotThrow(() -> repository.compressBackfilledChunks());

    verify(entityManager, times(2)).createNativeQuery(contains("compress_chunk"));
  }

  // ── compress_chunk throws on one chunk — continues without rethrowing ──────

  @Test
  void oneChunkFails_methodDoesNotThrow_otherChunkStillCompressed() {
    Query selectQuery = mock(Query.class);
    Query goodCompressQuery = mock(Query.class);
    Query badCompressQuery = mock(Query.class);

    when(entityManager.createNativeQuery(contains("timescaledb_information.chunks")))
      .thenReturn(selectQuery);
    when(selectQuery.setParameter(anyString(), any())).thenReturn(selectQuery);
    when(selectQuery.getResultList())
      .thenReturn(rowList(
        new Object[] { "_timescaledb_internal", "_hyper_1_1_chunk" },
        new Object[] { "_timescaledb_internal", "_hyper_1_2_chunk" }
      ));

    // First compress call throws; second succeeds.
    when(entityManager.createNativeQuery(contains("compress_chunk")))
      .thenReturn(badCompressQuery)
      .thenReturn(goodCompressQuery);
    when(badCompressQuery.getResultList())
      .thenThrow(new RuntimeException("chunk already compressed concurrently"));
    when(goodCompressQuery.getResultList()).thenReturn(List.of());

    // Must not propagate the exception.
    assertDoesNotThrow(() -> repository.compressBackfilledChunks());

    // Both compress_chunk calls were attempted.
    verify(entityManager, times(2)).createNativeQuery(contains("compress_chunk"));
    verify(goodCompressQuery, times(1)).getResultList();
  }

  // ── select query uses the 8-day cutoff parameter ──────────────────────────

  @Test
  void selectQuery_usesEightDayCutoffParameter() {
    final long eightDaysNs = 8L * 24L * 3600L * 1_000_000_000L;

    Query selectQuery = mock(Query.class);
    when(entityManager.createNativeQuery(contains("timescaledb_information.chunks")))
      .thenReturn(selectQuery);
    when(selectQuery.setParameter(anyString(), any())).thenReturn(selectQuery);
    when(selectQuery.getResultList()).thenReturn(List.of());

    assertDoesNotThrow(() -> repository.compressBackfilledChunks());

    verify(selectQuery).setParameter("cutoffNs", eightDaysNs);
  }
}
