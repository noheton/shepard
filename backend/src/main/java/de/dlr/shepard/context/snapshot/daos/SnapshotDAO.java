package de.dlr.shepard.context.snapshot.daos;

import de.dlr.shepard.common.identifier.AppIdGenerator;
import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.context.snapshot.entities.Snapshot;
import de.dlr.shepard.context.snapshot.entities.SnapshotEntry;
import jakarta.enterprise.context.RequestScoped;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
   * SNAPSHOT-LIST-1-REST — paginated list across all Collections, ordered
   * newest-first. No permission filter is applied here (the REST layer
   * scopes the result to the caller's readable subset; this DAO returns
   * the raw page).
   *
   * @param page zero-based page (clamped to {@code >= 0}).
   * @param size page size (clamped to {@code [1, 200]}).
   * @return ordered list of non-deleted snapshots for this page.
   */
  public List<Snapshot> findAll(int page, int size) {
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), 200);
    long skip = (long) safePage * (long) safeSize;
    String query =
      "MATCH (s:Snapshot) " +
      "WHERE (s.deleted IS NULL OR s.deleted = false) " +
      "RETURN s " +
      "ORDER BY s.snapshotCapturedAtMs DESC " +
      "SKIP $skip LIMIT $limit";
    return StreamSupport
      .stream(
        findByQuery(query, Map.of("skip", skip, "limit", (long) safeSize)).spliterator(),
        false
      )
      .toList();
  }

  /**
   * SNAPSHOT-LIST-1-REST — count of all non-deleted snapshots (no permission
   * scoping; used to populate the envelope {@code total} field on the global
   * list endpoint).
   */
  public long countAll() {
    String query =
      "MATCH (s:Snapshot) WHERE (s.deleted IS NULL OR s.deleted = false) RETURN count(s) AS total";
    return countFromQuery(query, Map.of());
  }

  /**
   * SNAPSHOT-LIST-1-REST — count for a single Collection (mirrors the filter
   * in {@link #findByCollectionAppId(String, int, int)}).
   */
  public long countByCollectionAppId(String collectionAppId) {
    String query =
      "MATCH (s:Snapshot)-[:SNAPSHOT_OF]->(c:Collection {appId: $collectionAppId}) " +
      "WHERE (s.deleted IS NULL OR s.deleted = false) " +
      "RETURN count(s) AS total";
    return countFromQuery(query, Map.of("collectionAppId", collectionAppId));
  }

  private long countFromQuery(String query, Map<String, Object> params) {
    org.neo4j.ogm.model.Result r = session.query(query, params);
    if (r == null) return 0L;
    for (Map<String, Object> row : r.queryResults()) {
      Object t = row.get("total");
      if (t instanceof Number n) return n.longValue();
    }
    return 0L;
  }

  /**
   * Returns all non-deleted {@link Snapshot} nodes whose root collection
   * carries the given {@code collectionAppId}, ordered by creation time
   * descending (newest snapshot first).
   *
   * @param collectionAppId the {@code appId} of the root {@link de.dlr.shepard.context.collection.entities.Collection}.
   * @return ordered list of snapshots; empty when none exist.
   */
  public List<Snapshot> findByCollectionAppId(String collectionAppId, int page, int size) {
    // Cypher SKIP/LIMIT require non-negative ints; the caller clamps but we
    // belt-and-brace here so a stray test or future caller can't blow up
    // Neo4j with a negative argument.
    int safePage = Math.max(page, 0);
    int safeSize = Math.max(size, 1);
    long skip = (long) safePage * (long) safeSize;
    String query =
      "MATCH (s:Snapshot)-[:SNAPSHOT_OF]->(c:Collection) " +
      "WHERE c.appId = $collectionAppId AND (s.deleted IS NULL OR s.deleted = false) " +
      "RETURN s " +
      "ORDER BY s.snapshotCapturedAtMs DESC " +
      "SKIP $skip LIMIT $limit";
    return StreamSupport
      .stream(
        findByQuery(
          query,
          Map.of("collectionAppId", collectionAppId, "skip", skip, "limit", (long) safeSize)
        ).spliterator(),
        false
      )
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
   * Returns the count of non-deleted {@link SnapshotEntry} nodes belonging to
   * the snapshot with the given Neo4j internal id.
   *
   * @param snapshotNeo4jId the OGM-managed {@code Long} id of the parent Snapshot.
   * @return the number of non-deleted entries; 0 when none exist.
   */
  public long countEntriesBySnapshot(long snapshotNeo4jId) {
    String query =
      "MATCH (e:SnapshotEntry)-[:ENTRY_OF]->(s:Snapshot) " +
      "WHERE id(s) = $id AND (e.deleted IS NULL OR e.deleted = false) " +
      "RETURN count(e) AS total";
    var iter = session.query(query, Map.of("id", snapshotNeo4jId)).iterator();
    if (!iter.hasNext()) return 0L;
    Object val = iter.next().get("total");
    return val instanceof Number n ? n.longValue() : 0L;
  }

  /**
   * Returns a bounded page of non-deleted {@link SnapshotEntry} nodes for the
   * snapshot identified by {@code snapshotNeo4jId}, ordered by
   * {@code entityAppId} ASC (same order as the unbounded overload).
   *
   * @param snapshotNeo4jId the OGM-managed {@code Long} id of the parent Snapshot.
   * @param skip the number of rows to skip ({@code page * pageSize}).
   * @param limit the maximum number of rows to return ({@code pageSize}).
   * @return the requested page of entries; empty when skip &ge; total.
   */
  public List<SnapshotEntry> findEntriesBySnapshot(long snapshotNeo4jId, int skip, int limit) {
    String query =
      "MATCH (e:SnapshotEntry)-[:ENTRY_OF]->(s:Snapshot) " +
      "WHERE id(s) = $id AND (e.deleted IS NULL OR e.deleted = false) " +
      "RETURN e " +
      "ORDER BY e.entityAppId ASC " +
      "SKIP $skip LIMIT $limit";
    return StreamSupport
      .stream(
        session.query(SnapshotEntry.class, query,
          Map.of("id", snapshotNeo4jId, "skip", (long) skip, "limit", (long) limit)).spliterator(),
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
   * Returns all entity {@code appId} strings captured in the snapshot as a
   * {@link Set}.
   *
   * <p>Convenience wrapper over {@link #findEntriesBySnapshot(long)} that
   * extracts the {@code entityAppId} scalar from each entry.
   *
   * @param snapshotNeo4jId the OGM-managed {@code Long} id of the parent
   *                        {@code Snapshot}.
   * @return set of {@code entityAppId} strings; empty when no entries exist.
   */
  public Set<String> getEntryAppIds(long snapshotNeo4jId) {
    return findEntriesBySnapshot(snapshotNeo4jId)
      .stream()
      .map(SnapshotEntry::getEntityAppId)
      .collect(Collectors.toSet());
  }

  /**
   * Returns a map of {@code entityAppId} → {@code revision} for all entries
   * in the given snapshot. Used by V2e diff computation.
   *
   * @param snapshotNeo4jId the OGM-managed Long id of the snapshot.
   * @return map of entityAppId to revision; empty when the snapshot has no entries.
   */
  public Map<String, Long> getEntryRevisionMap(long snapshotNeo4jId) {
    return findEntriesBySnapshot(snapshotNeo4jId)
      .stream()
      .collect(Collectors.toMap(
        SnapshotEntry::getEntityAppId,
        SnapshotEntry::getRevision,
        (a, b) -> b
      ));
  }

  /**
   * Filters the provided set of {@code appId} strings to only those that
   * exist as non-deleted {@code :DataObject} nodes in the graph.
   *
   * <p>Results are ordered by {@code appId} ascending for deterministic
   * output (mirrors the manifest endpoint ordering).
   *
   * @param appIds the candidate set of entity {@code appId} strings to check.
   * @return ordered list of {@code appId} strings that resolve to a live
   *         {@code :DataObject} node; empty when {@code appIds} is empty or
   *         none of the candidates are DataObjects.
   */
  public List<String> filterDataObjectAppIds(Collection<String> appIds) {
    if (appIds.isEmpty()) return List.of();
    String query =
      "MATCH (d:DataObject) " +
      "WHERE d.appId IN $appIds " +
      "AND (d.deleted IS NULL OR d.deleted = false) " +
      "RETURN d.appId AS appId " +
      "ORDER BY d.appId";
    org.neo4j.ogm.model.Result result = session.query(
      query,
      Map.of("appIds", new ArrayList<>(appIds))
    );
    List<String> out = new ArrayList<>();
    for (Map<String, Object> row : result) {
      Object val = row.get("appId");
      if (val != null) {
        out.add(val.toString());
      }
    }
    return out;
  }

  /**
   * Returns the count of non-deleted {@link SnapshotEntry} nodes in the given
   * snapshot whose {@code entityAppId} resolves to a live (non-deleted)
   * {@code :DataObject} node. Used to populate {@code totalDataObjects} in
   * the paginated pinned-read response without loading all entries.
   *
   * @param snapshotNeo4jId the OGM-managed {@code Long} id of the parent Snapshot.
   * @return count of DataObject-typed entries; 0 when none exist.
   */
  public long countDataObjectAppIds(long snapshotNeo4jId) {
    String query =
      "MATCH (e:SnapshotEntry)-[:ENTRY_OF]->(s:Snapshot) " +
      "WHERE id(s) = $id AND (e.deleted IS NULL OR e.deleted = false) " +
      "MATCH (d:DataObject {appId: e.entityAppId}) " +
      "WHERE (d.deleted IS NULL OR d.deleted = false) " +
      "RETURN count(d) AS total";
    return countFromQuery(query, Map.of("id", snapshotNeo4jId));
  }

  /**
   * Returns one page of {@code appId} strings for non-deleted
   * {@code :DataObject} nodes captured in the given snapshot, ordered by
   * {@code appId} ascending (same order as {@link #filterDataObjectAppIds}).
   * DB-side SKIP/LIMIT is applied so only the requested window is loaded.
   *
   * @param snapshotNeo4jId the OGM-managed {@code Long} id of the parent Snapshot.
   * @param skip            number of rows to skip ({@code page * pageSize}).
   * @param limit           maximum number of rows to return ({@code pageSize}).
   * @return paged list of DataObject appId strings; empty when skip &ge; total.
   */
  public List<String> findDataObjectAppIds(long snapshotNeo4jId, long skip, int limit) {
    String query =
      "MATCH (e:SnapshotEntry)-[:ENTRY_OF]->(s:Snapshot) " +
      "WHERE id(s) = $id AND (e.deleted IS NULL OR e.deleted = false) " +
      "MATCH (d:DataObject {appId: e.entityAppId}) " +
      "WHERE (d.deleted IS NULL OR d.deleted = false) " +
      "RETURN d.appId AS appId " +
      "ORDER BY d.appId ASC " +
      "SKIP $skip LIMIT $limit";
    org.neo4j.ogm.model.Result result = session.query(
      query,
      Map.of("id", snapshotNeo4jId, "skip", skip, "limit", (long) limit)
    );
    List<String> out = new ArrayList<>();
    for (Map<String, Object> row : result) {
      Object val = row.get("appId");
      if (val != null) out.add(val.toString());
    }
    return out;
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
