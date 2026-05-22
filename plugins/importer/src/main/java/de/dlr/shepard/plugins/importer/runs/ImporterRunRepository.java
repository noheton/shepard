package de.dlr.shepard.plugins.importer.runs;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * IMP1a / PR-2 — Panache repository for the {@code importer_run}
 * table. Mirrors {@code MigrationProgressRepository}'s shape so a
 * reviewer familiar with the P3 precedent recognises this surface
 * immediately.
 *
 * <p>Methods are scoped tightly to what {@link ImporterRunService}
 * actually needs in PR-2..PR-4:
 *
 * <ul>
 *   <li>{@link #findOne(UUID)} — single-row lookup; null becomes
 *       {@link Optional#empty()};
 *   <li>{@link #listByPrincipal(String, ImporterRunStatus, int, int)}
 *       — "my runs", optionally filtered by status, with paging;
 *   <li>{@link #listPending(int)} — what the scheduler claims in
 *       PR-4 (PR-2 ships the method; PR-4 wires it up);
 *   <li>{@link #listStale(Instant)} — what the reaper marks
 *       {@code JOB_STALLED} (same posture);
 *   <li>{@link #listTerminalOlderThan(Instant)} — GC sweep input.
 * </ul>
 *
 * <p>Heavy reads (LIMIT page-size) only — we never load the whole
 * table. The compound indexes shipped in
 * {@code V1.11.0__add_importer_run_table.sql} keep these queries
 * cheap at MFFD scale (hundreds of runs/day, low millions/year
 * worst case).
 */
@ApplicationScoped
public class ImporterRunRepository implements PanacheRepositoryBase<ImporterRun, UUID> {

  /** Single-row lookup by id. */
  public Optional<ImporterRun> findOne(UUID id) {
    return Optional.ofNullable(findById(id));
  }

  /**
   * Page the rows owned by {@code principal}, optionally filtering
   * by status. Sort: {@code created_at DESC} (most-recent first —
   * what a user typically wants when they open "My imports").
   */
  public List<ImporterRun> listByPrincipal(
    String principal,
    ImporterRunStatus statusFilter,
    int page,
    int size
  ) {
    if (statusFilter == null) {
      return find("principal = ?1 ORDER BY createdAt DESC", principal)
        .page(page, size)
        .list();
    }
    return find(
      "principal = ?1 AND status = ?2 ORDER BY createdAt DESC",
      principal,
      statusFilter
    )
      .page(page, size)
      .list();
  }

  /**
   * Page of {@code PENDING} rows — the scheduler claim pool.
   * PR-4 will use this with {@code SELECT ... FOR UPDATE SKIP LOCKED}
   * in a transactional claim transition; PR-2 ships only the
   * lookup, no claim semantics yet.
   */
  public List<ImporterRun> listPending(int limit) {
    return find("status = ?1 ORDER BY createdAt ASC", ImporterRunStatus.PENDING)
      .page(0, limit)
      .list();
  }

  /**
   * RUNNING rows whose {@code last_progress_at} is older than
   * {@code threshold} — the reaper input. PR-4 will flip these
   * to {@code FAILED} with {@code error_class=JOB_STALLED}.
   */
  public List<ImporterRun> listStale(Instant threshold) {
    return list(
      "status = ?1 AND lastProgressAt IS NOT NULL AND lastProgressAt < ?2",
      ImporterRunStatus.RUNNING,
      threshold
    );
  }

  /**
   * Terminal rows ({@code SUCCEEDED}/{@code FAILED}/{@code CANCELLED})
   * older than {@code threshold} — the GC sweep input. PR-4 will
   * delete these once {@code shepard.importer.retention-days}
   * expires.
   */
  public List<ImporterRun> listTerminalOlderThan(Instant threshold) {
    return list(
      "status IN (?1, ?2, ?3) AND finishedAt IS NOT NULL AND finishedAt < ?4",
      ImporterRunStatus.SUCCEEDED,
      ImporterRunStatus.FAILED,
      ImporterRunStatus.CANCELLED,
      threshold
    );
  }
}
