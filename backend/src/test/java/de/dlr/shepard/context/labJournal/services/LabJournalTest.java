package de.dlr.shepard.context.labJournal.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.security.JWTPrincipal;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.context.labJournal.entities.LabJournalEntry;
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

  @Inject
  AuthenticationContext authenticationContext;

  private Collection collection;
  private DataObject dataObject;

  private final String userName = "user_name";
  private final String collectionName = "collection_name";

  @BeforeEach
  public void setup() {
    User user = new User(userName);
    userService.createOrUpdateUser(user);
    authenticationContext.setPrincipal(new JWTPrincipal(userName, "key"));
    CollectionIO collectionIO = new CollectionIO();
    collectionIO.setName(collectionName);
    collection = collectionService.createCollection(collectionIO);
    collection.setPermissions(null);
    DataObjectIO dataObjectIO = new DataObjectIO();
    dataObject = dataObjectService.createDataObject(collection.getId(), dataObjectIO);
  }

  @Test
  public void createLabJournalEntry_success() {
    LabJournalEntry created = labJournalEntryService.createLabJournalEntry(dataObject.getId(), "content 1");
    LabJournalEntry actual = labJournalEntryService.getLabJournalEntry(created.getId());
    assertEquals(created, actual);
  }

  @Test
  public void getLabJournalEntries_forInsertedLabJournals_returnsAll() throws InterruptedException {
    List<LabJournalEntry> created = List.of(
      labJournalEntryService.createLabJournalEntry(dataObject.getId(), "content 1"),
      labJournalEntryService.createLabJournalEntry(dataObject.getId(), "content 2"),
      labJournalEntryService.createLabJournalEntry(dataObject.getId(), "content 3"),
      labJournalEntryService.createLabJournalEntry(dataObject.getId(), "content 4")
    );
    dataObject = dataObjectService.getDataObject(dataObject.getId());
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
    LabJournalEntry created = labJournalEntryService.createLabJournalEntry(dataObject.getId(), "content 1");
    labJournalEntryService.updateLabJournalEntry(created.getId(), "content 2");
    LabJournalEntry actual = labJournalEntryService.getLabJournalEntry(created.getId());
    assertEquals(actual.getContent(), created.getContent());
  }

  @Test
  public void updateLabJournalEntry_notExistsLabJournalEntry_throwsNotFound() {
    Long largeId = Long.MAX_VALUE;
    assertThrowsExactly(InvalidPathException.class, () ->
      labJournalEntryService.updateLabJournalEntry(largeId, "content")
    );
  }

  @Test
  public void deleteLabJournalEntry_success() {
    labJournalEntryService.createLabJournalEntry(dataObject.getId(), "content 1");
    labJournalEntryService.createLabJournalEntry(dataObject.getId(), "content 2");
    LabJournalEntry toBeDeleted = labJournalEntryService.createLabJournalEntry(dataObject.getId(), "content 3");
    dataObject = dataObjectService.getDataObject(dataObject.getId());
    labJournalEntryService.deleteLabJournalEntry(toBeDeleted.getId());
    List<LabJournalEntry> actual = labJournalEntryService.getLabJournalEntries(dataObject);
    assertFalse(actual.contains(toBeDeleted));
  }
}
