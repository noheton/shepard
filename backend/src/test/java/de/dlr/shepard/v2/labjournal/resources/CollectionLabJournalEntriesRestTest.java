package de.dlr.shepard.v2.labjournal.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.labJournal.entities.LabJournalEntry;
import de.dlr.shepard.context.labJournal.io.LabJournalEntryIO;
import de.dlr.shepard.v2.labjournal.daos.CollectionLabJournalEntriesDAO;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * UI-020 — Mockito unit tests for {@link CollectionLabJournalEntriesRest}.
 *
 * <p>Verifies the bulk-fetch endpoint enforces auth/permission ordering
 * (401 unauthenticated → 404 unknown collection → 403 no Read → 200 OK) and
 * forwards every non-deleted entry from the DAO as a populated
 * {@link LabJournalEntryIO}.
 */
class CollectionLabJournalEntriesRestTest {

  static final String COLL_APP_ID = "coll-appid-1";
  static final long COLL_OGM_ID = 42L;
  static final String CALLER = "alice";

  @Mock
  CollectionLabJournalEntriesDAO entriesDAO;

  @Mock
  PermissionsService permissionsService;

  @Mock
  EntityIdResolver entityIdResolver;

  @Mock
  SecurityContext sc;

  @Mock
  Principal principal;

  CollectionLabJournalEntriesRest resource;

  static final String DO_APP_ID_1 = "019506b4-dc55-7c92-b4e1-bf94db37e5b1";
  static final String DO_APP_ID_2 = "019506b4-dc55-7c92-b4e1-bf94db37e5b2";

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new CollectionLabJournalEntriesRest();
    resource.entriesDAO = entriesDAO;
    resource.permissionsService = permissionsService;
    resource.entityIdResolver = entityIdResolver;

    when(sc.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(true);
    when(entriesDAO.findByCollectionAppId(COLL_APP_ID))
      .thenReturn(List.of(
        buildEntry(11L, 101L, DO_APP_ID_1, "newer", new Date(2_000_000L)),
        buildEntry(12L, 102L, DO_APP_ID_2, "older", new Date(1_000_000L))
      ));
  }

  @Test
  void list_returns401WhenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);
    assertThat(resource.list(COLL_APP_ID, null, null, sc).getStatus()).isEqualTo(401);
  }

  @Test
  void list_returns404WhenCollectionNotFound() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenThrow(new NotFoundException());
    assertThat(resource.list(COLL_APP_ID, null, null, sc).getStatus()).isEqualTo(404);
  }

  @Test
  void list_returns403WhenNoReadPermission() {
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(false);
    assertThat(resource.list(COLL_APP_ID, null, null, sc).getStatus()).isEqualTo(403);
  }

  @Test
  void list_returns200WithEntriesAndDataObjectIdPopulated() {
    var r = resource.list(COLL_APP_ID, null, null, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    var body = (List<LabJournalEntryIO>) r.getEntity();
    assertThat(body).hasSize(2);
    // dataObjectId field must be populated — the frontend uses it to group.
    assertThat(body.get(0).getDataObjectId()).isEqualTo(101L);
    assertThat(body.get(1).getDataObjectId()).isEqualTo(102L);
    // APISIMP-LJE-DATAOBJECTID-NUMERIC: dataObjectAppId (UUID v7) must also be populated.
    assertThat(body.get(0).getDataObjectAppId()).isEqualTo(DO_APP_ID_1);
    assertThat(body.get(1).getDataObjectAppId()).isEqualTo(DO_APP_ID_2);
    // id and journalContent must round-trip.
    assertThat(body.get(0).getId()).isEqualTo(11L);
    assertThat(body.get(0).getJournalContent()).isEqualTo("newer");
  }

  /**
   * BUG-LJ-V1-COLL-ID regression — an entry whose {@code DataObject} back-reference
   * failed to hydrate must be skipped (with a WARN log) rather than NPE on
   * {@code LabJournalEntryIO}'s constructor. The DAO's Cypher fix is the primary
   * defence; this test pins the resource-level safety net so a future hydration
   * regression cannot resurface the HTTP 500.
   */
  @Test
  void list_skipsOrphanEntryWithNullDataObjectInsteadOf500() {
    LabJournalEntry orphan = new LabJournalEntry();
    orphan.setId(99L);
    orphan.setContent("orphan");
    // Intentionally do NOT call setDataObject — simulates the pre-fix
    // hydration miss.
    when(entriesDAO.findByCollectionAppId(COLL_APP_ID))
      .thenReturn(List.of(
        buildEntry(11L, 101L, DO_APP_ID_1, "hydrated", new Date(2_000_000L)),
        orphan
      ));
    var r = resource.list(COLL_APP_ID, null, null, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    var body = (List<LabJournalEntryIO>) r.getEntity();
    // The orphan is skipped; only the hydrated entry survives.
    assertThat(body).hasSize(1);
    assertThat(body.get(0).getDataObjectId()).isEqualTo(101L);
    assertThat(body.get(0).getDataObjectAppId()).isEqualTo(DO_APP_ID_1);
  }

  @Test
  void list_returns200WithEmptyListWhenNoEntries() {
    when(entriesDAO.findByCollectionAppId(COLL_APP_ID)).thenReturn(List.of());
    var r = resource.list(COLL_APP_ID, null, null, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    var body = (List<LabJournalEntryIO>) r.getEntity();
    assertThat(body).isEmpty();
  }

  @Test
  void list_usesCollectionOgmIdForPermissionCheck() {
    resource.list(COLL_APP_ID, null, null, sc);
    verify(permissionsService).isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER));
  }

  @Test
  void list_paginationReturnsSublistWhenPageAndSizeProvided() {
    var r = resource.list(COLL_APP_ID, 0, 1, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    var body = (List<LabJournalEntryIO>) r.getEntity();
    assertThat(body).hasSize(1);
    assertThat(body.get(0).getId()).isEqualTo(11L);
  }

  @Test
  void list_paginationPageBeyondRangeReturnsEmptyList() {
    var r = resource.list(COLL_APP_ID, 99, 10, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    var body = (List<LabJournalEntryIO>) r.getEntity();
    assertThat(body).isEmpty();
  }

  @Test
  void list_nullPageAndSizeReturnsAllEntries() {
    var r = resource.list(COLL_APP_ID, null, null, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    var body = (List<LabJournalEntryIO>) r.getEntity();
    assertThat(body).hasSize(2);
  }

  // ── @Parameter documentation regression tests ────────────────────────────

  private static java.lang.reflect.Parameter listParam(String qpName) throws NoSuchMethodException {
    java.lang.reflect.Method m = CollectionLabJournalEntriesRest.class.getMethod(
        "list", String.class, Integer.class, Integer.class, jakarta.ws.rs.core.SecurityContext.class);
    return java.util.Arrays.stream(m.getParameters())
        .filter(p -> {
          var qp = p.getAnnotation(jakarta.ws.rs.QueryParam.class);
          return qp != null && qpName.equals(qp.value());
        })
        .findFirst()
        .orElseThrow(() -> new AssertionError("No @QueryParam(\"" + qpName + "\") on list()"));
  }

  private static void assertParamDocumented(java.lang.reflect.Parameter param, String label) {
    var ann = param.getAnnotation(
        org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
    org.junit.jupiter.api.Assertions.assertNotNull(ann, label + " must carry @Parameter");
    org.junit.jupiter.api.Assertions.assertTrue(ann.description() != null && !ann.description().isBlank(),
        label + " @Parameter.description must be non-blank");
  }

  @Test
  void list_pageParamIsDocumented() throws NoSuchMethodException {
    assertParamDocumented(listParam("page"), "list.page");
  }

  @Test
  void list_pageSizeParamIsDocumented() throws NoSuchMethodException {
    assertParamDocumented(listParam("pageSize"), "list.pageSize");
  }

  /**
   * Build a {@link LabJournalEntry} attached to a {@link DataObject} carrying
   * the given shepardId and appId. Mirrors the v1 service path enough for the IO
   * constructor to round-trip {@code dataObjectId}, {@code dataObjectAppId},
   * {@code id}, {@code journalContent}, {@code createdAt}.
   */
  private LabJournalEntry buildEntry(long entryId, long dataObjectShepardId, String dataObjectAppId,
      String content, Date createdAt) {
    DataObject doNode = new DataObject();
    doNode.setShepardId(dataObjectShepardId);
    doNode.setAppId(dataObjectAppId);

    LabJournalEntry e = new LabJournalEntry();
    e.setId(entryId);
    e.setContent(content);
    e.setDataObject(doNode);
    e.setCreatedAt(createdAt);
    return e;
  }
}
