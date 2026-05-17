package de.dlr.shepard.context.snapshot.daos;

import de.dlr.shepard.common.identifier.AppIdGenerator;
import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.context.snapshot.entities.Snapshot;
import de.dlr.shepard.context.snapshot.entities.SnapshotEntry;
import jakarta.enterprise.context.RequestScoped;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

/**
 * V2b — data-access object for {@link Snapshot} and {@link SnapshotEntry}
 * nodes.
 *
 * <p>{@code SnapshotDAO} covers both entity types because snapshot entries are
 * never accessed independently — they are always read in the context of a
 * parent snapshot. Keeping them together avoids a proliferation of DAO classes
 * for a single feature slice.
 *
 * <p>Cross-references: {@code aidocs/41} §4; {@code aidocs/16} V2b.
 */
@RequestScoped
public class SnapshotDAO extends GenericDAO<Snapshot> {

  @Override
  public Class<Snapshot> getEntityType() {
    return Snapshot.class;
  }

  /**
   * Looks up a {@link Snapshot} by its application-level {@code appId}.
   *
   * @param appId UUID v7 identifier of the snapshot.
   * @return the matching snapshot, or {@code null} when none exists.
   */
  public Snapshot findByAppId(String appId) {
    String query =
      "MATCH (s:Snapshot) " +
      "WHERE s.appId = $appId AND (s.deleted IS NULL OR s.deleted = false) " +
      "RETURN s";
    var iter = findByQuery(query, Map.of("appId", appId)).iterator();
    return iter.hasNext() ? iter.next() : null;
  }

  /**
   * Returns all non-deleted {@link Snapshot} nodes whose root collection
   * carries the given {@code collectionAppId}, ordered by creation time
   * descending (newest snapshot first).
   *
   * @param collectionAppId the {@code appId} of the root {@link de.dlr.shepard.context.collection.entities.Collection}.
   * @return ordered list of snapshots; empty when none exist.
   */
  public List<Snapshot> findByCollectionAppId(String collectionAppId) {
    String query =
      "MATCH (s:Snapshot)-[:SNAPSHOT_OF]->(c:Collection) " +
      "WHERE c.appId = $collectionAppId AND (s.deleted IS NULL OR s.deleted = false) " +
      "RETURN s " +
      "ORDER BY s.snapshotCapturedAtMs DESC";
    return StreamSupport
      .stream(findByQuery(query, Map.of("collectionAppId", collectionAppId)).spliterator(), false)
      .toList();
  }

  /**
   * Returns all non-deleted {@link SnapshotEntry} nodes associated with the
   * given snapshot's Neo4j internal id, ordered by {@code entityAppId}
   * (stable, deterministic ordering for manifest output).
   *
   * @param snapshotNeo4jId the OGM-managed {@code Long} id of the parent
   *                        {@code Snapshot}.
   * @return list of entries; empty when none exist.
   */
  public List<SnapshotEntry> findEntriesBySnapshot(long snapshotNeo4jId) {
    String query =
      "MATCH (e:SnapshotEntry)-[:ENTRY_OF]->(s:Snapshot) " +
      "WHERE id(s) = $id AND (e.deleted IS NULL OR e.deleted = false) " +
      "RETURN e " +
      "ORDER BY e.entityAppId ASC";
    return StreamSupport
      .stream(
        session.query(SnapshotEntry.class, query, Map.of("id", snapshotNeo4jId)).spliterator(),
        false
      )
      .toList();
  }

  /**
   * Walks the Collection subtree identified by {@code collectionAppId} up to
   * 15 relationship hops and returns a list of {@code {entityAppId, revision}}
   * maps for every distinct {@code :VersionableEntity} node with a non-null
   * {@code appId}.
   *
   * <p>Nodes without an {@code appId} (pre-L2a rows not yet backfilled) are
   * silently skipped by the {@code WHERE e.appId IS NOT NULL} guard.
   *
   * @param collectionAppId the application-level identifier of the root Collection.
   * @return list of {@code {entityAppId: String, revision: Number}} maps.
   */
  public java.util.List<java.util.Map<String, Object>> walkCollectionSubtree(String collectionAppId) {
    String query =
      "MATCH (c:Collection {appId: $collectionAppId})-[*0..15]->(e:VersionableEntity) " +
      "WHERE e.appId IS NOT NULL " +
      "RETURN DISTINCT e.appId AS entityAppId, e.revision AS revision";
    org.neo4j.ogm.model.Result result = session.query(query, Map.of("collectionAppId", collectionAppId));
    java.util.List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
    for (java.util.Map<String, Object> row : result) {
      rows.add(row);
    }
    return rows;
  }

  /**
   * Persists a {@link SnapshotEntry} node. Mints a fresh UUID v7
   * {@code appId} via {@link AppIdGenerator#next()} before saving (since
   * this method bypasses the type-parameterised {@code createOrUpdate} that
   * normally handles minting for the primary entity type {@link Snapshot}).
   *
   * @param entry the entry to persist (appId may be null; will be set).
   * @return the saved entry (with the OGM-generated {@code id} populated).
   */
  public SnapshotEntry createEntry(SnapshotEntry entry) {
    if (entry.getAppId() == null) {
      entry.setAppId(AppIdGenerator.next());
    }
    session.save(entry, DEPTH_ENTITY);
    return entry;
  }
}
