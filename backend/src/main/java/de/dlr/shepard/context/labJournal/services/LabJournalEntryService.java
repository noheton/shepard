package de.dlr.shepard.context.labJournal.services;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.context.labJournal.daos.LabJournalEntryDAO;
import de.dlr.shepard.context.labJournal.entities.LabJournalEntry;
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

  private CollectionService collectionService;

  private UserService userService;

  private DateHelper dateHelper;

  @Inject
  LabJournalEntryService(
    LabJournalEntryDAO labJournalEntryDAO,
    DataObjectService dataObjectService,
    CollectionService collectionService,
    UserService userService,
    DateHelper dateHelper
  ) {
    this.labJournalEntryDAO = labJournalEntryDAO;
    this.dataObjectService = dataObjectService;
    this.collectionService = collectionService;
    this.userService = userService;
    this.dateHelper = dateHelper;
  }

  public LabJournalEntry createLabJournalEntry(Long dataObjectId, String content, String userName) {
    LabJournalEntry labJournalEntry = new LabJournalEntry();
    User user = userService.getUser(userName);
    DataObject dataObject = dataObjectService.getDataObject(dataObjectId);
    collectionService.assertUserIsAllowedToEditCollection(dataObject.getCollection().getId(), userName);

    labJournalEntry.setContent(content);
    labJournalEntry.setCreatedBy(user);
    labJournalEntry.setCreatedAt(dateHelper.getDate());
    labJournalEntry.setDataObject(dataObject);
    labJournalEntry = labJournalEntryDAO.createOrUpdate(labJournalEntry);
    return labJournalEntry;
  }

  public List<LabJournalEntry> getLabJournalEntries(DataObject dataObject, String userName) {
    if (null == dataObject) return new ArrayList<LabJournalEntry>();
    collectionService.assertUserIsAllowedToReadCollection(dataObject.getCollection().getId(), userName);

    List<LabJournalEntry> labJournalEntries = dataObject.getLabJournalEntries();
    List<Long> labJournalEntryIds = labJournalEntries.stream().map(LabJournalEntry::getId).collect(Collectors.toList());
    labJournalEntries = labJournalEntryDAO.findLabJournalEntriesByIds(labJournalEntryIds);
    return labJournalEntries
      .stream()
      .filter(labJournalEntry -> !labJournalEntry.isDeleted())
      .sorted(Comparator.comparing(LabJournalEntry::getCreatedAt))
      .collect(Collectors.toList());
  }

  /**
   * @deprecated this method is only a leftover until the URLPathChecker is removed
   */
  public LabJournalEntry getLabJournalEntry(long labJournalEntryId) {
    return labJournalEntryDAO.findByNeo4jId(labJournalEntryId);
  }

  public LabJournalEntry getLabJournalEntry(long labJournalEntryId, String userName) {
    collectionService.assertUserIsAllowedToReadCollection(getCollectionId(labJournalEntryId), userName);

    return labJournalEntryDAO.findByNeo4jId(labJournalEntryId);
  }

  public LabJournalEntry updateLabJournalEntry(long labJournalEntryId, String content, String userName) {
    collectionService.assertUserIsAllowedToEditCollection(getCollectionId(labJournalEntryId), userName);

    LabJournalEntry labJournalEntry = labJournalEntryDAO.findByNeo4jId(labJournalEntryId);
    if (null == labJournalEntry) return null;
    User user = userService.getUser(userName);
    labJournalEntry.setContent(content);
    labJournalEntry.setUpdatedAt(dateHelper.getDate());
    labJournalEntry.setUpdatedBy(user);
    labJournalEntry = labJournalEntryDAO.createOrUpdate(labJournalEntry);
    return labJournalEntry;
  }

  public boolean deleteLabJournalEntry(long labJournalEntryId, String userName) {
    collectionService.assertUserIsAllowedToEditCollection(getCollectionId(labJournalEntryId), userName);

    User user = userService.getUser(userName);
    return labJournalEntryDAO.deleteLabJournalEntry(labJournalEntryId, user, dateHelper.getDate());
  }

  private Long getCollectionId(Long labJournalEntryId) {
    LabJournalEntry labJournalEntry = labJournalEntryDAO.findByNeo4jId(labJournalEntryId);
    // TODO: Align this exception with replacement of URLPathChecker
    if (null == labJournalEntry) throw new InvalidPathException();
    DataObject dataObject = dataObjectService.getDataObject(labJournalEntry.getDataObject().getId());
    return dataObject.getCollection().getId();
  }
}
