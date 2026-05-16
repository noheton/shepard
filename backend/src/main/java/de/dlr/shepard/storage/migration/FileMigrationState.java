package de.dlr.shepard.storage.migration;

import java.time.Instant;

/**
 * FS1e1 — immutable snapshot of the current file-migration job state.
 * Held in an {@code AtomicReference} by {@link FileMigrationService};
 * replaced atomically on each progress update. Serialised to JSON by
 * the REST layer via the {@code FileMigrationStateIO} DTO.
 *
 * @param status           current lifecycle state
 * @param sourceProviderId id of the storage adapter being drained
 * @param targetProviderId id of the storage adapter receiving bytes
 * @param filesTotal       total number of files to migrate (counted
 *                         at job start from a Neo4j query; 0 for IDLE)
 * @param filesMigrated    files successfully moved so far
 * @param filesFailed      files that failed (skipped; migration
 *                         continues to the next file)
 * @param startedAt        when the migration job was triggered;
 *                         null for IDLE
 * @param updatedAt        last progress timestamp; null for IDLE
 * @param errorMessage     fatal error text when status is FAILED;
 *                         null otherwise
 */
public record FileMigrationState(
  FileMigrationStatus status,
  String sourceProviderId,
  String targetProviderId,
  long filesTotal,
  long filesMigrated,
  long filesFailed,
  Instant startedAt,
  Instant updatedAt,
  String errorMessage
) {
  /** Sentinel returned when no migration has been triggered. */
  public static FileMigrationState idle() {
    return new FileMigrationState(
      FileMigrationStatus.IDLE,
      null, null,
      0L, 0L, 0L,
      null, null, null
    );
  }

  /** Starting state right after a trigger. */
  public static FileMigrationState starting(String sourceId, String targetId) {
    Instant now = Instant.now();
    return new FileMigrationState(
      FileMigrationStatus.RUNNING,
      sourceId, targetId,
      0L, 0L, 0L,
      now, now, null
    );
  }

  public FileMigrationState withTotal(long total) {
    return new FileMigrationState(
      status, sourceProviderId, targetProviderId,
      total, filesMigrated, filesFailed,
      startedAt, Instant.now(), errorMessage
    );
  }

  public FileMigrationState withProgress(long migrated, long failed) {
    return new FileMigrationState(
      status, sourceProviderId, targetProviderId,
      filesTotal, migrated, failed,
      startedAt, Instant.now(), errorMessage
    );
  }

  public FileMigrationState withDone() {
    return new FileMigrationState(
      FileMigrationStatus.DONE,
      sourceProviderId, targetProviderId,
      filesTotal, filesMigrated, filesFailed,
      startedAt, Instant.now(), null
    );
  }

  public FileMigrationState withFailed(String error) {
    return new FileMigrationState(
      FileMigrationStatus.FAILED,
      sourceProviderId, targetProviderId,
      filesTotal, filesMigrated, filesFailed,
      startedAt, Instant.now(), error
    );
  }
}
