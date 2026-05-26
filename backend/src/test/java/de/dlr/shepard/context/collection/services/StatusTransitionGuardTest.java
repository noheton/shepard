package de.dlr.shepard.context.collection.services;

import static de.dlr.shepard.testing.fixtures.ShepardTestFixtures.aCollection;
import static de.dlr.shepard.testing.fixtures.ShepardTestFixtures.aDataObject;
import static de.dlr.shepard.testing.fixtures.ShepardTestFixtures.aUser;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.references.basicreference.daos.BasicReferenceDAO;
import de.dlr.shepard.context.version.daos.VersionDAO;
import de.dlr.shepard.context.version.entities.Version;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * MFG5 — unit tests for the DataObject status closed-enum enforcement and
 * transition guard, wired through {@link DataObjectService}.
 */
@QuarkusComponentTest
public class StatusTransitionGuardTest {

  @InjectMock
  DataObjectDAO dao;

  @InjectMock
  CollectionService collectionService;

  @InjectMock
  BasicReferenceDAO referenceDAO;

  @InjectMock
  UserDAO userDAO;

  @InjectMock
  VersionDAO versionDAO;

  @InjectMock
  DateHelper dateHelper;

  @InjectMock
  UserService userService;

  @InjectMock
  PermissionsService permissionsService;

  @Inject
  DataObjectService service;

  private final User defaultUser = aUser().username("testuser").build();

  // ─── Guard unit tests (no Quarkus wiring needed) ─────────────────────────

  @Test
  public void validateOnCreate_nullStatus_allowed() {
    assertDoesNotThrow(() -> StatusTransitionGuard.validateOnCreate(null));
  }

  @Test
  public void validateOnCreate_validStatus_allowed() {
    for (String status : StatusTransitionGuard.VALID_STATUSES) {
      assertDoesNotThrow(() -> StatusTransitionGuard.validateOnCreate(status));
    }
  }

  @Test
  public void validateOnCreate_invalidStatus_throws400() {
    assertThrows(InvalidBodyException.class,
      () -> StatusTransitionGuard.validateOnCreate("DELETED"));
  }

  @Test
  public void validateOnUpdate_nullProposed_allowed() {
    assertDoesNotThrow(() -> StatusTransitionGuard.validateOnUpdate("PUBLISHED", null));
  }

  @Test
  public void validateOnUpdate_nullCurrent_anyForwardMoveAllowed() {
    assertDoesNotThrow(() -> StatusTransitionGuard.validateOnUpdate(null, "PUBLISHED"));
  }

  @Test
  public void validateOnUpdate_sameState_allowed() {
    assertDoesNotThrow(() -> StatusTransitionGuard.validateOnUpdate("PUBLISHED", "PUBLISHED"));
  }

  @Test
  public void validateOnUpdate_publishedToDraft_throws409() {
    WebApplicationException ex = assertThrows(WebApplicationException.class,
      () -> StatusTransitionGuard.validateOnUpdate("PUBLISHED", "DRAFT"));
    assertEquals(409, ex.getResponse().getStatus());
  }

  @Test
  public void validateOnUpdate_publishedToInReview_throws409() {
    WebApplicationException ex = assertThrows(WebApplicationException.class,
      () -> StatusTransitionGuard.validateOnUpdate("PUBLISHED", "IN_REVIEW"));
    assertEquals(409, ex.getResponse().getStatus());
  }

  @Test
  public void validateOnUpdate_archivedToReady_throws409() {
    WebApplicationException ex = assertThrows(WebApplicationException.class,
      () -> StatusTransitionGuard.validateOnUpdate("ARCHIVED", "READY"));
    assertEquals(409, ex.getResponse().getStatus());
  }

  @Test
  public void validateOnUpdate_archivedToPublished_throws409() {
    WebApplicationException ex = assertThrows(WebApplicationException.class,
      () -> StatusTransitionGuard.validateOnUpdate("ARCHIVED", "PUBLISHED"));
    assertEquals(409, ex.getResponse().getStatus());
  }

  @Test
  public void validateOnUpdate_invalidStatus_throws400() {
    assertThrows(InvalidBodyException.class,
      () -> StatusTransitionGuard.validateOnUpdate("DRAFT", "BOGUS_STATE"));
  }

  // ─── Service-level integration tests ─────────────────────────────────────

  /**
   * MFG5 acceptance criterion 1: createDataObject with an invalid status value
   * must return 400 (InvalidBodyException).
   */
  @Test
  public void createDataObject_invalidStatus_returns400() {
    Collection collection = aCollection().id(10L).shepardId(100L).build();
    collection.setVersion(new Version(new UUID(1L, 2L)));

    DataObjectIO input = new DataObjectIO();
    input.setName("test");
    input.setStatus("COMPLETELY_INVALID");

    when(collectionService.getCollection(collection.getShepardId())).thenReturn(collection);
    when(userService.getCurrentUser()).thenReturn(defaultUser);

    assertThrows(InvalidBodyException.class,
      () -> service.createDataObject(collection.getShepardId(), input));
  }

  /**
   * MFG5 acceptance criterion 2: updateDataObject transitioning from
   * PUBLISHED to DRAFT must return 409 (WebApplicationException).
   */
  @Test
  public void updateDataObject_publishedToDraft_returns409() {
    Collection collection = aCollection().id(20L).shepardId(200L).build();
    DataObject published = aDataObject()
      .id(5L).shepardId(50L)
      .inCollection(collection)
      .withStatus("PUBLISHED")
      .build();

    DataObjectIO input = new DataObjectIO();
    input.setName(published.getName());
    input.setStatus("DRAFT");

    when(dao.findByShepardId(published.getShepardId())).thenReturn(published);
    when(collectionService.getCollection(collection.getShepardId())).thenReturn(collection);
    when(userService.getCurrentUser()).thenReturn(defaultUser);
    when(permissionsService.isAccessTypeAllowedForUser(
      eq(collection.getShepardId()), eq(AccessType.Read), any(), anyLong())).thenReturn(true);
    when(permissionsService.isAccessTypeAllowedForUser(
      eq(collection.getShepardId()), eq(AccessType.Write), any(), anyLong())).thenReturn(true);
    when(dateHelper.getDate()).thenReturn(new Date());

    WebApplicationException ex = assertThrows(WebApplicationException.class,
      () -> service.updateDataObject(collection.getShepardId(), published.getShepardId(), input));
    assertEquals(409, ex.getResponse().getStatus());
  }

  /**
   * MFG5 acceptance criterion 3: updateDataObject with a valid forward transition
   * (DRAFT → PUBLISHED) must succeed.
   */
  @Test
  public void updateDataObject_draftToPublished_succeeds() {
    Collection collection = aCollection().id(30L).shepardId(300L).build();
    DataObject draft = aDataObject()
      .id(6L).shepardId(60L)
      .inCollection(collection)
      .withStatus("DRAFT")
      .build();

    DataObjectIO input = new DataObjectIO();
    input.setName(draft.getName());
    input.setStatus("PUBLISHED");

    DataObject updated = aDataObject()
      .id(draft.getId()).shepardId(draft.getShepardId())
      .inCollection(collection)
      .withStatus("PUBLISHED")
      .build();

    when(dao.findByShepardId(draft.getShepardId())).thenReturn(draft);
    when(dao.findByNeo4jId(anyLong())).thenReturn(null);
    when(dao.createOrUpdate(any())).thenReturn(updated);
    when(collectionService.getCollection(collection.getShepardId())).thenReturn(collection);
    when(userService.getCurrentUser()).thenReturn(defaultUser);
    when(permissionsService.isAccessTypeAllowedForUser(
      eq(collection.getShepardId()), eq(AccessType.Read), any(), anyLong())).thenReturn(true);
    when(permissionsService.isAccessTypeAllowedForUser(
      eq(collection.getShepardId()), eq(AccessType.Write), any(), anyLong())).thenReturn(true);
    when(dateHelper.getDate()).thenReturn(new Date());

    assertDoesNotThrow(
      () -> service.updateDataObject(collection.getShepardId(), draft.getShepardId(), input));
  }
}
