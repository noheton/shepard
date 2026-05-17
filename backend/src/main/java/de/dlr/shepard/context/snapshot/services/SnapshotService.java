package de.dlr.shepard.context.snapshot.services;

import de.dlr.shepard.context.collection.daos.CollectionDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.snapshot.daos.SnapshotDAO;
import de.dlr.shepard.context.snapshot.entities.Snapshot;
import de.dlr.shepard.context.snapshot.entities.SnapshotEntry;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * V2b — service that orchestrates {@link Snapshot} creation.
 *
 * <p>Creation walks the Collection subtree via a Cypher traversal of depth 15
 * (matching the design limit in {@code aidocs/41 §4.1}) to collect every
 * distinct {@code (entityAppId, revision)} pair, then persists one
 * {@link SnapshotEntry} per pair.
 *
 * <p>The subtree walk uses a direct Cypher query on the DAO's session rather
 * than loading the full OGM graph, so the creation cost is O(entities-in-scope)
 * Cypher rows regardless of the relationship depth.
 *
 * <p>Cross-references: {@code aidocs/41} §3.3 and §4; {@code aidocs/16} V2b.
 */
@RequestScoped
public class SnapshotService {

  @Inject
  SnapshotDAO snapshotDAO;

  @Inject
  CollectionDAO collectionDAO;

  /**
   * Creates a {@link Snapshot} for the Collection identified by
   * {@code collectionAppId}.
   *
   * <p>The creation is a multi-step process:
   * <ol>
   *   <li>Resolve the root {@link Collection} by {@code collectionAppId}.</li>
   *   <li>Walk the subtree with a depth-15 Cypher traversal to collect
   *       {@code (entityAppId, revision)} for every reachable
   *       {@code :VersionableEntity}.</li>
   *   <li>Persist the {@link Snapshot} node with the entry count.</li>
   *   <li>Persist one {@link SnapshotEntry} per entity.</li>
   * </ol>
   *
   * @param collectionAppId    the {@code appId} of the root Collection.
   * @param name               user-visible label for the snapshot.
   * @param description        optional free-text description.
   * @param callerUsername     display name of the caller (frozen on the snapshot).
   * @return the persisted {@link Snapshot} with its generated {@code appId}.
   * @throws NotFoundException if no Collection with that {@code appId} exists.
   */
  public Snapshot createSnapshot(
    String collectionAppId,
    String name,
    String description,
    String callerUsername
  ) {
    // Resolve collection
    Collection collection = resolveCollection(collectionAppId);
    if (collection == null) {
      throw new NotFoundException("No Collection found with appId: " + collectionAppId);
    }

    // Walk the subtree — collect all (entityAppId, revision) pairs
    List<EntityRevisionRow> rows = walkSubtree(collectionAppId);
    Log.infof(
      "V2b snapshot: collection=%s, callerUsername=%s, entityCount=%d",
      collectionAppId,
      callerUsername,
      rows.size()
    );

    // Build and persist the Snapshot node first so SnapshotEntry nodes have a
    // target for their ENTRY_OF relationship.
    Snapshot snapshot = new Snapshot();
    snapshot.setName(name);
    snapshot.setDescription(description);
    snapshot.setSnapshotCapturedAtMs(Instant.now().toEpochMilli());
    snapshot.setSnapshotCreatedByUsername(callerUsername);
    snapshot.setCollection(collection);
    snapshot.setEntryCount(rows.size());
    snapshot = snapshotDAO.createOrUpdate(snapshot);

    // Persist one SnapshotEntry per entity in the subtree
    for (EntityRevisionRow row : rows) {
      SnapshotEntry entry = new SnapshotEntry();
      entry.setEntityAppId(row.entityAppId());
      entry.setRevision(row.revision());
      entry.setSnapshot(snapshot);
      snapshotDAO.createEntry(entry);
    }

    return snapshot;
  }

  /**
   * Returns all non-deleted snapshots for the given collection, newest first.
   *
   * @param collectionAppId the appId of the root Collection.
   * @return ordered list of snapshots; empty when none exist.
   */
  public List<Snapshot> listByCollection(String collectionAppId) {
    return snapshotDAO.findByCollectionAppId(collectionAppId);
  }

  /**
   * Returns the {@link Snapshot} identified by {@code snapshotAppId}, or
   * {@code null} when none exists or the snapshot is soft-deleted.
   *
   * @param snapshotAppId the application-level identifier.
   * @return the snapshot, or {@code null}.
   */
  public Snapshot findByAppId(String snapshotAppId) {
    return snapshotDAO.findByAppId(snapshotAppId);
  }

  /**
   * Returns all manifest entries for the snapshot identified by its Neo4j id.
   *
   * @param snapshotNeo4jId the OGM-managed Long id.
   * @return list of entries; empty when none exist.
   */
  public List<SnapshotEntry> findEntries(long snapshotNeo4jId) {
    return snapshotDAO.findEntriesBySnapshot(snapshotNeo4jId);
  }

  /**
   * Soft-deletes the {@link Snapshot} identified by {@code snapshotAppId}.
   * The associated {@link SnapshotEntry} rows are also soft-deleted.
   *
   * @param snapshotAppId the application-level identifier.
   * @return {@code true} if found and deleted; {@code false} if not found.
   */
  public boolean deleteSnapshot(String snapshotAppId) {
    Snapshot snapshot = snapshotDAO.findByAppId(snapshotAppId);
    if (snapshot == null) return false;

    // Soft-delete entries first
    List<SnapshotEntry> entries = snapshotDAO.findEntriesBySnapshot(snapshot.getId());
    for (SnapshotEntry entry : entries) {
      entry.setDeleted(true);
      snapshotDAO.createEntry(entry);
    }

    // Soft-delete the snapshot itself
    snapshot.setDeleted(true);
    snapshotDAO.createOrUpdate(snapshot);
    return true;
  }

  // ─── internal helpers ────────────────────────────────────────────────────

  private Collection resolveCollection(String collectionAppId) {
    // CollectionDAO does not have a findByAppId method; query via findByQuery.
    String query =
      "MATCH (c:Collection) WHERE c.appId = $appId AND (c.deleted IS NULL OR c.deleted = false) RETURN c";
    var iter = collectionDAO.findByQuery(query, Map.of("appId", collectionAppId)).iterator();
    return iter.hasNext() ? iter.next() : null;
  }

  /**
   * Walks the Collection subtree up to 15 hops deep and collects one
   * {@link EntityRevisionRow} per distinct {@code :VersionableEntity} node
   * that carries a non-null {@code appId}.
   *
   * <p>The query matches all outgoing paths from the Collection following
   * any relationship type, consistent with the design in
   * {@code aidocs/41 §4.1}. Nodes without an {@code appId} (pre-L2a rows
   * not yet backfilled) are silently skipped.
   */
  private List<EntityRevisionRow> walkSubtree(String collectionAppId) {
    List<Map<String, Object>> rawRows = snapshotDAO.walkCollectionSubtree(collectionAppId);
    List<EntityRevisionRow> rows = new ArrayList<>();
    for (Map<String, Object> record : rawRows) {
      String entityAppId = (String) record.get("entityAppId");
      long revision = ((Number) record.get("revision")).longValue();
      rows.add(new EntityRevisionRow(entityAppId, revision));
    }
    return rows;
  }

  /**
   * Lightweight projection of one tree-walk result row.
   */
  record EntityRevisionRow(String entityAppId, long revision) {}
}
