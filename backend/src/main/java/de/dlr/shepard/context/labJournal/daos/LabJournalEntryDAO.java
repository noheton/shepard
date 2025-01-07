package de.dlr.shepard.context.labJournal.daos;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.context.labJournal.entities.LabJournalEntry;
import jakarta.enterprise.context.RequestScoped;
import java.util.Date;
import java.util.List;

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
}
