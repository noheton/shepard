package de.dlr.shepard.plugins.importer.runs;

/**
 * IMP1a / PR-2 — state machine for an {@link ImporterRun}.
 *
 * <p>Mirrors the JobStatus enum proposed in
 * {@code aidocs/platform/32-long-running-process-pattern.md §3} —
 * deliberately identical names so a future generic
 * {@code JobService} can adopt this table by renaming the
 * underlying class without touching the SQL or the wire shape.
 *
 * <p>State transitions:
 *
 * <pre>
 *  PENDING ──claim──▶ RUNNING ──ok──▶ SUCCEEDED
 *     │                  │
 *     │                  ├──err──▶ FAILED
 *     │                  │
 *     │                  └──cancel/stalled──▶ CANCELLED / FAILED(JOB_STALLED)
 *     │
 *     └──cancel-while-queued──▶ CANCELLED
 * </pre>
 *
 * <p>Terminal states ({@link #SUCCEEDED}, {@link #FAILED},
 * {@link #CANCELLED}) never transition again. The scheduler / reaper
 * is responsible for honouring this invariant.
 */
public enum ImporterRunStatus {
  PENDING,
  RUNNING,
  SUCCEEDED,
  FAILED,
  CANCELLED;

  /**
   * Whether this status is terminal — no further transitions are
   * permitted. Used by the reaper, by cancellation logic, and by
   * the GC sweep to decide which rows to age out.
   */
  public boolean isTerminal() {
    return this == SUCCEEDED || this == FAILED || this == CANCELLED;
  }
}
