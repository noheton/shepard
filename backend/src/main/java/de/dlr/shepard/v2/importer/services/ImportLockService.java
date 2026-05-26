package de.dlr.shepard.v2.importer.services;

import de.dlr.shepard.common.identifier.AppIdGenerator;
import de.dlr.shepard.v2.importer.daos.ImportLockDAO;
import de.dlr.shepard.v2.importer.entities.ImportLock;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

/**
 * IMP-LOCK — business logic for the persistent import-in-progress lock.
 *
 * <h2>Lock lifecycle</h2>
 * <pre>
 *   acquire()
 *     │  (status = RUNNING, lastHeartbeatAt = now)
 *     ├─ heartbeat()
 *     │  (updates lastHeartbeatAt)
 *     ├─ release()
 *     │  (status → COMPLETED)
 *     └─ abandon(errorMessage)
 *        (status → FAILED)
 *
 *   cancel()    called by an instance-admin via REST DELETE
 *     (status → CANCELLED)
 * </pre>
 *
 * <h2>Idempotency and stale-lock eviction</h2>
 * <p>{@link #acquire} inspects any existing RUNNING lock before creating a new one:
 * <ul>
 *   <li>If a fresh RUNNING lock exists (heartbeat &lt; {@value #STALE_HEARTBEAT_MS} ms ago),
 *       {@link #acquire} returns {@code null} — the caller must treat this as a conflict
 *       and should not start a new import.</li>
 *   <li>If the heartbeat is stale (&ge; {@value #STALE_HEARTBEAT_MS} ms), the old lock is
 *       transitioned to {@code ABANDONED} and a new RUNNING lock is created.  The abandoned
 *       lock is preserved in Neo4j for audit purposes.</li>
 * </ul>
 *
 * <h2>Why not a single-row mutex</h2>
 * <p>Keeping all lock history (COMPLETED, FAILED, ABANDONED, …) lets operators reconstruct
 * the import timeline across restarts without consulting application logs.
 */
@ApplicationScoped
public class ImportLockService {

  /** Heartbeat age above which a RUNNING lock is considered abandoned (5 minutes). */
  static final long STALE_HEARTBEAT_MS = 5L * 60L * 1_000L;

  @Inject
  ImportLockDAO importLockDAO;

  // ─── Public API ───────────────────────────────────────────────────────────

  /**
   * Attempt to acquire a new import lock for the given collection.
   *
   * <p>If a fresh RUNNING lock already exists for any collection (last heartbeat &lt;
   * {@value #STALE_HEARTBEAT_MS} ms ago), returns {@code null} — caller should respond
   * with HTTP 409.
   *
   * <p>If a stale RUNNING lock is found (heartbeat &ge; {@value #STALE_HEARTBEAT_MS} ms
   * old), it is transitioned to {@code ABANDONED} and a new lock is created.
   *
   * @param targetCollectionAppId appId of the collection being imported into
   * @param startedBy             username of the authenticated caller
   * @return the newly created {@link ImportLock}, or {@code null} if a fresh lock conflicts
   */
  public ImportLock acquire(String targetCollectionAppId, String startedBy) {
    long now = System.currentTimeMillis();

    List<ImportLock> running = importLockDAO.findRunning();
    for (ImportLock existing : running) {
      long age = now - (existing.getLastHeartbeatAt() != null
        ? existing.getLastHeartbeatAt()
        : existing.getStartedAt());

      if (age < STALE_HEARTBEAT_MS) {
        // Fresh lock — caller must back off.
        Log.infof(
          "IMP-LOCK: acquire blocked — fresh RUNNING lock %s (heartbeat age %d ms)",
          existing.getLockId(), age
        );
        return null;
      }

      // Stale lock — mark ABANDONED so audit trail is preserved.
      Log.warnf(
        "IMP-LOCK: abandoning stale RUNNING lock %s (heartbeat age %d ms)",
        existing.getLockId(), age
      );
      existing.setStatus("ABANDONED");
      importLockDAO.createOrUpdate(existing);
    }

    // Create the new RUNNING lock.
    ImportLock lock = new ImportLock();
    lock.setLockId(AppIdGenerator.next());
    lock.setStartedAt(now);
    lock.setStartedBy(startedBy);
    lock.setTargetCollectionAppId(targetCollectionAppId);
    lock.setStatus("RUNNING");
    lock.setLastHeartbeatAt(now);

    ImportLock saved = importLockDAO.createOrUpdate(lock);
    Log.infof("IMP-LOCK: acquired lock %s for collection %s by %s",
      saved.getLockId(), targetCollectionAppId, startedBy);
    return saved;
  }

  /**
   * Update {@code lastHeartbeatAt} to the current time for the given lock.
   *
   * <p>Only RUNNING locks accept heartbeats.  Returns the updated lock, or
   * {@code null} if the lock was not found or is not in RUNNING status.
   *
   * @param lockId the lock's public identifier
   * @return updated lock, or {@code null} on not-found / wrong-status
   */
  public ImportLock heartbeat(String lockId) {
    ImportLock lock = importLockDAO.findByLockId(lockId);
    if (lock == null) {
      Log.debugf("IMP-LOCK: heartbeat ignored — lockId %s not found", lockId);
      return null;
    }
    if (!"RUNNING".equals(lock.getStatus())) {
      Log.debugf("IMP-LOCK: heartbeat ignored — lock %s has status %s",
        lockId, lock.getStatus());
      return null;
    }
    lock.setLastHeartbeatAt(System.currentTimeMillis());
    return importLockDAO.createOrUpdate(lock);
  }

  /**
   * Release a RUNNING lock with status {@code COMPLETED} (normal import completion).
   *
   * @param lockId the lock's public identifier
   * @return updated lock, or {@code null} on not-found / wrong-status
   */
  public ImportLock release(String lockId) {
    ImportLock lock = importLockDAO.findByLockId(lockId);
    if (lock == null) {
      Log.debugf("IMP-LOCK: release ignored — lockId %s not found", lockId);
      return null;
    }
    if (!"RUNNING".equals(lock.getStatus())) {
      Log.debugf("IMP-LOCK: release ignored — lock %s has status %s",
        lockId, lock.getStatus());
      return null;
    }
    lock.setStatus("COMPLETED");
    lock.setLastHeartbeatAt(System.currentTimeMillis());
    ImportLock saved = importLockDAO.createOrUpdate(lock);
    Log.infof("IMP-LOCK: lock %s released (COMPLETED)", lockId);
    return saved;
  }

  /**
   * Abandon a RUNNING lock with status {@code FAILED} and an error description
   * (import error path).
   *
   * @param lockId       the lock's public identifier
   * @param errorMessage human-readable error description (required)
   * @return updated lock, or {@code null} on not-found / wrong-status
   */
  public ImportLock abandon(String lockId, String errorMessage) {
    ImportLock lock = importLockDAO.findByLockId(lockId);
    if (lock == null) {
      Log.debugf("IMP-LOCK: abandon ignored — lockId %s not found", lockId);
      return null;
    }
    if (!"RUNNING".equals(lock.getStatus())) {
      Log.debugf("IMP-LOCK: abandon ignored — lock %s has status %s",
        lockId, lock.getStatus());
      return null;
    }
    lock.setStatus("FAILED");
    lock.setErrorMessage(errorMessage);
    lock.setLastHeartbeatAt(System.currentTimeMillis());
    ImportLock saved = importLockDAO.createOrUpdate(lock);
    Log.warnf("IMP-LOCK: lock %s abandoned (FAILED): %s", lockId, errorMessage);
    return saved;
  }

  /**
   * Cancel a lock (admin action).  Transitions any non-terminal lock to
   * {@code CANCELLED}.  Terminal locks (COMPLETED, FAILED, CANCELLED, ABANDONED)
   * are returned as-is without modification.
   *
   * @param lockId the lock's public identifier
   * @return updated lock, or {@code null} if not found
   */
  public ImportLock cancel(String lockId) {
    ImportLock lock = importLockDAO.findByLockId(lockId);
    if (lock == null) {
      Log.debugf("IMP-LOCK: cancel ignored — lockId %s not found", lockId);
      return null;
    }
    // Allow cancelling only non-terminal statuses.
    if ("RUNNING".equals(lock.getStatus())) {
      lock.setStatus("CANCELLED");
      lock.setLastHeartbeatAt(System.currentTimeMillis());
      ImportLock saved = importLockDAO.createOrUpdate(lock);
      Log.warnf("IMP-LOCK: lock %s cancelled by admin", lockId);
      return saved;
    }
    // Already terminal — return as-is (idempotent for admin intent).
    return lock;
  }

  /**
   * Return the current lock status: the most recent lock regardless of status,
   * or {@code null} if no locks have ever been created.
   *
   * @return latest lock, or {@code null}
   */
  public ImportLock findCurrent() {
    return importLockDAO.findLatest();
  }
}
