package de.dlr.shepard.context.labJournal.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.context.labJournal.entities.LabJournalEntryRevision;
import jakarta.enterprise.context.RequestScoped;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

/**
 * J1d — data-access object for {@link LabJournalEntryRevision} nodes.
 *
 * <p>Revisions are append-only; this DAO intentionally exposes no delete
 * method. The only write path is through {@link #createOrUpdate(Object)}
 * (inherited from {@link GenericDAO}).
 */
@RequestScoped
public class LabJournalEntryRevisionDAO extends GenericDAO<LabJournalEntryRevision> {

  @Override
  public Class<LabJournalEntryRevision> getEntityType() {
    return LabJournalEntryRevision.class;
  }

  /**
   * Counts non-deleted revisions attached to the given entry.
   *
   * @param entryNeo4jId the OGM-managed Long id of the {@code LabJournalEntry}.
   * @return number of revisions; 0 when the entry has never been edited.
   */
  public long countByEntry(long entryNeo4jId) {
    String query =
      "MATCH (e:LabJournalEntry)-[:has_lab_journal_revision]->(r:LabJournalEntryRevision) " +
      "WHERE id(e) = $id AND (r.deleted IS NULL OR r.deleted = false) " +
      "RETURN count(r) AS c";
    var it = session.query(query, Map.of("id", entryNeo4jId)).queryResults().iterator();
    if (!it.hasNext()) return 0L;
    Object c = it.next().get("c");
    return c instanceof Number n ? n.longValue() : 0L;
  }

  /**
   * Returns a bounded page of non-deleted revisions attached to the given entry,
   * ordered by {@code revisionNumber} descending (newest revision first).
   *
   * @param entryNeo4jId the OGM-managed Long id of the {@code LabJournalEntry}.
   * @param skip         number of rows to skip (0-based offset).
   * @param limit        maximum number of rows to return.
   * @return list of revisions, newest first; empty when none exist in the slice.
   */
  public List<LabJournalEntryRevision> findByEntry(long entryNeo4jId, int skip, int limit) {
    String query =
      "MATCH (e:LabJournalEntry)-[:has_lab_journal_revision]->(r:LabJournalEntryRevision) " +
      "WHERE id(e) = $id AND (r.deleted IS NULL OR r.deleted = false) " +
      "RETURN r " +
      "ORDER BY r.revisionNumber DESC " +
      "SKIP $skip LIMIT $limit";
    return StreamSupport
      .stream(findByQuery(query, Map.of("id", entryNeo4jId, "skip", skip, "limit", limit)).spliterator(), false)
      .toList();
  }
}
