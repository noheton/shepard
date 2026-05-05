package de.dlr.shepard.data.timeseries.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.data.timeseries.migration.services.MigrationProgressService;
import de.dlr.shepard.data.timeseries.migration.services.MigrationRunner;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import de.dlr.shepard.data.timeseries.model.TimeseriesEntity;
import de.dlr.shepard.data.timeseries.repositories.TimeseriesDataPointRepository;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class MigrationRunnerTest extends BaseTestCase {

  @Mock
  MigrationProgressService progressService;

  @Mock
  TimeseriesDataPointRepository dataPointRepository;

  @InjectMocks
  MigrationRunner runner;

  @Test
  public void migrateContainer_recordsProgressForEachBatch() throws Exception {
    long containerId = 42L;
    List<TimeseriesDataPoint> rows = generateRows(50_000);
    TimeseriesEntity ts = mockEntity();

    when(progressService.shouldSkip(containerId)).thenReturn(false);
    when(progressService.resumeBatchIndex(containerId)).thenReturn(0);

    AtomicInteger reportedBatches = new AtomicInteger();
    doAnswer(invocation -> {
      int startBatchIndex = invocation.getArgument(2);
      java.util.function.BiConsumer<Integer, Integer> reporter = invocation.getArgument(3);
      int totalBatches = (rows.size() + 19_999) / 20_000;
      for (int i = startBatchIndex; i < totalBatches; i++) {
        int from = i * 20_000;
        int to = Math.min(from + 20_000, rows.size());
        reporter.accept(i, to - from);
        reportedBatches.incrementAndGet();
      }
      return null;
    })
      .when(dataPointRepository)
      .insertManyDataPointsWithCopyCommandBatched(eq(rows), eq(ts), eq(0), any(), any());

    runner.migrateContainer(containerId, rows, ts);

    assertEquals(3, reportedBatches.get());
    verify(progressService).start(containerId, 50_000);
    verify(progressService, atLeastOnce()).recordBatch(eq(containerId), anyInt(), anyLong());
    verify(progressService).complete(containerId);
  }

  @Test
  public void migrateContainer_skipsIfCompleted() throws Exception {
    when(progressService.shouldSkip(99L)).thenReturn(true);
    runner.migrateContainer(99L, generateRows(10), mockEntity());
    verify(progressService, times(0)).start(anyLong(), anyLong());
    verify(dataPointRepository, times(0))
      .insertManyDataPointsWithCopyCommandBatched(any(), any(), anyInt(), any(), any());
  }

  @Test
  public void migrateContainer_resumesFromLastBatchIndex() throws Exception {
    long containerId = 7L;
    List<TimeseriesDataPoint> rows = generateRows(50_000);
    TimeseriesEntity ts = mockEntity();

    when(progressService.shouldSkip(containerId)).thenReturn(false);
    when(progressService.resumeBatchIndex(containerId)).thenReturn(2);

    AtomicInteger reported = new AtomicInteger();
    doAnswer(invocation -> {
      int startBatchIndex = invocation.getArgument(2);
      java.util.function.BiConsumer<Integer, Integer> reporter = invocation.getArgument(3);
      int totalBatches = (rows.size() + 19_999) / 20_000;
      for (int i = startBatchIndex; i < totalBatches; i++) {
        reporter.accept(i, 1);
        reported.incrementAndGet();
      }
      return null;
    })
      .when(dataPointRepository)
      .insertManyDataPointsWithCopyCommandBatched(eq(rows), eq(ts), eq(2), any(), any());

    runner.migrateContainer(containerId, rows, ts);

    assertEquals(1, reported.get());
    verify(progressService).complete(containerId);
  }

  @Test
  public void migrateContainer_marksFailedOnException() throws Exception {
    long containerId = 5L;
    List<TimeseriesDataPoint> rows = generateRows(20_000);
    TimeseriesEntity ts = mockEntity();
    when(progressService.shouldSkip(containerId)).thenReturn(false);
    when(progressService.resumeBatchIndex(containerId)).thenReturn(0);

    doAnswer(invocation -> {
      throw new SQLException("disk full");
    })
      .when(dataPointRepository)
      .insertManyDataPointsWithCopyCommandBatched(any(), any(), anyInt(), any(), any());

    assertThrows(SQLException.class, () -> runner.migrateContainer(containerId, rows, ts));
    verify(progressService).fail(eq(containerId), any());
  }

  @Test
  public void migrateContainer_resumeAfterMidwayFailure_continuesWhereLeftOff() throws Exception {
    long containerId = 11L;
    List<TimeseriesDataPoint> rows = generateRows(60_000);
    TimeseriesEntity ts = mockEntity();
    when(progressService.shouldSkip(containerId)).thenReturn(false);

    when(progressService.resumeBatchIndex(containerId)).thenReturn(0);
    AtomicInteger firstAttemptBatches = new AtomicInteger();
    doAnswer(invocation -> {
      int startBatchIndex = invocation.getArgument(2);
      java.util.function.BiConsumer<Integer, Integer> reporter = invocation.getArgument(3);
      java.util.function.BiConsumer<Integer, Throwable> errorReporter = invocation.getArgument(4);
      reporter.accept(startBatchIndex, 20_000);
      firstAttemptBatches.incrementAndGet();
      var ex = new SQLException("transient I/O");
      errorReporter.accept(startBatchIndex + 1, ex);
      throw ex;
    })
      .when(dataPointRepository)
      .insertManyDataPointsWithCopyCommandBatched(eq(rows), eq(ts), eq(0), any(), any());

    assertThrows(SQLException.class, () -> runner.migrateContainer(containerId, rows, ts));
    assertEquals(1, firstAttemptBatches.get());

    when(progressService.shouldSkip(containerId)).thenReturn(false);
    when(progressService.resumeBatchIndex(containerId)).thenReturn(1);
    AtomicInteger secondAttemptBatches = new AtomicInteger();
    doAnswer(invocation -> {
      int startBatchIndex = invocation.getArgument(2);
      java.util.function.BiConsumer<Integer, Integer> reporter = invocation.getArgument(3);
      int totalBatches = (rows.size() + 19_999) / 20_000;
      for (int i = startBatchIndex; i < totalBatches; i++) {
        reporter.accept(i, 20_000);
        secondAttemptBatches.incrementAndGet();
      }
      return null;
    })
      .when(dataPointRepository)
      .insertManyDataPointsWithCopyCommandBatched(eq(rows), eq(ts), eq(1), any(), any());

    runner.migrateContainer(containerId, rows, ts);

    assertEquals(2, secondAttemptBatches.get(), "should resume and process remaining batches only");
    verify(progressService).complete(containerId);
  }

  private TimeseriesEntity mockEntity() {
    return new TimeseriesEntity();
  }

  private List<TimeseriesDataPoint> generateRows(int n) {
    var rows = new ArrayList<TimeseriesDataPoint>(n);
    for (int i = 0; i < n; i++) {
      rows.add(new TimeseriesDataPoint(System.currentTimeMillis() * 1_000_000L + i, (double) i));
    }
    return rows;
  }
}
