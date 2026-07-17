package de.dlr.shepard.v2.quality.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.daos.CollectionPropertiesDAO;
import de.dlr.shepard.v2.quality.daos.DataQualityRequirementDAO;
import de.dlr.shepard.v2.quality.entities.DataQualityRequirement;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import de.dlr.shepard.v2.quality.io.CreateDQRIO;
import de.dlr.shepard.v2.quality.io.DQRIO;
import de.dlr.shepard.v2.quality.io.DQRResultIO;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * TPL10 — unit tests for {@link DataQualityRequirementService}.
 *
 * <p>Tests cover: assign / list / remove CRUD, permission enforcement, and
 * ANNOTATION_REQUIRED evaluation (pass and fail paths).
 */
class DataQualityRequirementServiceTest {

  static final String COLLECTION_APP_ID = "019e3c96-0000-7000-a000-000000000042";
  static final long   COLLECTION_OGM_ID = 42L;
  static final String ALICE = "alice";
  static final String DQR_APP_ID = "019e3c96-0000-7000-b000-000000000001";
  static final String DO_APP_ID_1 = "019e3c96-0000-7000-c000-000000000001";
  static final String DO_APP_ID_2 = "019e3c96-0000-7000-c000-000000000002";

  @Mock DataQualityRequirementDAO dao;
  @Mock CollectionPropertiesDAO   collectionPropertiesDAO;
  @Mock PermissionsService        permissionsService;

  DataQualityRequirementService service;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new DataQualityRequirementService();
    service.dao = dao;
    service.collectionPropertiesDAO = collectionPropertiesDAO;
    service.permissionsService = permissionsService;

    // Default: collection exists.
    when(collectionPropertiesDAO.findCollectionIdByAppId(COLLECTION_APP_ID))
      .thenReturn(Optional.of(COLLECTION_OGM_ID));

    // Default: all permissions granted.
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLLECTION_OGM_ID), any(AccessType.class), anyString(), anyLong()))
      .thenReturn(true);

    // Default: dao.createOrUpdate returns the entity with appId injected.
    when(dao.createOrUpdate(any(DataQualityRequirement.class)))
      .thenAnswer(inv -> {
        DataQualityRequirement d = inv.getArgument(0);
        if (d.getAppId() == null) d.setAppId(DQR_APP_ID);
        return d;
      });
  }

  // ─── assign() ────────────────────────────────────────────────────────────

  @Test
  void assignCreatesAndPersistsDQR() {
    CreateDQRIO body = new CreateDQRIO("status check", "requires status attr", "ANNOTATION_REQUIRED", "status", "ERROR", true);

    DQRIO result = service.assign(COLLECTION_APP_ID, body, ALICE);

    assertNotNull(result);
    assertEquals(DQR_APP_ID, result.dqrAppId());
    assertEquals("status check", result.name());
    assertEquals("ANNOTATION_REQUIRED", result.ruleType());
    assertEquals("status", result.ruleParam());
    assertEquals("ERROR", result.severity());
    assertTrue(result.enabled());

    // Verify the node was saved and the relationship was created.
    verify(dao).createOrUpdate(any(DataQualityRequirement.class));
    verify(dao).assignToCollection(DQR_APP_ID, COLLECTION_APP_ID);
  }

  @Test
  void assignDefaultsSeverityToErrorWhenNotProvided() {
    CreateDQRIO body = new CreateDQRIO("check", null, "ANNOTATION_REQUIRED", "status", null, null);

    service.assign(COLLECTION_APP_ID, body, ALICE);

    ArgumentCaptor<DataQualityRequirement> cap = ArgumentCaptor.forClass(DataQualityRequirement.class);
    verify(dao).createOrUpdate(cap.capture());
    assertEquals("ERROR", cap.getValue().getSeverity());
    assertTrue(cap.getValue().isEnabled());
  }

  @Test
  void assignDefaultsEnabledTrueWhenNotProvided() {
    CreateDQRIO body = new CreateDQRIO("check", null, "ANNOTATION_REQUIRED", "status", "WARN", null);

    service.assign(COLLECTION_APP_ID, body, ALICE);

    ArgumentCaptor<DataQualityRequirement> cap = ArgumentCaptor.forClass(DataQualityRequirement.class);
    verify(dao).createOrUpdate(cap.capture());
    assertTrue(cap.getValue().isEnabled());
  }

  @Test
  void assignThrowsForbiddenWhenNoWritePermission() {
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLLECTION_OGM_ID), eq(AccessType.Write), eq(ALICE), anyLong()))
      .thenReturn(false);

    CreateDQRIO body = new CreateDQRIO("check", null, "ANNOTATION_REQUIRED", "status", "ERROR", true);
    assertThrows(ForbiddenException.class, () -> service.assign(COLLECTION_APP_ID, body, ALICE));
    verify(dao, never()).createOrUpdate(any());
  }

  @Test
  void assignThrowsNotFoundWhenCollectionMissing() {
    when(collectionPropertiesDAO.findCollectionIdByAppId("nonexistent"))
      .thenReturn(Optional.empty());

    CreateDQRIO body = new CreateDQRIO("check", null, "ANNOTATION_REQUIRED", "status", "ERROR", true);
    assertThrows(NotFoundException.class, () -> service.assign("nonexistent", body, ALICE));
  }

  // ─── list() ──────────────────────────────────────────────────────────────

  @Test
  void listReturnsMappedDQRs() {
    DataQualityRequirement d1 = makeDQR(DQR_APP_ID, "status check", "ANNOTATION_REQUIRED", "status");
    when(dao.countByCollectionAppId(COLLECTION_APP_ID)).thenReturn(1L);
    when(dao.findByCollectionAppId(COLLECTION_APP_ID, 0L, 50)).thenReturn(List.of(d1));

    PagedResponseIO<DQRIO> result = service.list(COLLECTION_APP_ID, ALICE, 0L, 50);

    assertEquals(1, result.items().size());
    assertEquals(1L, result.total());
    assertEquals(DQR_APP_ID, result.items().get(0).dqrAppId());
    assertEquals("status check", result.items().get(0).name());
  }

  @Test
  void listReturnsEmptyWhenNoneAssigned() {
    when(dao.countByCollectionAppId(COLLECTION_APP_ID)).thenReturn(0L);
    when(dao.findByCollectionAppId(COLLECTION_APP_ID, 0L, 50)).thenReturn(List.of());

    PagedResponseIO<DQRIO> result = service.list(COLLECTION_APP_ID, ALICE, 0L, 50);

    assertTrue(result.items().isEmpty());
    assertEquals(0L, result.total());
  }

  @Test
  void listThrowsForbiddenWhenNoReadPermission() {
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLLECTION_OGM_ID), eq(AccessType.Read), eq(ALICE), anyLong()))
      .thenReturn(false);

    assertThrows(ForbiddenException.class, () -> service.list(COLLECTION_APP_ID, ALICE, 0L, 50));
    verify(dao, never()).findByCollectionAppId(anyString(), anyLong(), anyInt());
    verify(dao, never()).countByCollectionAppId(anyString());
  }

  // ─── remove() ────────────────────────────────────────────────────────────

  @Test
  void removeDeletesExistingDQR() {
    DataQualityRequirement d = makeDQR(DQR_APP_ID, "check", "ANNOTATION_REQUIRED", "status");
    d.setId(99L);
    when(dao.findByAppId(DQR_APP_ID)).thenReturn(d);
    when(dao.isAssignedToCollection(DQR_APP_ID, COLLECTION_APP_ID)).thenReturn(true);

    service.remove(COLLECTION_APP_ID, DQR_APP_ID, ALICE);

    verify(dao).deleteWithRelationships(99L);
  }

  @Test
  void removeThrowsNotFoundWhenDQRMissing() {
    when(dao.findByAppId("nonexistent-dqr")).thenReturn(null);

    assertThrows(NotFoundException.class, () -> service.remove(COLLECTION_APP_ID, "nonexistent-dqr", ALICE));
    verify(dao, never()).deleteWithRelationships(anyLong());
  }

  @Test
  void removeThrowsNotFoundWhenDQRNotAssignedToThisCollection() {
    DataQualityRequirement d = makeDQR(DQR_APP_ID, "check", "ANNOTATION_REQUIRED", "status");
    d.setId(99L);
    when(dao.findByAppId(DQR_APP_ID)).thenReturn(d);
    when(dao.isAssignedToCollection(DQR_APP_ID, COLLECTION_APP_ID)).thenReturn(false);

    assertThrows(NotFoundException.class, () -> service.remove(COLLECTION_APP_ID, DQR_APP_ID, ALICE));
    verify(dao, never()).deleteWithRelationships(anyLong());
  }

  @Test
  void removeThrowsForbiddenWhenNoWritePermission() {
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLLECTION_OGM_ID), eq(AccessType.Write), eq(ALICE), anyLong()))
      .thenReturn(false);

    assertThrows(ForbiddenException.class, () -> service.remove(COLLECTION_APP_ID, DQR_APP_ID, ALICE));
    verify(dao, never()).deleteWithRelationships(anyLong());
  }

  // ─── evaluate() — ANNOTATION_REQUIRED ───────────────────────────────────

  @Test
  void evaluateAnnotationRequiredPassesWhenAttributePresent() {
    DataQualityRequirement dqr = makeDQR(DQR_APP_ID, "status check", "ANNOTATION_REQUIRED", "status");
    dqr.setEnabled(true);
    when(dao.findByCollectionAppId(COLLECTION_APP_ID)).thenReturn(List.of(dqr));
    when(dao.findDataObjectAppIds(COLLECTION_APP_ID)).thenReturn(List.of(DO_APP_ID_1, DO_APP_ID_2));
    // Both DataObjects have the "status" attribute.
    when(dao.findDataObjectsHavingAttribute(COLLECTION_APP_ID, "status"))
      .thenReturn(Set.of(DO_APP_ID_1, DO_APP_ID_2));

    List<DQRResultIO> results = service.evaluate(COLLECTION_APP_ID, ALICE, 5000);

    assertEquals(2, results.size());
    assertTrue(results.stream().allMatch(DQRResultIO::passed));
    assertTrue(results.stream().allMatch(r -> r.dqrAppId().equals(DQR_APP_ID)));
  }

  @Test
  void evaluateAnnotationRequiredFailsWhenAttributeMissing() {
    DataQualityRequirement dqr = makeDQR(DQR_APP_ID, "status check", "ANNOTATION_REQUIRED", "status");
    dqr.setEnabled(true);
    when(dao.findByCollectionAppId(COLLECTION_APP_ID)).thenReturn(List.of(dqr));
    when(dao.findDataObjectAppIds(COLLECTION_APP_ID)).thenReturn(List.of(DO_APP_ID_1, DO_APP_ID_2));
    // Only DO_APP_ID_1 has the "status" attribute; DO_APP_ID_2 does not.
    when(dao.findDataObjectsHavingAttribute(COLLECTION_APP_ID, "status"))
      .thenReturn(Set.of(DO_APP_ID_1));

    List<DQRResultIO> results = service.evaluate(COLLECTION_APP_ID, ALICE, 5000);

    assertEquals(2, results.size());
    DQRResultIO pass = results.stream().filter(r -> r.dataObjectAppId().equals(DO_APP_ID_1)).findFirst().orElseThrow();
    DQRResultIO fail = results.stream().filter(r -> r.dataObjectAppId().equals(DO_APP_ID_2)).findFirst().orElseThrow();
    assertTrue(pass.passed());
    assertTrue(!fail.passed());
    assertEquals("Missing annotation: status", fail.message());
  }

  @Test
  void evaluateSkipsDisabledDQRs() {
    DataQualityRequirement enabled  = makeDQR(DQR_APP_ID, "check", "ANNOTATION_REQUIRED", "status");
    enabled.setEnabled(true);
    DataQualityRequirement disabled = makeDQR("disabled-dqr", "disabled", "ANNOTATION_REQUIRED", "license");
    disabled.setEnabled(false);

    when(dao.findByCollectionAppId(COLLECTION_APP_ID)).thenReturn(List.of(enabled, disabled));
    when(dao.findDataObjectAppIds(COLLECTION_APP_ID)).thenReturn(List.of(DO_APP_ID_1));
    when(dao.findDataObjectsHavingAttribute(COLLECTION_APP_ID, "status"))
      .thenReturn(Set.of(DO_APP_ID_1));

    List<DQRResultIO> results = service.evaluate(COLLECTION_APP_ID, ALICE, 5000);

    // Only one DQR evaluated — the disabled one is skipped.
    assertEquals(1, results.size());
    assertEquals(DQR_APP_ID, results.get(0).dqrAppId());
  }

  @Test
  void evaluateReturnsEmptyWhenNoDQRsAssigned() {
    when(dao.findByCollectionAppId(COLLECTION_APP_ID)).thenReturn(List.of());

    List<DQRResultIO> results = service.evaluate(COLLECTION_APP_ID, ALICE, 5000);

    assertTrue(results.isEmpty());
    // No DataObject fetch needed when no DQRs.
    verify(dao, never()).findDataObjectAppIds(anyString());
  }

  @Test
  void evaluateThrowsForbiddenWhenNoReadPermission() {
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLLECTION_OGM_ID), eq(AccessType.Read), eq(ALICE), anyLong()))
      .thenReturn(false);

    assertThrows(ForbiddenException.class, () -> service.evaluate(COLLECTION_APP_ID, ALICE, 5000));
  }

  // ─── evaluate() — early-exit (APISIMP-DQR-EVAL-INMEM) ────────────────────

  @Test
  void evaluateStopsAfterMaxItemsPlusOneResults() {
    // 3 DataObjects, maxItems=1 → service stops after 2 results (1+1)
    String do3 = "019e3c96-0000-7000-c000-000000000003";
    DataQualityRequirement dqr = makeDQR(DQR_APP_ID, "check", "ANNOTATION_REQUIRED", "status");
    dqr.setEnabled(true);
    when(dao.findByCollectionAppId(COLLECTION_APP_ID)).thenReturn(List.of(dqr));
    when(dao.findDataObjectAppIds(COLLECTION_APP_ID)).thenReturn(List.of(DO_APP_ID_1, DO_APP_ID_2, do3));
    when(dao.findDataObjectsHavingAttribute(COLLECTION_APP_ID, "status")).thenReturn(Set.of());

    List<DQRResultIO> results = service.evaluate(COLLECTION_APP_ID, ALICE, 1);

    // Must be exactly maxItems+1 = 2; the 3rd DataObject was never visited.
    assertEquals(2, results.size());
  }

  @Test
  void evaluateExactlyMaxItemsResultsAreNotTruncated() {
    // 2 DataObjects, maxItems=2 → service returns exactly 2 (no truncation signal)
    DataQualityRequirement dqr = makeDQR(DQR_APP_ID, "check", "ANNOTATION_REQUIRED", "status");
    dqr.setEnabled(true);
    when(dao.findByCollectionAppId(COLLECTION_APP_ID)).thenReturn(List.of(dqr));
    when(dao.findDataObjectAppIds(COLLECTION_APP_ID)).thenReturn(List.of(DO_APP_ID_1, DO_APP_ID_2));
    when(dao.findDataObjectsHavingAttribute(COLLECTION_APP_ID, "status")).thenReturn(Set.of());

    List<DQRResultIO> results = service.evaluate(COLLECTION_APP_ID, ALICE, 2);

    // Exactly 2 results — no overflow, caller sees truncated=false.
    assertEquals(2, results.size());
  }

  // ─── Helpers ─────────────────────────────────────────────────────────────

  private DataQualityRequirement makeDQR(String appId, String name, String ruleType, String ruleParam) {
    DataQualityRequirement d = new DataQualityRequirement();
    d.setAppId(appId);
    d.setName(name);
    d.setRuleType(ruleType);
    d.setRuleParam(ruleParam);
    d.setSeverity("ERROR");
    d.setEnabled(true);
    return d;
  }
}
