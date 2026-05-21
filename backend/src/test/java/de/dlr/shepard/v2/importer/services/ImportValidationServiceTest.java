package de.dlr.shepard.v2.importer.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.context.collection.daos.CollectionPropertiesDAO;
import de.dlr.shepard.v2.importer.daos.ImportPlanDAO;
import de.dlr.shepard.v2.importer.entities.ImportPlan;
import de.dlr.shepard.v2.importer.io.ImportManifestIO;
import de.dlr.shepard.v2.importer.io.ImportManifestIO.ManifestContainerIO;
import de.dlr.shepard.v2.importer.io.ImportManifestIO.ManifestDataObjectIO;
import de.dlr.shepard.v2.importer.io.ImportManifestIO.ManifestReferenceIO;
import de.dlr.shepard.v2.importer.io.ImportPlanIO.ImportSummaryIO;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * IMP1 — unit tests for {@link ImportValidationService}.
 *
 * <p>All Neo4j I/O is mocked; tests exercise the validation logic and
 * commitId / fingerprint determinism directly.
 */
class ImportValidationServiceTest {

  static final String COLL_APP_ID = "019e3c96-5678-7000-a000-000000000020";
  static final long COLL_OGM_ID = 99L;
  static final String ALICE = "alice";

  @Mock
  ImportPlanDAO importPlanDAO;

  @Mock
  CollectionPropertiesDAO collectionPropertiesDAO;

  ImportValidationService service;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new ImportValidationService();
    service.importPlanDAO = importPlanDAO;
    service.collectionPropertiesDAO = collectionPropertiesDAO;
    service.objectMapper = new ObjectMapper();

    // Default: collection found
    when(collectionPropertiesDAO.findCollectionIdByAppId(COLL_APP_ID))
      .thenReturn(Optional.of(COLL_OGM_ID));

    // Default: no name conflicts
    when(importPlanDAO.findExistingNames(eq(COLL_APP_ID), any()))
      .thenReturn(List.of());

    // Default: fingerprint
    when(importPlanDAO.getRawCollectionFingerprintInput(COLL_APP_ID))
      .thenReturn("0|0");

    // Default: no existing plan with same commitId
    when(importPlanDAO.findByCommitId(anyString()))
      .thenReturn(null);

    // Default: save returns entity
    when(importPlanDAO.createOrUpdate(any(ImportPlan.class)))
      .thenAnswer(inv -> {
        ImportPlan p = inv.getArgument(0);
        if (p.getAppId() == null) p.setAppId("generated-app-id");
        return p;
      });
  }

  // ─── Happy path ───────────────────────────────────────────────────────────

  @Test
  void happyPath_savesValidPlan() {
    ImportManifestIO manifest = singleDoManifest("do-1", "Object A", null);

    ImportPlan plan = service.validate(manifest, ALICE);

    assertEquals("VALID", plan.getStatus());
    assertNotNull(plan.getCommitId());
    assertTrue(plan.getCommitId().startsWith("sha256:"));
    verify(importPlanDAO).createOrUpdate(plan);
  }

  @Test
  void happyPath_commitIdIsNonNull() {
    ImportPlan plan = service.validate(singleDoManifest("do-1", "A", null), ALICE);
    assertNotNull(plan.getCommitId());
  }

  @Test
  void happyPath_expiresAt24hAfterValidatedAt() {
    ImportPlan plan = service.validate(singleDoManifest("do-1", "A", null), ALICE);
    assertNotNull(plan.getValidatedAt());
    assertNotNull(plan.getExpiresAt());
    assertEquals(86_400_000L, plan.getExpiresAt() - plan.getValidatedAt());
  }

  // ─── Collection not found ─────────────────────────────────────────────────

  @Test
  void collectionNotFound_returnsInvalidatedPlanWithError() {
    when(collectionPropertiesDAO.findCollectionIdByAppId(COLL_APP_ID))
      .thenReturn(Optional.empty());

    ImportPlan plan = service.validate(singleDoManifest("do-1", "A", null), ALICE);

    assertEquals("INVALIDATED", plan.getStatus());
    List<String> errors = service.extractErrors(plan);
    assertFalse(errors.isEmpty());
    assertTrue(errors.get(0).contains("Collection not found"));
    verify(importPlanDAO, never()).createOrUpdate(any());
  }

  // ─── Status validation ────────────────────────────────────────────────────

  @Test
  void invalidStatus_returnsInvalidatedPlan() {
    ImportManifestIO manifest = singleDoManifest("do-1", "A", "NOT_A_STATUS");

    ImportPlan plan = service.validate(manifest, ALICE);

    assertEquals("INVALIDATED", plan.getStatus());
    List<String> errors = service.extractErrors(plan);
    assertTrue(errors.stream().anyMatch(e -> e.contains("Invalid status")));
  }

  @Test
  void validStatuses_areAccepted() {
    for (String status : List.of("DRAFT", "IN_REVIEW", "READY", "PUBLISHED", "ARCHIVED")) {
      setUp(); // reset mocks
      ImportPlan plan = service.validate(singleDoManifest("do-1", "A", status), ALICE);
      assertEquals("VALID", plan.getStatus(), "Expected VALID for status=" + status);
    }
  }

  // ─── Duplicate localRef ───────────────────────────────────────────────────

  @Test
  void duplicateLocalRef_returnsInvalidatedPlan() {
    ImportManifestIO manifest = new ImportManifestIO(
      COLL_APP_ID,
      List.of(
        new ManifestDataObjectIO("do-1", "A", null, null, null, null, null),
        new ManifestDataObjectIO("do-1", "B", null, null, null, null, null)  // duplicate
      ),
      null, null, null
    );

    ImportPlan plan = service.validate(manifest, ALICE);

    assertEquals("INVALIDATED", plan.getStatus());
    List<String> errors = service.extractErrors(plan);
    assertTrue(errors.stream().anyMatch(e -> e.contains("Duplicate dataObject localRef")));
  }

  // ─── parentRef not in manifest ────────────────────────────────────────────

  @Test
  void parentRefNotInManifest_returnsInvalidatedPlan() {
    ImportManifestIO manifest = new ImportManifestIO(
      COLL_APP_ID,
      List.of(new ManifestDataObjectIO("do-1", "A", null, null, null, "do-missing", null)),
      null, null, null
    );

    ImportPlan plan = service.validate(manifest, ALICE);

    assertEquals("INVALIDATED", plan.getStatus());
    List<String> errors = service.extractErrors(plan);
    assertTrue(errors.stream().anyMatch(e -> e.contains("parentRef")));
  }

  // ─── predecessorRef not in manifest ──────────────────────────────────────

  @Test
  void predecessorRefNotInManifest_returnsInvalidatedPlan() {
    ImportManifestIO manifest = new ImportManifestIO(
      COLL_APP_ID,
      List.of(new ManifestDataObjectIO("do-1", "A", null, null, null, null, List.of("do-ghost"))),
      null, null, null
    );

    ImportPlan plan = service.validate(manifest, ALICE);

    assertEquals("INVALIDATED", plan.getStatus());
    List<String> errors = service.extractErrors(plan);
    assertTrue(errors.stream().anyMatch(e -> e.contains("predecessorRef")));
  }

  // ─── Invalid container type ───────────────────────────────────────────────

  @Test
  void invalidContainerType_returnsInvalidatedPlan() {
    ImportManifestIO manifest = new ImportManifestIO(
      COLL_APP_ID,
      List.of(new ManifestDataObjectIO("do-1", "A", null, null, null, null, null)),
      List.of(new ManifestContainerIO("c-1", "AUDIO", "my audio")),
      null,
      null
    );

    ImportPlan plan = service.validate(manifest, ALICE);

    assertEquals("INVALIDATED", plan.getStatus());
    List<String> errors = service.extractErrors(plan);
    assertTrue(errors.stream().anyMatch(e -> e.contains("Invalid container type")));
  }

  // ─── Reference pointing at unknown DataObject ─────────────────────────────

  @Test
  void referenceWithUnknownDataObjectRef_returnsInvalidatedPlan() {
    ImportManifestIO manifest = new ImportManifestIO(
      COLL_APP_ID,
      List.of(new ManifestDataObjectIO("do-1", "A", null, null, null, null, null)),
      List.of(new ManifestContainerIO("c-1", "FILE", "my file")),
      List.of(new ManifestReferenceIO("do-ghost", "c-1")),
      null
    );

    ImportPlan plan = service.validate(manifest, ALICE);

    assertEquals("INVALIDATED", plan.getStatus());
    List<String> errors = service.extractErrors(plan);
    assertTrue(errors.stream().anyMatch(e -> e.contains("dataObjectRef")));
  }

  // ─── Name conflict → warning, not error ──────────────────────────────────

  @Test
  void nameConflict_producesWarningNotError() {
    when(importPlanDAO.findExistingNames(eq(COLL_APP_ID), any()))
      .thenReturn(List.of("Object A"));

    ImportPlan plan = service.validate(singleDoManifest("do-1", "Object A", null), ALICE);

    assertEquals("VALID", plan.getStatus());
    assertTrue(service.extractErrors(plan).isEmpty());
    List<String> warnings = service.extractWarnings(plan);
    assertTrue(warnings.stream().anyMatch(w -> w.contains("Object A")));
  }

  // ─── CommitId determinism ─────────────────────────────────────────────────

  @Test
  void commitId_isNonNullAndPrefixed() {
    ImportPlan plan = service.validate(singleDoManifest("do-1", "A", null), ALICE);
    assertTrue(plan.getCommitId().startsWith("sha256:"));
    assertTrue(plan.getCommitId().length() > 10);
  }

  @Test
  void differentUsersSamManifest_produceDifferentCommitIds() {
    // Each validate call hits System.currentTimeMillis() — even identical manifest +
    // caller produces a unique commitId per call.  Verify two distinct callers
    // differ (both differ because username is in the hash input).
    ImportManifestIO manifest = singleDoManifest("do-1", "A", null);

    ImportPlan planAlice = service.validate(manifest, "alice");
    // Reset so second save works cleanly
    when(importPlanDAO.createOrUpdate(any(ImportPlan.class)))
      .thenAnswer(inv -> {
        ImportPlan p = inv.getArgument(0);
        if (p.getAppId() == null) p.setAppId("generated-app-id-2");
        return p;
      });
    ImportPlan planBob = service.validate(manifest, "bob");

    assertNotEquals(planAlice.getCommitId(), planBob.getCommitId());
  }

  // ─── Fingerprint input ────────────────────────────────────────────────────

  @Test
  void fingerprintStoredOnPlan() {
    ImportPlan plan = service.validate(singleDoManifest("do-1", "A", null), ALICE);
    assertNotNull(plan.getCollectionFingerprint());
    assertFalse(plan.getCollectionFingerprint().isEmpty());
  }

  // ─── Summary extraction ───────────────────────────────────────────────────

  @Test
  void extractSummary_countsCorrectly() {
    ImportManifestIO manifest = new ImportManifestIO(
      COLL_APP_ID,
      List.of(
        new ManifestDataObjectIO("do-1", "A", null, null, null, null, null),
        new ManifestDataObjectIO("do-2", "B", null, null, null, null, null)
      ),
      List.of(new ManifestContainerIO("c-1", "FILE", "my file")),
      List.of(new ManifestReferenceIO("do-1", "c-1")),
      null
    );

    ImportPlan plan = service.validate(manifest, ALICE);
    ImportSummaryIO summary = service.extractSummary(plan);

    assertEquals(2, summary.wouldCreateDataObjects());
    assertEquals(1, summary.wouldCreateContainers());
    assertEquals(1, summary.wouldCreateReferences());
    assertEquals(0, summary.wouldSkipDataObjects());
  }

  // ─── Reuse of existing plan ───────────────────────────────────────────────

  @Test
  void existingPlanWithSameCommitId_isReused() {
    ImportPlan existing = new ImportPlan(7L);
    existing.setAppId("existing-plan-app-id");
    existing.setCommitId("sha256:existingcommit");
    existing.setStatus("VALID");
    existing.setExpiresAt(System.currentTimeMillis() + 86_400_000L);
    existing.setValidatedAt(System.currentTimeMillis());
    existing.setSummaryJson("{\"wouldCreateDataObjects\":1,\"wouldCreateContainers\":0,\"wouldCreateReferences\":0,\"wouldSkipDataObjects\":0}");
    existing.setWarningsJson("[]");

    when(importPlanDAO.findByCommitId(anyString())).thenReturn(existing);

    ImportPlan plan = service.validate(singleDoManifest("do-1", "A", null), ALICE);

    assertEquals("existing-plan-app-id", plan.getAppId());
    verify(importPlanDAO, never()).createOrUpdate(any());
  }

  // ─── Factories ────────────────────────────────────────────────────────────

  private ImportManifestIO singleDoManifest(String localRef, String name, String status) {
    return new ImportManifestIO(
      COLL_APP_ID,
      List.of(new ManifestDataObjectIO(localRef, name, null, status, null, null, null)),
      null,
      null,
      null
    );
  }
}
