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
import de.dlr.shepard.context.labJournal.daos.LabJournalEntryRevisionDAO;
import de.dlr.shepard.context.labJournal.entities.LabJournalEntry;
import de.dlr.shepard.context.labJournal.entities.LabJournalEntryRevision;
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
  LabJournalEntryRevisionDAO labJournalEntryRevisionDAO;

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

  /**
   * Returns all non-deleted lab journal entries for the given {@code dataObjectId},
   * sorted newest-first.
   *
   * <p>This is the preferred call site — callers do not need to resolve the
   * {@link DataObject} themselves, and the REST resource no longer needs to
   * inject {@link DataObjectService} just for list queries.
   *
   * @param dataObjectId the Neo4j / OGM id of the owning DataObject
   * @return ordered list; empty when the DataObject has no entries
   */
  public List<LabJournalEntry> getLabJournalEntriesByDataObjectId(Long dataObjectId) {
    DataObject dataObject = dataObjectService.getDataObject(dataObjectId);
    return getLabJournalEntries(dataObject);
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

  /**
   * Asserts that the currently authenticated user is the creator of the given
   * lab journal entry.
   *
   * <p>This check enforces the creator-only write policy: only the user who
   * created an entry is allowed to modify or delete it. It is called by
   * {@link #updateLabJournalEntry} and from the PATCH path in the REST resource
   * (after a successful merge) so that both update shapes share the same rule.
   *
   * @param labJournalEntry the fully-hydrated entry to check
   * @throws InvalidAuthException (→ HTTP 403) when the current user is not the creator
   */
  public void assertIsCreator(LabJournalEntry labJournalEntry) {
    String currentUsername = userService.getCurrentUser().getUsername();
    if (labJournalEntry.getCreatedBy() == null ||
        !labJournalEntry.getCreatedBy().getUsername().equals(currentUsername)) {
      throw new InvalidAuthException(
        "Only the creator of a lab journal entry may modify or delete it"
      );
    }
  }

  public LabJournalEntry updateLabJournalEntry(long labJournalEntryId, String content) {
    collectionService.assertIsAllowedToEditCollection(getCollectionId(labJournalEntryId));

    LabJournalEntry labJournalEntry = labJournalEntryDAO.findByNeo4jId(labJournalEntryId);
    if (null == labJournalEntry) return null;

    // J1d — capture the old content as an append-only revision BEFORE
    // overwriting it. Skip creating a revision when the content is unchanged
    // (no-op update) to avoid polluting the history with empty diffs.
    String oldContent = labJournalEntry.getContent();
    if (oldContent != null && !oldContent.equals(content)) {
      User user = userService.getCurrentUser();
      int nextRevisionNumber = labJournalEntryRevisionDAO.findByEntry(labJournalEntryId).size() + 1;
      LabJournalEntryRevision revision = new LabJournalEntryRevision();
      revision.setContent(oldContent);
      revision.setRevisionNumber(nextRevisionNumber);
      revision.setCreatedAt(dateHelper.getDate());
      revision.setCreatedBy(user);
      revision.setLabJournalEntry(labJournalEntry);
      labJournalEntryRevisionDAO.createOrUpdate(revision);
    }

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
    // Safe: findByNeo4jId → session.load(depth=1) hydrates BOTH directions including
    // the INCOMING has_labjournalentry edge. Do NOT replace with session.query("RETURN lje")
    // unless you add CypherQueryHelper.getReturnPart("lje") — that would be the BUG-LJ-V1 class.
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
