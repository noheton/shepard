package de.dlr.shepard.v2.containers.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.permission.io.PermissionsIO;
import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.common.util.PermissionType;
import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.v2.containers.io.ContainerV2IO;
import de.dlr.shepard.v2.containers.services.ContainersV2Service;
import de.dlr.shepard.v2.containers.spi.ContainerFileDownload;
import de.dlr.shepard.v2.containers.spi.ContainerKindHandler;
import jakarta.ws.rs.core.SecurityContext;
import java.io.InputStream;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * V2CONV-A3 — plain-Mockito unit "integration" coverage for
 * {@link ContainersV2Rest}: the create/get/patch/delete/list status-code +
 * permission-gating contract of the unified {@code /v2/containers} surface.
 * Mirrors {@code ReferencesV2RestTest}.
 */
class ContainersV2RestTest {

  private static final String CALLER = "alice";
  private static final long CONTAINER_NEO_ID = 42L;
  private static final String APP_ID = "container-app-9";

  @Mock
  ContainersV2Service containersService;

  @Mock
  PermissionsService permissionsService;

  @Mock
  ContainerKindHandler handler;

  @Mock
  SecurityContext securityContext;

  @Mock
  Principal principal;

  final ObjectMapper om = new ObjectMapper();
  ContainersV2Rest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new ContainersV2Rest();
    resource.containersService = containersService;
    resource.permissionsService = permissionsService;
    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
  }

  private FileContainer container() {
    var c = new FileContainer(CONTAINER_NEO_ID);
    c.setAppId(APP_ID);
    return c;
  }

  private ContainersV2Service.ResolvedContainer resolved() {
    return new ContainersV2Service.ResolvedContainer(handler, container());
  }

  // ─── create ──────────────────────────────────────────────────────────────

  @Test
  void create_returns201WhenAuthenticated() throws Exception {
    when(containersService.create(eq("file"), any())).thenReturn(new ContainerV2IO());
    var r = resource.create("file", om.readTree("{\"name\":\"scans\"}"), securityContext);
    assertEquals(201, r.getStatus());
  }

  @Test
  void create_returns400WhenKindMissing() throws Exception {
    var r = resource.create(null, om.readTree("{}"), securityContext);
    assertEquals(400, r.getStatus());
  }

  @Test
  void create_returns401WhenUnauthenticated() throws Exception {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    var r = resource.create("file", om.readTree("{}"), securityContext);
    assertEquals(401, r.getStatus());
  }

  @Test
  void create_returns400WhenUnknownKind() throws Exception {
    when(containersService.create(eq("hdf"), any()))
      .thenThrow(new jakarta.ws.rs.BadRequestException("unknown kind"));
    var r = resource.create("hdf", om.readTree("{\"name\":\"x\"}"), securityContext);
    assertEquals(400, r.getStatus());
  }

  // ─── get ───────────────────────────────────────────────────────────────────

  @Test
  void get_returns200WhenAllowed() {
    when(containersService.resolveByAppId(APP_ID)).thenReturn(Optional.of(resolved()));
    when(permissionsService.isAccessTypeAllowedForUser(eq(CONTAINER_NEO_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(true);
    when(handler.toIO(any())).thenReturn(new ContainerV2IO());
    var r = resource.get(APP_ID, securityContext);
    assertEquals(200, r.getStatus());
  }

  @Test
  void get_returns404WhenUnknown() {
    when(containersService.resolveByAppId(APP_ID)).thenReturn(Optional.empty());
    var r = resource.get(APP_ID, securityContext);
    assertEquals(404, r.getStatus());
  }

  @Test
  void get_returns403WhenNoRead() {
    when(containersService.resolveByAppId(APP_ID)).thenReturn(Optional.of(resolved()));
    when(permissionsService.isAccessTypeAllowedForUser(eq(CONTAINER_NEO_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(false);
    var r = resource.get(APP_ID, securityContext);
    assertEquals(403, r.getStatus());
  }

  // ─── patch ─────────────────────────────────────────────────────────────────

  @Test
  void patch_returns200WhenAllowed() throws Exception {
    when(containersService.resolveByAppId(APP_ID)).thenReturn(Optional.of(resolved()));
    when(permissionsService.isAccessTypeAllowedForUser(eq(CONTAINER_NEO_ID), eq(AccessType.Write), eq(CALLER)))
      .thenReturn(true);
    when(containersService.patchByAppId(eq(APP_ID), any())).thenReturn(new ContainerV2IO());
    var r = resource.patch(APP_ID, om.readTree("{\"name\":\"n\"}"), securityContext);
    assertEquals(200, r.getStatus());
  }

  @Test
  void patch_returns400WhenBodyNotObject() throws Exception {
    var r = resource.patch(APP_ID, om.readTree("[1,2,3]"), securityContext);
    assertEquals(400, r.getStatus());
  }

  @Test
  void patch_returns403WhenNoWrite() throws Exception {
    when(containersService.resolveByAppId(APP_ID)).thenReturn(Optional.of(resolved()));
    when(permissionsService.isAccessTypeAllowedForUser(eq(CONTAINER_NEO_ID), eq(AccessType.Write), eq(CALLER)))
      .thenReturn(false);
    var r = resource.patch(APP_ID, om.readTree("{\"name\":\"n\"}"), securityContext);
    assertEquals(403, r.getStatus());
  }

  // ─── delete ──────────────────────────────────────────────────────────────

  @Test
  void delete_returns204WhenAllowed() {
    when(containersService.resolveByAppId(APP_ID)).thenReturn(Optional.of(resolved()));
    when(permissionsService.isAccessTypeAllowedForUser(eq(CONTAINER_NEO_ID), eq(AccessType.Write), eq(CALLER)))
      .thenReturn(true);
    var r = resource.delete(APP_ID, securityContext);
    assertEquals(204, r.getStatus());
    verify(containersService).deleteByAppId(APP_ID);
  }

  @Test
  void delete_returns404WhenUnknown() {
    when(containersService.resolveByAppId(APP_ID)).thenReturn(Optional.empty());
    var r = resource.delete(APP_ID, securityContext);
    assertEquals(404, r.getStatus());
  }

  // ─── file download (V2CONV-A7-HDF) ─────────────────────────────────────────

  private void allowRead() {
    when(containersService.resolveByAppId(APP_ID)).thenReturn(Optional.of(resolved()));
    when(permissionsService.isAccessTypeAllowedForUser(eq(CONTAINER_NEO_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(true);
  }

  @Test
  void downloadFile_returns200WithMediaTypeAndDisposition() {
    allowRead();
    var download = new ContainerFileDownload(
      200,
      InputStream.nullInputStream(),
      9L,
      null,
      "bytes",
      "primary.h5",
      "application/x-hdf5"
    );
    when(handler.downloadFile(eq(APP_ID), eq(null))).thenReturn(Optional.of(download));

    var r = resource.downloadFile(APP_ID, null, securityContext);
    assertEquals(200, r.getStatus());
    assertEquals("application/x-hdf5", r.getMediaType().toString());
    String cd = (String) r.getHeaders().getFirst("Content-Disposition");
    org.junit.jupiter.api.Assertions.assertNotNull(cd, "Content-Disposition must be set");
    org.junit.jupiter.api.Assertions.assertTrue(cd.contains("attachment"), "must be attachment");
    org.junit.jupiter.api.Assertions.assertTrue(cd.contains("primary.h5"), "filename must be present");
  }

  @Test
  void downloadFile_returns206WithRangeHeaders() {
    allowRead();
    var download = new ContainerFileDownload(
      206,
      InputStream.nullInputStream(),
      4L,
      "bytes 0-3/9",
      "bytes",
      "run-data.h5",
      "application/x-hdf5"
    );
    when(handler.downloadFile(eq(APP_ID), eq("bytes=0-3"))).thenReturn(Optional.of(download));

    var r = resource.downloadFile(APP_ID, "bytes=0-3", securityContext);
    assertEquals(206, r.getStatus());
    assertEquals("bytes 0-3/9", r.getHeaders().getFirst("Content-Range"));
    assertEquals("bytes", r.getHeaders().getFirst("Accept-Ranges"));
  }

  @Test
  void downloadFile_returns415WhenKindHasNoFilePayload() {
    allowRead();
    when(handler.kind()).thenReturn("timeseries");
    when(handler.downloadFile(eq(APP_ID), eq(null))).thenReturn(Optional.empty());
    var r = resource.downloadFile(APP_ID, null, securityContext);
    assertEquals(415, r.getStatus());
  }

  @Test
  void downloadFile_returns404WhenUnknown() {
    when(containersService.resolveByAppId(APP_ID)).thenReturn(Optional.empty());
    var r = resource.downloadFile(APP_ID, null, securityContext);
    assertEquals(404, r.getStatus());
  }

  @Test
  void downloadFile_returns403WhenNoRead() {
    when(containersService.resolveByAppId(APP_ID)).thenReturn(Optional.of(resolved()));
    when(permissionsService.isAccessTypeAllowedForUser(eq(CONTAINER_NEO_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(false);
    var r = resource.downloadFile(APP_ID, null, securityContext);
    assertEquals(403, r.getStatus());
  }

  @Test
  void downloadFile_returns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    var r = resource.downloadFile(APP_ID, null, securityContext);
    assertEquals(401, r.getStatus());
  }

  // ─── permissions helpers ───────────────────────────────────────────────────

  private Permissions permissions() {
    return new Permissions(container(), new User(CALLER), PermissionType.Private);
  }

  // ─── getPermissions ────────────────────────────────────────────────────────

  @Test
  void getPermissions_returns200WhenManageAllowed() {
    when(containersService.resolveByAppId(APP_ID)).thenReturn(Optional.of(resolved()));
    when(permissionsService.isAccessTypeAllowedForUser(eq(CONTAINER_NEO_ID), eq(AccessType.Manage), eq(CALLER)))
      .thenReturn(true);
    when(permissionsService.getPermissionsOfEntityOptional(CONTAINER_NEO_ID))
      .thenReturn(Optional.of(permissions()));
    var r = resource.getPermissions(APP_ID, securityContext);
    assertEquals(200, r.getStatus());
  }

  @Test
  void getPermissions_returns404WhenUnknownAppId() {
    when(containersService.resolveByAppId(APP_ID)).thenReturn(Optional.empty());
    var r = resource.getPermissions(APP_ID, securityContext);
    assertEquals(404, r.getStatus());
  }

  @Test
  void getPermissions_returns403WhenNoManage() {
    when(containersService.resolveByAppId(APP_ID)).thenReturn(Optional.of(resolved()));
    when(permissionsService.isAccessTypeAllowedForUser(eq(CONTAINER_NEO_ID), eq(AccessType.Manage), eq(CALLER)))
      .thenReturn(false);
    var r = resource.getPermissions(APP_ID, securityContext);
    assertEquals(403, r.getStatus());
  }

  @Test
  void getPermissions_returns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    var r = resource.getPermissions(APP_ID, securityContext);
    assertEquals(401, r.getStatus());
  }

  @Test
  void getPermissions_returns404WhenPermsEmpty() {
    // resolve + gate succeed, but the permissions node is absent in the DB
    when(containersService.resolveByAppId(APP_ID)).thenReturn(Optional.of(resolved()));
    when(permissionsService.isAccessTypeAllowedForUser(eq(CONTAINER_NEO_ID), eq(AccessType.Manage), eq(CALLER)))
      .thenReturn(true);
    when(permissionsService.getPermissionsOfEntityOptional(CONTAINER_NEO_ID))
      .thenReturn(Optional.empty());
    var r = resource.getPermissions(APP_ID, securityContext);
    assertEquals(404, r.getStatus());
  }

  // ─── patchPermissions ──────────────────────────────────────────────────────

  @Test
  void patchPermissions_returns200WhenManageAllowed() throws Exception {
    when(containersService.resolveByAppId(APP_ID)).thenReturn(Optional.of(resolved()));
    when(permissionsService.isAccessTypeAllowedForUser(eq(CONTAINER_NEO_ID), eq(AccessType.Manage), eq(CALLER)))
      .thenReturn(true);
    when(permissionsService.getPermissionsOfEntityOptional(CONTAINER_NEO_ID))
      .thenReturn(Optional.of(permissions()));
    when(permissionsService.updatePermissionsByNeo4jId(any(PermissionsIO.class), eq(CONTAINER_NEO_ID)))
      .thenReturn(permissions());
    var r = resource.patchPermissions(APP_ID, om.readTree("{\"permissionType\":\"Private\"}"), securityContext);
    assertEquals(200, r.getStatus());
  }

  @Test
  void patchPermissions_returns400WhenBodyNotObject() throws Exception {
    var r = resource.patchPermissions(APP_ID, om.readTree("[1,2,3]"), securityContext);
    assertEquals(400, r.getStatus());
  }

  @Test
  void patchPermissions_returns404WhenUnknownAppId() throws Exception {
    when(containersService.resolveByAppId(APP_ID)).thenReturn(Optional.empty());
    var r = resource.patchPermissions(APP_ID, om.readTree("{\"permissionType\":\"Private\"}"), securityContext);
    assertEquals(404, r.getStatus());
  }

  @Test
  void patchPermissions_returns403WhenNoManage() throws Exception {
    when(containersService.resolveByAppId(APP_ID)).thenReturn(Optional.of(resolved()));
    when(permissionsService.isAccessTypeAllowedForUser(eq(CONTAINER_NEO_ID), eq(AccessType.Manage), eq(CALLER)))
      .thenReturn(false);
    var r = resource.patchPermissions(APP_ID, om.readTree("{\"permissionType\":\"Private\"}"), securityContext);
    assertEquals(403, r.getStatus());
  }

  @Test
  void patchPermissions_returns404WhenPermsEmpty() throws Exception {
    // resolve + gate succeed, but the permissions node is absent in the DB
    when(containersService.resolveByAppId(APP_ID)).thenReturn(Optional.of(resolved()));
    when(permissionsService.isAccessTypeAllowedForUser(eq(CONTAINER_NEO_ID), eq(AccessType.Manage), eq(CALLER)))
      .thenReturn(true);
    when(permissionsService.getPermissionsOfEntityOptional(CONTAINER_NEO_ID))
      .thenReturn(Optional.empty());
    var r = resource.patchPermissions(APP_ID, om.readTree("{\"permissionType\":\"Private\"}"), securityContext);
    assertEquals(404, r.getStatus());
  }

  // ─── list ──────────────────────────────────────────────────────────────────

  @Test
  void list_returns200WithNameFilter() {
    when(containersService.list(eq("file"), eq("sca"))).thenReturn(List.of(new ContainerV2IO()));
    var r = resource.list("file", "sca", securityContext);
    assertEquals(200, r.getStatus());
    verify(containersService).list("file", "sca");
  }

  @Test
  void list_returns400WhenMissingKind() {
    var r = resource.list(null, null, securityContext);
    assertEquals(400, r.getStatus());
  }

  @Test
  void list_returns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    var r = resource.list("file", null, securityContext);
    assertEquals(401, r.getStatus());
  }
}
