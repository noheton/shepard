package de.dlr.shepard.context.collection.services;

import static de.dlr.shepard.testing.fixtures.ShepardTestFixtures.aCollection;
import static de.dlr.shepard.testing.fixtures.ShepardTestFixtures.aDataObject;
import static de.dlr.shepard.testing.fixtures.ShepardTestFixtures.aUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.daos.PermissionsDAO;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * BUG-PREDECESSOR-IDS-NUMERIC-IN-V2-PATCH — unit tests proving that
 * {@code DataObjectIO.predecessorAppIds} is accepted alongside (and in addition
 * to) the legacy {@code predecessorIds} numeric array when the PATCH handler
 * calls {@link DataObjectService#updateDataObject}.
 *
 * <p>Four cases:
 * <ol>
 *   <li>PATCH with {@code predecessorIds} only — existing behavior preserved.</li>
 *   <li>PATCH with {@code predecessorAppIds} only — appId resolves, predecessor set updated.</li>
 *   <li>PATCH with both fields — union of the two sets applied.</li>
 *   <li>PATCH with an unresolvable appId in {@code predecessorAppIds} — WARN logged,
 *       graceful (no exception, unresolvable entry silently skipped).</li>
 * </ol>
 */
@QuarkusComponentTest
public class DataObjectPatchPredecessorAppIdTest {

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

  @InjectMock
  PermissionsDAO permissionsDAO;

  @Inject
  DataObjectService service;

  private final User defaultUser = aUser().username("alice").build();
  private final Date date = new Date(100);

  // ── helpers ─────────────────────────────────────────────────────────────────

  private Collection makeCollection(long id, long shepardId) {
    Collection c = aCollection().id(id).shepardId(shepardId).build();
    Version v = new Version(new UUID(1L, 2L));
    c.setVersion(v);
    return c;
  }

  private DataObject makeSubject(Collection collection) {
    return aDataObject()
      .id(1L).shepardId(10L)
      .named("subject")
      .inCollection(collection)
      .build();
  }

  private void stubDefaults(Collection collection, DataObject subject) {
    when(dao.findByShepardId(subject.getShepardId())).thenReturn(subject);
    when(dao.createOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));
    when(dateHelper.getDate()).thenReturn(date);
    when(userService.getCurrentUser()).thenReturn(defaultUser);
    when(collectionService.getCollection(collection.getShepardId())).thenReturn(collection);
    when(
      permissionsService.isAccessTypeAllowedForUser(
        anyLong(), eq(AccessType.Read), eq(defaultUser.getUsername()), anyLong()
      )
    ).thenReturn(true);
  }

  // ── case 1: predecessorIds only — existing behavior preserved ─────────────

  @Test
  public void patchWithPredecessorIdsOnly_existingBehaviourPreserved() {
    Collection collection = makeCollection(2L, 20L);
    DataObject subject = makeSubject(collection);
    DataObject pred1 = aDataObject().id(4L).shepardId(40L).inCollection(collection).build();

    stubDefaults(collection, subject);
    // findByCollectionAndShepardIds is called by findRelatedDataObjects (PERF5).
    when(dao.findByCollectionAndShepardIds(eq(collection.getShepardId()), anyList()))
      .thenReturn(List.of(pred1));
    // findByNeo4jId is called in getDataObject for each predecessor.
    when(dao.findByNeo4jId(pred1.getId())).thenReturn(pred1);

    DataObjectIO input = new DataObjectIO();
    input.setName("subject");
    input.setPredecessorIds(new long[] { pred1.getShepardId() });
    // predecessorAppIds is null (absent from body) — must not trigger any appId lookup.

    DataObject result = service.updateDataObject(collection.getShepardId(), subject.getShepardId(), input);

    // predecessor set must contain pred1.
    assertTrue(
      result.getPredecessors().stream().anyMatch(p -> p.getShepardId().equals(pred1.getShepardId())),
      "predecessor resolved from predecessorIds must appear in result"
    );
  }

  // ── case 2: predecessorAppIds only ─────────────────────────────────────────

  @Test
  public void patchWithPredecessorAppIdsOnly_appIdResolvesToPredecessor() {
    Collection collection = makeCollection(3L, 30L);
    DataObject subject = makeSubject(collection);
    String predAppId = "019e6ffc-aaaa-7abc-9def-000000000099";
    DataObject pred2 = aDataObject()
      .id(5L).shepardId(50L)
      .appId(predAppId)
      .inCollection(collection)
      .build();

    stubDefaults(collection, subject);
    // predecessorIds is null/empty → findRelatedDataObjects returns [].
    when(dao.findByCollectionAndShepardIds(eq(collection.getShepardId()), anyList()))
      .thenReturn(new ArrayList<>());
    // findByAppId is called for the appId-keyed predecessor.
    when(dao.findByAppId(predAppId)).thenReturn(pred2);
    when(dao.findByNeo4jId(pred2.getId())).thenReturn(pred2);

    DataObjectIO input = new DataObjectIO();
    input.setName("subject");
    input.setPredecessorIds(new long[0]);
    input.setPredecessorAppIds(List.of(predAppId));

    DataObject result = service.updateDataObject(collection.getShepardId(), subject.getShepardId(), input);

    assertTrue(
      result.getPredecessors().stream().anyMatch(p -> predAppId.equals(p.getAppId())),
      "predecessor resolved from predecessorAppIds must appear in result"
    );
    verify(dao).findByAppId(predAppId);
  }

  // ── case 3: both fields — union applied ────────────────────────────────────

  @Test
  public void patchWithBothFields_unionApplied() {
    Collection collection = makeCollection(4L, 40L);
    DataObject subject = makeSubject(collection);

    DataObject pred1 = aDataObject().id(6L).shepardId(60L).inCollection(collection).build();
    String pred2AppId = "019e6ffc-bbbb-7abc-9def-000000000088";
    DataObject pred2 = aDataObject()
      .id(7L).shepardId(70L)
      .appId(pred2AppId)
      .inCollection(collection)
      .build();

    stubDefaults(collection, subject);
    when(dao.findByCollectionAndShepardIds(eq(collection.getShepardId()), anyList()))
      .thenReturn(List.of(pred1));
    when(dao.findByNeo4jId(pred1.getId())).thenReturn(pred1);
    when(dao.findByAppId(pred2AppId)).thenReturn(pred2);
    when(dao.findByNeo4jId(pred2.getId())).thenReturn(pred2);

    DataObjectIO input = new DataObjectIO();
    input.setName("subject");
    input.setPredecessorIds(new long[] { pred1.getShepardId() });
    input.setPredecessorAppIds(List.of(pred2AppId));

    DataObject result = service.updateDataObject(collection.getShepardId(), subject.getShepardId(), input);

    List<DataObject> preds = result.getPredecessors();
    assertTrue(
      preds.stream().anyMatch(p -> p.getShepardId().equals(pred1.getShepardId())),
      "pred1 (from predecessorIds) must appear in union"
    );
    assertTrue(
      preds.stream().anyMatch(p -> pred2AppId.equals(p.getAppId())),
      "pred2 (from predecessorAppIds) must appear in union"
    );
    assertEquals(2, preds.size(), "union must contain exactly 2 unique predecessors");
  }

  // ── case 4: unresolvable appId — WARN logged, no exception ─────────────────

  @Test
  public void patchWithUnresolvableAppId_gracefulSkip() {
    Collection collection = makeCollection(5L, 50L);
    DataObject subject = makeSubject(collection);

    String missingAppId = "019e6ffc-cccc-7abc-9def-000000000000";

    stubDefaults(collection, subject);
    when(dao.findByCollectionAndShepardIds(eq(collection.getShepardId()), anyList()))
      .thenReturn(new ArrayList<>());
    // findByAppId returns null — simulates DataObject not found.
    when(dao.findByAppId(missingAppId)).thenReturn(null);

    DataObjectIO input = new DataObjectIO();
    input.setName("subject");
    input.setPredecessorIds(new long[0]);
    input.setPredecessorAppIds(List.of(missingAppId));

    // Must not throw — fail-soft per CLAUDE.md.
    DataObject result = service.updateDataObject(collection.getShepardId(), subject.getShepardId(), input);

    assertTrue(result.getPredecessors().isEmpty(), "unresolvable appId must produce empty predecessor set");
    verify(dao).findByAppId(missingAppId);
  }
}
