package de.dlr.shepard.v2.importer.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.daos.CollectionPropertiesDAO;
import de.dlr.shepard.v2.importer.daos.ImportPlanDAO;
import de.dlr.shepard.v2.importer.entities.ImportLock;
import de.dlr.shepard.v2.importer.entities.ImportPlan;
import de.dlr.shepard.v2.importer.io.ImportJobRequestIO;
import de.dlr.shepard.v2.importer.io.ImportJobResultIO;
import de.dlr.shepard.v2.importer.io.ImportJobResultIO.CreatedEntityIO;
import de.dlr.shepard.v2.importer.services.ImportExecutionService;
import de.dlr.shepard.v2.importer.services.ImportLockService;
import de.dlr.shepard.v2.importer.services.ImportValidationService;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * IMP2 — unit tests for {@link ImportJobsV2Rest}.
 *
 * <p>Mock-based; no Quarkus boot.  Follows the same pattern as
 * {@link ImportV2RestTest}.
 */
class ImportJobsV2RestTest {

  static final String COLL_APP_ID = "019e3c96-1234-7000-a000-000000000010";
  static final long COLL_OGM_ID = 42L;
  static final String CALLER = "alice";
  static final String COMMIT_ID = "sha256:abc123def456";

  /**
   * A raw fingerprint that produces a deterministic SHA-256 hex we can use in
   * tests.  The resource's {@code sha256hex()} is package-private so we can
   * call it directly.
   */
  static final String RAW_FINGERPRINT = "3|1716218461000";
  static final String FINGERPRINT = ImportJobsV2Rest.sha256hex(RAW_FINGERPRINT);

  @Mock
  ImportPlanDAO importPlanDAO;
  @Mock
  ImportValidationService validationService;
  @Mock
  ImportExecutionService executionService;
  @Mock
  ImportLockService lockService;
  @Mock
  PermissionsService permissionsService;
  @Mock
  CollectionPropertiesDAO collectionPropertiesDAO;
  @Mock
  SecurityContext sc;
  @Mock
  Principal principal;

  ImportJobsV2Rest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new ImportJobsV2Rest();
    resource.importPlanDAO = importPlanDAO;
    resource.validationService = validationService;
    resource.executionService = executionService;
    resource.lockService = lockService;
    resource.permissionsService = permissionsService;
    resource.collectionPropertiesDAO = collectionPropertiesDAO;

    when(sc.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);

    // Default: collection exists, caller has Write, fingerprint unchanged.
    when(collectionPropertiesDAO.findCollectionIdByAppId(COLL_APP_ID))
      .thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Write), eq(CALLER)))
      .thenReturn(true);
    when(importPlanDAO.getRawCollectionFingerprintInput(COLL_APP_ID))
      .thenReturn(RAW_FINGERPRINT);
  }

  // ─── Happy path: 201 COMPLETED ────────────────────────────────────────────

  @Test
  void happyPath_returns201WithCreatedEntities() {
    ImportPlan plan = makeValidPlan();
    ImportLock lock = makeLock("lock-1");
    ImportJobResultIO jobResult = new ImportJobResultIO(
      "job-app-id-1",
      COMMIT_ID,
      "COMPLETED",
      List.of(new CreatedEntityIO("do-1", "app-do-1", "DataObject")),
      List.of(new CreatedEntityIO("c-1", "app-c-1", "TimeseriesContainer")),
      List.of()
    );

    when(importPlanDAO.findByCommitId(COMMIT_ID)).thenReturn(plan);
    when(lockService.acquire(COLL_APP_ID, CALLER)).thenReturn(lock);
    when(executionService.execute(plan, COLL_OGM_ID)).thenReturn(jobResult);

    Response r = resource.executeImport(new ImportJobRequestIO(COMMIT_ID), sc);

    assertEquals(201, r.getStatus());
    ImportJobResultIO body = (ImportJobResultIO) r.getEntity();
    assertNotNull(body);
    assertEquals("COMPLETED", body.status());
    assertEquals(1, body.dataObjects().size());
    assertEquals("do-1", body.dataObjects().get(0).localRef());
    assertEquals("app-do-1", body.dataObjects().get(0).appId());
    assertTrue(body.errors().isEmpty());

    // Plan must be marked USED.
    verify(importPlanDAO).createOrUpdate(any(ImportPlan.class));
    // Lock must be released.
    verify(lockService).release("lock-1");
  }

  // ─── Partial failure: 207 ─────────────────────────────────────────────────

  @Test
  void partialFailure_returns207WithErrors() {
    ImportPlan plan = makeValidPlan();
    ImportLock lock = makeLock("lock-2");
    ImportJobResultIO jobResult = new ImportJobResultIO(
      "job-app-id-2",
      COMMIT_ID,
      "PARTIAL_FAILURE",
      List.of(
        new CreatedEntityIO("do-1", "app-do-1", "DataObject"),
        new CreatedEntityIO("do-2", null, "DataObject")
      ),
      List.of(),
      List.of("Failed to create DataObject 'do-2': name required")
    );

    when(importPlanDAO.findByCommitId(COMMIT_ID)).thenReturn(plan);
    when(lockService.acquire(COLL_APP_ID, CALLER)).thenReturn(lock);
    when(executionService.execute(plan, COLL_OGM_ID)).thenReturn(jobResult);

    Response r = resource.executeImport(new ImportJobRequestIO(COMMIT_ID), sc);

    assertEquals(207, r.getStatus());
    ImportJobResultIO body = (ImportJobResultIO) r.getEntity();
    assertEquals("PARTIAL_FAILURE", body.status());
    assertFalse(body.errors().isEmpty());
  }

  // ─── commitId not found: 404 ──────────────────────────────────────────────

  @Test
  void commitIdNotFound_returns404() {
    when(importPlanDAO.findByCommitId(COMMIT_ID)).thenReturn(null);

    Response r = resource.executeImport(new ImportJobRequestIO(COMMIT_ID), sc);

    assertEquals(404, r.getStatus());
    verify(executionService, never()).execute(any(), anyLong());
  }

  // ─── Plan already USED: 409 ───────────────────────────────────────────────

  @Test
  void planAlreadyUsed_returns409() {
    ImportPlan plan = makeValidPlan();
    plan.setStatus("USED");
    when(importPlanDAO.findByCommitId(COMMIT_ID)).thenReturn(plan);

    Response r = resource.executeImport(new ImportJobRequestIO(COMMIT_ID), sc);

    assertEquals(409, r.getStatus());
    verify(executionService, never()).execute(any(), anyLong());
    verify(lockService, never()).acquire(anyString(), anyString());
  }

  // ─── Plan expired (status EXPIRED): 410 ──────────────────────────────────

  @Test
  void planExpiredByStatus_returns410() {
    ImportPlan plan = makeValidPlan();
    plan.setStatus("EXPIRED");
    when(importPlanDAO.findByCommitId(COMMIT_ID)).thenReturn(plan);

    Response r = resource.executeImport(new ImportJobRequestIO(COMMIT_ID), sc);

    assertEquals(410, r.getStatus());
    verify(executionService, never()).execute(any(), anyLong());
  }

  // ─── Plan expired (expiresAt in past): 410 ────────────────────────────────

  @Test
  void planExpiredByTime_returns410() {
    ImportPlan plan = makeValidPlan();
    // Set expiresAt 24 h in the past.
    plan.setExpiresAt(System.currentTimeMillis() - 86_400_001L);
    when(importPlanDAO.findByCommitId(COMMIT_ID)).thenReturn(plan);

    Response r = resource.executeImport(new ImportJobRequestIO(COMMIT_ID), sc);

    assertEquals(410, r.getStatus());
    verify(executionService, never()).execute(any(), anyLong());
  }

  // ─── Pre-IMP2 plan (null manifestJson): 410 ──────────────────────────────

  @Test
  void preImp2PlanNoManifestJson_returns410() {
    ImportPlan plan = makeValidPlan();
    plan.setManifestJson(null);
    when(importPlanDAO.findByCommitId(COMMIT_ID)).thenReturn(plan);

    Response r = resource.executeImport(new ImportJobRequestIO(COMMIT_ID), sc);

    assertEquals(410, r.getStatus());
    verify(executionService, never()).execute(any(), anyLong());
  }

  // ─── Plan INVALIDATED: 422 ────────────────────────────────────────────────

  @Test
  void planInvalidated_returns422() {
    ImportPlan plan = makeValidPlan();
    plan.setStatus("INVALIDATED");
    when(importPlanDAO.findByCommitId(COMMIT_ID)).thenReturn(plan);

    Response r = resource.executeImport(new ImportJobRequestIO(COMMIT_ID), sc);

    assertEquals(422, r.getStatus());
    verify(executionService, never()).execute(any(), anyLong());
  }

  // ─── Fingerprint mismatch: 409 ────────────────────────────────────────────

  @Test
  void fingerprintMismatch_returns409() {
    ImportPlan plan = makeValidPlan();
    // Override fingerprint to something that won't match the default RAW_FINGERPRINT.
    plan.setCollectionFingerprint("sha256:completely-different-fingerprint");
    when(importPlanDAO.findByCommitId(COMMIT_ID)).thenReturn(plan);
    // getRawCollectionFingerprintInput returns RAW_FINGERPRINT (set in setUp).

    Response r = resource.executeImport(new ImportJobRequestIO(COMMIT_ID), sc);

    assertEquals(409, r.getStatus());
    verify(executionService, never()).execute(any(), anyLong());
    verify(lockService, never()).acquire(anyString(), anyString());
  }

  // ─── Import lock conflict: 409 ────────────────────────────────────────────

  @Test
  void importLockConflict_returns409() {
    ImportPlan plan = makeValidPlan();
    when(importPlanDAO.findByCommitId(COMMIT_ID)).thenReturn(plan);
    // acquire() returns null → fresh lock conflict.
    when(lockService.acquire(COLL_APP_ID, CALLER)).thenReturn(null);

    Response r = resource.executeImport(new ImportJobRequestIO(COMMIT_ID), sc);

    assertEquals(409, r.getStatus());
    verify(executionService, never()).execute(any(), anyLong());
  }

  // ─── Unauthenticated: 401 ─────────────────────────────────────────────────

  @Test
  void unauthenticated_returns401() {
    when(sc.getUserPrincipal()).thenReturn(null);

    Response r = resource.executeImport(new ImportJobRequestIO(COMMIT_ID), sc);

    assertEquals(401, r.getStatus());
    verify(importPlanDAO, never()).findByCommitId(anyString());
  }

  // ─── Forbidden (no Write on collection): 403 ─────────────────────────────

  @Test
  void forbidden_returns403() {
    ImportPlan plan = makeValidPlan();
    when(importPlanDAO.findByCommitId(COMMIT_ID)).thenReturn(plan);
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Write), eq(CALLER)))
      .thenReturn(false);

    Response r = resource.executeImport(new ImportJobRequestIO(COMMIT_ID), sc);

    assertEquals(403, r.getStatus());
    verify(executionService, never()).execute(any(), anyLong());
    verify(lockService, never()).acquire(anyString(), anyString());
  }

  // ─── Factories ────────────────────────────────────────────────────────────

  /** A minimal plan in VALID status with all fields required by the pre-flight checks. */
  private ImportPlan makeValidPlan() {
    ImportPlan plan = new ImportPlan(1L);
    plan.setAppId("app-plan-1");
    plan.setCommitId(COMMIT_ID);
    plan.setStatus("VALID");
    plan.setCollectionAppId(COLL_APP_ID);
    plan.setValidatedBy(CALLER);
    plan.setValidatedAt(System.currentTimeMillis());
    plan.setExpiresAt(System.currentTimeMillis() + 86_400_000L);
    plan.setCollectionFingerprint(FINGERPRINT);
    plan.setManifestJson("{\"collectionAppId\":\"" + COLL_APP_ID + "\",\"dataObjects\":[],\"containers\":null,\"references\":null,\"agentContext\":null}");
    return plan;
  }

  private ImportLock makeLock(String lockId) {
    ImportLock lock = new ImportLock();
    lock.setLockId(lockId);
    lock.setStatus("RUNNING");
    lock.setStartedBy(CALLER);
    lock.setTargetCollectionAppId(COLL_APP_ID);
    lock.setStartedAt(System.currentTimeMillis());
    return lock;
  }
}
