package de.dlr.shepard.context.snapshot.services;

import de.dlr.shepard.common.identifier.AppIdGenerator;
import de.dlr.shepard.context.collection.daos.CollectionDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.context.snapshot.daos.SnapshotDAO;
import de.dlr.shepard.context.snapshot.entities.Snapshot;
import de.dlr.shepard.context.snapshot.entities.SnapshotEntry;
import de.dlr.shepard.v2.annotations.daos.SemanticAnnotationV2DAO;
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
 * <p>PROV1i — after the snapshot and its entries are persisted, the service
 * automatically emits a {@code rdf:type prov:Entity} {@link SemanticAnnotation}
 * for the snapshot's {@code appId}. This makes every snapshot queryable via
 * SPARQL as a {@code prov:Entity} without the import script having to do it
 * manually. The typing is best-effort: a failure logs a WARN but does not
 * roll back the snapshot (snapshot integrity is more important than the
 * optional typing decoration).
 *
 * <p>Cross-references: {@code aidocs/41} §3.3 and §4; {@code aidocs/16} V2b,
 * PROV1i.
 */
@RequestScoped
public class SnapshotService {

  /** PROV1i — IRI for the {@code rdf:type} predicate. */
  static final String RDF_TYPE_IRI = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";

  /** PROV1i — IRI for {@code prov:Entity}. */
  static final String PROV_ENTITY_IRI = "http://www.w3.org/ns/prov#Entity";

  /** PROV1i — human-readable label for the {@code rdf:type} predicate (snapshotted). */
  static final String RDF_TYPE_LABEL = "rdf:type";

  /** PROV1i — human-readable label for {@code prov:Entity} (snapshotted). */
  static final String PROV_ENTITY_LABEL = "prov:Entity";

  /** PROV1i — source tag identifying this annotation as system-generated. */
  static final String SOURCE_SYSTEM = "system";

  @Inject
  SnapshotDAO snapshotDAO;

  @Inject
  CollectionDAO collectionDAO;

  @Inject
  SemanticAnnotationV2DAO semanticAnnotationV2DAO;

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

    // PROV1i — emit rdf:type prov:Entity for the new snapshot so it is
    // queryable via SPARQL without the import script having to do it manually.
    emitProvEntityTyping(snapshot);

    return snapshot;
  }

  /**
   * PROV1i — creates a {@code rdf:type prov:Entity} {@link SemanticAnnotation}
   * for the given snapshot. The annotation is best-effort: a failure is logged
   * as WARN but does NOT prevent the snapshot from being returned to the caller.
   *
   * <p>The annotation uses:
   * <ul>
   *   <li>{@code subjectAppId} = snapshot's {@code appId}</li>
   *   <li>{@code subjectKind} = {@code "Snapshot"}</li>
   *   <li>{@code propertyIRI} = {@code http://www.w3.org/1999/02/22-rdf-syntax-ns#type}</li>
   *   <li>{@code valueIRI} = {@code http://www.w3.org/ns/prov#Entity}</li>
   *   <li>{@code source} = {@code "system"}</li>
   *   <li>{@code sourceMode} = {@code "ai"} (system-generated, not human-authored)</li>
   * </ul>
   *
   * @param snapshot the freshly persisted snapshot; must have a non-null {@code appId}.
   */
  void emitProvEntityTyping(Snapshot snapshot) {
    if (snapshot == null || snapshot.getAppId() == null) {
      Log.warn("PROV1i: snapshot or its appId is null — skipping prov:Entity typing");
      return;
    }
    try {
      SemanticAnnotation typing = new SemanticAnnotation();
      typing.setAppId(AppIdGenerator.next());
      typing.setSubjectAppId(snapshot.getAppId());
      typing.setSubjectKind("Snapshot");
      typing.setPropertyIRI(RDF_TYPE_IRI);
      typing.setPropertyName(RDF_TYPE_LABEL);
      typing.setValueIRI(PROV_ENTITY_IRI);
      typing.setValueName(PROV_ENTITY_LABEL);
      typing.setSource(SOURCE_SYSTEM);
      typing.setSourceMode("ai");   // system-generated, not human-authored
      typing.setConfidence(1.0);
      semanticAnnotationV2DAO.createOrUpdate(typing);
      Log.infof("PROV1i: emitted prov:Entity typing for snapshot %s", snapshot.getAppId());
    } catch (Exception e) {
      Log.warnf(
        e,
        "PROV1i: failed to emit prov:Entity typing for snapshot %s — snapshot persisted; typing skipped",
        snapshot.getAppId()
      );
    }
  }

  /**
   * Returns one page of non-deleted snapshots for the given collection,
   * newest first. {@code page} is 0-indexed; {@code size} is the maximum
   * number of rows returned.
   */
  public List<Snapshot> listByCollection(String collectionAppId, int page, int size) {
    return snapshotDAO.findByCollectionAppId(collectionAppId, page, size);
  }

  /**
   * SNAPSHOT-LIST-1-REST — paginated list across all collections,
   * newest first. No permission filter is applied; the REST layer is
   * responsible for scoping the result to the caller's readable subset.
   */
  public List<Snapshot> listAll(int page, int size) {
    return snapshotDAO.findAll(page, size);
  }

  /** SNAPSHOT-LIST-1-REST — total unfiltered snapshot count for the envelope. */
  public long countAll() {
    return snapshotDAO.countAll();
  }

  /** SNAPSHOT-LIST-1-REST — total per-collection snapshot count for the envelope. */
  public long countByCollection(String collectionAppId) {
    return snapshotDAO.countByCollectionAppId(collectionAppId);
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
   * Returns the total count of non-deleted manifest entries for the given snapshot.
   * Used by the paginated manifest endpoint to populate the {@code total} envelope field.
   *
   * @param snapshotNeo4jId the OGM-managed Long id.
   * @return entry count; 0 when the snapshot has no entries.
   */
  public long countEntries(long snapshotNeo4jId) {
    return snapshotDAO.countEntriesBySnapshot(snapshotNeo4jId);
  }

  /**
   * Returns a bounded page of manifest entries for the given snapshot.
   * Pushes SKIP/LIMIT to Cypher so only the requested rows are loaded.
   *
   * @param snapshotNeo4jId the OGM-managed Long id.
   * @param skip            number of rows to skip ({@code page * pageSize}).
   * @param limit           maximum number of rows to return ({@code pageSize}).
   * @return the requested page of entries; empty when skip &ge; total.
   */
  public List<SnapshotEntry> findEntriesPage(long snapshotNeo4jId, int skip, int limit) {
    return snapshotDAO.findEntriesBySnapshot(snapshotNeo4jId, skip, limit);
  }

  /**
   * Returns a map of {@code entityAppId → revision} for all entries of the
   * given snapshot. Used by V2e diff computation.
   *
   * @param snapshotNeo4jId the OGM-managed Long id of the snapshot.
   * @return map of entityAppId to revision; empty when the snapshot has no entries.
   */
  public Map<String, Long> getEntryRevisionMap(long snapshotNeo4jId) {
    return snapshotDAO.getEntryRevisionMap(snapshotNeo4jId);
  }

  /**
   * V2c — returns the list of {@code appId} strings for all {@code :DataObject}
   * nodes that were captured in the given snapshot.
   *
   * <p>The method first collects all {@code entityAppId} values from the
   * snapshot's {@link SnapshotEntry} set, then filters them to only those
   * that resolve to non-deleted {@code :DataObject} nodes. Results are ordered
   * by {@code appId} ascending (deterministic, matching the manifest ordering).
   *
   * <p>Collections, References, and soft-deleted entities are excluded from
   * the returned list.
   *
   * @param snapshot the snapshot whose entries should be filtered.
   * @return ordered list of DataObject {@code appId} strings; empty when the
   *         snapshot contains no DataObjects.
   */
  public List<String> listDataObjectAppIds(Snapshot snapshot) {
    java.util.Set<String> allEntryAppIds = snapshotDAO.getEntryAppIds(snapshot.getId());
    return snapshotDAO.filterDataObjectAppIds(allEntryAppIds);
  }

  /**
   * Returns the total count of live {@code :DataObject} entries captured in
   * the given snapshot. Delegates to a single Cypher COUNT query — no
   * client-side loading.
   *
   * @param snapshot the snapshot to count DataObject entries for.
   * @return count of DataObject-typed entries; 0 when the snapshot is empty.
   */
  public long countDataObjectAppIds(Snapshot snapshot) {
    return snapshotDAO.countDataObjectAppIds(snapshot.getId());
  }

  /**
   * Returns one page of {@code :DataObject} {@code appId} strings captured
   * in the snapshot. DB-side SKIP/LIMIT; no in-memory slicing.
   *
   * @param snapshot the snapshot to read from.
   * @param skip     number of rows to skip ({@code page * pageSize}).
   * @param limit    maximum rows to return ({@code pageSize}).
   * @return paged list of DataObject appId strings.
   */
  public List<String> listDataObjectAppIdsPage(Snapshot snapshot, long skip, int limit) {
    return snapshotDAO.findDataObjectAppIds(snapshot.getId(), skip, limit);
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
