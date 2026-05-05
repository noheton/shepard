package de.dlr.shepard.data.timeseries.migration.io;

import de.dlr.shepard.data.timeseries.migration.model.MigrationProgress;
import de.dlr.shepard.data.timeseries.migration.model.MigrationProgressStatus;
import java.time.Duration;
import java.time.Instant;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@Schema(name = "MigrationProgress")
public class MigrationProgressIO {

  private long containerId;
  private long rowsTotal;
  private long rowsMigrated;
  private long rowsFailed;
  private int lastBatchIndex;
  private MigrationProgressStatus status;
  private Instant startedAt;
  private Instant lastUpdateAt;
  private String errors;
  private Long estimatedRemainingSeconds;

  public MigrationProgressIO(MigrationProgress p) {
    this.containerId = p.getContainerId();
    this.rowsTotal = p.getRowsTotal();
    this.rowsMigrated = p.getRowsMigrated();
    this.rowsFailed = p.getRowsFailed();
    this.lastBatchIndex = p.getLastBatchIndex();
    this.status = p.getStatus();
    this.startedAt = p.getStartedAt();
    this.lastUpdateAt = p.getLastUpdateAt();
    this.errors = p.getErrors();
    this.estimatedRemainingSeconds = computeRemaining(p);
  }

  private static Long computeRemaining(MigrationProgress p) {
    if (p.getStatus() != MigrationProgressStatus.RUNNING) return null;
    if (p.getStartedAt() == null || p.getRowsMigrated() <= 0) return null;
    long remaining = p.getRowsTotal() - p.getRowsMigrated();
    if (remaining <= 0) return 0L;
    long elapsed = Duration.between(p.getStartedAt(), Instant.now()).toSeconds();
    if (elapsed <= 0) return null;
    double rowsPerSecond = (double) p.getRowsMigrated() / elapsed;
    if (rowsPerSecond <= 0) return null;
    return (long) (remaining / rowsPerSecond);
  }
}
