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
   * Returns all non-deleted revisions attached to the given entry, ordered
   * by {@code revisionNumber} descending (newest revision first).
   *
   * @param entryNeo4jId the OGM-managed Long id of the {@code LabJournalEntry}.
   * @return list of revisions, newest first; empty when none exist.
   */
  public List<LabJournalEntryRevision> findByEntry(long entryNeo4jId) {
    String query =
      "MATCH (e:LabJournalEntry)-[:has_lab_journal_revision]->(r:LabJournalEntryRevision) " +
      "WHERE id(e) = $id AND (r.deleted IS NULL OR r.deleted = false) " +
      "RETURN r " +
      "ORDER BY r.revisionNumber DESC";
    return StreamSupport
      .stream(findByQuery(query, Map.of("id", entryNeo4jId)).spliterator(), false)
      .toList();
  }
}
