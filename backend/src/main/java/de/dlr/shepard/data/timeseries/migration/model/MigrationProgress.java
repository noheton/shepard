package de.dlr.shepard.data.timeseries.migration.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "migration_progress")
public class MigrationProgress {

  @Id
  @Column(name = "container_id", nullable = false)
  private long containerId;

  @Column(name = "rows_total", nullable = false)
  private long rowsTotal;

  @Column(name = "rows_migrated", nullable = false)
  private long rowsMigrated;

  @Column(name = "rows_failed", nullable = false)
  private long rowsFailed;

  @Column(name = "last_batch_index", nullable = false)
  private int lastBatchIndex;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private MigrationProgressStatus status;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "last_update_at", nullable = false)
  private Instant lastUpdateAt;

  @Column(name = "errors", columnDefinition = "TEXT", nullable = false)
  private String errors;

  public MigrationProgress() {
    this.status = MigrationProgressStatus.PENDING;
    this.lastUpdateAt = Instant.now();
    this.errors = "";
  }

  public MigrationProgress(long containerId, long rowsTotal) {
    this();
    this.containerId = containerId;
    this.rowsTotal = rowsTotal;
  }

  public long getContainerId() {
    return containerId;
  }

  public void setContainerId(long containerId) {
    this.containerId = containerId;
  }

  public long getRowsTotal() {
    return rowsTotal;
  }

  public void setRowsTotal(long rowsTotal) {
    this.rowsTotal = rowsTotal;
  }

  public long getRowsMigrated() {
    return rowsMigrated;
  }

  public void setRowsMigrated(long rowsMigrated) {
    this.rowsMigrated = rowsMigrated;
  }

  public long getRowsFailed() {
    return rowsFailed;
  }

  public void setRowsFailed(long rowsFailed) {
    this.rowsFailed = rowsFailed;
  }

  public int getLastBatchIndex() {
    return lastBatchIndex;
  }

  public void setLastBatchIndex(int lastBatchIndex) {
    this.lastBatchIndex = lastBatchIndex;
  }

  public MigrationProgressStatus getStatus() {
    return status;
  }

  public void setStatus(MigrationProgressStatus status) {
    this.status = status;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(Instant startedAt) {
    this.startedAt = startedAt;
  }

  public Instant getLastUpdateAt() {
    return lastUpdateAt;
  }

  public void setLastUpdateAt(Instant lastUpdateAt) {
    this.lastUpdateAt = lastUpdateAt;
  }

  public String getErrors() {
    return errors;
  }

  public void setErrors(String errors) {
    this.errors = errors;
  }
}
