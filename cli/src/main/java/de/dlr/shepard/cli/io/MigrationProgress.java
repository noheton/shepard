package de.dlr.shepard.cli.io;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Wire-shape mirror of the backend's {@code MigrationProgressIO}
 * (see {@code de.dlr.shepard.data.timeseries.migration.io.MigrationProgressIO}).
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)} keeps the
 * CLI forward-compatible if the backend adds more fields.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class MigrationProgress {

  private final long containerId;
  private final long rowsTotal;
  private final long rowsMigrated;
  private final long rowsFailed;
  private final int lastBatchIndex;
  private final String status;
  private final Instant startedAt;
  private final Instant lastUpdateAt;
  private final String errors;
  private final Long estimatedRemainingSeconds;

  public MigrationProgress(
    @JsonProperty("containerId") long containerId,
    @JsonProperty("rowsTotal") long rowsTotal,
    @JsonProperty("rowsMigrated") long rowsMigrated,
    @JsonProperty("rowsFailed") long rowsFailed,
    @JsonProperty("lastBatchIndex") int lastBatchIndex,
    @JsonProperty("status") String status,
    @JsonProperty("startedAt") Instant startedAt,
    @JsonProperty("lastUpdateAt") Instant lastUpdateAt,
    @JsonProperty("errors") String errors,
    @JsonProperty("estimatedRemainingSeconds") Long estimatedRemainingSeconds
  ) {
    this.containerId = containerId;
    this.rowsTotal = rowsTotal;
    this.rowsMigrated = rowsMigrated;
    this.rowsFailed = rowsFailed;
    this.lastBatchIndex = lastBatchIndex;
    this.status = status;
    this.startedAt = startedAt;
    this.lastUpdateAt = lastUpdateAt;
    this.errors = errors;
    this.estimatedRemainingSeconds = estimatedRemainingSeconds;
  }

  public long getContainerId() { return containerId; }
  public long getRowsTotal() { return rowsTotal; }
  public long getRowsMigrated() { return rowsMigrated; }
  public long getRowsFailed() { return rowsFailed; }
  public int getLastBatchIndex() { return lastBatchIndex; }
  public String getStatus() { return status; }
  public Instant getStartedAt() { return startedAt; }
  public Instant getLastUpdateAt() { return lastUpdateAt; }
  public String getErrors() { return errors; }
  public Long getEstimatedRemainingSeconds() { return estimatedRemainingSeconds; }
}
