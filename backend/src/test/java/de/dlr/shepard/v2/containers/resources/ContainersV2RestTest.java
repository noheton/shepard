package de.dlr.shepard.v2.containers.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.mockito.Mockito.never;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.common.exceptions.ProblemJson;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.UUID;
import de.dlr.shepard.auth.permission.io.PermissionsIO;
import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.auth.permission.model.Roles;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.entities.UserGroup;
import de.dlr.shepard.auth.users.services.UserGroupService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.common.util.PermissionType;
import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import de.dlr.shepard.v2.containers.io.ContainerV2IO;
import de.dlr.shepard.v2.containers.services.ContainersV2Service;
import de.dlr.shepard.v2.containers.spi.ContainerFileDownload;
import de.dlr.shepard.v2.containers.spi.ContainerKindHandler;
import de.dlr.shepard.v2.integrity.SafeDeleteConflict;
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
  UserGroupService userGroupService;

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
    resource.userGroupService = userGroupService;
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
  void delete_returns204WhenAllowed_noLinkedDOs() {
    when(containersService.resolveByAppId(APP_ID)).thenReturn(Optional.of(resolved()));
    when(permissionsService.isAccessTypeAllowedForUser(eq(CONTAINER_NEO_ID), eq(AccessType.Write), eq(CALLER)))
      .thenReturn(true);
    // handler.findLinkedDataObjectAppIds returns Optional.empty() by default → kind has no linked-DO concept → safe to delete
    var r = resource.delete(APP_ID, false, securityContext);
    assertEquals(204, r.getStatus());
    verify(containersService).deleteByAppId(APP_ID);
  }

  @Test
  void delete_returns204WhenNoActiveRefs() {
    when(containersService.resolveByAppId(APP_ID)).thenReturn(Optional.of(resolved()));
    when(permissionsService.isAccessTypeAllowedForUser(eq(CONTAINER_NEO_ID), eq(AccessType.Write), eq(CALLER)))
      .thenReturn(true);
    when(handler.findLinkedDataObjectAppIds(APP_ID)).thenReturn(Optional.of(List.of()));
    var r = resource.delete(APP_ID, false, securityContext);
    assertEquals(204, r.getStatus());
    verify(containersService).deleteByAppId(APP_ID);
  }

  @Test
  void delete_returns409WhenActiveRefsAndNoForce() {
    when(containersService.resolveByAppId(APP_ID)).thenReturn(Optional.of(resolved()));
    when(permissionsService.isAccessTypeAllowedForUser(eq(CONTAINER_NEO_ID), eq(AccessType.Write), eq(CALLER)))
      .thenReturn(true);
    when(handler.findLinkedDataObjectAppIds(APP_ID)).thenReturn(Optional.of(List.of("linked-do-app-1")));
    var r = resource.delete(APP_ID, false, securityContext);
    assertEquals(409, r.getStatus());
    SafeDeleteConflict body = (SafeDeleteConflict) r.getEntity();
    assertEquals(1, body.referenceCount());
    assertEquals(List.of("linked-do-app-1"), body.sampleDataObjectAppIds());
    verify(containersService, never()).deleteByAppId(APP_ID);
  }

  @Test
  void delete_returns204WhenForceIgnoresActiveRefs() {
    when(containersService.resolveByAppId(APP_ID)).thenReturn(Optional.of(resolved()));
    when(permissionsService.isAccessTypeAllowedForUser(eq(CONTAINER_NEO_ID), eq(AccessType.Write), eq(CALLER)))
      .thenReturn(true);
    // With force=true, findLinkedDataObjectAppIds is NOT consulted
    var r = resource.delete(APP_ID, true, securityContext);
    assertEquals(204, r.getStatus());
    verify(containersService).deleteByAppId(APP_ID);
  }

  @Test
  void delete_returns404WhenUnknown() {
    when(containersService.resolveByAppId(APP_ID)).thenReturn(Optional.empty());
    var r = resource.delete(APP_ID, false, securityContext);
    assertEquals(404, r.getStatus());
  }

  // ─── put (P21-V2-METADATA-EDIT) ────────────────────────────────────────────

  @Test
  void put_returns200WhenAllowed() throws Exception {
    when(containersService.resolveByAppId(APP_ID)).thenReturn(Optional.of(resolved()));
    when(permissionsService.isAccessTypeAllowedForUser(eq(CONTAINER_NEO_ID), eq(AccessType.Write), eq(CALLER)))
      .thenReturn(true);
    when(containersService.putByAppId(eq(APP_ID), any())).thenReturn(new ContainerV2IO());
    var r = resource.put(APP_ID, om.readTree("{\"name\":\"new-name\"}"), securityContext);
    assertEquals(200, r.getStatus());
    verify(containersService).putByAppId(eq(APP_ID), any());
  }

  @Test
  void put_returns400WhenBodyNotObject() throws Exception {
    var r = resource.put(APP_ID, om.readTree("\"notanobject\""), securityContext);
    assertEquals(400, r.getStatus());
  }

  @Test
  void put_returns401WhenUnauthenticated() throws Exception {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    var r = resource.put(APP_ID, om.readTree("{\"name\":\"n\"}"), securityContext);
    assertEquals(401, r.getStatus());
  }

  @Test
  void put_returns403WhenNoWrite() throws Exception {
    when(containersService.resolveByAppId(APP_ID)).thenReturn(Optional.of(resolved()));
    when(permissionsService.isAccessTypeAllowedForUser(eq(CONTAINER_NEO_ID), eq(AccessType.Write), eq(CALLER)))
      .thenReturn(false);
    var r = resource.put(APP_ID, om.readTree("{\"name\":\"n\"}"), securityContext);
    assertEquals(403, r.getStatus());
  }

  @Test
  void put_returns404WhenUnknown() throws Exception {
    when(containersService.resolveByAppId(APP_ID)).thenReturn(Optional.empty());
    var r = resource.put(APP_ID, om.readTree("{\"name\":\"n\"}"), securityContext);
    assertEquals(404, r.getStatus());
  }

  @Test
  void put_returns400WhenServiceThrowsBadRequest() throws Exception {
    when(containersService.resolveByAppId(APP_ID)).thenReturn(Optional.of(resolved()));
    when(permissionsService.isAccessTypeAllowedForUser(eq(CONTAINER_NEO_ID), eq(AccessType.Write), eq(CALLER)))
      .thenReturn(true);
    when(containersService.putByAppId(eq(APP_ID), any()))
      .thenThrow(new jakarta.ws.rs.BadRequestException("'name' must be non-blank"));
    var r = resource.put(APP_ID, om.readTree("{\"name\":\"x\"}"), securityContext);
    assertEquals(400, r.getStatus());
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

  // APISIMP-CONTAINERS-PERMS-IO-NUMERIC: numeric group IDs rejected; appIds accepted

  @Test
  void patchPermissions_returns400WhenOnlyReaderGroupIdsProvidedWithoutAppIds() throws Exception {
    when(containersService.resolveByAppId(APP_ID)).thenReturn(Optional.of(resolved()));
    when(permissionsService.isAccessTypeAllowedForUser(eq(CONTAINER_NEO_ID), eq(AccessType.Manage), eq(CALLER)))
      .thenReturn(true);
    when(permissionsService.getPermissionsOfEntityOptional(CONTAINER_NEO_ID))
      .thenReturn(Optional.of(permissions()));
    var r = resource.patchPermissions(APP_ID,
      om.readTree("{\"readerGroupIds\":[10,11]}"), securityContext);
    assertEquals(400, r.getStatus());
  }

  @Test
  void patchPermissions_returns400WhenOnlyWriterGroupIdsProvidedWithoutAppIds() throws Exception {
    when(containersService.resolveByAppId(APP_ID)).thenReturn(Optional.of(resolved()));
    when(permissionsService.isAccessTypeAllowedForUser(eq(CONTAINER_NEO_ID), eq(AccessType.Manage), eq(CALLER)))
      .thenReturn(true);
    when(permissionsService.getPermissionsOfEntityOptional(CONTAINER_NEO_ID))
      .thenReturn(Optional.of(permissions()));
    var r = resource.patchPermissions(APP_ID,
      om.readTree("{\"writerGroupIds\":[20]}"), securityContext);
    assertEquals(400, r.getStatus());
  }

  @Test
  void patchPermissions_returns200WhenReaderGroupAppIdsProvided() throws Exception {
    when(containersService.resolveByAppId(APP_ID)).thenReturn(Optional.of(resolved()));
    when(permissionsService.isAccessTypeAllowedForUser(eq(CONTAINER_NEO_ID), eq(AccessType.Manage), eq(CALLER)))
      .thenReturn(true);
    when(permissionsService.getPermissionsOfEntityOptional(CONTAINER_NEO_ID))
      .thenReturn(Optional.of(permissions()));
    UserGroup g = new UserGroup(77L);
    g.setAppId("group-app-77");
    when(userGroupService.getUserGroupByAppId("group-app-77")).thenReturn(g);
    when(permissionsService.updatePermissionsByNeo4jId(any(PermissionsIO.class), eq(CONTAINER_NEO_ID)))
      .thenReturn(permissions());
    var r = resource.patchPermissions(APP_ID,
      om.readTree("{\"readerGroupAppIds\":[\"group-app-77\"]}"), securityContext);
    assertEquals(200, r.getStatus());
  }

  @Test
  void patchPermissions_returns200WhenWriterGroupAppIdsProvided() throws Exception {
    when(containersService.resolveByAppId(APP_ID)).thenReturn(Optional.of(resolved()));
    when(permissionsService.isAccessTypeAllowedForUser(eq(CONTAINER_NEO_ID), eq(AccessType.Manage), eq(CALLER)))
      .thenReturn(true);
    when(permissionsService.getPermissionsOfEntityOptional(CONTAINER_NEO_ID))
      .thenReturn(Optional.of(permissions()));
    UserGroup g = new UserGroup(88L);
    g.setAppId("group-app-88");
    when(userGroupService.getUserGroupByAppId("group-app-88")).thenReturn(g);
    when(permissionsService.updatePermissionsByNeo4jId(any(PermissionsIO.class), eq(CONTAINER_NEO_ID)))
      .thenReturn(permissions());
    var r = resource.patchPermissions(APP_ID,
      om.readTree("{\"writerGroupAppIds\":[\"group-app-88\"]}"), securityContext);
    assertEquals(200, r.getStatus());
  }

  // ─── listVersions (APISIMP-PV-UNIFY) ──────────────────────────────────────

  @Test
  void listVersions_returns200WithVersionList() {
    allowRead();
    var v = new de.dlr.shepard.v2.file.io.PayloadVersionIO(
      "pv-app-1", 1L, "oid1", "AABBCC", 512L, "alice", "2026-06-01T00:00:00Z"
    );
    when(handler.listVersions(eq(APP_ID), eq("sensor.csv"))).thenReturn(Optional.of(List.of(v)));
    var r = resource.listVersions(APP_ID, "sensor.csv", securityContext);
    assertEquals(200, r.getStatus());
    var list = (List<?>) r.getEntity();
    assertEquals(1, list.size());
  }

  @Test
  void listVersions_returns200WithEmptyList() {
    allowRead();
    when(handler.listVersions(eq(APP_ID), eq("sensor.csv"))).thenReturn(Optional.of(List.of()));
    var r = resource.listVersions(APP_ID, "sensor.csv", securityContext);
    assertEquals(200, r.getStatus());
    var list = (List<?>) r.getEntity();
    assertEquals(0, list.size());
  }

  @Test
  void listVersions_returns415WhenKindHasNoVersioning() {
    allowRead();
    when(handler.kind()).thenReturn("timeseries");
    when(handler.listVersions(eq(APP_ID), eq("sensor.csv"))).thenReturn(Optional.empty());
    var r = resource.listVersions(APP_ID, "sensor.csv", securityContext);
    assertEquals(415, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
    ProblemJson body = (ProblemJson) r.getEntity();
    assertEquals("/problems/containers.versioning-unsupported", body.type());
    assertEquals(415, body.status());
  }

  @Test
  void listVersions_returns404WhenUnknown() {
    when(containersService.resolveByAppId(APP_ID)).thenReturn(Optional.empty());
    var r = resource.listVersions(APP_ID, "sensor.csv", securityContext);
    assertEquals(404, r.getStatus());
  }

  @Test
  void listVersions_returns403WhenNoRead() {
    when(containersService.resolveByAppId(APP_ID)).thenReturn(Optional.of(resolved()));
    when(permissionsService.isAccessTypeAllowedForUser(eq(CONTAINER_NEO_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(false);
    var r = resource.listVersions(APP_ID, "sensor.csv", securityContext);
    assertEquals(403, r.getStatus());
  }

  @Test
  void listVersions_returns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    var r = resource.listVersions(APP_ID, "sensor.csv", securityContext);
    assertEquals(401, r.getStatus());
  }

  // ─── linked DataObjects (APISIMP-CONT-LDO-UNIFY) ───────────────────────────

  @Test
  void getLinkedDataObjects_returns200WithList() {
    allowRead();
    var col = new de.dlr.shepard.context.collection.entities.Collection();
    col.setShepardId(1L);
    var dataObject = new de.dlr.shepard.context.collection.entities.DataObject();
    dataObject.setAppId("do-app-1");
    dataObject.setCollection(col);
    var doIo = new de.dlr.shepard.context.collection.io.DataObjectIO(dataObject);
    when(handler.countLinkedDataObjects(eq(APP_ID))).thenReturn(Optional.of(1L));
    when(handler.listLinkedDataObjectsPaged(eq(APP_ID), eq(0), eq(50))).thenReturn(Optional.of(List.of(doIo)));
    var r = resource.getLinkedDataObjects(APP_ID, 0, 50, securityContext);
    assertEquals(200, r.getStatus());
    var page = (PagedResponseIO<?>) r.getEntity();
    assertEquals(1, page.total());
    assertEquals(1, page.items().size());
  }

  @Test
  void getLinkedDataObjects_returns200WithEmptyList() {
    allowRead();
    when(handler.countLinkedDataObjects(eq(APP_ID))).thenReturn(Optional.of(0L));
    when(handler.listLinkedDataObjectsPaged(eq(APP_ID), eq(0), eq(50))).thenReturn(Optional.of(List.of()));
    var r = resource.getLinkedDataObjects(APP_ID, 0, 50, securityContext);
    assertEquals(200, r.getStatus());
    var page = (PagedResponseIO<?>) r.getEntity();
    assertEquals(0, page.total());
    assertEquals(0, page.items().size());
  }

  @Test
  void getLinkedDataObjects_paginatesCorrectly() {
    allowRead();
    var col = new de.dlr.shepard.context.collection.entities.Collection();
    col.setShepardId(1L);
    var items = new java.util.ArrayList<de.dlr.shepard.context.collection.io.DataObjectIO>();
    for (int i = 0; i < 5; i++) {
      var do_ = new de.dlr.shepard.context.collection.entities.DataObject();
      do_.setAppId("do-app-" + i);
      do_.setCollection(col);
      items.add(new de.dlr.shepard.context.collection.io.DataObjectIO(do_));
    }
    when(handler.countLinkedDataObjects(eq(APP_ID))).thenReturn(Optional.of(5L));
    when(handler.listLinkedDataObjectsPaged(eq(APP_ID), eq(0), eq(2)))
        .thenReturn(Optional.of(items.subList(0, 2)));
    when(handler.listLinkedDataObjectsPaged(eq(APP_ID), eq(4), eq(2)))
        .thenReturn(Optional.of(items.subList(4, 5)));
    // page 0, size 2 → skip=0, limit=2 → items 0–1, total 5
    var r = resource.getLinkedDataObjects(APP_ID, 0, 2, securityContext);
    assertEquals(200, r.getStatus());
    var page = (PagedResponseIO<?>) r.getEntity();
    assertEquals(5, page.total());
    assertEquals(2, page.items().size());
    assertEquals(0, page.page());
    assertEquals(2, page.pageSize());
    // page 2, size 2 → skip=4, limit=2 → item 4, total 5
    var r2 = resource.getLinkedDataObjects(APP_ID, 2, 2, securityContext);
    var page2 = (PagedResponseIO<?>) r2.getEntity();
    assertEquals(5, page2.total());
    assertEquals(1, page2.items().size());
  }

  @Test
  void getLinkedDataObjects_returns415WhenKindHasNoLinkedConcept() {
    allowRead();
    when(handler.kind()).thenReturn("hdf");
    when(handler.countLinkedDataObjects(eq(APP_ID))).thenReturn(Optional.empty());
    var r = resource.getLinkedDataObjects(APP_ID, 0, 50, securityContext);
    assertEquals(415, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
    ProblemJson body = (ProblemJson) r.getEntity();
    assertEquals("/problems/containers.linked-data-objects-unsupported", body.type());
    assertEquals(415, body.status());
  }

  @Test
  void getLinkedDataObjects_returns404WhenUnknown() {
    when(containersService.resolveByAppId(APP_ID)).thenReturn(Optional.empty());
    var r = resource.getLinkedDataObjects(APP_ID, 0, 50, securityContext);
    assertEquals(404, r.getStatus());
  }

  @Test
  void getLinkedDataObjects_returns403WhenNoRead() {
    when(containersService.resolveByAppId(APP_ID)).thenReturn(Optional.of(resolved()));
    when(permissionsService.isAccessTypeAllowedForUser(eq(CONTAINER_NEO_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(false);
    var r = resource.getLinkedDataObjects(APP_ID, 0, 50, securityContext);
    assertEquals(403, r.getStatus());
  }

  @Test
  void getLinkedDataObjects_returns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    var r = resource.getLinkedDataObjects(APP_ID, 0, 50, securityContext);
    assertEquals(401, r.getStatus());
  }

  // ─── getRoles (V2-SWEEP-003-1) ─────────────────────────────────────────────

  @Test
  void getRoles_returns200WhenAllowed() {
    when(containersService.resolveByAppId(APP_ID)).thenReturn(Optional.of(resolved()));
    when(permissionsService.isAccessTypeAllowedForUser(eq(CONTAINER_NEO_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(true);
    when(permissionsService.getUserRolesOnEntity(CONTAINER_NEO_ID, CALLER))
      .thenReturn(new Roles(true, false, false, true));
    var r = resource.getRoles(APP_ID, securityContext);
    assertEquals(200, r.getStatus());
  }

  @Test
  void getRoles_returns404WhenUnknownAppId() {
    when(containersService.resolveByAppId(APP_ID)).thenReturn(Optional.empty());
    var r = resource.getRoles(APP_ID, securityContext);
    assertEquals(404, r.getStatus());
  }

  @Test
  void getRoles_returns403WhenNoRead() {
    when(containersService.resolveByAppId(APP_ID)).thenReturn(Optional.of(resolved()));
    when(permissionsService.isAccessTypeAllowedForUser(eq(CONTAINER_NEO_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(false);
    var r = resource.getRoles(APP_ID, securityContext);
    assertEquals(403, r.getStatus());
  }

  @Test
  void getRoles_returns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    var r = resource.getRoles(APP_ID, securityContext);
    assertEquals(401, r.getStatus());
  }

  // ─── list ──────────────────────────────────────────────────────────────────

  @Test
  void list_returns200WithQFilter() {
    when(containersService.count(eq("file"), eq("sca"))).thenReturn(1);
    when(containersService.list(eq("file"), eq("sca"), eq(0), eq(50))).thenReturn(List.of(new ContainerV2IO()));
    var r = resource.list("file", "sca", 0, 50, securityContext);
    assertEquals(200, r.getStatus());
    verify(containersService).count("file", "sca");
    verify(containersService).list("file", "sca", 0, 50);
  }

  /** Regression: ?name= must no longer be accepted — dropped at APISIMP-NAME-ALIAS-RETIRE. */
  @Test
  void list_nameParamIsGone() {
    var method = java.util.Arrays.stream(ContainersV2Rest.class.getMethods())
        .filter(m -> m.getName().equals("list"))
        .findFirst().orElseThrow();
    boolean hasNameParam = java.util.Arrays.stream(method.getParameters())
        .anyMatch(p -> {
          var qp = p.getAnnotation(jakarta.ws.rs.QueryParam.class);
          return qp != null && "name".equals(qp.value());
        });
    org.junit.jupiter.api.Assertions.assertFalse(hasNameParam,
        "list() must not declare @QueryParam(\"name\") after APISIMP-NAME-ALIAS-RETIRE");
  }

  @Test
  void list_returns400WhenMissingKind() {
    var r = resource.list(null, null, 0, 50, securityContext);
    assertEquals(400, r.getStatus());
  }

  @Test
  void list_returns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    var r = resource.list("file", null, 0, 50, securityContext);
    assertEquals(401, r.getStatus());
  }

  @Test
  void list_xTotalCountHeaderIsPresent() {
    when(containersService.count(eq("file"), isNull())).thenReturn(2);
    when(containersService.list(eq("file"), isNull(), eq(0), eq(50))).thenReturn(List.of(new ContainerV2IO(), new ContainerV2IO()));
    var r = resource.list("file", null, 0, 50, securityContext);
    assertEquals(200, r.getStatus());
  }

  @Test
  void list_paginationReturnsSublistWhenPageAndSizeProvided() {
    when(containersService.count(eq("file"), isNull())).thenReturn(3);
    when(containersService.list(eq("file"), isNull(), eq(0), eq(2)))
        .thenReturn(List.of(new ContainerV2IO(), new ContainerV2IO()));
    var r = resource.list("file", null, 0, 2, securityContext);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    var body = (PagedResponseIO<ContainerV2IO>) r.getEntity();
    assertEquals(2, body.items().size());
  }

  @Test
  void list_paginationPageBeyondRangeReturnsEmptyList() {
    when(containersService.count(eq("file"), isNull())).thenReturn(1);
    when(containersService.list(eq("file"), isNull(), eq(990), eq(10))).thenReturn(List.of());
    var r = resource.list("file", null, 99, 10, securityContext);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    var body = (PagedResponseIO<ContainerV2IO>) r.getEntity();
    assertEquals(0, body.items().size());
  }

  @Test
  void list_pageSizeCappedAt200() {
    var page0 = java.util.stream.IntStream.range(0, 200)
        .mapToObj(i -> new ContainerV2IO())
        .collect(java.util.stream.Collectors.toList());
    when(containersService.count(eq("file"), isNull())).thenReturn(250);
    when(containersService.list(eq("file"), isNull(), eq(0), eq(200))).thenReturn(page0);
    var r = resource.list("file", null, 0, 200, securityContext);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    var body = (PagedResponseIO<ContainerV2IO>) r.getEntity();
    assertEquals(200, body.items().size());
  }

  @Test
  void list_qParamIsDocumented() throws NoSuchMethodException {
    var method = java.util.Arrays.stream(ContainersV2Rest.class.getMethods())
        .filter(m -> m.getName().equals("list"))
        .findFirst().orElseThrow();
    var ann = java.util.Arrays.stream(method.getParameters())
        .filter(p -> p.getAnnotation(QueryParam.class) != null
            && "q".equals(p.getAnnotation(QueryParam.class).value()))
        .map(p -> p.getAnnotation(org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class))
        .findFirst().orElse(null);
    org.junit.jupiter.api.Assertions.assertNotNull(ann, "list() q @QueryParam must have @Parameter");
    org.junit.jupiter.api.Assertions.assertFalse(ann.description().isBlank(), "list() q @Parameter description must be non-blank");
  }

  @Test
  void list_pageParamIsDocumented() throws NoSuchMethodException {
    var method = java.util.Arrays.stream(ContainersV2Rest.class.getMethods())
        .filter(m -> m.getName().equals("list"))
        .findFirst().orElseThrow();
    var ann = java.util.Arrays.stream(method.getParameters())
        .filter(p -> p.getAnnotation(QueryParam.class) != null
            && "page".equals(p.getAnnotation(QueryParam.class).value()))
        .map(p -> p.getAnnotation(org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class))
        .findFirst().orElse(null);
    org.junit.jupiter.api.Assertions.assertNotNull(ann, "list() page @QueryParam must have @Parameter");
    org.junit.jupiter.api.Assertions.assertFalse(ann.description().isBlank(), "list() page @Parameter description must be non-blank");
  }

  @Test
  void list_pageSizeParamIsDocumented() throws NoSuchMethodException {
    var method = java.util.Arrays.stream(ContainersV2Rest.class.getMethods())
        .filter(m -> m.getName().equals("list"))
        .findFirst().orElseThrow();
    var ann = java.util.Arrays.stream(method.getParameters())
        .filter(p -> p.getAnnotation(QueryParam.class) != null
            && "pageSize".equals(p.getAnnotation(QueryParam.class).value()))
        .map(p -> p.getAnnotation(org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class))
        .findFirst().orElse(null);
    org.junit.jupiter.api.Assertions.assertNotNull(ann, "list() pageSize @QueryParam must have @Parameter");
    org.junit.jupiter.api.Assertions.assertFalse(ann.description().isBlank(), "list() pageSize @Parameter description must be non-blank");
  }

  // ─── APISIMP-MAX-POINTS-PARAM-CASE regression ──────────────────────────────

  @Test
  void getChannelData_maxPointsAnnotationIsCamelCase() throws NoSuchMethodException {
    // Regression guard: @QueryParam on maxPoints parameter must be "maxPoints" (camelCase),
    // not "max_points" (snake_case). All other v2 query params use camelCase.
    Method method = ContainersV2Rest.class.getMethod(
        "getChannelData", String.class, UUID.class, Long.class, Long.class,
        String.class, Integer.class, jakarta.ws.rs.core.SecurityContext.class);
    String actual = Arrays.stream(method.getParameters())
        .filter(p -> p.getAnnotation(QueryParam.class) != null
            && p.getAnnotation(QueryParam.class).value().contains("oint"))
        .map(p -> p.getAnnotation(QueryParam.class).value())
        .findFirst().orElse("NOT_FOUND");
    assertEquals("maxPoints", actual);
  }

  // ─── APISIMP-THUMBNAIL-SIZE-PARAM-NAME regression ───────────────────────────

  @Test
  void getThumbnail_sizeParamAnnotationMatchesJavaName() throws NoSuchMethodException {
    Method method = ContainersV2Rest.class.getMethod(
        "getThumbnail", String.class, String.class, int.class,
        jakarta.ws.rs.core.SecurityContext.class);
    String actual = Arrays.stream(method.getParameters())
        .filter(p -> p.getAnnotation(QueryParam.class) != null
            && p.getAnnotation(QueryParam.class).value().equals("size"))
        .map(p -> p.getName())
        .findFirst().orElse("NOT_FOUND");
    assertEquals("size", actual);
  }

  // ─── APISIMP-CONTAINERS-THUMBNAIL-SIZE-PARAM regression ──────────────────────

  @Test
  void getThumbnail_sizeParamIsDocumented() throws NoSuchMethodException {
    Method method = ContainersV2Rest.class.getMethod(
        "getThumbnail", String.class, String.class, int.class,
        jakarta.ws.rs.core.SecurityContext.class);
    String desc = Arrays.stream(method.getParameters())
        .filter(p -> p.getAnnotation(QueryParam.class) != null
            && "size".equals(p.getAnnotation(QueryParam.class).value()))
        .map(p -> {
          var ann = p.getAnnotation(org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
          return ann != null ? ann.description() : "";
        })
        .findFirst().orElse("");
    org.junit.jupiter.api.Assertions.assertFalse(
        desc.isBlank(),
        "getThumbnail size @Parameter description must be present and non-blank — got: '" + desc + "'");
  }

  // ─── APISIMP-CHANNEL-DATA-TIME-UNIT-UNDOCUMENTED regression ───────────────

  @Test
  void getChannelData_startParamHasNanosecondsInDescription() throws NoSuchMethodException {
    Method method = ContainersV2Rest.class.getMethod(
        "getChannelData", String.class, UUID.class, Long.class, Long.class,
        String.class, Integer.class, jakarta.ws.rs.core.SecurityContext.class);
    String startDesc = Arrays.stream(method.getParameters())
        .filter(p -> p.getAnnotation(QueryParam.class) != null
            && "start".equals(p.getAnnotation(QueryParam.class).value()))
        .map(p -> {
          var ann = p.getAnnotation(org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
          return ann != null ? ann.description() : "";
        })
        .findFirst().orElse("");
    org.junit.jupiter.api.Assertions.assertTrue(
        startDesc.toLowerCase().contains("nanosecond"),
        "start @Parameter description must mention 'nanosecond' — got: " + startDesc);
  }

  @Test
  void getChannelData_endParamHasNanosecondsInDescription() throws NoSuchMethodException {
    Method method = ContainersV2Rest.class.getMethod(
        "getChannelData", String.class, UUID.class, Long.class, Long.class,
        String.class, Integer.class, jakarta.ws.rs.core.SecurityContext.class);
    String endDesc = Arrays.stream(method.getParameters())
        .filter(p -> p.getAnnotation(QueryParam.class) != null
            && "end".equals(p.getAnnotation(QueryParam.class).value()))
        .map(p -> {
          var ann = p.getAnnotation(org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
          return ann != null ? ann.description() : "";
        })
        .findFirst().orElse("");
    org.junit.jupiter.api.Assertions.assertTrue(
        endDesc.toLowerCase().contains("nanosecond"),
        "end @Parameter description must mention 'nanosecond' — got: " + endDesc);
  }

  // ─── APISIMP-CHANNEL-DOWNSAMPLE-UNDOCUMENTED regression ─────────────────────

  @Test
  void getChannelData_downsampleParamHasParameterAnnotation() throws NoSuchMethodException {
    Method method = ContainersV2Rest.class.getMethod(
        "getChannelData", String.class, UUID.class, Long.class, Long.class,
        String.class, Integer.class, jakarta.ws.rs.core.SecurityContext.class);
    String desc = Arrays.stream(method.getParameters())
        .filter(p -> p.getAnnotation(QueryParam.class) != null
            && "downsample".equals(p.getAnnotation(QueryParam.class).value()))
        .map(p -> {
          var ann = p.getAnnotation(org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
          return ann != null ? ann.description() : "";
        })
        .findFirst().orElse("");
    org.junit.jupiter.api.Assertions.assertFalse(
        desc.isBlank(),
        "downsample @Parameter description must be present and non-blank — got: '" + desc + "'");
  }

  @Test
  void getChannelData_maxPointsParamHasParameterAnnotation() throws NoSuchMethodException {
    Method method = ContainersV2Rest.class.getMethod(
        "getChannelData", String.class, UUID.class, Long.class, Long.class,
        String.class, Integer.class, jakarta.ws.rs.core.SecurityContext.class);
    String desc = Arrays.stream(method.getParameters())
        .filter(p -> p.getAnnotation(QueryParam.class) != null
            && "maxPoints".equals(p.getAnnotation(QueryParam.class).value()))
        .map(p -> {
          var ann = p.getAnnotation(org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
          return ann != null ? ann.description() : "";
        })
        .findFirst().orElse("");
    org.junit.jupiter.api.Assertions.assertFalse(
        desc.isBlank(),
        "maxPoints @Parameter description must be present and non-blank — got: '" + desc + "'");
  }

  // ─── APISIMP-CONTAINERS-V2-PARAMS-UNDOCUMENTED-1 regression ─────────────────

  @Test
  void create_kindParamIsRequiredAndDescribed() {
    var method = Arrays.stream(ContainersV2Rest.class.getMethods())
        .filter(m -> m.getName().equals("create"))
        .findFirst().orElseThrow();
    var ann = Arrays.stream(method.getParameters())
        .filter(p -> p.getAnnotation(QueryParam.class) != null
            && "kind".equals(p.getAnnotation(QueryParam.class).value()))
        .map(p -> p.getAnnotation(org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class))
        .findFirst().orElse(null);
    org.junit.jupiter.api.Assertions.assertNotNull(ann, "create() kind @QueryParam must have @Parameter");
    org.junit.jupiter.api.Assertions.assertTrue(ann.required(), "create() kind must be required=true");
    org.junit.jupiter.api.Assertions.assertFalse(ann.description().isBlank(), "create() kind description must be non-blank");
  }

  @Test
  void delete_forceParamIsDescribed() {
    var method = Arrays.stream(ContainersV2Rest.class.getMethods())
        .filter(m -> m.getName().equals("delete"))
        .findFirst().orElseThrow();
    var ann = Arrays.stream(method.getParameters())
        .filter(p -> p.getAnnotation(QueryParam.class) != null
            && "force".equals(p.getAnnotation(QueryParam.class).value()))
        .map(p -> p.getAnnotation(org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class))
        .findFirst().orElse(null);
    org.junit.jupiter.api.Assertions.assertNotNull(ann, "delete() force @QueryParam must have @Parameter");
    org.junit.jupiter.api.Assertions.assertFalse(ann.description().isBlank(), "delete() force description must be non-blank");
  }

  @Test
  void list_kindParamIsRequiredAndDescribed() {
    var method = Arrays.stream(ContainersV2Rest.class.getMethods())
        .filter(m -> m.getName().equals("list"))
        .findFirst().orElseThrow();
    var kindAnn = Arrays.stream(method.getParameters())
        .filter(p -> p.getAnnotation(QueryParam.class) != null
            && "kind".equals(p.getAnnotation(QueryParam.class).value()))
        .map(p -> p.getAnnotation(org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class))
        .findFirst().orElse(null);
    org.junit.jupiter.api.Assertions.assertNotNull(kindAnn, "list() kind @QueryParam must have @Parameter");
    org.junit.jupiter.api.Assertions.assertTrue(kindAnn.required(), "list() kind must be required=true");
    org.junit.jupiter.api.Assertions.assertFalse(kindAnn.description().isBlank(), "list() kind description must be non-blank");
  }

  // ─── APISIMP-CONTAINERS-V2-PARAMS-UNDOCUMENTED-2 regression ─────────────────

  @Test
  void listChannels_pageParamHasParameterAnnotation() throws NoSuchMethodException {
    Method method = ContainersV2Rest.class.getMethod(
        "listChannels", String.class, int.class, int.class,
        jakarta.ws.rs.core.SecurityContext.class);
    String desc = Arrays.stream(method.getParameters())
        .filter(p -> p.getAnnotation(QueryParam.class) != null
            && "page".equals(p.getAnnotation(QueryParam.class).value()))
        .map(p -> {
          var ann = p.getAnnotation(org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
          return ann != null ? ann.description() : "";
        })
        .findFirst().orElse("");
    org.junit.jupiter.api.Assertions.assertFalse(
        desc.isBlank(),
        "listChannels page @Parameter description must be present and non-blank — got: '" + desc + "'");
  }

  @Test
  void listChannels_pageSizeParamHasParameterAnnotation() throws NoSuchMethodException {
    Method method = ContainersV2Rest.class.getMethod(
        "listChannels", String.class, int.class, int.class,
        jakarta.ws.rs.core.SecurityContext.class);
    String desc = Arrays.stream(method.getParameters())
        .filter(p -> p.getAnnotation(QueryParam.class) != null
            && "pageSize".equals(p.getAnnotation(QueryParam.class).value()))
        .map(p -> {
          var ann = p.getAnnotation(org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
          return ann != null ? ann.description() : "";
        })
        .findFirst().orElse("");
    org.junit.jupiter.api.Assertions.assertFalse(
        desc.isBlank(),
        "listChannels pageSize @Parameter description must be present and non-blank — got: '" + desc + "'");
  }

  @Test
  void getLiveWindow_shepardIdParamHasParameterAnnotation() throws NoSuchMethodException {
    Method method = ContainersV2Rest.class.getMethod(
        "getLiveWindow", String.class, UUID.class,
        String.class, String.class, String.class, String.class, String.class,
        int.class, boolean.class, jakarta.ws.rs.core.SecurityContext.class);
    String desc = Arrays.stream(method.getParameters())
        .filter(p -> p.getAnnotation(QueryParam.class) != null
            && "shepardId".equals(p.getAnnotation(QueryParam.class).value()))
        .map(p -> {
          var ann = p.getAnnotation(org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
          return ann != null ? ann.description() : "";
        })
        .findFirst().orElse("");
    org.junit.jupiter.api.Assertions.assertFalse(
        desc.isBlank(),
        "getLiveWindow shepardId @Parameter description must be present — got: '" + desc + "'");
  }

  @Test
  void getLiveWindow_windowSecondsParamHasParameterAnnotation() throws NoSuchMethodException {
    Method method = ContainersV2Rest.class.getMethod(
        "getLiveWindow", String.class, UUID.class,
        String.class, String.class, String.class, String.class, String.class,
        int.class, boolean.class, jakarta.ws.rs.core.SecurityContext.class);
    String desc = Arrays.stream(method.getParameters())
        .filter(p -> p.getAnnotation(QueryParam.class) != null
            && "windowSeconds".equals(p.getAnnotation(QueryParam.class).value()))
        .map(p -> {
          var ann = p.getAnnotation(org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
          return ann != null ? ann.description() : "";
        })
        .findFirst().orElse("");
    org.junit.jupiter.api.Assertions.assertFalse(
        desc.isBlank(),
        "getLiveWindow windowSeconds @Parameter description must be present — got: '" + desc + "'");
  }

  @Test
  void getLiveWindow_withBoundaryPointsParamHasParameterAnnotation() throws NoSuchMethodException {
    Method method = ContainersV2Rest.class.getMethod(
        "getLiveWindow", String.class, UUID.class,
        String.class, String.class, String.class, String.class, String.class,
        int.class, boolean.class, jakarta.ws.rs.core.SecurityContext.class);
    String desc = Arrays.stream(method.getParameters())
        .filter(p -> p.getAnnotation(QueryParam.class) != null
            && "withBoundaryPoints".equals(p.getAnnotation(QueryParam.class).value()))
        .map(p -> {
          var ann = p.getAnnotation(org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
          return ann != null ? ann.description() : "";
        })
        .findFirst().orElse("");
    org.junit.jupiter.api.Assertions.assertFalse(
        desc.isBlank(),
        "getLiveWindow withBoundaryPoints @Parameter description must be present — got: '" + desc + "'");
  }

  // ─── APISIMP-CONTAINER-CHANNEL-ANNOTATIONS-UNCAPPED ─────────────────────────

  @Test
  void listChannelAnnotations_xTotalCountHeaderIsPresent() {
    when(containersService.resolveByAppId(APP_ID)).thenReturn(Optional.of(resolved()));
    when(permissionsService.isAccessTypeAllowedForUser(
        eq(CONTAINER_NEO_ID), eq(AccessType.Read), eq(CALLER))).thenReturn(true);
    when(handler.listChannelAnnotations(eq(APP_ID), eq("ch-1"), eq(0), eq(200)))
        .thenReturn(Optional.of(
            Response.ok(new PagedResponseIO<>(List.of(), 0, 0, 200))
                .build()));
    var r = resource.listChannelAnnotations(APP_ID, "ch-1", 0, 200, securityContext);
    assertEquals(200, r.getStatus());
  }

  @Test
  void listChannelAnnotations_pageParamHasParameterAnnotation() throws NoSuchMethodException {
    Method method = ContainersV2Rest.class.getMethod(
        "listChannelAnnotations", String.class, String.class,
        int.class, int.class, jakarta.ws.rs.core.SecurityContext.class);
    String desc = Arrays.stream(method.getParameters())
        .filter(p -> p.getAnnotation(QueryParam.class) != null
            && "page".equals(p.getAnnotation(QueryParam.class).value()))
        .map(p -> {
          var ann = p.getAnnotation(org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
          return ann != null ? ann.description() : "";
        })
        .findFirst().orElse("");
    org.junit.jupiter.api.Assertions.assertFalse(
        desc.isBlank(),
        "listChannelAnnotations page @Parameter description must be present — got: '" + desc + "'");
  }

  @Test
  void listChannelAnnotations_pageSizeParamHasParameterAnnotation() throws NoSuchMethodException {
    Method method = ContainersV2Rest.class.getMethod(
        "listChannelAnnotations", String.class, String.class,
        int.class, int.class, jakarta.ws.rs.core.SecurityContext.class);
    String desc = Arrays.stream(method.getParameters())
        .filter(p -> p.getAnnotation(QueryParam.class) != null
            && "pageSize".equals(p.getAnnotation(QueryParam.class).value()))
        .map(p -> {
          var ann = p.getAnnotation(org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
          return ann != null ? ann.description() : "";
        })
        .findFirst().orElse("");
    org.junit.jupiter.api.Assertions.assertFalse(
        desc.isBlank(),
        "listChannelAnnotations pageSize @Parameter description must be present — got: '" + desc + "'");
  }

  // ─── APISIMP-CONTAINER-TEMPORAL-ANNOTATIONS-UNCAPPED ────────────────────────

  @Test
  void listTemporalAnnotations_xTotalCountHeaderIsPresent() {
    when(containersService.resolveByAppId(APP_ID)).thenReturn(Optional.of(resolved()));
    when(permissionsService.isAccessTypeAllowedForUser(
        eq(CONTAINER_NEO_ID), eq(AccessType.Read), eq(CALLER))).thenReturn(true);
    when(handler.listTemporalAnnotations(eq(APP_ID), eq(0), eq(200)))
        .thenReturn(Optional.of(
            Response.ok(new PagedResponseIO<>(List.of(), 0, 0, 200))
                .build()));
    var r = resource.listTemporalAnnotations(APP_ID, 0, 200, securityContext);
    assertEquals(200, r.getStatus());
  }

  @Test
  void listTemporalAnnotations_pageParamHasParameterAnnotation() throws NoSuchMethodException {
    Method method = ContainersV2Rest.class.getMethod(
        "listTemporalAnnotations", String.class,
        int.class, int.class, jakarta.ws.rs.core.SecurityContext.class);
    String desc = Arrays.stream(method.getParameters())
        .filter(p -> p.getAnnotation(QueryParam.class) != null
            && "page".equals(p.getAnnotation(QueryParam.class).value()))
        .map(p -> {
          var ann = p.getAnnotation(org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
          return ann != null ? ann.description() : "";
        })
        .findFirst().orElse("");
    org.junit.jupiter.api.Assertions.assertFalse(
        desc.isBlank(),
        "listTemporalAnnotations page @Parameter description must be present — got: '" + desc + "'");
  }

  // ─── APISIMP-MAXPOINTS-BOXED regression ──────────────────────────────────

  @Test
  void getChannelData_maxPointsParamHasMinConstraint() throws NoSuchMethodException {
    Method method = ContainersV2Rest.class.getMethod(
        "getChannelData", String.class, UUID.class, Long.class, Long.class,
        String.class, Integer.class, jakarta.ws.rs.core.SecurityContext.class);
    Min min = Arrays.stream(method.getParameters())
        .filter(p -> p.getAnnotation(QueryParam.class) != null
            && "maxPoints".equals(p.getAnnotation(QueryParam.class).value()))
        .map(p -> p.getAnnotation(Min.class))
        .findFirst().orElse(null);
    org.junit.jupiter.api.Assertions.assertNotNull(min,
        "maxPoints must have @Min on the REST param — got null");
    assertEquals(1L, min.value(), "maxPoints @Min must be 1");
  }

  @Test
  void getChannelData_maxPointsParamHasMaxConstraint() throws NoSuchMethodException {
    Method method = ContainersV2Rest.class.getMethod(
        "getChannelData", String.class, UUID.class, Long.class, Long.class,
        String.class, Integer.class, jakarta.ws.rs.core.SecurityContext.class);
    Max max = Arrays.stream(method.getParameters())
        .filter(p -> p.getAnnotation(QueryParam.class) != null
            && "maxPoints".equals(p.getAnnotation(QueryParam.class).value()))
        .map(p -> p.getAnnotation(Max.class))
        .findFirst().orElse(null);
    org.junit.jupiter.api.Assertions.assertNotNull(max,
        "maxPoints must have @Max on the REST param — got null");
    assertEquals(5000L, max.value(), "maxPoints @Max must be 5000");
  }

  @Test
  void listTemporalAnnotations_pageSizeParamHasParameterAnnotation() throws NoSuchMethodException {
    Method method = ContainersV2Rest.class.getMethod(
        "listTemporalAnnotations", String.class,
        int.class, int.class, jakarta.ws.rs.core.SecurityContext.class);
    String desc = Arrays.stream(method.getParameters())
        .filter(p -> p.getAnnotation(QueryParam.class) != null
            && "pageSize".equals(p.getAnnotation(QueryParam.class).value()))
        .map(p -> {
          var ann = p.getAnnotation(org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
          return ann != null ? ann.description() : "";
        })
        .findFirst().orElse("");
    org.junit.jupiter.api.Assertions.assertFalse(
        desc.isBlank(),
        "listTemporalAnnotations pageSize @Parameter description must be present — got: '" + desc + "'");
  }
}
