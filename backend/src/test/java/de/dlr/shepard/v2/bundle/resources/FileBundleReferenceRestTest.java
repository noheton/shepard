package de.dlr.shepard.v2.bundle.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.file.daos.FileBundleReferenceDAO;
import de.dlr.shepard.context.references.file.daos.FileGroupDAO;
import de.dlr.shepard.context.references.file.entities.FileBundleReference;
import de.dlr.shepard.context.references.file.entities.FileGroup;
import de.dlr.shepard.context.references.file.io.CreateFileGroupIO;
import de.dlr.shepard.context.references.file.io.FileGroupIO;
import de.dlr.shepard.context.references.file.services.FileGroupService;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.storage.FileStorageService;
import de.dlr.shepard.v2.bundle.io.FileBundleReferenceIO;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Plain-Mockito unit tests for {@link FileBundleReferenceRest} (FR1a,
 * see {@code aidocs/53 §1.6}). Same shape as
 * {@code GitReferenceRestTest} — wires the resource by hand and
 * exercises every endpoint plus its 401 / 403 / 404 / 400 branches.
 */
class FileBundleReferenceRestTest {

  private static final String BUNDLE_APP_ID = "bundle-appid-1";
  private static final String GROUP_APP_ID = "group-appid-1";
  private static final String CALLER = "alice";
  private static final long DO_OGM_ID = 42L;

  @Mock
  FileBundleReferenceDAO fileBundleReferenceDAO;

  @Mock
  FileGroupDAO fileGroupDAO;

  @Mock
  FileGroupService fileGroupService;

  @Mock
  FileStorageService fileStorageService;

  @Mock
  PermissionsService permissionsService;

  @Mock
  EntityIdResolver entityIdResolver;

  @Mock
  SecurityContext securityContext;

  @Mock
  Principal principal;

  FileBundleReferenceRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new FileBundleReferenceRest();
    resource.fileBundleReferenceDAO = fileBundleReferenceDAO;
    resource.fileGroupDAO = fileGroupDAO;
    resource.fileGroupService = fileGroupService;
    resource.fileStorageService = fileStorageService;
    resource.permissionsService = permissionsService;
    resource.entityIdResolver = entityIdResolver;
    resource.objectMapper = new ObjectMapper();
    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
    // Production resolves access via the 3-arg overload (no jwtIat) and the
    // appId-based check; stub both positively so happy-path tests pass the gate.
    when(permissionsService.isAccessTypeAllowedForUser(eq(DO_OGM_ID), any(AccessType.class), eq(CALLER)))
      .thenReturn(true);
    when(permissionsService.isAccessAllowedForDataObjectAppId(anyString(), any(AccessType.class), eq(CALLER)))
      .thenReturn(true);
  }

  private FileBundleReference existingBundle() {
    var parent = new DataObject(DO_OGM_ID);
    parent.setAppId("do-app-id");
    parent.setShepardId(123L);
    var bundle = new FileBundleReference(7L);
    bundle.setAppId(BUNDLE_APP_ID);
    bundle.setName("my bundle");
    bundle.setDataObject(parent);
    var fc = new FileContainer(99L);
    fc.setMongoId("mongo-id-99");
    bundle.setFileContainer(fc);
    return bundle;
  }

  // ─── GET /v2/bundles/{appId} ──────────────────────────────────────────────

  @Test
  void getBundle_returns200WithGroups() {
    var bundle = existingBundle();
    var group = new FileGroup(1L);
    group.setName("default");
    group.setIndex(0);
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(bundle);
    when(fileGroupDAO.findByBundleAppId(BUNDLE_APP_ID)).thenReturn(List.of(group));

    var r = resource.getBundle(BUNDLE_APP_ID, securityContext);
    assertEquals(200, r.getStatus());
    var io = (FileBundleReferenceIO) r.getEntity();
    assertEquals(BUNDLE_APP_ID, io.getAppId());
    assertEquals("mongo-id-99", io.getContainerMongoId());
    assertEquals(1, io.getGroups().size());
    assertEquals("default", io.getGroups().get(0).getName());
  }

  @Test
  void getBundle_returns404WhenMissing() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(null);
    assertEquals(404, resource.getBundle(BUNDLE_APP_ID, securityContext).getStatus());
  }

  @Test
  void getBundle_returns401WhenUnauthenticated() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(securityContext.getUserPrincipal()).thenReturn(null);
    assertEquals(401, resource.getBundle(BUNDLE_APP_ID, securityContext).getStatus());
  }

  @Test
  void getBundle_returns403WhenNoReadPermission() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq("do-app-id"), eq(AccessType.Read), eq(CALLER))).thenReturn(false);
    assertEquals(403, resource.getBundle(BUNDLE_APP_ID, securityContext).getStatus());
  }

  // ─── GET /v2/bundles/{appId}/groups ───────────────────────────────────────

  @Test
  void listGroups_returns200WithPagedEnvelope() {
    var g1 = new FileGroup(1L);
    g1.setIndex(0);
    g1.setName("default");
    var g2 = new FileGroup(2L);
    g2.setIndex(1);
    g2.setName("phase1");
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.countGroups(BUNDLE_APP_ID)).thenReturn(2L);
    when(fileGroupService.listGroups(BUNDLE_APP_ID, 0, 50)).thenReturn(List.of(g1, g2));

    var r = resource.listGroups(BUNDLE_APP_ID, 0, 50, securityContext);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<FileGroupIO> envelope = (PagedResponseIO<FileGroupIO>) r.getEntity();
    assertEquals(2L, envelope.total());
    assertEquals(0, envelope.page());
    assertEquals(50, envelope.pageSize());
    assertEquals(2, envelope.items().size());
    assertEquals("default", envelope.items().get(0).getName());
  }

  @Test
  void listGroups_returns200EmptyPage() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.countGroups(BUNDLE_APP_ID)).thenReturn(0L);
    when(fileGroupService.listGroups(BUNDLE_APP_ID, 0, 50)).thenReturn(List.of());

    var r = resource.listGroups(BUNDLE_APP_ID, 0, 50, securityContext);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<FileGroupIO> envelope = (PagedResponseIO<FileGroupIO>) r.getEntity();
    assertEquals(0L, envelope.total());
    assertTrue(envelope.items().isEmpty());
  }

  @Test
  void listGroups_returns404WhenBundleMissing() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(null);
    assertEquals(404, resource.listGroups(BUNDLE_APP_ID, 0, 50, securityContext).getStatus());
  }

  @Test
  void listGroups_returns403WhenNoReadPermission() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq("do-app-id"), eq(AccessType.Read), eq(CALLER))).thenReturn(false);
    assertEquals(403, resource.listGroups(BUNDLE_APP_ID, 0, 50, securityContext).getStatus());
  }

  // ─── POST /v2/bundles/{appId}/groups ──────────────────────────────────────

  @Test
  void createGroup_returns201() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    var saved = new FileGroup(99L);
    saved.setName("phase1");
    saved.setIndex(1);
    saved.setAppId(GROUP_APP_ID);
    when(fileGroupService.createGroup(eq(BUNDLE_APP_ID), any(CreateFileGroupIO.class))).thenReturn(saved);

    var body = new CreateFileGroupIO();
    body.setName("phase1");
    var r = resource.createGroup(BUNDLE_APP_ID, body, securityContext);
    assertEquals(201, r.getStatus());
    assertEquals(GROUP_APP_ID, ((FileGroupIO) r.getEntity()).getAppId());
  }

  @Test
  void createGroup_returns400WhenNameMissing() {
    var body = new CreateFileGroupIO();
    var r = resource.createGroup(BUNDLE_APP_ID, body, securityContext);
    assertEquals(400, r.getStatus());
  }

  @Test
  void createGroup_returns400WhenNameBlank() {
    var body = new CreateFileGroupIO();
    body.setName("  ");
    var r = resource.createGroup(BUNDLE_APP_ID, body, securityContext);
    assertEquals(400, r.getStatus());
  }

  @Test
  void createGroup_returns404WhenBundleMissing() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(null);
    var body = new CreateFileGroupIO();
    body.setName("p1");
    assertEquals(404, resource.createGroup(BUNDLE_APP_ID, body, securityContext).getStatus());
  }

  @Test
  void createGroup_returns403WhenNoWritePermission() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq("do-app-id"), eq(AccessType.Write), eq(CALLER))).thenReturn(false);
    var body = new CreateFileGroupIO();
    body.setName("p1");
    assertEquals(403, resource.createGroup(BUNDLE_APP_ID, body, securityContext).getStatus());
  }

  @Test
  void createGroup_translatesServiceBadRequestTo400() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.createGroup(eq(BUNDLE_APP_ID), any(CreateFileGroupIO.class)))
      .thenThrow(new BadRequestException("invalid"));
    var body = new CreateFileGroupIO();
    body.setName("p1");
    assertEquals(400, resource.createGroup(BUNDLE_APP_ID, body, securityContext).getStatus());
  }

  // ─── GET /v2/bundles/{appId}/groups/{groupAppId} ──────────────────────────

  @Test
  void getGroup_returns200WhenChildOfBundle() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    var g = new FileGroup(1L);
    g.setName("default");
    g.setAppId(GROUP_APP_ID);
    when(fileGroupService.getByAppId(GROUP_APP_ID)).thenReturn(g);
    when(fileGroupService.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn(BUNDLE_APP_ID);

    var r = resource.getGroup(BUNDLE_APP_ID, GROUP_APP_ID, securityContext);
    assertEquals(200, r.getStatus());
    assertEquals(GROUP_APP_ID, ((FileGroupIO) r.getEntity()).getAppId());
  }

  @Test
  void getGroup_returns404WhenBundleMissing() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(null);
    assertEquals(404, resource.getGroup(BUNDLE_APP_ID, GROUP_APP_ID, securityContext).getStatus());
  }

  @Test
  void getGroup_returns404WhenGroupMissing() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.getByAppId(GROUP_APP_ID)).thenReturn(null);
    assertEquals(404, resource.getGroup(BUNDLE_APP_ID, GROUP_APP_ID, securityContext).getStatus());
  }

  @Test
  void getGroup_returns404WhenGroupBelongsToDifferentBundle() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    var g = new FileGroup(1L);
    g.setName("default");
    g.setAppId(GROUP_APP_ID);
    when(fileGroupService.getByAppId(GROUP_APP_ID)).thenReturn(g);
    when(fileGroupService.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn("different-bundle");
    assertEquals(404, resource.getGroup(BUNDLE_APP_ID, GROUP_APP_ID, securityContext).getStatus());
  }

  // ─── PATCH /v2/bundles/{appId}/groups/{groupAppId} ────────────────────────

  private static JsonNode json(String s) {
    try {
      return new ObjectMapper().readTree(s);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void patchGroup_returns200() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn(BUNDLE_APP_ID);
    var updated = new FileGroup(1L);
    updated.setName("renamed");
    updated.setAppId(GROUP_APP_ID);
    when(fileGroupService.patchGroup(eq(GROUP_APP_ID), any())).thenReturn(updated);

    var r = resource.patchGroup(BUNDLE_APP_ID, GROUP_APP_ID, json("{\"name\":\"renamed\"}"), securityContext);
    assertEquals(200, r.getStatus());
    assertEquals("renamed", ((FileGroupIO) r.getEntity()).getName());
  }

  @Test
  void patchGroup_returns400WhenBodyNotObject() {
    var r = resource.patchGroup(BUNDLE_APP_ID, GROUP_APP_ID, json("\"oops\""), securityContext);
    assertEquals(400, r.getStatus());
  }

  @Test
  void patchGroup_returns400WhenBodyNull() {
    assertEquals(400, resource.patchGroup(BUNDLE_APP_ID, GROUP_APP_ID, null, securityContext).getStatus());
  }

  @Test
  void patchGroup_returns404WhenBundleMissing() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(null);
    assertEquals(404, resource.patchGroup(BUNDLE_APP_ID, GROUP_APP_ID, json("{}"), securityContext).getStatus());
  }

  @Test
  void patchGroup_returns403WhenNoWritePermission() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq("do-app-id"), eq(AccessType.Write), eq(CALLER))).thenReturn(false);
    assertEquals(403, resource.patchGroup(BUNDLE_APP_ID, GROUP_APP_ID, json("{}"), securityContext).getStatus());
  }

  @Test
  void patchGroup_returns404WhenGroupBelongsToDifferentBundle() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn("different-bundle");
    assertEquals(404, resource.patchGroup(BUNDLE_APP_ID, GROUP_APP_ID, json("{\"name\":\"x\"}"), securityContext).getStatus());
  }

  @Test
  void patchGroup_translatesServiceBadRequestTo400() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn(BUNDLE_APP_ID);
    when(fileGroupService.patchGroup(eq(GROUP_APP_ID), any())).thenThrow(new BadRequestException("bad"));
    assertEquals(400, resource.patchGroup(BUNDLE_APP_ID, GROUP_APP_ID, json("{\"name\":\"\"}"), securityContext).getStatus());
  }

  // ─── DELETE /v2/bundles/{appId}/groups/{groupAppId} ───────────────────────

  @Test
  void deleteGroup_returns204() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn(BUNDLE_APP_ID);

    var r = resource.deleteGroup(BUNDLE_APP_ID, GROUP_APP_ID, false, securityContext);
    assertEquals(204, r.getStatus());
    verify(fileGroupService).deleteGroup(GROUP_APP_ID, false);
  }

  @Test
  void deleteGroup_returns400WhenServiceRefuses() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn(BUNDLE_APP_ID);
    org.mockito.Mockito.doThrow(new BadRequestException("has files")).when(fileGroupService).deleteGroup(GROUP_APP_ID, false);

    var r = resource.deleteGroup(BUNDLE_APP_ID, GROUP_APP_ID, false, securityContext);
    assertEquals(400, r.getStatus());
  }

  @Test
  void deleteGroup_returns404WhenBundleMissing() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(null);
    assertEquals(404, resource.deleteGroup(BUNDLE_APP_ID, GROUP_APP_ID, false, securityContext).getStatus());
  }

  @Test
  void deleteGroup_returns403WhenNoWritePermission() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq("do-app-id"), eq(AccessType.Write), eq(CALLER))).thenReturn(false);
    assertEquals(403, resource.deleteGroup(BUNDLE_APP_ID, GROUP_APP_ID, false, securityContext).getStatus());
  }

  @Test
  void deleteGroup_returns404WhenGroupBelongsToDifferentBundle() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn("other-bundle");
    assertEquals(404, resource.deleteGroup(BUNDLE_APP_ID, GROUP_APP_ID, false, securityContext).getStatus());
  }

  // ─── POST /v2/bundles/{appId}/groups/{groupAppId}/files ────────────────────

  @Test
  void uploadFileIntoGroup_returns400WhenFilePartMissing() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn(BUNDLE_APP_ID);
    var r = resource.uploadFileIntoGroup(BUNDLE_APP_ID, GROUP_APP_ID, null, securityContext);
    assertEquals(400, r.getStatus());
  }

  @Test
  void uploadFileIntoGroup_returns404WhenBundleMissing() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(null);
    var r = resource.uploadFileIntoGroup(BUNDLE_APP_ID, GROUP_APP_ID, null, securityContext);
    assertEquals(404, r.getStatus());
  }

  @Test
  void uploadFileIntoGroup_returns403WhenNoWritePermission() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq("do-app-id"), eq(AccessType.Write), eq(CALLER))).thenReturn(false);
    assertEquals(403, resource.uploadFileIntoGroup(BUNDLE_APP_ID, GROUP_APP_ID, null, securityContext).getStatus());
  }

  @Test
  void uploadFileIntoGroup_returns404WhenGroupBelongsToDifferentBundle() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn("other");
    assertEquals(404, resource.uploadFileIntoGroup(BUNDLE_APP_ID, GROUP_APP_ID, null, securityContext).getStatus());
  }

  @Test
  void getBundle_includesContainerMongoId() {
    var bundle = existingBundle();
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(bundle);
    when(fileGroupDAO.findByBundleAppId(BUNDLE_APP_ID)).thenReturn(List.of());
    var r = resource.getBundle(BUNDLE_APP_ID, securityContext);
    var io = (FileBundleReferenceIO) r.getEntity();
    assertNotNull(io.getContainerMongoId());
    assertTrue(io.getGroups().isEmpty());
  }

  @Test
  void createGroup_translatesServiceNotFoundTo404() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.createGroup(eq(BUNDLE_APP_ID), any(CreateFileGroupIO.class)))
      .thenThrow(new NotFoundException());
    var body = new CreateFileGroupIO();
    body.setName("p1");
    assertEquals(404, resource.createGroup(BUNDLE_APP_ID, body, securityContext).getStatus());
  }

  @Test
  void deleteGroup_neverCallsServiceWhenForbidden() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq("do-app-id"), eq(AccessType.Write), eq(CALLER))).thenReturn(false);
    resource.deleteGroup(BUNDLE_APP_ID, GROUP_APP_ID, false, securityContext);
    verify(fileGroupService, never()).deleteGroup(any(), org.mockito.ArgumentMatchers.anyBoolean());
  }

  // ── MFFD-IMAGEBUNDLE-PAGINATE-1 / APISIMP-FILEBUNDLE-LISTGROUPFILES-IN-MEMORY-PAGING ─

  /**
   * Build a list of N synthetic ShepardFile records starting at index {@code from}.
   * Used by pagination tests to drive the Cypher-pushed slicing.
   */
  private List<de.dlr.shepard.data.file.entities.ShepardFile> syntheticFiles(int from, int count) {
    var files = new java.util.ArrayList<de.dlr.shepard.data.file.entities.ShepardFile>(count);
    for (int i = from; i < from + count; i++) {
      var f = new de.dlr.shepard.data.file.entities.ShepardFile();
      f.setFilename("frame-" + i + ".png");
      files.add(f);
    }
    return files;
  }

  /** The pagination endpoint walks `checkAccess` → ensure the perm gate passes. */
  private void stubReadAllowedForBundleParent() {
    when(permissionsService.isAccessAllowedForDataObjectAppId("do-app-id", AccessType.Read, CALLER)).thenReturn(true);
  }

  @Test
  void listGroupFiles_defaultParams_returnsFirstPage() {
    stubReadAllowedForBundleParent();
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn(BUNDLE_APP_ID);
    when(fileGroupService.countFiles(GROUP_APP_ID)).thenReturn(500L);
    when(fileGroupService.listFiles(GROUP_APP_ID, 0, FileBundleReferenceRest.DEFAULT_FILES_PAGE_SIZE))
        .thenReturn(syntheticFiles(0, FileBundleReferenceRest.DEFAULT_FILES_PAGE_SIZE));

    var r = resource.listGroupFiles(BUNDLE_APP_ID, GROUP_APP_ID, null, null, securityContext);
    assertEquals(200, r.getStatus());
    var paged = (de.dlr.shepard.v2.bundle.io.PagedFilesIO) r.getEntity();
    assertEquals(0, paged.getPage());
    assertEquals(FileBundleReferenceRest.DEFAULT_FILES_PAGE_SIZE, paged.getSize());
    assertEquals(500L, paged.getTotalElements());
    assertEquals(3, paged.getTotalPages()); // ceil(500 / 200) = 3
    assertEquals(FileBundleReferenceRest.DEFAULT_FILES_PAGE_SIZE, paged.getItems().size());
    assertEquals("frame-0.png", paged.getItems().get(0).getFilename());
    assertEquals("frame-199.png", paged.getItems().get(199).getFilename());
  }

  @Test
  void listGroupFiles_page1Size50_returnsCorrectSlice() {
    stubReadAllowedForBundleParent();
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn(BUNDLE_APP_ID);
    when(fileGroupService.countFiles(GROUP_APP_ID)).thenReturn(120L);
    when(fileGroupService.listFiles(GROUP_APP_ID, 50, 50)).thenReturn(syntheticFiles(50, 50));

    var r = resource.listGroupFiles(BUNDLE_APP_ID, GROUP_APP_ID, 1, 50, securityContext);
    var paged = (de.dlr.shepard.v2.bundle.io.PagedFilesIO) r.getEntity();
    assertEquals(50, paged.getItems().size());
    assertEquals("frame-50.png", paged.getItems().get(0).getFilename());
    assertEquals("frame-99.png", paged.getItems().get(49).getFilename());
    assertEquals(120L, paged.getTotalElements());
    assertEquals(3, paged.getTotalPages());
  }

  @Test
  void listGroupFiles_pageBeyondEnd_returnsEmptyItems() {
    stubReadAllowedForBundleParent();
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn(BUNDLE_APP_ID);
    when(fileGroupService.countFiles(GROUP_APP_ID)).thenReturn(38L);
    // skip = 5 * 200 = 1000 >= 38 → listFiles never called

    var r = resource.listGroupFiles(BUNDLE_APP_ID, GROUP_APP_ID, 5, 200, securityContext);
    var paged = (de.dlr.shepard.v2.bundle.io.PagedFilesIO) r.getEntity();
    assertEquals(0, paged.getItems().size());
    assertEquals(38L, paged.getTotalElements());
    assertEquals(1, paged.getTotalPages());
  }

  @Test
  void listGroupFiles_oversizedPageSize_clampsToMax() {
    stubReadAllowedForBundleParent();
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn(BUNDLE_APP_ID);
    when(fileGroupService.countFiles(GROUP_APP_ID)).thenReturn(2000L);
    when(fileGroupService.listFiles(GROUP_APP_ID, 0, FileBundleReferenceRest.MAX_FILES_PAGE_SIZE))
        .thenReturn(syntheticFiles(0, FileBundleReferenceRest.MAX_FILES_PAGE_SIZE));

    // Caller asks for 5000 per page — server caps at MAX_FILES_PAGE_SIZE.
    var r = resource.listGroupFiles(BUNDLE_APP_ID, GROUP_APP_ID, 0, 5000, securityContext);
    var paged = (de.dlr.shepard.v2.bundle.io.PagedFilesIO) r.getEntity();
    assertEquals(FileBundleReferenceRest.MAX_FILES_PAGE_SIZE, paged.getSize());
    assertEquals(FileBundleReferenceRest.MAX_FILES_PAGE_SIZE, paged.getItems().size());
  }

  @Test
  void listGroupFiles_negativePageSize_clampsToOne() {
    stubReadAllowedForBundleParent();
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn(BUNDLE_APP_ID);
    when(fileGroupService.countFiles(GROUP_APP_ID)).thenReturn(10L);
    when(fileGroupService.listFiles(GROUP_APP_ID, 0, 1)).thenReturn(syntheticFiles(0, 1));

    var r = resource.listGroupFiles(BUNDLE_APP_ID, GROUP_APP_ID, null, -50, securityContext);
    var paged = (de.dlr.shepard.v2.bundle.io.PagedFilesIO) r.getEntity();
    assertEquals(1, paged.getSize());
    assertEquals(1, paged.getItems().size());
    assertEquals(10, paged.getTotalPages());
  }

  @Test
  void listGroupFiles_returns404WhenBundleMissing() {
    stubReadAllowedForBundleParent();
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(null);
    var r = resource.listGroupFiles(BUNDLE_APP_ID, GROUP_APP_ID, null, null, securityContext);
    assertEquals(404, r.getStatus());
  }

  @Test
  void listGroupFiles_returns404WhenGroupNotFound() {
    stubReadAllowedForBundleParent();
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn(null);
    assertEquals(404, resource.listGroupFiles(BUNDLE_APP_ID, GROUP_APP_ID, null, null, securityContext).getStatus());
  }

  @Test
  void listGroupFiles_returns404WhenGroupBelongsToDifferentBundle() {
    stubReadAllowedForBundleParent();
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn("some-other-bundle");
    assertEquals(404, resource.listGroupFiles(BUNDLE_APP_ID, GROUP_APP_ID, null, null, securityContext).getStatus());
  }

  @Test
  void listGroupFiles_returns401WhenUnauthenticated() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(securityContext.getUserPrincipal()).thenReturn(null);
    var r = resource.listGroupFiles(BUNDLE_APP_ID, GROUP_APP_ID, null, null, securityContext);
    assertEquals(401, r.getStatus());
  }

  @Test
  void listGroupFiles_emptyGroup_returnsZeroItems() {
    stubReadAllowedForBundleParent();
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn(BUNDLE_APP_ID);
    when(fileGroupService.countFiles(GROUP_APP_ID)).thenReturn(0L);
    // skip=0 >= total=0 → listFiles never called

    var r = resource.listGroupFiles(BUNDLE_APP_ID, GROUP_APP_ID, null, null, securityContext);
    var paged = (de.dlr.shepard.v2.bundle.io.PagedFilesIO) r.getEntity();
    assertEquals(0L, paged.getTotalElements());
    assertEquals(0, paged.getTotalPages());
    assertEquals(0, paged.getItems().size());
  }

  // ─── APISIMP-COLLECTION-LIST-BUNDLE-SIZE-PAGESIZE reflection guards ────────

  @Test
  void deleteGroup_forceParam_hasParameterAnnotationWithDescription() throws NoSuchMethodException {
    java.lang.reflect.Method m = FileBundleReferenceRest.class.getMethod(
        "deleteGroup", String.class, String.class, boolean.class, jakarta.ws.rs.core.SecurityContext.class);
    java.lang.reflect.Parameter param = java.util.Arrays.stream(m.getParameters())
        .filter(p -> { var qp = p.getAnnotation(jakarta.ws.rs.QueryParam.class); return qp != null && "force".equals(qp.value()); })
        .findFirst().orElse(null);
    assertNotNull(param, "deleteGroup.force must carry @QueryParam");
    var ann = param.getAnnotation(org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
    assertNotNull(ann, "deleteGroup.force must carry @Parameter annotation");
    assertTrue(ann.description() != null && !ann.description().isBlank(), "@Parameter.description must be non-blank for deleteGroup.force");
  }

  @Test
  void listGroupFiles_pageParam_hasParameterAnnotationWithDescription() throws NoSuchMethodException {
    java.lang.reflect.Method m = FileBundleReferenceRest.class.getMethod(
        "listGroupFiles", String.class, String.class, Integer.class, Integer.class, jakarta.ws.rs.core.SecurityContext.class);
    java.lang.reflect.Parameter param = java.util.Arrays.stream(m.getParameters())
        .filter(p -> { var qp = p.getAnnotation(jakarta.ws.rs.QueryParam.class); return qp != null && "page".equals(qp.value()); })
        .findFirst().orElse(null);
    assertNotNull(param, "listGroupFiles.page must carry @QueryParam");
    var ann = param.getAnnotation(org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
    assertNotNull(ann, "listGroupFiles.page must carry @Parameter annotation");
    assertTrue(ann.description() != null && !ann.description().isBlank(), "@Parameter.description must be non-blank for listGroupFiles.page");
  }

  @Test
  void listGroupFiles_pageSizeParam_hasParameterAnnotationWithDescription() throws NoSuchMethodException {
    java.lang.reflect.Method m = FileBundleReferenceRest.class.getMethod(
        "listGroupFiles", String.class, String.class, Integer.class, Integer.class, jakarta.ws.rs.core.SecurityContext.class);
    java.lang.reflect.Parameter param = java.util.Arrays.stream(m.getParameters())
        .filter(p -> { var qp = p.getAnnotation(jakarta.ws.rs.QueryParam.class); return qp != null && "pageSize".equals(qp.value()); })
        .findFirst().orElse(null);
    assertNotNull(param, "listGroupFiles.pageSize must carry @QueryParam");
    var ann = param.getAnnotation(org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
    assertNotNull(ann, "listGroupFiles.pageSize must carry @Parameter annotation");
    assertTrue(ann.description() != null && !ann.description().isBlank(), "@Parameter.description must be non-blank for listGroupFiles.pageSize");
  }
}
