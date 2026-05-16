package de.dlr.shepard.storage.migration;

/**
 * FS1e1 — lifecycle states for a big-bang file-storage migration job.
 *
 * <p>State transitions:
 * <pre>
 *   IDLE → RUNNING → DONE
 *                 ↘ FAILED
 * </pre>
 * Only one migration may run at a time; a new trigger while
 * {@code RUNNING} is rejected by
 * {@link FileMigrationService#triggerMigration}.
 */
public enum FileMigrationStatus {
  /** No migration has been triggered since the last restart. */
  IDLE,
  /** A migration job is actively streaming files between adapters. */
  RUNNING,
  /** Migration completed without unrecoverable errors. */
  DONE,
  /** Migration aborted — see {@code errorMessage} on the state record. */
  FAILED
}
