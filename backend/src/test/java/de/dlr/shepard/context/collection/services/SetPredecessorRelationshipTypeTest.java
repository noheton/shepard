package de.dlr.shepard.context.collection.services;

import static de.dlr.shepard.testing.fixtures.ShepardTestFixtures.aCollection;
import static de.dlr.shepard.testing.fixtures.ShepardTestFixtures.aDataObject;
import static de.dlr.shepard.testing.fixtures.ShepardTestFixtures.aUser;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.basicreference.daos.BasicReferenceDAO;
import de.dlr.shepard.context.version.daos.VersionDAO;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * QM1b — unit tests for {@link DataObjectService#setPredecessorRelationshipType}.
 *
 * <p>Exercises the four shapes the endpoint care about:
 * <ul>
 *   <li>Happy path: an existing predecessor edge gets annotated with
 *       {@code "fair2r:concession"}; the JSON gets persisted.</li>
 *   <li>Re-annotation (idempotency adjacent): the same predecessor's type
 *       gets replaced rather than duplicated.</li>
 *   <li>Bad relationship type → 400 (InvalidBodyException).</li>
 *   <li>Missing predecessor edge → 404 (InvalidPathException).</li>
 * </ul>
 */
@QuarkusComponentTest
public class SetPredecessorRelationshipTypeTest {

  @InjectMock DataObjectDAO dao;
  @InjectMock CollectionService collectionService;
  @InjectMock BasicReferenceDAO referenceDAO;
  @InjectMock UserDAO userDAO;
  @InjectMock VersionDAO versionDAO;
  @InjectMock DateHelper dateHelper;
  @InjectMock UserService userService;
  @InjectMock PermissionsService permissionsService;

  @Inject DataObjectService service;

  private final User defaultUser = aUser().username("qm1b-tester").build();

  private DataObject makeSubjectWithOnePredecessor(String predecessorAppId) {
    Collection collection = aCollection().id(7L).shepardId(70L).build();
    DataObject pred = aDataObject()
      .id(11L).shepardId(110L).appId(predecessorAppId)
      .inCollection(collection)
      .build();
    DataObject subject = aDataObject()
      .id(22L).shepardId(220L)
      .inCollection(collection)
      .withPredecessors(List.of(pred))
      .build();
    return subject;
  }

  @Test
  public void happyPath_annotatesEdgeWithConcession() {
    String predAppId = "01930a2b-0000-7000-0000-000000000aaa";
    DataObject subject = makeSubjectWithOnePredecessor(predAppId);

    when(dao.findByShepardId(subject.getShepardId())).thenReturn(subject);
    when(dao.createOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));
    when(userService.getCurrentUser()).thenReturn(defaultUser);
    when(permissionsService.isAccessTypeAllowedForUser(
      anyLong(), eq(AccessType.Write), any(), anyLong())).thenReturn(true);
    when(permissionsService.isAccessTypeAllowedForUser(
      anyLong(), eq(AccessType.Read), any(), anyLong())).thenReturn(true);

    DataObject updated = service.setPredecessorRelationshipType(
      subject.getShepardId(), predAppId, "fair2r:concession"
    );

    assertNotNull(updated.getTypedPredecessorsJson(),
      "typedPredecessorsJson must be populated after QM1b annotation");
    assertTrue(updated.getTypedPredecessorsJson().contains("fair2r:concession"),
      "stored JSON must include the new fair2r:concession type");
    assertTrue(updated.getTypedPredecessorsJson().contains(predAppId),
      "stored JSON must include the predecessor appId");
  }

  @Test
  public void reAnnotation_replacesPriorType() {
    // Subject already has a typedPredecessorsJson with the same predecessor
    // typed as fair2r:repairs. Calling QM1b with fair2r:concession should
    // REPLACE the existing entry rather than append a duplicate.
    String predAppId = "01930a2b-0000-7000-0000-000000000bbb";
    DataObject subject = makeSubjectWithOnePredecessor(predAppId);
    subject.setTypedPredecessorsJson(
      "[{\"predecessorAppId\":\"" + predAppId + "\",\"relationshipType\":\"fair2r:repairs\"}]"
    );

    when(dao.findByShepardId(subject.getShepardId())).thenReturn(subject);
    when(dao.createOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));
    when(userService.getCurrentUser()).thenReturn(defaultUser);
    when(permissionsService.isAccessTypeAllowedForUser(
      anyLong(), eq(AccessType.Write), any(), anyLong())).thenReturn(true);

    DataObject updated = service.setPredecessorRelationshipType(
      subject.getShepardId(), predAppId, "fair2r:concession"
    );

    String json = updated.getTypedPredecessorsJson();
    assertNotNull(json);
    assertTrue(json.contains("fair2r:concession"),
      "Replacement must contain the new type");
    // The pre-existing 'fair2r:repairs' must be gone, not co-listed.
    assertEquals(
      json.indexOf("fair2r:repairs"), -1,
      "Stored JSON must REPLACE the prior relationship type, not duplicate it. JSON was: " + json
    );
  }

  @Test
  public void invalidRelationshipType_throws400() {
    String predAppId = "01930a2b-0000-7000-0000-000000000ccc";
    DataObject subject = makeSubjectWithOnePredecessor(predAppId);

    when(dao.findByShepardId(subject.getShepardId())).thenReturn(subject);

    assertThrows(InvalidBodyException.class,
      () -> service.setPredecessorRelationshipType(
        subject.getShepardId(), predAppId, "owl:sameAs"
      ),
      "Unknown relationship types must be rejected with 400 (InvalidBodyException)");
  }

  @Test
  public void blankRelationshipType_throws400() {
    String predAppId = "01930a2b-0000-7000-0000-000000000ddd";
    DataObject subject = makeSubjectWithOnePredecessor(predAppId);

    when(dao.findByShepardId(subject.getShepardId())).thenReturn(subject);

    assertThrows(InvalidBodyException.class,
      () -> service.setPredecessorRelationshipType(
        subject.getShepardId(), predAppId, ""
      ),
      "Blank relationship types must be rejected");
  }

  @Test
  public void noSuchEdge_throws404() {
    // Subject has predecessor with appId 'X' but the caller asks to annotate
    // an unrelated predecessor 'Y' — service must throw InvalidPathException (→ 404).
    String linkedAppId = "01930a2b-0000-7000-0000-000000000eee";
    String unrelatedAppId = "01930a2b-0000-7000-0000-000000000fff";
    DataObject subject = makeSubjectWithOnePredecessor(linkedAppId);

    when(dao.findByShepardId(subject.getShepardId())).thenReturn(subject);
    when(userService.getCurrentUser()).thenReturn(defaultUser);
    when(permissionsService.isAccessTypeAllowedForUser(
      anyLong(), eq(AccessType.Write), any(), anyLong())).thenReturn(true);

    assertThrows(InvalidPathException.class,
      () -> service.setPredecessorRelationshipType(
        subject.getShepardId(), unrelatedAppId, "fair2r:concession"
      ),
      "Annotating a non-existent predecessor edge must throw 404");
  }

  @Test
  public void missingDataObject_throws404() {
    when(dao.findByShepardId(99999L)).thenReturn(null);

    assertThrows(InvalidPathException.class,
      () -> service.setPredecessorRelationshipType(
        99999L, "01930a2b-0000-7000-0000-000000000fff", "fair2r:concession"
      ),
      "Missing DataObject must throw 404");
  }

  @Test
  public void allFourQm1bTypes_validate() {
    // Smoke test: each of the QM1b-canonical types must pass the relationship-type guard.
    String predAppId = "01930a2b-0000-7000-0000-000000000111";
    for (String rt : new String[] {
      "prov:wasInformedBy", "prov:wasRevisionOf", "fair2r:repairs", "fair2r:concession"
    }) {
      DataObject subject = makeSubjectWithOnePredecessor(predAppId);
      when(dao.findByShepardId(subject.getShepardId())).thenReturn(subject);
      when(dao.createOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));
      when(userService.getCurrentUser()).thenReturn(defaultUser);
      when(permissionsService.isAccessTypeAllowedForUser(
        anyLong(), eq(AccessType.Write), any(), anyLong())).thenReturn(true);

      final String type = rt;
      assertDoesNotThrow(
        () -> service.setPredecessorRelationshipType(subject.getShepardId(), predAppId, type),
        "QM1b type '" + type + "' must be accepted by setPredecessorRelationshipType");
    }
  }
}
