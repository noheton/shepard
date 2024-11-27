package de.dlr.shepard.labJournal.dao;

import de.dlr.shepard.labJournal.entities.LabJournal;
import de.dlr.shepard.neo4Core.dao.VersionableEntityDAO;
import de.dlr.shepard.neo4Core.entities.User;
import jakarta.enterprise.context.RequestScoped;
import java.util.Date;

@RequestScoped
public class LabJournalDAO extends VersionableEntityDAO<LabJournal> {

  @Override
  public Class<LabJournal> getEntityType() {
    return LabJournal.class;
  }

  public boolean deleteLabJournal(long shepardId, User user, Date updatedAt) {
    LabJournal labJournal = findByShepardId(shepardId);
    if (null == labJournal) return false;
    labJournal.setUpdatedAt(updatedAt);
    labJournal.setUpdatedBy(user);
    labJournal.setDeleted(true);
    createOrUpdate(labJournal);
    return true;
  }
}
