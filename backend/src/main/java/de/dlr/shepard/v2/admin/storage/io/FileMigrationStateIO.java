package de.dlr.shepard.v2.admin.storage.io;

import de.dlr.shepard.storage.migration.FileMigrationState;
import de.dlr.shepard.storage.migration.FileMigrationStatus;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** FS1e1 — response shape for the migration status endpoints. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileMigrationStateIO {

  @Schema(description = "Current lifecycle state of the migration job.")
  private FileMigrationStatus status;

  @Schema(description = "Id of the adapter being drained.", nullable = true)
  private String sourceProviderId;

  @Schema(description = "Id of the adapter receiving files.", nullable = true)
  private String targetProviderId;

  @Schema(description = "Total number of files to migrate (counted at job start).")
  private long filesTotal;

  @Schema(description = "Files successfully moved to the target adapter so far.")
  private long filesMigrated;

  @Schema(description = "Files that failed (skipped; migration continued).")
  private long filesFailed;

  @Schema(description = "When the migration job was triggered.", nullable = true)
  private Instant startedAt;

  @Schema(description = "Last progress update timestamp.", nullable = true)
  private Instant updatedAt;

  @Schema(description = "Fatal error message when status is FAILED.", nullable = true)
  private String errorMessage;

  public static FileMigrationStateIO from(FileMigrationState state) {
    return new FileMigrationStateIO(
      state.status(),
      state.sourceProviderId(),
      state.targetProviderId(),
      state.filesTotal(),
      state.filesMigrated(),
      state.filesFailed(),
      state.startedAt(),
      state.updatedAt(),
      state.errorMessage()
    );
  }
}
