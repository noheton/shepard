package de.dlr.shepard.v2.importer.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.v2.importer.entities.ImportLock;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * IMP-LOCK — data-access layer for {@link ImportLock} nodes.
 *
 * <p>Provides targeted Cypher queries for lock lifecycle operations.
 * The DAO keeps queries minimal — business-rule logic lives in
 * {@link de.dlr.shepard.v2.importer.services.ImportLockService}.
 */
@ApplicationScoped
public class ImportLockDAO extends GenericDAO<ImportLock> {

  @Override
  public Class<ImportLock> getEntityType() {
    return ImportLock.class;
  }

  /**
   * Find a lock by its public {@code lockId}.
   *
   * @param lockId the UUID-v7 lock identifier (not the OGM id or appId)
   * @return the matching lock, or {@code null} if not found
   */
  public ImportLock findByLockId(String lockId) {
    String query =
      "MATCH (l:ImportLock) WHERE l.lockId = $lockId RETURN l LIMIT 1";
    var iter = findByQuery(query, Map.of("lockId", lockId)).iterator();
    return iter.hasNext() ? iter.next() : null;
  }

  /**
   * Return all locks with status {@code RUNNING}, newest first.
   *
   * @return running locks ordered by {@code startedAt} descending
   */
  public List<ImportLock> findRunning() {
    String query =
      "MATCH (l:ImportLock) WHERE l.status = 'RUNNING' " +
      "RETURN l ORDER BY l.startedAt DESC";
    List<ImportLock> result = new ArrayList<>();
    for (var lock : findByQuery(query, Map.of())) {
      result.add(lock);
    }
    return result;
  }

  /**
   * Return the most recent lock regardless of status, or {@code null} if none exists.
   * Used by {@code GET /v2/import/lock} to surface the current or last-known state.
   *
   * @return the most recently started lock, or {@code null}
   */
  public ImportLock findLatest() {
    String query =
      "MATCH (l:ImportLock) RETURN l ORDER BY l.startedAt DESC LIMIT 1";
    var iter = findByQuery(query, Map.of()).iterator();
    return iter.hasNext() ? iter.next() : null;
  }

  /**
   * Delete all locks whose {@code startedAt} is older than the given epoch-millis threshold.
   *
   * <p>Only terminal locks (COMPLETED, FAILED, CANCELLED, ABANDONED) are pruned — RUNNING
   * locks are never deleted by this method.
   *
   * @param olderThanEpochMs epoch millis boundary; locks started before this are eligible
   */
  public void deleteTerminalOlderThan(long olderThanEpochMs) {
    String query =
      "MATCH (l:ImportLock) " +
      "WHERE l.startedAt < $threshold " +
      "AND l.status IN ['COMPLETED', 'FAILED', 'CANCELLED', 'ABANDONED'] " +
      "DETACH DELETE l";
    session.query(query, Map.of("threshold", olderThanEpochMs));
  }
}
