package de.dlr.shepard.labJournal.dao;

import de.dlr.shepard.labJournal.entities.LabJournalEntry;
import de.dlr.shepard.neo4Core.dao.GenericDAO;
import de.dlr.shepard.neo4Core.entities.User;
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
