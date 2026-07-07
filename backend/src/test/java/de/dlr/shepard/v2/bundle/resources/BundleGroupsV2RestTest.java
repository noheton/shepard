package de.dlr.shepard.v2.bundle.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.file.daos.FileBundleReferenceDAO;
import de.dlr.shepard.context.references.file.entities.FileBundleReference;
import de.dlr.shepard.context.references.file.entities.FileGroup;
import de.dlr.shepard.context.references.file.io.CreateFileGroupIO;
import de.dlr.shepard.context.references.file.io.FileGroupIO;
import de.dlr.shepard.context.references.file.services.FileGroupService;
import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.data.file.entities.ShepardFile;
import de.dlr.shepard.storage.FileStorageService;
import de.dlr.shepard.v2.bundle.io.PagedFilesIO;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.List;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Plain-Mockito unit tests for {@link BundleGroupsV2Rest} (APISIMP-BUNDLE-REF-KIND-UNIFY slice 1).
 * Exercises all 7 group endpoints and their 401 / 403 / 404 / 400 branches.
 * Mirrors {@link FileBundleReferenceRestTest} style.
 */
class BundleGroupsV2RestTest {

  private static final String BUNDLE_APP_ID = "bundle-appid-1";
  private static final String GROUP_APP_ID  = "group-appid-1";
  private static final String CALLER        = "alice";
  private static final long   DO_OGM_ID     = 42L;

  @Mock FileBundleReferenceDAO fileBundleReferenceDAO;
  @Mock FileGroupService       fileGroupService;
  @Mock FileStorageService     fileStorageService;
  @Mock PermissionsService     permissionsService;
  @Mock SecurityContext        securityContext;
  @Mock Principal              principal;
  @Mock FileUpload             fileUpload;

  BundleGroupsV2Rest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new BundleGroupsV2Rest();
    resource.fileBundleReferenceDAO = fileBundleReferenceDAO;
    resource.fileGroupService       = fileGroupService;
    resource.fileStorageService     = fileStorageService;
    resource.permissionsService     = permissionsService;
    resource.objectMapper           = new ObjectMapper();
    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
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

  private FileGroup existingGroup() {
    var g = new FileGroup(1L);
    g.setAppId(GROUP_APP_ID);
    g.setName("default");
    g.setIndex(0);
    return g;
  }

  // ─── GET /v2/references/{bundleAppId}/groups ──────────────────────────────

  @Test
  void listGroups_returns200WithPagedEnvelope() {
    var g1 = existingGroup();
    var g2 = new FileGroup(2L);
    g2.setName("phase1");
    g2.setIndex(1);
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.countGroups(BUNDLE_APP_ID)).thenReturn(2L);
    when(fileGroupService.listGroups(BUNDLE_APP_ID, 0, 50)).thenReturn(List.of(g1, g2));

    var r = resource.listGroups(BUNDLE_APP_ID, 0, 50, securityContext);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<FileGroupIO> envelope = (PagedResponseIO<FileGroupIO>) r.getEntity();
    assertEquals(2L, envelope.total());
    assertEquals(2, envelope.items().size());
  }

  @Test
  void listGroups_returns404WhenBundleMissing() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(null);
    assertEquals(404, resource.listGroups(BUNDLE_APP_ID, 0, 50, securityContext).getStatus());
  }

  @Test
  void listGroups_returns401WhenUnauthenticated() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(securityContext.getUserPrincipal()).thenReturn(null);
    assertEquals(401, resource.listGroups(BUNDLE_APP_ID, 0, 50, securityContext).getStatus());
  }

  @Test
  void listGroups_returns403WhenNoPermission() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(permissionsService.isAccessAllowedForDataObjectAppId(anyString(), eq(AccessType.Read), eq(CALLER))).thenReturn(false);
    assertEquals(403, resource.listGroups(BUNDLE_APP_ID, 0, 50, securityContext).getStatus());
  }

  // ─── POST /v2/references/{bundleAppId}/groups ─────────────────────────────

  @Test
  void createGroup_returns201() {
    var body = new CreateFileGroupIO();
    body.setName("new-group");
    var created = existingGroup();
    created.setName("new-group");
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.createGroup(eq(BUNDLE_APP_ID), any())).thenReturn(created);

    var r = resource.createGroup(BUNDLE_APP_ID, body, securityContext);
    assertEquals(201, r.getStatus());
    var io = (FileGroupIO) r.getEntity();
    assertEquals("new-group", io.getName());
  }

  @Test
  void createGroup_returns400WhenNameBlank() {
    var body = new CreateFileGroupIO();
    body.setName("  ");
    assertEquals(400, resource.createGroup(BUNDLE_APP_ID, body, securityContext).getStatus());
    verify(fileBundleReferenceDAO, never()).findByAppId(anyString());
  }

  @Test
  void createGroup_returns400WhenNameNull() {
    assertEquals(400, resource.createGroup(BUNDLE_APP_ID, null, securityContext).getStatus());
  }

  @Test
  void createGroup_returns404WhenBundleMissing() {
    var body = new CreateFileGroupIO();
    body.setName("g");
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(null);
    assertEquals(404, resource.createGroup(BUNDLE_APP_ID, body, securityContext).getStatus());
  }

  @Test
  void createGroup_returns403WhenNoWritePermission() {
    var body = new CreateFileGroupIO();
    body.setName("g");
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(permissionsService.isAccessAllowedForDataObjectAppId(anyString(), eq(AccessType.Write), eq(CALLER))).thenReturn(false);
    assertEquals(403, resource.createGroup(BUNDLE_APP_ID, body, securityContext).getStatus());
  }

  // ─── GET /v2/references/{bundleAppId}/groups/{groupAppId} ─────────────────

  @Test
  void getGroup_returns200() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.getByAppId(GROUP_APP_ID)).thenReturn(existingGroup());
    when(fileGroupService.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn(BUNDLE_APP_ID);

    var r = resource.getGroup(BUNDLE_APP_ID, GROUP_APP_ID, securityContext);
    assertEquals(200, r.getStatus());
    assertEquals(GROUP_APP_ID, ((FileGroupIO) r.getEntity()).getAppId());
  }

  @Test
  void getGroup_returns404WhenGroupMissing() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.getByAppId(GROUP_APP_ID)).thenReturn(null);
    assertEquals(404, resource.getGroup(BUNDLE_APP_ID, GROUP_APP_ID, securityContext).getStatus());
  }

  @Test
  void getGroup_returns404WhenGroupNotInBundle() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.getByAppId(GROUP_APP_ID)).thenReturn(existingGroup());
    when(fileGroupService.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn("other-bundle");
    assertEquals(404, resource.getGroup(BUNDLE_APP_ID, GROUP_APP_ID, securityContext).getStatus());
  }

  // ─── PATCH /v2/references/{bundleAppId}/groups/{groupAppId} ──────────────

  @Test
  void patchGroup_returns200() throws Exception {
    var mapper = new ObjectMapper();
    var body = mapper.readTree("{\"name\":\"renamed\"}");
    var updated = existingGroup();
    updated.setName("renamed");
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn(BUNDLE_APP_ID);
    when(fileGroupService.patchGroup(eq(GROUP_APP_ID), any())).thenReturn(updated);

    var r = resource.patchGroup(BUNDLE_APP_ID, GROUP_APP_ID, body, securityContext);
    assertEquals(200, r.getStatus());
    assertEquals("renamed", ((FileGroupIO) r.getEntity()).getName());
  }

  @Test
  void patchGroup_returns400WhenBodyNotObject() {
    assertEquals(400, resource.patchGroup(BUNDLE_APP_ID, GROUP_APP_ID, null, securityContext).getStatus());
  }

  @Test
  void patchGroup_returns404WhenGroupNotInBundle() throws Exception {
    var mapper = new ObjectMapper();
    var body = mapper.readTree("{\"name\":\"x\"}");
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn("other-bundle");
    assertEquals(404, resource.patchGroup(BUNDLE_APP_ID, GROUP_APP_ID, body, securityContext).getStatus());
  }

  // ─── DELETE /v2/references/{bundleAppId}/groups/{groupAppId} ─────────────

  @Test
  void deleteGroup_returns204() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn(BUNDLE_APP_ID);
    assertEquals(204, resource.deleteGroup(BUNDLE_APP_ID, GROUP_APP_ID, false, securityContext).getStatus());
    verify(fileGroupService).deleteGroup(GROUP_APP_ID, false);
  }

  @Test
  void deleteGroup_returns400WhenServiceThrowsBadRequest() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn(BUNDLE_APP_ID);
    doThrow(new BadRequestException("has files")).when(fileGroupService).deleteGroup(GROUP_APP_ID, false);
    assertEquals(400, resource.deleteGroup(BUNDLE_APP_ID, GROUP_APP_ID, false, securityContext).getStatus());
  }

  @Test
  void deleteGroup_returns404WhenGroupNotInBundle() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn("other-bundle");
    assertEquals(404, resource.deleteGroup(BUNDLE_APP_ID, GROUP_APP_ID, false, securityContext).getStatus());
  }

  // ─── GET /v2/references/{bundleAppId}/groups/{groupAppId}/files ───────────

  @Test
  void listGroupFiles_returns200WithPagedFiles() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn(BUNDLE_APP_ID);
    when(fileGroupService.countFiles(GROUP_APP_ID)).thenReturn(3L);
    var f1 = new ShepardFile(null, "frame001.png", null);
    when(fileGroupService.listFiles(eq(GROUP_APP_ID), eq(0), eq(200))).thenReturn(List.of(f1));

    var r = resource.listGroupFiles(BUNDLE_APP_ID, GROUP_APP_ID, 0, 200, securityContext);
    assertEquals(200, r.getStatus());
    var envelope = (PagedFilesIO) r.getEntity();
    assertEquals(3L, envelope.getTotalElements());
    assertEquals(1, envelope.getItems().size());
  }

  @Test
  void listGroupFiles_acceptsMaxPageSize() {
    // Bean Validation (@Max(1000)) rejects pageSize > 1000 at the JAX-RS layer.
    // Unit tests bypass BV, so we verify the boundary value (1000) passes correctly.
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn(BUNDLE_APP_ID);
    when(fileGroupService.countFiles(GROUP_APP_ID)).thenReturn(0L);

    var r = resource.listGroupFiles(BUNDLE_APP_ID, GROUP_APP_ID, 0, BundleGroupsV2Rest.MAX_FILES_PAGE_SIZE, securityContext);
    assertEquals(200, r.getStatus());
    var envelope = (PagedFilesIO) r.getEntity();
    assertEquals(BundleGroupsV2Rest.MAX_FILES_PAGE_SIZE, envelope.getSize());
  }

  @Test
  void listGroupFiles_returns404WhenGroupNotFound() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn(null);
    assertEquals(404, resource.listGroupFiles(BUNDLE_APP_ID, GROUP_APP_ID, 0, 200, securityContext).getStatus());
  }

  // ─── checkAccess() branch coverage ───────────────────────────────────────

  @Test
  void listGroups_returns404WhenBundleHasNoDataObject() {
    var bundle = new FileBundleReference(7L);
    bundle.setAppId(BUNDLE_APP_ID);
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(bundle);
    assertEquals(404, resource.listGroups(BUNDLE_APP_ID, 0, 50, securityContext).getStatus());
  }

  @Test
  void listGroups_200WhenDoAppIdNullAndOgmAccessAllowed() {
    var parent = new DataObject(DO_OGM_ID);
    // appId stays null → OGM-id fallback path; setUp stubs isAccessTypeAllowedForUser(DO_OGM_ID) → true
    var bundle = new FileBundleReference(7L);
    bundle.setAppId(BUNDLE_APP_ID);
    bundle.setDataObject(parent);
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(bundle);
    when(fileGroupService.countGroups(BUNDLE_APP_ID)).thenReturn(0L);
    when(fileGroupService.listGroups(BUNDLE_APP_ID, 0, 50)).thenReturn(List.of());
    assertEquals(200, resource.listGroups(BUNDLE_APP_ID, 0, 50, securityContext).getStatus());
  }

  @Test
  void listGroups_403WhenDoAppIdNullAndOgmAccessDenied() {
    var parent = new DataObject(DO_OGM_ID);
    var bundle = new FileBundleReference(7L);
    bundle.setAppId(BUNDLE_APP_ID);
    bundle.setDataObject(parent);
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(bundle);
    when(permissionsService.isAccessTypeAllowedForUser(eq(DO_OGM_ID), any(AccessType.class), eq(CALLER))).thenReturn(false);
    assertEquals(403, resource.listGroups(BUNDLE_APP_ID, 0, 50, securityContext).getStatus());
  }

  // ─── createGroup() service-exception branches ─────────────────────────────

  @Test
  void createGroup_returns400WhenServiceThrowsBadRequest() {
    var body = new CreateFileGroupIO();
    body.setName("g");
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    doThrow(new BadRequestException("dup")).when(fileGroupService).createGroup(eq(BUNDLE_APP_ID), any());
    assertEquals(400, resource.createGroup(BUNDLE_APP_ID, body, securityContext).getStatus());
  }

  @Test
  void createGroup_returns404WhenServiceThrowsNotFound() {
    var body = new CreateFileGroupIO();
    body.setName("g");
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    doThrow(new NotFoundException("gone")).when(fileGroupService).createGroup(eq(BUNDLE_APP_ID), any());
    assertEquals(404, resource.createGroup(BUNDLE_APP_ID, body, securityContext).getStatus());
  }

  // ─── patchGroup() service-exception branches + jsonNodeToMap types ────────

  @Test
  void patchGroup_returns400WhenServiceThrowsBadRequest() throws Exception {
    var body = new ObjectMapper().readTree("{\"name\":\"x\"}");
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn(BUNDLE_APP_ID);
    doThrow(new BadRequestException("bad")).when(fileGroupService).patchGroup(eq(GROUP_APP_ID), any());
    assertEquals(400, resource.patchGroup(BUNDLE_APP_ID, GROUP_APP_ID, body, securityContext).getStatus());
  }

  @Test
  void patchGroup_returns404WhenServiceThrowsNotFound() throws Exception {
    var body = new ObjectMapper().readTree("{\"name\":\"x\"}");
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn(BUNDLE_APP_ID);
    doThrow(new NotFoundException("gone")).when(fileGroupService).patchGroup(eq(GROUP_APP_ID), any());
    assertEquals(404, resource.patchGroup(BUNDLE_APP_ID, GROUP_APP_ID, body, securityContext).getStatus());
  }

  @Test
  void patchGroup_handlesAllJsonNodeTypes() throws Exception {
    // Exercises boolean, int/long, float (number), object, null, and array (else) branches in jsonNodeToMap
    var body = new ObjectMapper().readTree(
        "{\"flag\":true,\"count\":42,\"ratio\":3.14,\"sub\":{\"k\":\"v\"},\"tag\":null,\"arr\":[1]}");
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn(BUNDLE_APP_ID);
    when(fileGroupService.patchGroup(eq(GROUP_APP_ID), any())).thenReturn(existingGroup());
    assertEquals(200, resource.patchGroup(BUNDLE_APP_ID, GROUP_APP_ID, body, securityContext).getStatus());
  }

  // ─── deleteGroup() service-exception branch ──────────────────────────────

  @Test
  void deleteGroup_returns404WhenServiceThrowsNotFound() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn(BUNDLE_APP_ID);
    doThrow(new NotFoundException("gone")).when(fileGroupService).deleteGroup(GROUP_APP_ID, false);
    assertEquals(404, resource.deleteGroup(BUNDLE_APP_ID, GROUP_APP_ID, false, securityContext).getStatus());
  }

  // ─── listGroupFiles() extra branches ─────────────────────────────────────

  @Test
  void listGroupFiles_returnsEmptyListWhenSkipBeyondTotal() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn(BUNDLE_APP_ID);
    when(fileGroupService.countFiles(GROUP_APP_ID)).thenReturn(5L);
    // page=1, pageSize=200 → skip=200 which is >= total=5
    var r = resource.listGroupFiles(BUNDLE_APP_ID, GROUP_APP_ID, 1, 200, securityContext);
    assertEquals(200, r.getStatus());
    var envelope = (PagedFilesIO) r.getEntity();
    assertEquals(0, envelope.getItems().size());
    assertEquals(5L, envelope.getTotalElements());
  }

  @Test
  void listGroupFiles_returns404WhenGroupNotInBundle() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn("other-bundle");
    assertEquals(404, resource.listGroupFiles(BUNDLE_APP_ID, GROUP_APP_ID, 0, 50, securityContext).getStatus());
  }

  // ─── uploadFileIntoGroup() branches ──────────────────────────────────────

  @Test
  void uploadFileIntoGroup_returns404WhenGroupNotInBundle() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn("other-bundle");
    assertEquals(404, resource.uploadFileIntoGroup(BUNDLE_APP_ID, GROUP_APP_ID, null, securityContext).getStatus());
  }

  @Test
  void uploadFileIntoGroup_returns400WhenUploadNull() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(existingBundle());
    when(fileGroupService.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn(BUNDLE_APP_ID);
    assertEquals(400, resource.uploadFileIntoGroup(BUNDLE_APP_ID, GROUP_APP_ID, null, securityContext).getStatus());
  }

  @Test
  void uploadFileIntoGroup_returns400WhenFileContainerNull() {
    var parent = new DataObject(DO_OGM_ID);
    parent.setAppId("do-app-id");
    var bundle = new FileBundleReference(7L);
    bundle.setAppId(BUNDLE_APP_ID);
    bundle.setDataObject(parent);
    // fileContainer intentionally not set → null
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(bundle);
    when(fileGroupService.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn(BUNDLE_APP_ID);
    when(permissionsService.isAccessAllowedForDataObjectAppId(anyString(), any(AccessType.class), eq(CALLER))).thenReturn(true);
    when(fileUpload.uploadedFile()).thenReturn(Path.of("/tmp/dummy"));
    assertEquals(400, resource.uploadFileIntoGroup(BUNDLE_APP_ID, GROUP_APP_ID, fileUpload, securityContext).getStatus());
  }
}
