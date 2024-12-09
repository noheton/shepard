package de.dlr.shepard.labJournal.services;

import de.dlr.shepard.labJournal.dao.LabJournalEntryDAO;
import de.dlr.shepard.labJournal.entities.LabJournalEntry;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.services.DataObjectService;
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

  private DataObjectService dataObjectService;

  private UserDAO userDAO;

  private DateHelper dateHelper;

  @Inject
  LabJournalEntryService(
    LabJournalEntryDAO labJournalEntryDAO,
    DataObjectService dataObjectService,
    UserDAO userDAO,
    DateHelper dateHelper
  ) {
    this.labJournalEntryDAO = labJournalEntryDAO;
    this.dataObjectService = dataObjectService;
    this.userDAO = userDAO;
    this.dateHelper = dateHelper;
  }

  public LabJournalEntry createLabJournalEntry(Long dataObjectId, String content, String userName) {
    LabJournalEntry labJournalEntry = new LabJournalEntry();
    User user = userDAO.find(userName);
    DataObject dataObject = dataObjectService.getDataObjectByNeo4jId(dataObjectId);
    labJournalEntry.setContent(content);
    labJournalEntry.setCreatedBy(user);
    labJournalEntry.setCreatedAt(dateHelper.getDate());
    labJournalEntry.setDataObject(dataObject);
    labJournalEntry = labJournalEntryDAO.createOrUpdate(labJournalEntry);
    return labJournalEntry;
  }

  public List<LabJournalEntry> getLabJournalEntries(DataObject dataObject) {
    if (null == dataObject) return new ArrayList<LabJournalEntry>();
    List<LabJournalEntry> labJournalEntries = dataObject.getLabJournalEntries();
    List<Long> labJournalEntryIds = labJournalEntries.stream().map(LabJournalEntry::getId).collect(Collectors.toList());
    labJournalEntries = labJournalEntryDAO.findLabJournalEntriesByIds(labJournalEntryIds);
    return labJournalEntries
      .stream()
      .filter(labJournalEntry -> !labJournalEntry.isDeleted())
      .sorted(Comparator.comparing(LabJournalEntry::getCreatedAt))
      .collect(Collectors.toList());
  }

  public LabJournalEntry getLabJournalEntry(long labJournalEntryId) {
    return labJournalEntryDAO.findByNeo4jId(labJournalEntryId);
  }

  public LabJournalEntry updateLabJournalEntry(long labJournalEntryId, String content, String userName) {
    LabJournalEntry labJournalEntry = labJournalEntryDAO.findByNeo4jId(labJournalEntryId);
    if (null == labJournalEntry) return null;
    User user = userDAO.find(userName);
    labJournalEntry.setContent(content);
    labJournalEntry.setUpdatedAt(dateHelper.getDate());
    labJournalEntry.setUpdatedBy(user);
    labJournalEntry = labJournalEntryDAO.createOrUpdate(labJournalEntry);
    return labJournalEntry;
  }

  public boolean deleteLabJournalEntry(long labJournalEntryId, String userName) {
    User user = userDAO.find(userName);
    return labJournalEntryDAO.deleteLabJournalEntry(labJournalEntryId, user, dateHelper.getDate());
  }

  public Long getCollectionId(Long labJournalEntryId) {
    LabJournalEntry labJournalEntry = labJournalEntryDAO.findByNeo4jId(labJournalEntryId);
    if (null == labJournalEntry) return null;
    DataObject dataObject = dataObjectService.getDataObjectByNeo4jId(labJournalEntry.getDataObject().getId());
    return dataObject.getCollection().getId();
  }
}
