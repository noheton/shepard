package de.dlr.shepard.data.timeseries.migration.services;

import de.dlr.shepard.data.timeseries.migration.model.MigrationProgress;
import de.dlr.shepard.data.timeseries.migration.model.MigrationProgressStatus;
import de.dlr.shepard.data.timeseries.migration.repositories.MigrationProgressRepository;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class MigrationProgressService {

  public static final int MAX_ERRORS_PER_TASK = 20;

  @Inject
  MigrationProgressRepository repository;

  @Transactional
  public MigrationProgress start(long containerId, long rowsTotal) {
    var existing = repository.find(containerId);
    MigrationProgress progress = existing.orElseGet(() -> {
      var p = new MigrationProgress(containerId, rowsTotal);
      repository.persist(p);
      return p;
    });

    if (progress.getStatus() == MigrationProgressStatus.COMPLETED) {
      Log.infof("Migration for container %d already completed, skipping", containerId);
      return progress;
    }

    progress.setRowsTotal(rowsTotal);
    progress.setStatus(MigrationProgressStatus.RUNNING);
    if (progress.getStartedAt() == null) {
      progress.setStartedAt(Instant.now());
    }
    progress.setLastUpdateAt(Instant.now());
    return progress;
  }

  @Transactional
  public MigrationProgress recordBatch(long containerId, int batchIndex, long rowsInBatch) {
    var progress = requireProgress(containerId);
    progress.setLastBatchIndex(batchIndex);
    progress.setRowsMigrated(progress.getRowsMigrated() + rowsInBatch);
    progress.setStatus(MigrationProgressStatus.RUNNING);
    progress.setLastUpdateAt(Instant.now());
    return progress;
  }

  @Transactional
  public MigrationProgress recordError(long containerId, long rowsInBatch, String errorMessage) {
    var progress = requireProgress(containerId);
    progress.setRowsFailed(progress.getRowsFailed() + rowsInBatch);
    progress.setLastUpdateAt(Instant.now());
    progress.setErrors(appendError(progress.getErrors(), errorMessage));
    return progress;
  }

  @Transactional
  public MigrationProgress complete(long containerId) {
    var progress = requireProgress(containerId);
    progress.setStatus(MigrationProgressStatus.COMPLETED);
    progress.setLastUpdateAt(Instant.now());
    return progress;
  }

  @Transactional
  public MigrationProgress fail(long containerId, String errorMessage) {
    var progress = requireProgress(containerId);
    progress.setStatus(MigrationProgressStatus.FAILED);
    progress.setLastUpdateAt(Instant.now());
    progress.setErrors(appendError(progress.getErrors(), errorMessage));
    return progress;
  }

  public Optional<MigrationProgress> getProgress(long containerId) {
    return repository.find(containerId);
  }

  public List<MigrationProgress> listAll() {
    return repository.listAll();
  }

  /**
   * Resume index: callers should skip up to and including this batch.
   * Returns 0 if no progress exists, completed status, or failed (manual retry).
   */
  public int resumeBatchIndex(long containerId) {
    var progress = repository.find(containerId).orElse(null);
    if (progress == null) return 0;
    return switch (progress.getStatus()) {
      case RUNNING, PENDING -> progress.getLastBatchIndex();
      case COMPLETED, FAILED -> 0;
    };
  }

  public boolean shouldSkip(long containerId) {
    return repository.find(containerId)
      .map(p -> p.getStatus() == MigrationProgressStatus.COMPLETED)
      .orElse(false);
  }

  private MigrationProgress requireProgress(long containerId) {
    return repository
      .find(containerId)
      .orElseThrow(() -> new IllegalStateException("No migration progress for container " + containerId));
  }

  private String appendError(String existing, String message) {
    var entry = "[" + Instant.now() + "] " + message;
    if (existing == null || existing.isBlank()) return entry;
    var lines = existing.split("\n");
    var newLines = new java.util.ArrayList<String>(lines.length + 1);
    java.util.Collections.addAll(newLines, lines);
    newLines.add(entry);
    while (newLines.size() > MAX_ERRORS_PER_TASK) newLines.removeFirst();
    return String.join("\n", newLines);
  }
}
