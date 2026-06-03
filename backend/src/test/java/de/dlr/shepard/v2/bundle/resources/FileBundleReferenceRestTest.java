package de.dlr.shepard.v2.bundle.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.data.file.services.FileService;
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
  FileService fileService;

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
    resource.fileService = fileService;
    resource.permissionsService = permissionsService;
    resource.entityIdResolver = entityIdResolver;
    resource.objectMapper = new ObjectMapper();
    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq("do-app-id"), any(AccessType.class), eq(CALLER))).thenReturn(true);
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
  void listGroups_returns200WithRows() {
    var g1 = new FileGroup(1L);
    g1.setIndex(0);
    g1.setName("default");
    var g2 = new FileGroup(2L);
    g2.setIndex(1);
    g2.setName("phase1");
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.listGroups(BUNDLE_APP_ID)).thenReturn(List.of(g1, g2));

    var r = resource.listGroups(BUNDLE_APP_ID, securityContext);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    List<FileGroupIO> rows = (List<FileGroupIO>) r.getEntity();
    assertEquals(2, rows.size());
  }

  @Test
  void listGroups_returns404WhenBundleMissing() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(null);
    assertEquals(404, resource.listGroups(BUNDLE_APP_ID, securityContext).getStatus());
  }

  @Test
  void listGroups_returns403WhenNoReadPermission() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq("do-app-id"), eq(AccessType.Read), eq(CALLER))).thenReturn(false);
    assertEquals(403, resource.listGroups(BUNDLE_APP_ID, securityContext).getStatus());
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

  // ── MFFD-IMAGEBUNDLE-PAGINATE-1 — paginated GET /files ───────────────────

  /**
   * Build a FileGroup populated with N synthetic ShepardFile records.
   * Used by every pagination test to drive the slicing math.
   */
  private FileGroup populatedGroup(int fileCount) {
    var group = new FileGroup(11L);
    group.setAppId(GROUP_APP_ID);
    group.setName("default");
    group.setIndex(0);
    var files = new java.util.ArrayList<de.dlr.shepard.data.file.entities.ShepardFile>(fileCount);
    for (int i = 0; i < fileCount; i++) {
      var f = new de.dlr.shepard.data.file.entities.ShepardFile();
      f.setFilename("frame-" + i + ".png");
      files.add(f);
    }
    group.setFiles(files);
    return group;
  }

  /** The pagination endpoint walks `checkAccess` → ensure the perm gate passes. */
  private void stubReadAllowedForBundleParent() {
    when(permissionsService.isAccessAllowedForDataObjectAppId("do-app-id", AccessType.Read, CALLER)).thenReturn(true);
  }

  @Test
  void listGroupFiles_defaultParams_returnsFirstPage() {
    stubReadAllowedForBundleParent();
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.getByAppId(GROUP_APP_ID)).thenReturn(populatedGroup(500));
    when(fileGroupService.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn(BUNDLE_APP_ID);

    var r = resource.listGroupFiles(BUNDLE_APP_ID, GROUP_APP_ID, null, null, securityContext);
    assertEquals(200, r.getStatus());
    var paged = (de.dlr.shepard.v2.bundle.io.PagedFilesIO) r.getEntity();
    assertEquals(0, paged.getPage());
    assertEquals(FileBundleReferenceRest.DEFAULT_FILES_PAGE_SIZE, paged.getSize());
    assertEquals(500L, paged.getTotalElements());
    assertEquals(3, paged.getTotalPages()); // 500 / 200 = ceil 3
    assertEquals(200, paged.getItems().size());
    assertEquals("frame-0.png", paged.getItems().get(0).getFilename());
    assertEquals("frame-199.png", paged.getItems().get(199).getFilename());
  }

  @Test
  void listGroupFiles_page1Size50_returnsCorrectSlice() {
    stubReadAllowedForBundleParent();
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.getByAppId(GROUP_APP_ID)).thenReturn(populatedGroup(120));
    when(fileGroupService.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn(BUNDLE_APP_ID);

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
    when(fileGroupService.getByAppId(GROUP_APP_ID)).thenReturn(populatedGroup(38));
    when(fileGroupService.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn(BUNDLE_APP_ID);

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
    when(fileGroupService.getByAppId(GROUP_APP_ID)).thenReturn(populatedGroup(2000));
    when(fileGroupService.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn(BUNDLE_APP_ID);

    // Caller asks for 5000 per page — server caps at MAX_FILES_PAGE_SIZE.
    var r = resource.listGroupFiles(BUNDLE_APP_ID, GROUP_APP_ID, 0, 5000, securityContext);
    var paged = (de.dlr.shepard.v2.bundle.io.PagedFilesIO) r.getEntity();
    assertEquals(FileBundleReferenceRest.MAX_FILES_PAGE_SIZE, paged.getSize());
    assertEquals(1000, paged.getItems().size());
  }

  @Test
  void listGroupFiles_negativePageSize_clampsToOne() {
    stubReadAllowedForBundleParent();
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.getByAppId(GROUP_APP_ID)).thenReturn(populatedGroup(10));
    when(fileGroupService.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn(BUNDLE_APP_ID);

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
  void listGroupFiles_returns404WhenGroupBelongsToDifferentBundle() {
    stubReadAllowedForBundleParent();
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.getByAppId(GROUP_APP_ID)).thenReturn(populatedGroup(5));
    when(fileGroupService.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn("some-other-bundle");

    var r = resource.listGroupFiles(BUNDLE_APP_ID, GROUP_APP_ID, null, null, securityContext);
    assertEquals(404, r.getStatus());
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
    when(fileGroupService.getByAppId(GROUP_APP_ID)).thenReturn(populatedGroup(0));
    when(fileGroupService.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn(BUNDLE_APP_ID);

    var r = resource.listGroupFiles(BUNDLE_APP_ID, GROUP_APP_ID, null, null, securityContext);
    var paged = (de.dlr.shepard.v2.bundle.io.PagedFilesIO) r.getEntity();
    assertEquals(0L, paged.getTotalElements());
    assertEquals(0, paged.getTotalPages());
    assertEquals(0, paged.getItems().size());
  }
}
