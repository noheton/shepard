package de.dlr.shepard.v2.importer.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.daos.CollectionPropertiesDAO;
import de.dlr.shepard.v2.importer.daos.ImportPlanDAO;
import de.dlr.shepard.v2.importer.entities.ImportPlan;
import de.dlr.shepard.v2.importer.io.ImportContextIO;
import de.dlr.shepard.v2.importer.io.ImportManifestIO;
import de.dlr.shepard.v2.importer.io.ImportManifestIO.ManifestDataObjectIO;
import de.dlr.shepard.v2.importer.io.ImportPlanIO;
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
 * IMP1 — unit tests for {@link ImportV2Rest}.
 *
 * <p>Mock-based, no Quarkus boot. Follows the same six-shape grid used by
 * {@code CollectionV2RestTest} and {@code CollectionWatchersRestTest}.
 */
class ImportV2RestTest {

  static final String COLL_APP_ID = "019e3c96-1234-7000-a000-000000000010";
  static final long COLL_OGM_ID = 42L;
  static final String CALLER = "alice";
  static final String COMMIT_ID = "sha256:abc123def456";

  @Mock
  ImportValidationService validationService;

  @Mock
  ImportPlanDAO importPlanDAO;

  @Mock
  PermissionsService permissionsService;

  @Mock
  CollectionPropertiesDAO collectionPropertiesDAO;

  @Mock
  SecurityContext sc;

  @Mock
  Principal principal;

  ImportV2Rest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new ImportV2Rest();
    resource.validationService = validationService;
    resource.importPlanDAO = importPlanDAO;
    resource.permissionsService = permissionsService;
    resource.collectionPropertiesDAO = collectionPropertiesDAO;

    when(sc.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);

    // Default: collection exists and caller has Write
    when(collectionPropertiesDAO.findCollectionIdByAppId(COLL_APP_ID))
      .thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Write), eq(CALLER)))
      .thenReturn(true);
  }

  // ─── POST /validate — happy path ──────────────────────────────────────────

  @Test
  void validateHappyPath_returns200WithCommitId() {
    ImportManifestIO manifest = makeMinimalManifest();
    ImportPlan plan = makeValidPlan();

    when(validationService.validate(manifest, CALLER)).thenReturn(plan);
    when(validationService.extractErrors(plan)).thenReturn(List.of());
    when(validationService.extractWarnings(plan)).thenReturn(List.of());
    when(validationService.extractSummary(plan)).thenReturn(
      new ImportPlanIO.ImportSummaryIO(1, 0, 0, 0));

    Response r = resource.validate(manifest, sc);

    assertEquals(200, r.getStatus());
    ImportPlanIO io = (ImportPlanIO) r.getEntity();
    assertNotNull(io);
    assertEquals(COMMIT_ID, io.commitId());
    assertEquals("VALID", io.status());
    assertTrue(io.errors().isEmpty());
  }

  // ─── POST /validate — collection not found ────────────────────────────────

  @Test
  void validateCollectionNotFound_returns404() {
    ImportManifestIO manifest = makeMinimalManifest();
    when(collectionPropertiesDAO.findCollectionIdByAppId(COLL_APP_ID))
      .thenReturn(Optional.empty());

    Response r = resource.validate(manifest, sc);

    assertEquals(404, r.getStatus());
    verify(validationService, never()).validate(any(), any());
  }

  // ─── POST /validate — forbidden ───────────────────────────────────────────

  @Test
  void validateForbidden_returns403() {
    ImportManifestIO manifest = makeMinimalManifest();
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Write), eq(CALLER)))
      .thenReturn(false);

    Response r = resource.validate(manifest, sc);

    assertEquals(403, r.getStatus());
    verify(validationService, never()).validate(any(), any());
  }

  // ─── POST /validate — unauthenticated ────────────────────────────────────

  @Test
  void validateUnauthenticated_returns401() {
    when(sc.getUserPrincipal()).thenReturn(null);
    Response r = resource.validate(makeMinimalManifest(), sc);
    assertEquals(401, r.getStatus());
  }

  // ─── POST /validate — hard errors → 422 ──────────────────────────────────

  @Test
  void validateDuplicateLocalRef_returns422() {
    ImportManifestIO manifest = makeMinimalManifest();
    ImportPlan invalidPlan = makeInvalidPlan("Duplicate dataObject localRef: do-1");

    when(validationService.validate(manifest, CALLER)).thenReturn(invalidPlan);
    when(validationService.extractErrors(invalidPlan))
      .thenReturn(List.of("Duplicate dataObject localRef: do-1"));
    when(validationService.extractWarnings(invalidPlan)).thenReturn(List.of());
    when(validationService.extractSummary(invalidPlan)).thenReturn(
      new ImportPlanIO.ImportSummaryIO(0, 0, 0, 0));

    Response r = resource.validate(manifest, sc);

    assertEquals(422, r.getStatus());
    ImportPlanIO io = (ImportPlanIO) r.getEntity();
    assertNull(io.commitId());
    assertEquals(1, io.errors().size());
    assertTrue(io.errors().get(0).contains("Duplicate"));
  }

  @Test
  void validateInvalidStatus_returns422() {
    ImportManifestIO manifest = makeManifestWithStatus("BOGUS");
    ImportPlan invalidPlan = makeInvalidPlan("Invalid status 'BOGUS' on dataObject 'do-1'");

    when(validationService.validate(manifest, CALLER)).thenReturn(invalidPlan);
    when(validationService.extractErrors(invalidPlan))
      .thenReturn(List.of("Invalid status 'BOGUS' on dataObject 'do-1'"));
    when(validationService.extractWarnings(invalidPlan)).thenReturn(List.of());
    when(validationService.extractSummary(invalidPlan)).thenReturn(
      new ImportPlanIO.ImportSummaryIO(0, 0, 0, 0));

    Response r = resource.validate(manifest, sc);

    assertEquals(422, r.getStatus());
    ImportPlanIO io = (ImportPlanIO) r.getEntity();
    assertNull(io.commitId());
    assertTrue(io.errors().get(0).contains("BOGUS"));
  }

  @Test
  void validateParentRefNotInManifest_returns422() {
    ImportManifestIO manifest = makeManifestWithParentRef("do-missing");
    ImportPlan invalidPlan = makeInvalidPlan(
      "dataObject 'do-1' parentRef 'do-missing' not in manifest");

    when(validationService.validate(manifest, CALLER)).thenReturn(invalidPlan);
    when(validationService.extractErrors(invalidPlan))
      .thenReturn(List.of("dataObject 'do-1' parentRef 'do-missing' not in manifest"));
    when(validationService.extractWarnings(invalidPlan)).thenReturn(List.of());
    when(validationService.extractSummary(invalidPlan)).thenReturn(
      new ImportPlanIO.ImportSummaryIO(0, 0, 0, 0));

    Response r = resource.validate(manifest, sc);

    assertEquals(422, r.getStatus());
    ImportPlanIO io = (ImportPlanIO) r.getEntity();
    assertNull(io.commitId());
    assertTrue(io.errors().get(0).contains("parentRef"));
  }

  // ─── POST /validate — name conflict → 200 with warning ───────────────────

  @Test
  void validateNameConflict_returns200WithWarning() {
    ImportManifestIO manifest = makeMinimalManifest();
    ImportPlan plan = makeValidPlan();

    when(validationService.validate(manifest, CALLER)).thenReturn(plan);
    when(validationService.extractErrors(plan)).thenReturn(List.of());
    when(validationService.extractWarnings(plan))
      .thenReturn(List.of("DataObject name already exists in collection: 'my-object'"));
    when(validationService.extractSummary(plan)).thenReturn(
      new ImportPlanIO.ImportSummaryIO(0, 0, 0, 1));

    Response r = resource.validate(manifest, sc);

    assertEquals(200, r.getStatus());
    ImportPlanIO io = (ImportPlanIO) r.getEntity();
    assertEquals(COMMIT_ID, io.commitId());
    assertEquals(1, io.warnings().size());
    assertTrue(io.warnings().get(0).contains("already exists"));
    assertEquals(0, io.errors().size());
    assertEquals(1, io.summary().wouldSkipDataObjects());
  }

  // ─── GET /plans/{commitId} ────────────────────────────────────────────────

  @Test
  void getPlanFound_returns200() {
    ImportPlan plan = makeValidPlan();
    when(importPlanDAO.findByCommitId(COMMIT_ID)).thenReturn(plan);
    when(validationService.extractErrors(plan)).thenReturn(List.of());
    when(validationService.extractWarnings(plan)).thenReturn(List.of());
    when(validationService.extractSummary(plan)).thenReturn(
      new ImportPlanIO.ImportSummaryIO(1, 0, 0, 0));

    Response r = resource.getPlan(COMMIT_ID, sc);

    assertEquals(200, r.getStatus());
    ImportPlanIO io = (ImportPlanIO) r.getEntity();
    assertEquals(COMMIT_ID, io.commitId());
  }

  @Test
  void getPlanNotFound_returns404() {
    when(importPlanDAO.findByCommitId(COMMIT_ID)).thenReturn(null);
    Response r = resource.getPlan(COMMIT_ID, sc);
    assertEquals(404, r.getStatus());
  }

  @Test
  void getPlanUnauthenticated_returns401() {
    when(sc.getUserPrincipal()).thenReturn(null);
    Response r = resource.getPlan(COMMIT_ID, sc);
    assertEquals(401, r.getStatus());
  }

  // ─── GET /context ─────────────────────────────────────────────────────────

  @Test
  void testGetContext_withoutSemanticGraph() {
    // Collection exists and caller has Read permission.
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID, AccessType.Read, CALLER))
      .thenReturn(true);
    when(importPlanDAO.countDataObjects(COLL_APP_ID)).thenReturn(47L);
    when(importPlanDAO.getRawCollectionFingerprintInput(COLL_APP_ID)).thenReturn("47|1716218461000");

    Response r = resource.getContext(COLL_APP_ID, false, sc);

    assertEquals(200, r.getStatus());
    ImportContextIO body = (ImportContextIO) r.getEntity();
    assertNotNull(body);
    assertEquals(COLL_APP_ID, body.collectionAppId());
    assertEquals(47L, body.dataObjectCount());
    assertNotNull(body.collectionFingerprint());
    assertTrue(
      body.collectionFingerprint().startsWith("sha256:"),
      "fingerprint must carry sha256: prefix"
    );
    // semanticGraph must be absent (null) when includeSemanticGraph=false.
    assertNull(body.semanticGraph(), "semanticGraph must be null when not requested");
  }

  @Test
  void testGetContext_withSemanticGraph() {
    // Collection exists and caller has Read permission.
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID, AccessType.Read, CALLER))
      .thenReturn(true);
    when(importPlanDAO.countDataObjects(COLL_APP_ID)).thenReturn(3L);
    when(importPlanDAO.getRawCollectionFingerprintInput(COLL_APP_ID)).thenReturn("3|1716218461000");

    Response r = resource.getContext(COLL_APP_ID, true, sc);

    assertEquals(200, r.getStatus());
    ImportContextIO body = (ImportContextIO) r.getEntity();
    assertNotNull(body);
    assertEquals(COLL_APP_ID, body.collectionAppId());
    assertEquals(3L, body.dataObjectCount());
    // semanticGraph must be present (non-null) when requested.
    assertNotNull(body.semanticGraph(),
      "semanticGraph must be present when includeSemanticGraph=true");
    // Annotation list may be empty (collection-scoped DAO not yet wired — see TODO).
    assertNotNull(body.semanticGraph().annotations());
  }

  @Test
  void getContext_unauthenticated_returns401() {
    when(sc.getUserPrincipal()).thenReturn(null);
    Response r = resource.getContext(COLL_APP_ID, false, sc);
    assertEquals(401, r.getStatus());
  }

  @Test
  void getContext_missingCollectionAppId_returns400() {
    Response r = resource.getContext(null, false, sc);
    assertEquals(400, r.getStatus());
  }

  @Test
  void getContext_blankCollectionAppId_returns400() {
    Response r = resource.getContext("   ", false, sc);
    assertEquals(400, r.getStatus());
  }

  @Test
  void getContext_collectionNotFound_returns404() {
    when(collectionPropertiesDAO.findCollectionIdByAppId("no-such-id"))
      .thenReturn(Optional.empty());
    Response r = resource.getContext("no-such-id", false, sc);
    assertEquals(404, r.getStatus());
  }

  @Test
  void getContext_forbidden_returns403() {
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(false);
    Response r = resource.getContext(COLL_APP_ID, false, sc);
    assertEquals(403, r.getStatus());
  }

  // ─── Factories ────────────────────────────────────────────────────────────

  private ImportManifestIO makeMinimalManifest() {
    return new ImportManifestIO(
      COLL_APP_ID,
      List.of(new ManifestDataObjectIO("do-1", "my-object", null, null, null, null, null)),
      null,
      null,
      null
    );
  }

  private ImportManifestIO makeManifestWithStatus(String status) {
    return new ImportManifestIO(
      COLL_APP_ID,
      List.of(new ManifestDataObjectIO("do-1", "my-object", null, status, null, null, null)),
      null,
      null,
      null
    );
  }

  private ImportManifestIO makeManifestWithParentRef(String parentRef) {
    return new ImportManifestIO(
      COLL_APP_ID,
      List.of(new ManifestDataObjectIO("do-1", "my-object", null, null, null, parentRef, null)),
      null,
      null,
      null
    );
  }

  private ImportPlan makeValidPlan() {
    ImportPlan plan = new ImportPlan(1L);
    plan.setAppId("app-plan-1");
    plan.setCommitId(COMMIT_ID);
    plan.setStatus("VALID");
    plan.setCollectionAppId(COLL_APP_ID);
    plan.setValidatedBy(CALLER);
    plan.setValidatedAt(System.currentTimeMillis());
    plan.setExpiresAt(System.currentTimeMillis() + 86_400_000L);
    plan.setSummaryJson("{\"wouldCreateDataObjects\":1,\"wouldCreateContainers\":0,\"wouldCreateReferences\":0,\"wouldSkipDataObjects\":0}");
    plan.setWarningsJson("[]");
    return plan;
  }

  private ImportPlan makeInvalidPlan(String error) {
    ImportPlan plan = new ImportPlan();
    plan.setStatus("INVALIDATED");
    plan.setCollectionAppId(COLL_APP_ID);
    plan.setValidatedBy(CALLER);
    plan.setManifestHash("ERRORS:" + error);
    plan.setSummaryJson("{\"wouldCreateDataObjects\":0,\"wouldCreateContainers\":0,\"wouldCreateReferences\":0,\"wouldSkipDataObjects\":0}");
    plan.setWarningsJson("[]");
    return plan;
  }
}
