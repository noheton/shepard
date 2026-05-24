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
   * @param collectionAppId application-level UUID-v7 of the Collection.
   * @return a list of entries sorted by {@code createdAt} descending (newest first).
   */
  public List<LabJournalEntry> findByCollectionAppId(String collectionAppId) {
    if (collectionAppId == null || collectionAppId.isBlank()) return List.of();

    // Returning the LabJournalEntry node via the OGM ensures the related
    // createdBy / updatedBy User entities load with the standard depth-1 hooks
    // so LabJournalEntryIO sees populated User objects (display-name resolver
    // would otherwise NPE on the User-relationship traversal).
    String query =
      "MATCH (coll:Collection {appId: $appId})" +
      "-[:" + Constants.HAS_DATAOBJECT + "]->(do:DataObject)" +
      "-[:" + Constants.HAS_LABJOURNAL_ENTRY + "]->(lje:LabJournalEntry) " +
      "WHERE (do.deleted IS NULL OR do.deleted = false) " +
      "  AND (lje.deleted IS NULL OR lje.deleted = false) " +
      "RETURN DISTINCT lje";

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
