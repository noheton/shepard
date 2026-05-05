package de.dlr.shepard.data.timeseries.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.data.timeseries.migration.model.MigrationProgress;
import de.dlr.shepard.data.timeseries.migration.model.MigrationProgressStatus;
import de.dlr.shepard.data.timeseries.migration.repositories.MigrationProgressRepository;
import de.dlr.shepard.data.timeseries.migration.services.MigrationProgressService;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class MigrationProgressServiceTest extends BaseTestCase {

  @Mock
  MigrationProgressRepository repository;

  @InjectMocks
  MigrationProgressService service;

  @Test
  public void start_createsRunningProgress_whenNoneExists() {
    AtomicReference<MigrationProgress> persisted = new AtomicReference<>();
    when(repository.find(1L)).thenReturn(Optional.empty());
    doAnswer(invocation -> {
      persisted.set(invocation.getArgument(0));
      return null;
    })
      .when(repository)
      .persist(any(MigrationProgress.class));

    var progress = service.start(1L, 100_000);

    assertEquals(MigrationProgressStatus.RUNNING, progress.getStatus());
    assertEquals(100_000, progress.getRowsTotal());
    assertNotNull(progress.getStartedAt());
    assertNotNull(persisted.get());
  }

  @Test
  public void start_skipsIfAlreadyCompleted() {
    var existing = new MigrationProgress(1L, 50);
    existing.setStatus(MigrationProgressStatus.COMPLETED);
    when(repository.find(1L)).thenReturn(Optional.of(existing));

    var result = service.start(1L, 1234);

    assertEquals(MigrationProgressStatus.COMPLETED, result.getStatus());
    assertEquals(50, result.getRowsTotal());
  }

  @Test
  public void recordBatch_incrementsRowsAndIndex() {
    var progress = new MigrationProgress(1L, 1000);
    progress.setStatus(MigrationProgressStatus.RUNNING);
    when(repository.find(1L)).thenReturn(Optional.of(progress));

    service.recordBatch(1L, 1, 100);
    service.recordBatch(1L, 2, 100);
    service.recordBatch(1L, 3, 100);

    assertEquals(300, progress.getRowsMigrated());
    assertEquals(3, progress.getLastBatchIndex());
    assertEquals(MigrationProgressStatus.RUNNING, progress.getStatus());
  }

  @Test
  public void complete_setsCompletedStatus() {
    var progress = new MigrationProgress(1L, 100);
    progress.setStatus(MigrationProgressStatus.RUNNING);
    when(repository.find(1L)).thenReturn(Optional.of(progress));

    service.complete(1L);

    assertEquals(MigrationProgressStatus.COMPLETED, progress.getStatus());
  }

  @Test
  public void fail_setsFailedStatusAndRecordsError() {
    var progress = new MigrationProgress(1L, 100);
    progress.setStatus(MigrationProgressStatus.RUNNING);
    when(repository.find(1L)).thenReturn(Optional.of(progress));

    service.fail(1L, "boom");

    assertEquals(MigrationProgressStatus.FAILED, progress.getStatus());
    assertTrue(progress.getErrors().contains("boom"));
  }

  @Test
  public void resumeBatchIndex_returnsLastIndex_whenRunning() {
    var progress = new MigrationProgress(1L, 1000);
    progress.setStatus(MigrationProgressStatus.RUNNING);
    progress.setLastBatchIndex(5);
    when(repository.find(1L)).thenReturn(Optional.of(progress));

    assertEquals(5, service.resumeBatchIndex(1L));
  }

  @Test
  public void resumeBatchIndex_returnsZero_whenAbsentOrCompletedOrFailed() {
    when(repository.find(1L)).thenReturn(Optional.empty());
    assertEquals(0, service.resumeBatchIndex(1L));

    var done = new MigrationProgress(2L, 100);
    done.setStatus(MigrationProgressStatus.COMPLETED);
    done.setLastBatchIndex(7);
    when(repository.find(2L)).thenReturn(Optional.of(done));
    assertEquals(0, service.resumeBatchIndex(2L));

    var failed = new MigrationProgress(3L, 100);
    failed.setStatus(MigrationProgressStatus.FAILED);
    failed.setLastBatchIndex(7);
    when(repository.find(3L)).thenReturn(Optional.of(failed));
    assertEquals(0, service.resumeBatchIndex(3L));
  }

  @Test
  public void shouldSkip_trueOnlyWhenCompleted() {
    var progress = new MigrationProgress(1L, 1);
    progress.setStatus(MigrationProgressStatus.RUNNING);
    when(repository.find(1L)).thenReturn(Optional.of(progress));
    assertEquals(false, service.shouldSkip(1L));

    progress.setStatus(MigrationProgressStatus.COMPLETED);
    assertEquals(true, service.shouldSkip(1L));
  }

  @Test
  public void recordError_capsErrorList() {
    var progress = new MigrationProgress(1L, 100);
    progress.setStatus(MigrationProgressStatus.RUNNING);
    when(repository.find(1L)).thenReturn(Optional.of(progress));

    for (int i = 0; i < MigrationProgressService.MAX_ERRORS_PER_TASK + 5; i++) {
      service.recordError(1L, 0, "err-" + i);
    }
    long lineCount = progress.getErrors().lines().count();
    assertEquals(MigrationProgressService.MAX_ERRORS_PER_TASK, lineCount);
    assertTrue(progress.getErrors().contains("err-" + (MigrationProgressService.MAX_ERRORS_PER_TASK + 4)));
  }
}
