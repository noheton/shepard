package de.dlr.shepard.context.labJournal.daos;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.context.labJournal.entities.LabJournalEntry;
import jakarta.enterprise.context.RequestScoped;
import java.util.Date;
import java.util.List;
import java.util.Map;

@RequestScoped
public class LabJournalEntryDAO extends GenericDAO<LabJournalEntry> {

  @Override
  public Class<LabJournalEntry> getEntityType() {
    return LabJournalEntry.class;
  }

  public boolean deleteLabJournalEntry(long id, User user, Date updatedAt) {
    LabJournalEntry labJournalEntry = findByNeo4jId(id);
    if (null == labJournalEntry) return false;
    labJournalEntry.setUpdatedAt(updatedAt);
    labJournalEntry.setUpdatedBy(user);
    labJournalEntry.setDeleted(true);
    createOrUpdate(labJournalEntry);
    return true;
  }

  public List<LabJournalEntry> findLabJournalEntriesByIds(List<Long> ids) {
    return List.copyOf(session.loadAll(getEntityType(), ids));
  }

  /**
   * J1a — resolve a {@code LabJournalEntry} by its application-level
   * {@code appId} (UUID v7). Returns {@code null} when no entry carries
   * that {@code appId} (including entries whose {@code appId} is still
   * {@code null} from before the L2a backfill).
   *
   * @param appId the application-level identifier to look up.
   * @return the matching entry, or {@code null} if not found.
   */
  public LabJournalEntry findByAppId(String appId) {
    String query =
      "MATCH " +
      CypherQueryHelper.getObjectPart("e", "LabJournalEntry", false) +
      " WHERE e.appId = $appId " +
      CypherQueryHelper.getReturnPart("e");
    var iter = findByQuery(query, Map.of("appId", appId)).iterator();
    return iter.hasNext() ? iter.next() : null;
  }
}
