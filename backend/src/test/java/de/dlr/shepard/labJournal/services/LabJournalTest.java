package de.dlr.shepard.labJournal.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import de.dlr.shepard.labJournal.entities.LabJournalEntry;
import de.dlr.shepard.neo4Core.entities.Collection;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.io.CollectionIO;
import de.dlr.shepard.neo4Core.io.DataObjectIO;
import de.dlr.shepard.neo4Core.services.CollectionService;
import de.dlr.shepard.neo4Core.services.DataObjectService;
import de.dlr.shepard.neo4Core.services.UserService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class LabJournalTest {

  @Inject
  LabJournalEntryService labJournalEntryService;

  @Inject
  DataObjectService dataObjectService;

  @Inject
  CollectionService collectionService;

  @Inject
  UserService userService;

  private Collection collection;
  private DataObject dataObject;

  private final String userName = "user_name";
  private final String collectionName = "collection_name";

  @BeforeEach
  public void setup() {
    User user = new User(userName);
    userService.createUser(user);
    CollectionIO collectionIO = new CollectionIO();
    collectionIO.setName(collectionName);
    collection = collectionService.createCollection(collectionIO, userName);
    collection.setPermissions(null);
    DataObjectIO dataObjectIO = new DataObjectIO();
    dataObject = dataObjectService.createDataObjectByCollectionShepardId(collection.getId(), dataObjectIO, userName);
  }

  @Test
  public void createLabJournalEntry_success() {
    LabJournalEntry created = labJournalEntryService.createLabJournalEntry(dataObject.getId(), "content 1", userName);
    LabJournalEntry actual = labJournalEntryService.getLabJournalEntry(created.getId());
    assertEquals(created, actual);
  }

  @Test
  public void getLabJournalEntries_forInsertedLabJournals_returnsAll() throws InterruptedException {
    List<LabJournalEntry> created = List.of(
      labJournalEntryService.createLabJournalEntry(dataObject.getId(), "content 1", userName),
      labJournalEntryService.createLabJournalEntry(dataObject.getId(), "content 2", userName),
      labJournalEntryService.createLabJournalEntry(dataObject.getId(), "content 3", userName),
      labJournalEntryService.createLabJournalEntry(dataObject.getId(), "content 4", userName)
    );
    dataObject = dataObjectService.getDataObjectByNeo4jId(dataObject.getId());
    List<LabJournalEntry> actual = labJournalEntryService.getLabJournalEntries(dataObject);
    assertEquals(created, actual);
  }

  @Test
  public void getLabJournalEntries_nullDataObject_returnsEmptyList() throws InterruptedException {
    List<LabJournalEntry> emptyList = List.of();
    List<LabJournalEntry> actual = labJournalEntryService.getLabJournalEntries(null);
    assertEquals(emptyList, actual);
  }

  @Test
  public void updateLabJournalEntry_success() {
    LabJournalEntry created = labJournalEntryService.createLabJournalEntry(dataObject.getId(), "content 1", userName);
    labJournalEntryService.updateLabJournalEntry(created.getId(), "content 2", userName);
    LabJournalEntry actual = labJournalEntryService.getLabJournalEntry(created.getId());
    assertEquals(actual.getContent(), created.getContent());
  }

  @Test
  public void updateLabJournalEntry_notExistsLabJournalEntry_returnsNull() {
    Long largeId = Long.MAX_VALUE;
    LabJournalEntry actual = labJournalEntryService.updateLabJournalEntry(largeId, "content", userName);
    assertNull(actual);
  }

  @Test
  public void deleteLabJournalEntry_success() {
    labJournalEntryService.createLabJournalEntry(dataObject.getId(), "content 1", userName);
    labJournalEntryService.createLabJournalEntry(dataObject.getId(), "content 2", userName);
    LabJournalEntry toBeDeleted = labJournalEntryService.createLabJournalEntry(
      dataObject.getId(),
      "content 3",
      userName
    );
    dataObject = dataObjectService.getDataObjectByNeo4jId(dataObject.getId());
    labJournalEntryService.deleteLabJournalEntry(toBeDeleted.getId(), userName);
    List<LabJournalEntry> actual = labJournalEntryService.getLabJournalEntries(dataObject);
    assertFalse(actual.contains(toBeDeleted));
  }

  @Test
  public void getCollectionId_labJournalEntryExists_returnsId() {
    LabJournalEntry labJournalEntry = labJournalEntryService.createLabJournalEntry(
      dataObject.getId(),
      "content 1",
      userName
    );
    Long actual = labJournalEntryService.getCollectionId(labJournalEntry.getId());
    assertEquals(actual, dataObject.getCollection().getId());
  }

  @Test
  public void getCollectionId_labJournalEntryNotExists_returnsNull() {
    Long largeId = Long.MAX_VALUE;
    Long actual = labJournalEntryService.getCollectionId(largeId);
    assertNull(actual);
  }
}
