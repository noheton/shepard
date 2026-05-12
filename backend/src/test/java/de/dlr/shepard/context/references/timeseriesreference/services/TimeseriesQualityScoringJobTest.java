package de.dlr.shepard.context.references.timeseriesreference.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.context.references.timeseriesreference.daos.TimeseriesReferenceDAO;
import de.dlr.shepard.context.references.timeseriesreference.model.ReferencedTimeseriesNodeEntity;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import de.dlr.shepard.data.timeseries.model.TimeseriesEntity;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPointsQueryParams;
import de.dlr.shepard.data.timeseries.model.enums.DataPointValueType;
import de.dlr.shepard.data.timeseries.repositories.TimeseriesDataPointRepository;
import de.dlr.shepard.data.timeseries.repositories.TimeseriesRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * AI1c — Mockito unit tests for {@link TimeseriesQualityScoringJob}.
 * No Quarkus context, no databases — the job's contract is "ask the
 * DAO for stale refs, fetch points, score, persist".
 */
class TimeseriesQualityScoringJobTest {

  @Mock
  TimeseriesReferenceDAO dao;

  @Mock
  TimeseriesRepository timeseriesRepository;

  @Mock
  TimeseriesDataPointRepository timeseriesDataPointRepository;

  TimeseriesQualityScoringJob job;
  TimeseriesQualityScorer scorer;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    scorer = new TimeseriesQualityScorer();
    // Spy so we can stub fetchPoints without hauling the data-point
    // repository through the test.
    job = spy(new TimeseriesQualityScoringJob());
    job.timeseriesReferenceDAO = dao;
    job.scorer = scorer;
    job.timeseriesRepository = timeseriesRepository;
    job.timeseriesDataPointRepository = timeseriesDataPointRepository;
    job.enabled = true;
    job.batchSize = 100;
  }

  // ---------------------------------------------------------------
  //  Fixture helpers
  // ---------------------------------------------------------------

  private static TimeseriesReference newRef(long shepardId) {
    TimeseriesReference ref = new TimeseriesReference(shepardId);
    ref.setShepardId(shepardId);
    TimeseriesContainer c = new TimeseriesContainer(99L);
    ref.setTimeseriesContainer(c);
    ReferencedTimeseriesNodeEntity entity = new ReferencedTimeseriesNodeEntity(
      "m", "d", "l", "s", "f"
    );
    List<ReferencedTimeseriesNodeEntity> list = new ArrayList<>();
    list.add(entity);
    ref.setReferencedTimeseriesList(list);
    ref.setStart(0L);
    ref.setEnd(1_000_000_000L);
    return ref;
  }

  private static List<TimeseriesDataPoint> perfect(int n) {
    List<TimeseriesDataPoint> out = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      out.add(new TimeseriesDataPoint(i * 1_000_000L, 5.0));
    }
    return out;
  }

  // ---------------------------------------------------------------
  //  Toggle gate
  // ---------------------------------------------------------------

  @Test
  void runIsNoOpWhenDisabled() {
    job.enabled = false;
    job.runScoring();
    verify(dao, never()).findNeedingScoring(anyLong(), anyInt());
    verify(dao, never()).createOrUpdate(any());
  }

  // ---------------------------------------------------------------
  //  Stale-only behaviour — DAO is told the cutoff, batch size cap
  // ---------------------------------------------------------------

  @Test
  void runAsksDAOForStaleRefsOnly() {
    when(dao.findNeedingScoring(anyLong(), anyInt())).thenReturn(List.of());

    long before = System.currentTimeMillis();
    job.runScoring();
    long after = System.currentTimeMillis();

    ArgumentCaptor<Long> cutoff = ArgumentCaptor.forClass(Long.class);
    ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
    verify(dao).findNeedingScoring(cutoff.capture(), limit.capture());

    long expectedLow = before - TimeseriesQualityScoringJob.RESCORING_INTERVAL_MILLIS;
    long expectedHigh = after - TimeseriesQualityScoringJob.RESCORING_INTERVAL_MILLIS;
    assertTrue(cutoff.getValue() >= expectedLow, "cutoff " + cutoff.getValue() + " < " + expectedLow);
    assertTrue(cutoff.getValue() <= expectedHigh, "cutoff " + cutoff.getValue() + " > " + expectedHigh);
    assertEquals(100, limit.getValue(), "batch size should pass through unchanged when in range");
  }

  @Test
  void runClampsAbsurdlyHighBatchSize() {
    job.batchSize = 1_000_000;
    when(dao.findNeedingScoring(anyLong(), anyInt())).thenReturn(List.of());
    job.runScoring();
    ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
    verify(dao).findNeedingScoring(anyLong(), limit.capture());
    assertEquals(TimeseriesQualityScoringJob.MAX_BATCH_SIZE, limit.getValue());
  }

  @Test
  void runClampsNegativeBatchSize() {
    job.batchSize = -5;
    when(dao.findNeedingScoring(anyLong(), anyInt())).thenReturn(List.of());
    job.runScoring();
    ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
    verify(dao).findNeedingScoring(anyLong(), limit.capture());
    assertEquals(TimeseriesQualityScoringJob.MIN_BATCH_SIZE, limit.getValue());
  }

  @Test
  void runEarlyExitsOnEmptyBatch() {
    when(dao.findNeedingScoring(anyLong(), anyInt())).thenReturn(List.of());
    job.runScoring();
    verify(dao, never()).createOrUpdate(any());
  }

  @Test
  void runSwallowsDAOLookupFailure() {
    when(dao.findNeedingScoring(anyLong(), anyInt())).thenThrow(new RuntimeException("Neo4j burped"));
    // Must not throw; the scheduler keeps running.
    job.runScoring();
    verify(dao, never()).createOrUpdate(any());
  }

  // ---------------------------------------------------------------
  //  End-to-end scoring path
  // ---------------------------------------------------------------

  @Test
  void runScoresAndPersistsBatch() {
    TimeseriesReference r1 = newRef(1L);
    TimeseriesReference r2 = newRef(2L);
    when(dao.findNeedingScoring(anyLong(), anyInt())).thenReturn(List.of(r1, r2));
    // Stub the fetch on the spy so we don't go through the repository.
    lenient().doReturn(perfect(200)).when(job).fetchPoints(anyLong(), any(Timeseries.class), any(TimeseriesDataPointsQueryParams.class));

    job.runScoring();

    verify(dao, times(2)).createOrUpdate(any(TimeseriesReference.class));
    assertNotNull(r1.getQualityScore(), "r1 should have been scored");
    assertNotNull(r2.getQualityScore(), "r2 should have been scored");
    assertNotNull(r1.getLastScoredAt());
    assertNotNull(r2.getLastScoredAt());
  }

  @Test
  void runStampsLastScoredAtEvenWhenScoreEmpty() {
    TimeseriesReference r = newRef(1L);
    when(dao.findNeedingScoring(anyLong(), anyInt())).thenReturn(List.of(r));
    // Too-small sample — scorer returns empty.
    lenient().doReturn(perfect(3)).when(job).fetchPoints(anyLong(), any(Timeseries.class), any(TimeseriesDataPointsQueryParams.class));

    job.runScoring();

    verify(dao, times(1)).createOrUpdate(any(TimeseriesReference.class));
    assertNull(r.getQualityScore(), "score should remain unset when sample below min");
    assertNotNull(r.getLastScoredAt(), "lastScoredAt must still be stamped so we don't busy-loop");
  }

  @Test
  void runSkipsRefWithoutContainer() {
    TimeseriesReference r = newRef(1L);
    r.setTimeseriesContainer(null);
    when(dao.findNeedingScoring(anyLong(), anyInt())).thenReturn(List.of(r));
    job.runScoring();
    // computeScore returns empty; we still persist lastScoredAt.
    verify(dao, times(1)).createOrUpdate(any(TimeseriesReference.class));
    assertNull(r.getQualityScore());
    assertNotNull(r.getLastScoredAt());
  }

  @Test
  void runSkipsRefWithoutChannels() {
    TimeseriesReference r = newRef(1L);
    r.setReferencedTimeseriesList(new ArrayList<>());
    when(dao.findNeedingScoring(anyLong(), anyInt())).thenReturn(List.of(r));
    job.runScoring();
    verify(dao, times(1)).createOrUpdate(any(TimeseriesReference.class));
    assertNull(r.getQualityScore());
  }

  @Test
  void runContinuesPastPerRefFailure() {
    TimeseriesReference r1 = newRef(1L);
    TimeseriesReference r2 = newRef(2L);
    when(dao.findNeedingScoring(anyLong(), anyInt())).thenReturn(List.of(r1, r2));
    lenient().doThrow(new RuntimeException("postgres down"))
      .when(job).fetchPoints(eq(99L), any(Timeseries.class), any(TimeseriesDataPointsQueryParams.class));

    // Doesn't throw — per-ref failure is logged and the loop continues.
    job.runScoring();
    // Neither ref was persisted (both failed). The job must not blow up.
    verify(dao, never()).createOrUpdate(any());
  }

  // ---------------------------------------------------------------
  //  Sample-tail trimming
  // ---------------------------------------------------------------

  @Test
  void runTrimsToRecommendedSampleSize() {
    TimeseriesReference r = newRef(1L);
    when(dao.findNeedingScoring(anyLong(), anyInt())).thenReturn(List.of(r));
    int over = TimeseriesQualityScorer.RECOMMENDED_SAMPLE_SIZE + 500;
    lenient().doReturn(perfect(over))
      .when(job).fetchPoints(anyLong(), any(Timeseries.class), any(TimeseriesDataPointsQueryParams.class));

    // Should still produce a score (perfect signal); the trim is internal.
    job.runScoring();
    assertNotNull(r.getQualityScore());
    assertTrue(r.getQualityScore() >= 0.95, "trimmed perfect tail should still score near 1");
  }

  @Test
  void runHandlesEmptyDataPoints() {
    TimeseriesReference r = newRef(1L);
    when(dao.findNeedingScoring(anyLong(), anyInt())).thenReturn(List.of(r));
    lenient().doReturn(List.<TimeseriesDataPoint>of())
      .when(job).fetchPoints(anyLong(), any(Timeseries.class), any(TimeseriesDataPointsQueryParams.class));

    job.runScoring();
    verify(dao, times(1)).createOrUpdate(any(TimeseriesReference.class));
    assertNull(r.getQualityScore());
    assertNotNull(r.getLastScoredAt());
  }

  // ---------------------------------------------------------------
  //  clampBatchSize helper — direct
  // ---------------------------------------------------------------

  @Test
  void clampBatchSizeBounds() {
    assertEquals(TimeseriesQualityScoringJob.MIN_BATCH_SIZE, TimeseriesQualityScoringJob.clampBatchSize(0));
    assertEquals(TimeseriesQualityScoringJob.MIN_BATCH_SIZE, TimeseriesQualityScoringJob.clampBatchSize(-100));
    assertEquals(TimeseriesQualityScoringJob.MAX_BATCH_SIZE, TimeseriesQualityScoringJob.clampBatchSize(Integer.MAX_VALUE));
    assertEquals(500, TimeseriesQualityScoringJob.clampBatchSize(500));
  }

  // ---------------------------------------------------------------
  //  fetchPoints integration with stubbed repositories
  // ---------------------------------------------------------------

  @Test
  void fetchPointsReturnsEmptyWhenChannelMissing() {
    when(timeseriesRepository.findTimeseries(anyLong(), any(Timeseries.class)))
      .thenReturn(Optional.empty());
    List<TimeseriesDataPoint> points = job.fetchPoints(
      99L,
      new Timeseries("m", "d", "l", "s", "f"),
      new TimeseriesDataPointsQueryParams(0L, 1L, null, null, null)
    );
    assertTrue(points.isEmpty());
  }

  @Test
  void fetchPointsQueriesRepositoryWhenChannelPresent() {
    TimeseriesEntity entity = new TimeseriesEntity(
      99L,
      new Timeseries("m", "d", "l", "s", "f"),
      DataPointValueType.Double
    );
    when(timeseriesRepository.findTimeseries(anyLong(), any(Timeseries.class)))
      .thenReturn(Optional.of(entity));
    when(timeseriesDataPointRepository.queryDataPoints(anyInt(), any(), any()))
      .thenReturn(perfect(15));
    List<TimeseriesDataPoint> points = job.fetchPoints(
      99L,
      new Timeseries("m", "d", "l", "s", "f"),
      new TimeseriesDataPointsQueryParams(0L, 1L, null, null, null)
    );
    assertEquals(15, points.size());
  }
}
