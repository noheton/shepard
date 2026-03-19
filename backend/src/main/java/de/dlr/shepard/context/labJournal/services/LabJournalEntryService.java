package de.dlr.shepard.context.labJournal.services;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.context.labJournal.daos.LabJournalEntryDAO;
import de.dlr.shepard.context.labJournal.entities.LabJournalEntry;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@RequestScoped
public class LabJournalEntryService {

  @Inject
  LabJournalEntryDAO labJournalEntryDAO;

  @Inject
  DataObjectService dataObjectService;

  @Inject
  CollectionService collectionService;

  @Inject
  UserService userService;

  @Inject
  DateHelper dateHelper;

  public LabJournalEntry createLabJournalEntry(Long dataObjectId, String content) {
    DataObject dataObject = dataObjectService.getDataObject(dataObjectId);
    collectionService.assertIsAllowedToEditCollection(dataObject.getCollection().getId());

    User user = userService.getCurrentUser();
    LabJournalEntry labJournalEntry = new LabJournalEntry();
    labJournalEntry.setContent(content);
    labJournalEntry.setCreatedBy(user);
    labJournalEntry.setCreatedAt(dateHelper.getDate());
    labJournalEntry.setDataObject(dataObject);
    labJournalEntry = labJournalEntryDAO.createOrUpdate(labJournalEntry);
    return labJournalEntry;
  }

  public List<LabJournalEntry> getLabJournalEntries(DataObject dataObject) {
    if (null == dataObject) return new ArrayList<LabJournalEntry>();
    collectionService.assertIsAllowedToReadCollection(dataObject.getCollection().getId());

    List<LabJournalEntry> labJournalEntries = dataObject.getLabJournalEntries();
    List<Long> labJournalEntryIds = labJournalEntries.stream().map(LabJournalEntry::getId).collect(Collectors.toList());
    labJournalEntries = labJournalEntryDAO.findLabJournalEntriesByIds(labJournalEntryIds);
    return labJournalEntries
      .stream()
      .filter(labJournalEntry -> !labJournalEntry.isDeleted())
      .sorted(Comparator.comparing(LabJournalEntry::getCreatedAt).reversed())
      .collect(Collectors.toList());
  }

  public LabJournalEntry getLabJournalEntry(long labJournalEntryId) {
    collectionService.assertIsAllowedToReadCollection(getCollectionId(labJournalEntryId));

    return labJournalEntryDAO.findByNeo4jId(labJournalEntryId);
  }

  public LabJournalEntry updateLabJournalEntry(long labJournalEntryId, String content) {
    collectionService.assertIsAllowedToEditCollection(getCollectionId(labJournalEntryId));

    LabJournalEntry labJournalEntry = labJournalEntryDAO.findByNeo4jId(labJournalEntryId);
    if (null == labJournalEntry) return null;
    User user = userService.getCurrentUser();
    labJournalEntry.setContent(content);
    labJournalEntry.setUpdatedAt(dateHelper.getDate());
    labJournalEntry.setUpdatedBy(user);
    labJournalEntry = labJournalEntryDAO.createOrUpdate(labJournalEntry);
    return labJournalEntry;
  }

  public boolean deleteLabJournalEntry(long labJournalEntryId) {
    collectionService.assertIsAllowedToEditCollection(getCollectionId(labJournalEntryId));

    User user = userService.getCurrentUser();
    return labJournalEntryDAO.deleteLabJournalEntry(labJournalEntryId, user, dateHelper.getDate());
  }

  /**
   * Gets a collectionId for a given labJournalEntryId
   *
   * @param labJournalEntryId
   * @return Long
   * @throws InvalidPathException if labJournal is not accessible, or if the associated dataobject is not accessible
   * @throws InvalidAuthException if user has no read permissions on associated DataObject
   */
  private Long getCollectionId(Long labJournalEntryId) {
    LabJournalEntry labJournalEntry = labJournalEntryDAO.findByNeo4jId(labJournalEntryId);

    if (null == labJournalEntry || labJournalEntry.isDeleted()) {
      String errorMsg = "LabJournal with Id %s cannot be found or is deleted".formatted(labJournalEntryId);
      Log.error(errorMsg);
      throw new InvalidPathException(errorMsg);
    }

    DataObject dataObject = dataObjectService.getDataObject(labJournalEntry.getDataObject().getId());
    return dataObject.getCollection().getId();
  }
}
