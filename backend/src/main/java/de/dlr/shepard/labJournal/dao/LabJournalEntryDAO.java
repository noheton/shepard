package de.dlr.shepard.labJournal.dao;

import de.dlr.shepard.labJournal.entities.LabJournalEntry;
import de.dlr.shepard.neo4Core.dao.VersionableEntityDAO;
import de.dlr.shepard.neo4Core.entities.User;
import jakarta.enterprise.context.RequestScoped;
import java.util.Date;

@RequestScoped
public class LabJournalEntryDAO extends VersionableEntityDAO<LabJournalEntry> {

  @Override
  public Class<LabJournalEntry> getEntityType() {
    return LabJournalEntry.class;
  }

  public boolean deleteLabJournal(long shepardId, User user, Date updatedAt) {
    LabJournalEntry labJournalEntry = findByShepardId(shepardId);
    if (null == labJournalEntry) return false;
    labJournalEntry.setUpdatedAt(updatedAt);
    labJournalEntry.setUpdatedBy(user);
    labJournalEntry.setDeleted(true);
    createOrUpdate(labJournalEntry);
    return true;
  }
}
