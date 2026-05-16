package de.dlr.shepard.cli.io;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * FS1e1 — CLI-side mirror of {@code FileMigrationStateIO} from
 * {@code GET /v2/admin/files/migrate/status} and the trigger response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class FileMigrationState {

  private final String status;
  private final String sourceProviderId;
  private final String targetProviderId;
  private final long filesTotal;
  private final long filesMigrated;
  private final long filesFailed;
  private final Instant startedAt;
  private final Instant updatedAt;
  private final String errorMessage;

  public FileMigrationState(
    @JsonProperty("status") String status,
    @JsonProperty("sourceProviderId") String sourceProviderId,
    @JsonProperty("targetProviderId") String targetProviderId,
    @JsonProperty("filesTotal") long filesTotal,
    @JsonProperty("filesMigrated") long filesMigrated,
    @JsonProperty("filesFailed") long filesFailed,
    @JsonProperty("startedAt") Instant startedAt,
    @JsonProperty("updatedAt") Instant updatedAt,
    @JsonProperty("errorMessage") String errorMessage
  ) {
    this.status = status;
    this.sourceProviderId = sourceProviderId;
    this.targetProviderId = targetProviderId;
    this.filesTotal = filesTotal;
    this.filesMigrated = filesMigrated;
    this.filesFailed = filesFailed;
    this.startedAt = startedAt;
    this.updatedAt = updatedAt;
    this.errorMessage = errorMessage;
  }

  public String getStatus() { return status; }
  public String getSourceProviderId() { return sourceProviderId; }
  public String getTargetProviderId() { return targetProviderId; }
  public long getFilesTotal() { return filesTotal; }
  public long getFilesMigrated() { return filesMigrated; }
  public long getFilesFailed() { return filesFailed; }
  public Instant getStartedAt() { return startedAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public String getErrorMessage() { return errorMessage; }
}
