package de.dlr.shepard.v2.importer.entities;

import de.dlr.shepard.common.identifier.HasAppId;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;

/**
 * IMP-LOCK — persistent import-in-progress marker.
 *
 * <p>An {@code :ImportLock} node is created by a client importer before it starts a
 * long-running import operation.  It survives backend restarts, so a restarting server can
 * advertise "an import was in progress" rather than silently losing state.
 *
 * <p>Graph shape:
 * <pre>
 *   (:ImportLock { appId, lockId, startedAt, startedBy,
 *                  targetCollectionAppId, status,
 *                  lastHeartbeatAt, errorMessage })
 * </pre>
 *
 * <p>Status lifecycle:
 * <ul>
 *   <li>{@code RUNNING}   — lock is active; heartbeats expected every ~30 s.</li>
 *   <li>{@code COMPLETED} — import finished normally; lock was released.</li>
 *   <li>{@code FAILED}    — import aborted; {@link #errorMessage} carries the reason.</li>
 *   <li>{@code CANCELLED} — an {@code instance-admin} cancelled the lock via
 *       {@code DELETE /v2/import/lock/{lockId}}.</li>
 *   <li>{@code ABANDONED} — a new acquire found this lock stale (heartbeat older than 5 min)
 *       and transitioned it to ABANDONED before creating a fresh RUNNING lock.</li>
 * </ul>
 *
 * <p>Idempotency: {@link de.dlr.shepard.v2.importer.services.ImportLockService#acquire}
 * checks for an existing RUNNING lock before creating one.  If a RUNNING lock has a fresh
 * heartbeat (&lt; 5 min), the acquire call fails with a conflict.  If the heartbeat is stale
 * (&ge; 5 min), the old lock is transitioned to ABANDONED and a fresh lock is created —
 * preserving audit history.
 *
 * <p>Relation to {@code appId}: {@link #lockId} is minted independently (UUID v7) at acquire
 * time and is the public identifier for lock operations.  {@link #appId} is minted by
 * {@code GenericDAO.createOrUpdate} per the standard HasAppId contract; it is the Neo4j-level
 * stable identifier and may differ from {@code lockId}.  Callers always reference locks by
 * {@code lockId} on the REST surface.
 */
@NodeEntity
@Getter
@Setter
@NoArgsConstructor
@ToString
public class ImportLock implements HasAppId {

  @Id
  @GeneratedValue
  private Long id;

  /** UUID v7 minted by GenericDAO on first save. */
  @Property("appId")
  private String appId;

  /**
   * Public lock identifier (UUID v7, minted at acquire time).
   * Callers use this value in REST paths and heartbeat calls.
   */
  @Property("lockId")
  private String lockId;

  /** Epoch millis when the lock was acquired. */
  @Property("startedAt")
  private Long startedAt;

  /** Username of the caller who acquired the lock. */
  @Property("startedBy")
  private String startedBy;

  /** AppId of the Collection being imported into. */
  @Property("targetCollectionAppId")
  private String targetCollectionAppId;

  /**
   * Lock lifecycle status.  One of: {@code RUNNING}, {@code COMPLETED},
   * {@code FAILED}, {@code CANCELLED}, {@code ABANDONED}.
   * Stored as String to match the {@link de.dlr.shepard.v2.importer.entities.ImportPlan}
   * convention (OGM enum mapping is fragile).
   */
  @Property("status")
  private String status;

  /**
   * Epoch millis of the most recent heartbeat.  Updated by the client importer
   * approximately every 30 s while the import is running.
   * A value older than 5 minutes is treated as stale by a subsequent acquire call.
   */
  @Property("lastHeartbeatAt")
  private Long lastHeartbeatAt;

  /**
   * Human-readable error description set on status transition to {@code FAILED}.
   * {@code null} for all other statuses.
   */
  @Property("errorMessage")
  private String errorMessage;

  /** Testing helper: create a lock with a known Neo4j id. */
  public ImportLock(long id) {
    this.id = id;
  }

  @Override
  public int hashCode() {
    return Objects.hash(appId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof ImportLock other)) return false;
    return Objects.equals(appId, other.appId);
  }
}
