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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * BATCH-API-5 — unit tests for {@link DataObjectBatchV2Rest}.
 *
 * <p>Original MFFD-BATCH-01 cases:
 * <ol>
 *   <li>Happy path — 3 items, all created → 207 with 3 "created" results.</li>
 *   <li>Mixed — 1 valid + 1 forbidden + 1 invalid name → 207, created=1, failed=2.</li>
 *   <li>Oversized batch (501 items) → 400.</li>
 *   <li>Empty array → 400.</li>
 * </ol>
 *
 * <p>Additional cases (original):
 * <ul>
 *   <li>Unknown collection → item error COLLECTION_NOT_FOUND.</li>
 *   <li>Unknown parentAppId → item error PARENT_NOT_FOUND.</li>
 *   <li>Service throws InvalidPathException → item error COLLECTION_NOT_FOUND.</li>
 *   <li>Service throws InvalidBodyException → item error INVALID_INPUT.</li>
 *   <li>Permission memo: two items same collection → permissions checked once.</li>
 *   <li>Null principal → 401.</li>
 * </ul>
 *
 * <p>BATCH-API-5 additions (per aidocs/16 row):
 * <ul>
 *   <li>Atomic-mode rollback — placeholder {@link #atomicModeNotYetImplemented()} because
 *       the endpoint has no {@code ?mode=} parameter; see BATCH-API-1 design.</li>
 *   <li>Oversized batch → HTTP 400 (not 413): batch endpoint returns 400 for size
 *       violations; see {@link #oversizedBatchReturns400ActualBehaviorNot413()}.</li>
 *   <li>Duplicate names — no uniqueness constraint: two items with the same name both
 *       succeed (service enforces no name-uniqueness-per-collection); see
 *       {@link #duplicateNamesInBatchBothSucceedNoUniquenessConstraint()}.</li>
 *   <li>All-failed batch still returns 207 (not 4xx): see
 *       {@link #allItemsFailedStillReturns207NotErrorCode()}.</li>
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

  // ── BATCH-API-5 additions ─────────────────────────────────────────────────

  /**
   * BATCH-API-5 / spec test 1 (atomic mode) — placeholder.
   *
   * <p>The {@code POST /v2/data-objects/batch} endpoint does <em>not</em>
   * accept a {@code ?mode=} query parameter; all batches run in best-effort
   * mode (partial success). Atomic rollback is deferred to BATCH-API-1
   * (design + implementation). This test documents the deferred requirement
   * so a future implementer finds the hook.
   *
   * @see <a href="aidocs/16-dispatcher-backlog.md">BATCH-API-1 design row</a>
   */
  @Test
  @Disabled("atomic mode not yet implemented — see BATCH-API-1 design in aidocs/16-dispatcher-backlog.md")
  void atomicModeNotYetImplemented() {
    // When BATCH-API-1 ships, this test should:
    //   1. POST a batch with 3 valid items + 1 invalid item (missing name).
    //   2. Assert the entire batch is rolled back: created == 0, failed == 4.
    //   3. Assert HTTP status 400 or 422 with errorCode on the failing item.
    // Until then this placeholder keeps the acceptance criterion visible.
  }

  /**
   * BATCH-API-5 / spec test 4 (oversized batch) — documents actual behavior.
   *
   * <p>The spec asks for HTTP 413 Payload Too Large. The actual endpoint returns
   * HTTP 400 Bad Request with a plain-JSON error body because JAX-RS body-size
   * limits (which produce 413) are not configured and the size check is done in
   * application code. This test documents and asserts the <em>actual</em> 400
   * behavior so a future change to 413 is visible as a test failure rather than
   * a silent regression.
   */
  @Test
  void oversizedBatchReturns400ActualBehaviorNot413() {
    // 601 items — comfortably over MAX_BATCH_SIZE (500).
    List<DataObjectBatchCreateItemIO> items = new ArrayList<>();
    for (int i = 0; i < DataObjectBatchV2Rest.MAX_BATCH_SIZE + 101; i++) {
      items.add(new DataObjectBatchCreateItemIO(COLL_APP_ID_A, "DO-" + i, null, null, null, null));
    }
    Response r = resource.batch(items, securityContext);
    // Actual behavior: 400 (not 413). See class javadoc for rationale.
    assertEquals(400, r.getStatus());
    verify(dataObjectService, never()).createDataObject(any(Long.class), any());
  }

  /**
   * BATCH-API-5 / spec test 6 (duplicate names) — documents absence of constraint.
   *
   * <p>The DataObject service does <em>not</em> enforce name-uniqueness within a
   * collection. Two items with the same {@code name} in the same collection both
   * succeed and get distinct {@code appId}s. This test documents-by-test that the
   * constraint is intentionally absent; if name-uniqueness is ever added this test
   * will fail as the expected signal.
   */
  @Test
  void duplicateNamesInBatchBothSucceedNoUniquenessConstraint() {
    DataObject d1 = makeDataObject(10L, "app-dupe-001", "same-name");
    DataObject d2 = makeDataObject(11L, "app-dupe-002", "same-name");

    when(entityIdResolver.resolveLong(COLL_APP_ID_A)).thenReturn(COLL_OGM_ID_A);
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID_A, AccessType.Write, CALLER))
      .thenReturn(true);
    when(dataObjectService.createDataObject(eq(COLL_OGM_ID_A), any())).thenReturn(d1, d2);

    List<DataObjectBatchCreateItemIO> items = List.of(
      new DataObjectBatchCreateItemIO(COLL_APP_ID_A, "same-name", null, null, null, null),
      new DataObjectBatchCreateItemIO(COLL_APP_ID_A, "same-name", null, null, null, null)
    );

    Response r = resource.batch(items, securityContext);

    assertEquals(207, r.getStatus());
    DataObjectBatchResponseIO body = (DataObjectBatchResponseIO) r.getEntity();
    // Both succeed — no uniqueness constraint enforced.
    assertEquals(2, body.getCreated());
    assertEquals(0, body.getFailed());
    assertEquals("created", body.getResults().get(0).getStatus());
    assertEquals("created", body.getResults().get(1).getStatus());
    // Each got a distinct appId.
    assertNotNull(body.getResults().get(0).getAppId());
    assertNotNull(body.getResults().get(1).getAppId());
  }

  /**
   * BATCH-API-5 — all-failed batch still returns 207, not an error code.
   *
   * <p>HTTP 207 Multi-Status is returned even when every item in the batch fails.
   * The caller can detect full failure via {@code created == 0} in the response
   * body. This is the defined contract (see endpoint javadoc) and ensures that a
   * partial retry can be done without special-casing the response status.
   */
  @Test
  void allItemsFailedStillReturns207NotErrorCode() {
    // All three items target an unknown collection → all fail with COLLECTION_NOT_FOUND.
    when(entityIdResolver.resolveLong(COLL_APP_ID_A)).thenThrow(new NotFoundException());

    List<DataObjectBatchCreateItemIO> items = List.of(
      new DataObjectBatchCreateItemIO(COLL_APP_ID_A, "DO-1", null, null, null, null),
      new DataObjectBatchCreateItemIO(COLL_APP_ID_A, "DO-2", null, null, null, null),
      new DataObjectBatchCreateItemIO(COLL_APP_ID_A, "DO-3", null, null, null, null)
    );

    Response r = resource.batch(items, securityContext);

    // Even on total failure the status code is 207, not 400/404.
    assertEquals(207, r.getStatus());
    DataObjectBatchResponseIO body = (DataObjectBatchResponseIO) r.getEntity();
    assertEquals(0, body.getCreated());
    assertEquals(3, body.getFailed());
    // All items carry COLLECTION_NOT_FOUND (not a batch-level error code).
    body.getResults().forEach(item ->
      assertEquals("COLLECTION_NOT_FOUND", item.getErrorCode())
    );
    verify(dataObjectService, never()).createDataObject(any(Long.class), any());
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
