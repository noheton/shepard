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

  /**
   * Single-query bulk fetch of every non-deleted lab journal entry attached to
   * any non-deleted DataObject in the given collection.
   *
   * <p><b>Hydration note (BUG-LJ-V1-COLL-ID, 2026-05-24).</b> The first cut of
   * this DAO returned {@code DISTINCT lje} only, on the assumption that the
   * OGM's depth-1 hydration would fill the incoming {@code has_labjournalentry}
   * edge back to the owning {@link DataObject}. It does not — when {@code
   * session.query(EntityType, ...)} is given a projection that returns just the
   * leaf node, the OGM hydrates the OUTGOING relationships ({@code createdBy},
   * {@code updatedBy}) but not the INCOMING reverse-relationship. So {@link
   * LabJournalEntry#getDataObject()} came back {@code null} for every entry on
   * the real LUMEN data, and {@link LabJournalEntryIO}'s constructor NPEd on
   * {@code labJournalEntry.getDataObject().getShepardId()}. The endpoint
   * returned HTTP 500 for any collection that actually has lab journal entries
   * (empty collections like MFFD-Dropbox returned 200 [] and hid the bug).
   *
   * <p>The fix is the canonical {@link
   * de.dlr.shepard.common.util.CypherQueryHelper#getReturnPart} pattern:
   * project a depth-1 neighbourhood path alongside the entity so the OGM
   * hydrates every direction. The {@code DISTINCT lje} stays in the WITH-clause
   * so the same LJE isn't returned twice when its neighbourhood path branches.
   *
   * @param collectionAppId application-level UUID-v7 of the Collection.
   * @return a list of entries sorted by {@code createdAt} descending (newest first).
   */
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
