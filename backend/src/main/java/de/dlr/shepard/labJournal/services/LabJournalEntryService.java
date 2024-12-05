package de.dlr.shepard.labJournal.services;

import de.dlr.shepard.labJournal.dao.LabJournalEntryDAO;
import de.dlr.shepard.labJournal.entities.LabJournalEntry;
import de.dlr.shepard.neo4Core.dao.CollectionDAO;
import de.dlr.shepard.neo4Core.dao.DataObjectDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.dao.VersionDAO;
import de.dlr.shepard.neo4Core.entities.Collection;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.util.DateHelper;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@RequestScoped
public class LabJournalEntryService {

  private LabJournalEntryDAO labJournalEntryDAO;

  private DataObjectDAO dataObjectDAO;

  private VersionDAO versionDAO;

  private CollectionDAO collectionDAO;

  private UserDAO userDAO;

  private DateHelper dateHelper;

  @Inject
  LabJournalEntryService(
    LabJournalEntryDAO labJournalEntryDAO,
    DataObjectDAO dataObjectDAO,
    VersionDAO versionDAO,
    CollectionDAO collectionDAO,
    UserDAO userDAO,
    DateHelper dateHelper
  ) {
    this.labJournalEntryDAO = labJournalEntryDAO;
    this.dataObjectDAO = dataObjectDAO;
    this.versionDAO = versionDAO;
    this.collectionDAO = collectionDAO;
    this.userDAO = userDAO;
    this.dateHelper = dateHelper;
  }

  public LabJournalEntry CreateLabJournalEntry(Long dataObjectId, String labJournalEntryContent, String userName) {
    LabJournalEntry labJournalEntry = new LabJournalEntry();
    User user = userDAO.find(userName);
    DataObject dataObject = dataObjectDAO.findByShepardId(dataObjectId);
    Collection collection = collectionDAO.findByShepardId(dataObject.getCollection().getId());
    labJournalEntry.setDescription(labJournalEntryContent);
    labJournalEntry.setCreatedBy(user);
    labJournalEntry.setCreatedAt(dateHelper.getDate());
    labJournalEntry.setDataObject(dataObject);
    labJournalEntry = labJournalEntryDAO.createOrUpdate(labJournalEntry);
    labJournalEntry.setShepardId(labJournalEntry.getId());
    labJournalEntry = labJournalEntryDAO.createOrUpdate(labJournalEntry);
    versionDAO.createLink(labJournalEntry.getId(), collection.getVersion().getUid());
    return labJournalEntry;
  }

  public List<LabJournalEntry> getLabJournalEntries(DataObject dataObject) {
    if (null == dataObject) return new ArrayList<LabJournalEntry>();
    List<LabJournalEntry> labJournalEntries = dataObject.getLabJournalEntries();
    List<Long> labJournalEntryIds = labJournalEntries
      .stream()
      .map(LabJournalEntry::getShepardId)
      .collect(Collectors.toList());
    labJournalEntries = labJournalEntryDAO.findByShepardIds(labJournalEntryIds);
    return labJournalEntries
      .stream()
      .sorted(Comparator.comparing(LabJournalEntry::getCreatedAt))
      .collect(Collectors.toList());
  }

  public LabJournalEntry getLabJournalEntry(long labJournalEntryId) {
    return labJournalEntryDAO.findByShepardId(labJournalEntryId);
  }

  public LabJournalEntry updateLabJournalEntry(long labJournalEntryId, String labJournalEntryContent, String userName) {
    LabJournalEntry labJournalEntry = labJournalEntryDAO.findByShepardId(labJournalEntryId);
    if (null == labJournalEntry) return null;
    User user = userDAO.find(userName);
    labJournalEntry.setDescription(labJournalEntryContent);
    labJournalEntry.setUpdatedAt(dateHelper.getDate());
    labJournalEntry.setUpdatedBy(user);
    labJournalEntry = labJournalEntryDAO.createOrUpdate(labJournalEntry);
    return labJournalEntry;
  }

  public boolean deleteLabJournal(long labJournalEntryId, String userName) {
    User user = userDAO.find(userName);
    return labJournalEntryDAO.deleteLabJournal(labJournalEntryId, user, dateHelper.getDate());
  }

  public Long getCollectionId(Long labJournalEntryId) {
    LabJournalEntry labJournalEntry = labJournalEntryDAO.findByShepardId(labJournalEntryId);
    if (null == labJournalEntry) return null;
    DataObject dataObject = dataObjectDAO.findByShepardId(labJournalEntry.getDataObject().getShepardId());
    return dataObject.getCollection().getShepardId();
  }
}
