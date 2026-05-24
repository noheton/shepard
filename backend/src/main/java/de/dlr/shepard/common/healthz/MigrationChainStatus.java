package de.dlr.shepard.common.healthz;

import java.util.List;

/**
 * Immutable snapshot of the Neo4j migration chain integrity.
 *
 * <p>Produced by {@link MigrationChainInspector} and consumed by
 * {@link MigrationChainReadinessCheck}. Held by reference until it
 * passes its staleness window.
 *
 * @param healthy {@code true} iff every classpath migration is APPLIED
 *                with a checksum matching the {@code __Neo4jMigration}
 *                node, and the recorded chain matches the local chain
 *                (no extra applied versions, no out-of-order, no
 *                missing or repaired entries).
 * @param outcome the human-readable outcome name reported by the
 *                migrations library ({@code VALID},
 *                {@code DIFFERENT_CONTENT}, {@code DIFFERENT_COUNT},
 *                {@code INCOMPLETE_MIGRATIONS}, ...). May be
 *                {@code "CHECK_FAILED"} when the inspector itself
 *                could not run (e.g. neo4j unreachable).
 * @param pendingVersions ordered list of migration versions present on
 *                the classpath but not yet applied to the database.
 *                Empty when {@code healthy} is true.
 * @param warnings raw warnings returned by the library's
 *                {@code Migrations.validate()} call. Useful for the
 *                runbook (mismatch direction, mismatching checksum,
 *                ...). Empty when {@code healthy} is true.
 * @param errorMessage when the inspector itself raised (driver gone,
 *                config invalid, etc.). {@code null} on success.
 * @param checkedAtEpochMs wall-clock time the snapshot was taken.
 */
public record MigrationChainStatus(
  boolean healthy,
  String outcome,
  List<String> pendingVersions,
  List<String> warnings,
  String errorMessage,
  long checkedAtEpochMs
) {
  public MigrationChainStatus {
    pendingVersions = pendingVersions == null ? List.of() : List.copyOf(pendingVersions);
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
  }

  /** Convenience: build a "could-not-check" status (driver down, etc.). */
  public static MigrationChainStatus checkFailed(String error, long nowMs) {
    return new MigrationChainStatus(false, "CHECK_FAILED", List.of(), List.of(), error, nowMs);
  }

  /** Convenience: build a fully-healthy status. */
  public static MigrationChainStatus healthy(long nowMs) {
    return new MigrationChainStatus(true, "VALID", List.of(), List.of(), null, nowMs);
  }
}
