package de.dlr.shepard.v2.labjournal.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.labJournal.entities.LabJournalEntry;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * UI-020 — bulk fetch DAO: walks
 * {@code Collection → DataObject → LabJournalEntry} for a single collection appId
 * and returns every non-deleted entry in one query.
 *
 * <p>Replaces the per-DataObject N+1 round-trip pattern that broke MFFD-Dropbox
 * (8500 concurrent {@code GET /shepard/api/labJournalEntries?dataObjectId=N}
 * requests on collection page load — browser socket exhaustion + thousands of
 * console errors).
 *
 * <p>Mirrors the deletion / sort semantics of
 * {@link de.dlr.shepard.context.labJournal.services.LabJournalEntryService#getLabJournalEntries}:
 * skip {@code DataObject.deleted=true}, skip {@code LabJournalEntry.deleted=true},
 * sort by {@code createdAt DESC} (newest first).
 */
@ApplicationScoped
public class CollectionLabJournalEntriesDAO extends GenericDAO<LabJournalEntry> {

  @Override
  public Class<LabJournalEntry> getEntityType() {
    return LabJournalEntry.class;
  }

  /** Count distinct non-deleted lab journal entries attached to the collection. */
  public long countByCollectionAppId(String collectionAppId) {
    if (collectionAppId == null || collectionAppId.isBlank()) return 0L;
    String query =
      "MATCH (coll:Collection {appId: $appId})" +
      "-[:" + Constants.HAS_DATAOBJECT + "]->(do:DataObject)" +
      "-[:" + Constants.HAS_LABJOURNAL_ENTRY + "]->(lje:LabJournalEntry) " +
      "WHERE (do.deleted IS NULL OR do.deleted = false) " +
      "  AND (lje.deleted IS NULL OR lje.deleted = false) " +
      "RETURN count(DISTINCT lje) AS c";
    var result = session.query(query, Map.of("appId", collectionAppId));
    var it = result.queryResults().iterator();
    if (!it.hasNext()) return 0L;
    Object c = it.next().get("c");
    return c instanceof Number n ? n.longValue() : 0L;
  }

  /**
   * Bounded fetch: returns up to {@code limit} non-deleted entries starting at {@code skip},
   * ordered by createdAt DESC then by Neo4j node id DESC for determinism.
   * Sort and SKIP/LIMIT are pushed to Cypher before the path-expansion step.
   */
  public List<LabJournalEntry> findByCollectionAppId(String collectionAppId, int skip, int limit) {
    if (collectionAppId == null || collectionAppId.isBlank()) return List.of();
    String query =
      "MATCH (coll:Collection {appId: $appId})" +
      "-[:" + Constants.HAS_DATAOBJECT + "]->(do:DataObject)" +
      "-[:" + Constants.HAS_LABJOURNAL_ENTRY + "]->(lje:LabJournalEntry) " +
      "WHERE (do.deleted IS NULL OR do.deleted = false) " +
      "  AND (lje.deleted IS NULL OR lje.deleted = false) " +
      "WITH DISTINCT lje " +
      "ORDER BY lje.createdAt DESC, id(lje) DESC " +
      "SKIP $skip LIMIT $limit " +
      "MATCH path=(lje)-[*0..1]-(n) " +
      "WHERE n.deleted = false OR n.deleted IS NULL " +
      "RETURN lje, nodes(path), relationships(path)";
    List<LabJournalEntry> result = new ArrayList<>();
    for (var lje : findByQuery(query, Map.of("appId", collectionAppId, "skip", (long) skip, "limit", (long) limit))) {
      if (lje != null && !lje.isDeleted()) result.add(lje);
    }
    return result;
  }

  /**
   * @deprecated Use {@link #findByCollectionAppId(String, int, int)} with explicit bounds.
   *     Kept for backwards compatibility; loads all entries.
   */
  @Deprecated
  public List<LabJournalEntry> findByCollectionAppId(String collectionAppId) {
    if (collectionAppId == null || collectionAppId.isBlank()) return List.of();

    // Project a depth-1 neighbourhood path so the OGM hydrates BOTH the
    // INCOMING has_labjournalentry edge back to the DataObject (needed so
    // LabJournalEntryIO can read getDataObject().getShepardId()) AND the
    // OUTGOING created_by / updated_by edges to User (needed for the
    // display-name resolver). Filtering on n.deleted inside the neighbourhood
    // walk prevents soft-deleted neighbours from contaminating the hydrated
    // graph (matches the CypherQueryHelper.Neighborhood.EVERYTHING shape).
    String query =
      "MATCH (coll:Collection {appId: $appId})" +
      "-[:" + Constants.HAS_DATAOBJECT + "]->(do:DataObject)" +
      "-[:" + Constants.HAS_LABJOURNAL_ENTRY + "]->(lje:LabJournalEntry) " +
      "WHERE (do.deleted IS NULL OR do.deleted = false) " +
      "  AND (lje.deleted IS NULL OR lje.deleted = false) " +
      "WITH DISTINCT lje " +
      "MATCH path=(lje)-[*0..1]-(n) " +
      "WHERE n.deleted = false OR n.deleted IS NULL " +
      "RETURN lje, nodes(path), relationships(path)";

    List<LabJournalEntry> result = new ArrayList<>();
    for (var lje : findByQuery(query, Map.of("appId", collectionAppId))) {
      if (lje != null && !lje.isDeleted()) {
        result.add(lje);
      }
    }
    result.sort(Comparator.comparing(
      LabJournalEntry::getCreatedAt,
      Comparator.nullsLast(Comparator.reverseOrder())
    ));
    // Stable secondary sort to keep tests deterministic when two entries share createdAt.
    result.sort((a, b) -> {
      int c = Objects.compare(
        b.getCreatedAt(),
        a.getCreatedAt(),
        Comparator.nullsLast(Comparator.naturalOrder())
      );
      if (c != 0) return c;
      Long ai = a.getId();
      Long bi = b.getId();
      if (ai == null && bi == null) return 0;
      if (ai == null) return 1;
      if (bi == null) return -1;
      return Long.compare(bi, ai);
    });
    return result;
  }
}
