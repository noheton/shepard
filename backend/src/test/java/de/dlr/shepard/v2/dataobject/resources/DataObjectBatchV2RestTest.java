package de.dlr.shepard.v2.dataobject.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.v2.dataobject.io.DataObjectBatchCreateItemIO;
import de.dlr.shepard.v2.dataobject.io.DataObjectBatchResponseIO;
import de.dlr.shepard.v2.dataobject.io.DataObjectBatchResultItemIO;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * MFFD-BATCH-01 — unit tests for {@link DataObjectBatchV2Rest}.
 *
 * <p>Covers the four cases called out in the task spec:
 * <ol>
 *   <li>Happy path — 3 items, all created → 207 with 3 "created" results.</li>
 *   <li>Mixed — 1 valid + 1 forbidden + 1 invalid name → 207, created=1, failed=2.</li>
 *   <li>Oversized batch (501 items) → 400.</li>
 *   <li>Empty array → 400.</li>
 * </ol>
 *
 * <p>Additional cases:
 * <ul>
 *   <li>Unknown collection → item error COLLECTION_NOT_FOUND.</li>
 *   <li>Unknown parentAppId → item error PARENT_NOT_FOUND.</li>
 *   <li>Service throws InvalidPathException → item error COLLECTION_NOT_FOUND.</li>
 *   <li>Service throws InvalidBodyException → item error INVALID_INPUT.</li>
 *   <li>Permission memo: two items same collection → permissions checked once.</li>
 *   <li>Null principal → 401.</li>
 * </ul>
 */
class DataObjectBatchV2RestTest {

  static final String COLL_APP_ID_A = "018f9c5a-7e26-7000-a000-000000000010";
  static final String COLL_APP_ID_B = "018f9c5a-7e26-7000-a000-000000000011";
  static final String PARENT_APP_ID = "018f9c5a-7e26-7000-a000-000000000099";
  static final long COLL_OGM_ID_A = 42L;
  static final long COLL_OGM_ID_B = 43L;
  static final long PARENT_OGM_ID = 77L;
  static final String CALLER = "alice";

  /** Build a minimal DataObject stub that survives {@code new DataObjectIO(d)}. */
  static DataObject makeDataObject(long ogmId, String appId, String name) {
    Collection coll = new Collection();
    coll.setShepardId(COLL_OGM_ID_A);
    DataObject d = new DataObject();
    d.setShepardId(ogmId);
    d.setAppId(appId);
    d.setName(name);
    d.setCollection(coll);
    return d;
  }

  @Mock
  DataObjectService dataObjectService;

  @Mock
  PermissionsService permissionsService;

  @Mock
  EntityIdResolver entityIdResolver;

  @Mock
  SecurityContext securityContext;

  @Mock
  Principal principal;

  DataObjectBatchV2Rest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new DataObjectBatchV2Rest();
    resource.dataObjectService = dataObjectService;
    resource.permissionsService = permissionsService;
    resource.entityIdResolver = entityIdResolver;

    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
  }

  // ── 400 path validation ───────────────────────────────────────────────────

  @Test
  void emptyArrayReturns400() {
    Response r = resource.batch(List.of(), securityContext);
    assertEquals(400, r.getStatus());
    verify(dataObjectService, never()).createDataObject(any(Long.class), any());
  }

  @Test
  void nullBodyReturns400() {
    Response r = resource.batch(null, securityContext);
    assertEquals(400, r.getStatus());
  }

  @Test
  void oversizedBatchReturns400() {
    List<DataObjectBatchCreateItemIO> items = new ArrayList<>();
    for (int i = 0; i < DataObjectBatchV2Rest.MAX_BATCH_SIZE + 1; i++) {
      items.add(new DataObjectBatchCreateItemIO(COLL_APP_ID_A, "DO-" + i, null, null, null, null));
    }
    Response r = resource.batch(items, securityContext);
    assertEquals(400, r.getStatus());
    verify(dataObjectService, never()).createDataObject(any(Long.class), any());
  }

  // ── 401 auth ──────────────────────────────────────────────────────────────

  @Test
  void nullPrincipalReturns401() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    List<DataObjectBatchCreateItemIO> items =
      List.of(new DataObjectBatchCreateItemIO(COLL_APP_ID_A, "TR-001", null, null, null, null));
    Response r = resource.batch(items, securityContext);
    assertEquals(401, r.getStatus());
  }

  // ── happy path: 3 items all created ───────────────────────────────────────

  @Test
  void happyPath3ItemsAllCreated() {
    DataObject d1 = makeDataObject(1L, "00000000-0000-7000-0000-000000000001", "TR-001");
    DataObject d2 = makeDataObject(2L, "00000000-0000-7000-0000-000000000002", "TR-002");
    DataObject d3 = makeDataObject(3L, "00000000-0000-7000-0000-000000000003", "TR-003");

    when(entityIdResolver.resolveLong(COLL_APP_ID_A)).thenReturn(COLL_OGM_ID_A);
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID_A, AccessType.Write, CALLER))
      .thenReturn(true);
    when(dataObjectService.createDataObject(eq(COLL_OGM_ID_A), any())).thenReturn(d1, d2, d3);

    List<DataObjectBatchCreateItemIO> items = List.of(
      new DataObjectBatchCreateItemIO(COLL_APP_ID_A, "TR-001", null, null, null, null),
      new DataObjectBatchCreateItemIO(COLL_APP_ID_A, "TR-002", "desc", null, null, "DRAFT"),
      new DataObjectBatchCreateItemIO(COLL_APP_ID_A, "TR-003", null, null, null, null)
    );

    Response r = resource.batch(items, securityContext);

    assertEquals(207, r.getStatus());
    DataObjectBatchResponseIO body = (DataObjectBatchResponseIO) r.getEntity();
    assertNotNull(body);
    assertEquals(3, body.getCreated());
    assertEquals(0, body.getFailed());
    assertEquals(3, body.getResults().size());

    DataObjectBatchResultItemIO first = body.getResults().get(0);
    assertEquals(0, first.getIndex());
    assertEquals("created", first.getStatus());
    assertEquals("00000000-0000-7000-0000-000000000001", first.getAppId());
    assertNull(first.getErrorCode());

    DataObjectBatchResultItemIO second = body.getResults().get(1);
    assertEquals(1, second.getIndex());
    assertEquals("created", second.getStatus());

    DataObjectBatchResultItemIO third = body.getResults().get(2);
    assertEquals(2, third.getIndex());
    assertEquals("created", third.getStatus());
  }

  // ── mixed: 1 valid + 1 forbidden + 1 blank name ──────────────────────────

  @Test
  void mixedBatchOneForbiddenOneInvalidName() {
    DataObject d1 = makeDataObject(10L, "00000000-0000-7000-0000-000000000010", "good");

    // Collection A: allowed
    when(entityIdResolver.resolveLong(COLL_APP_ID_A)).thenReturn(COLL_OGM_ID_A);
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID_A, AccessType.Write, CALLER))
      .thenReturn(true);
    when(dataObjectService.createDataObject(eq(COLL_OGM_ID_A), any())).thenReturn(d1);

    // Collection B: forbidden
    when(entityIdResolver.resolveLong(COLL_APP_ID_B)).thenReturn(COLL_OGM_ID_B);
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID_B, AccessType.Write, CALLER))
      .thenReturn(false);

    List<DataObjectBatchCreateItemIO> items = List.of(
      new DataObjectBatchCreateItemIO(COLL_APP_ID_A, "good-item", null, null, null, null),
      new DataObjectBatchCreateItemIO(COLL_APP_ID_B, "forbidden-item", null, null, null, null),
      new DataObjectBatchCreateItemIO(COLL_APP_ID_A, " ", null, null, null, null) // blank name
    );

    Response r = resource.batch(items, securityContext);

    assertEquals(207, r.getStatus());
    DataObjectBatchResponseIO body = (DataObjectBatchResponseIO) r.getEntity();
    assertEquals(1, body.getCreated());
    assertEquals(2, body.getFailed());

    List<DataObjectBatchResultItemIO> results = body.getResults();
    assertEquals("created", results.get(0).getStatus());
    assertEquals("FORBIDDEN", results.get(1).getErrorCode());
    assertEquals("INVALID_INPUT", results.get(2).getErrorCode());
  }

  // ── collection not found → per-item COLLECTION_NOT_FOUND ─────────────────

  @Test
  void unknownCollectionYieldsCollectionNotFoundError() {
    when(entityIdResolver.resolveLong(COLL_APP_ID_A)).thenThrow(new NotFoundException());

    List<DataObjectBatchCreateItemIO> items =
      List.of(new DataObjectBatchCreateItemIO(COLL_APP_ID_A, "TR-001", null, null, null, null));

    Response r = resource.batch(items, securityContext);

    assertEquals(207, r.getStatus());
    DataObjectBatchResponseIO body = (DataObjectBatchResponseIO) r.getEntity();
    assertEquals(0, body.getCreated());
    assertEquals(1, body.getFailed());
    assertEquals("COLLECTION_NOT_FOUND", body.getResults().get(0).getErrorCode());
    verify(dataObjectService, never()).createDataObject(any(Long.class), any());
  }

  // ── parent not found → per-item PARENT_NOT_FOUND ─────────────────────────

  @Test
  void unknownParentAppIdYieldsParentNotFoundError() {
    when(entityIdResolver.resolveLong(COLL_APP_ID_A)).thenReturn(COLL_OGM_ID_A);
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID_A, AccessType.Write, CALLER))
      .thenReturn(true);
    when(entityIdResolver.resolveLong(PARENT_APP_ID)).thenThrow(new NotFoundException());

    List<DataObjectBatchCreateItemIO> items =
      List.of(new DataObjectBatchCreateItemIO(COLL_APP_ID_A, "child-DO", null, PARENT_APP_ID, null, null));

    Response r = resource.batch(items, securityContext);

    assertEquals(207, r.getStatus());
    DataObjectBatchResponseIO body = (DataObjectBatchResponseIO) r.getEntity();
    assertEquals(0, body.getCreated());
    assertEquals(1, body.getFailed());
    assertEquals("PARENT_NOT_FOUND", body.getResults().get(0).getErrorCode());
    verify(dataObjectService, never()).createDataObject(any(Long.class), any());
  }

  // ── service throws InvalidPathException → COLLECTION_NOT_FOUND ────────────

  @Test
  void serviceThrowsInvalidPathExceptionMappedToCollectionNotFound() {
    when(entityIdResolver.resolveLong(COLL_APP_ID_A)).thenReturn(COLL_OGM_ID_A);
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID_A, AccessType.Write, CALLER))
      .thenReturn(true);
    when(dataObjectService.createDataObject(eq(COLL_OGM_ID_A), any()))
      .thenThrow(new InvalidPathException("collection gone"));

    List<DataObjectBatchCreateItemIO> items =
      List.of(new DataObjectBatchCreateItemIO(COLL_APP_ID_A, "TR-001", null, null, null, null));

    Response r = resource.batch(items, securityContext);

    assertEquals(207, r.getStatus());
    DataObjectBatchResponseIO body = (DataObjectBatchResponseIO) r.getEntity();
    assertEquals(0, body.getCreated());
    assertEquals(1, body.getFailed());
    assertEquals("COLLECTION_NOT_FOUND", body.getResults().get(0).getErrorCode());
  }

  // ── service throws InvalidBodyException → INVALID_INPUT ───────────────────

  @Test
  void serviceThrowsInvalidBodyExceptionMappedToInvalidInput() {
    when(entityIdResolver.resolveLong(COLL_APP_ID_A)).thenReturn(COLL_OGM_ID_A);
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID_A, AccessType.Write, CALLER))
      .thenReturn(true);
    when(dataObjectService.createDataObject(eq(COLL_OGM_ID_A), any()))
      .thenThrow(new InvalidBodyException("successors must be empty"));

    List<DataObjectBatchCreateItemIO> items =
      List.of(new DataObjectBatchCreateItemIO(COLL_APP_ID_A, "TR-001", null, null, null, null));

    Response r = resource.batch(items, securityContext);

    assertEquals(207, r.getStatus());
    DataObjectBatchResponseIO body = (DataObjectBatchResponseIO) r.getEntity();
    assertEquals("INVALID_INPUT", body.getResults().get(0).getErrorCode());
  }

  // ── permission memo: two items same collection → one perms check ──────────

  @Test
  void permissionMemoReducesPermissionsChecks() {
    DataObject d1 = makeDataObject(1L, "aaa", "DO-1");
    DataObject d2 = makeDataObject(2L, "bbb", "DO-2");

    when(entityIdResolver.resolveLong(COLL_APP_ID_A)).thenReturn(COLL_OGM_ID_A);
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID_A, AccessType.Write, CALLER))
      .thenReturn(true);
    when(dataObjectService.createDataObject(eq(COLL_OGM_ID_A), any())).thenReturn(d1, d2);

    List<DataObjectBatchCreateItemIO> items = List.of(
      new DataObjectBatchCreateItemIO(COLL_APP_ID_A, "DO-1", null, null, null, null),
      new DataObjectBatchCreateItemIO(COLL_APP_ID_A, "DO-2", null, null, null, null)
    );

    Response r = resource.batch(items, securityContext);

    assertEquals(207, r.getStatus());
    DataObjectBatchResponseIO body = (DataObjectBatchResponseIO) r.getEntity();
    assertEquals(2, body.getCreated());
    // Permission checked only ONCE despite two items (memo hit on second).
    verify(permissionsService, times(1))
      .isAccessTypeAllowedForUser(COLL_OGM_ID_A, AccessType.Write, CALLER);
  }

  // ── item with null name → INVALID_INPUT (null treated same as blank) ───────

  @Test
  void nullNameYieldsInvalidInputError() {
    when(entityIdResolver.resolveLong(COLL_APP_ID_A)).thenReturn(COLL_OGM_ID_A);
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID_A, AccessType.Write, CALLER))
      .thenReturn(true);

    List<DataObjectBatchCreateItemIO> items =
      List.of(new DataObjectBatchCreateItemIO(COLL_APP_ID_A, null, null, null, null, null));

    Response r = resource.batch(items, securityContext);

    assertEquals(207, r.getStatus());
    DataObjectBatchResponseIO body = (DataObjectBatchResponseIO) r.getEntity();
    assertEquals(0, body.getCreated());
    assertEquals(1, body.getFailed());
    assertEquals("INVALID_INPUT", body.getResults().get(0).getErrorCode());
    verify(dataObjectService, never()).createDataObject(any(Long.class), any());
  }

  // ── service throws InvalidAuthException → FORBIDDEN ──────────────────────

  @Test
  void serviceThrowsInvalidAuthExceptionMappedToForbidden() {
    when(entityIdResolver.resolveLong(COLL_APP_ID_A)).thenReturn(COLL_OGM_ID_A);
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID_A, AccessType.Write, CALLER))
      .thenReturn(true);
    when(dataObjectService.createDataObject(eq(COLL_OGM_ID_A), any()))
      .thenThrow(new InvalidAuthException("not allowed at service level"));

    List<DataObjectBatchCreateItemIO> items =
      List.of(new DataObjectBatchCreateItemIO(COLL_APP_ID_A, "TR-001", null, null, null, null));

    Response r = resource.batch(items, securityContext);

    assertEquals(207, r.getStatus());
    DataObjectBatchResponseIO body = (DataObjectBatchResponseIO) r.getEntity();
    assertEquals("FORBIDDEN", body.getResults().get(0).getErrorCode());
  }

  // ── unexpected runtime exception → INTERNAL_ERROR (not 500) ─────────────

  @Test
  void unexpectedExceptionMappedToInternalError() {
    when(entityIdResolver.resolveLong(COLL_APP_ID_A)).thenReturn(COLL_OGM_ID_A);
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID_A, AccessType.Write, CALLER))
      .thenReturn(true);
    when(dataObjectService.createDataObject(eq(COLL_OGM_ID_A), any()))
      .thenThrow(new RuntimeException("DB unreachable simulation"));

    List<DataObjectBatchCreateItemIO> items =
      List.of(new DataObjectBatchCreateItemIO(COLL_APP_ID_A, "TR-001", null, null, null, null));

    Response r = resource.batch(items, securityContext);

    assertEquals(207, r.getStatus());
    DataObjectBatchResponseIO body = (DataObjectBatchResponseIO) r.getEntity();
    assertEquals(0, body.getCreated());
    assertEquals(1, body.getFailed());
    assertEquals("INTERNAL_ERROR", body.getResults().get(0).getErrorCode());
  }

  // ── index ordering preserved across mixed results ─────────────────────────

  @Test
  void resultIndexesMatchInputPositions() {
    DataObject d0 = makeDataObject(100L, "app-100", "DO-0");
    DataObject d2 = makeDataObject(102L, "app-102", "DO-2");

    when(entityIdResolver.resolveLong(COLL_APP_ID_A)).thenReturn(COLL_OGM_ID_A);
    when(entityIdResolver.resolveLong(COLL_APP_ID_B)).thenThrow(new NotFoundException());
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID_A, AccessType.Write, CALLER))
      .thenReturn(true);
    when(dataObjectService.createDataObject(eq(COLL_OGM_ID_A), any())).thenReturn(d0, d2);

    List<DataObjectBatchCreateItemIO> items = List.of(
      new DataObjectBatchCreateItemIO(COLL_APP_ID_A, "DO-0", null, null, null, null), // index 0
      new DataObjectBatchCreateItemIO(COLL_APP_ID_B, "DO-1", null, null, null, null), // index 1: unknown coll
      new DataObjectBatchCreateItemIO(COLL_APP_ID_A, "DO-2", null, null, null, null)  // index 2
    );

    Response r = resource.batch(items, securityContext);
    DataObjectBatchResponseIO body = (DataObjectBatchResponseIO) r.getEntity();

    assertEquals(0, body.getResults().get(0).getIndex());
    assertEquals("created", body.getResults().get(0).getStatus());

    assertEquals(1, body.getResults().get(1).getIndex());
    assertEquals("error", body.getResults().get(1).getStatus());

    assertEquals(2, body.getResults().get(2).getIndex());
    assertEquals("created", body.getResults().get(2).getStatus());

    assertEquals(2, body.getCreated());
    assertEquals(1, body.getFailed());
  }
}
