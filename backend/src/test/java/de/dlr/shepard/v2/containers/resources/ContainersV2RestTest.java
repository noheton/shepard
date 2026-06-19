package de.dlr.shepard.v2.containers.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.mockito.Mockito.never;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.common.exceptions.ProblemJson;
import jakarta.ws.rs.QueryParam;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.UUID;
import de.dlr.shepard.auth.permission.io.PermissionsIO;
import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.auth.permission.model.Roles;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.common.util.PermissionType;
import de.dlr.shepard.data.file.entities.FileContainer;
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
  }

  @Test
  void listVersions_returns200WithEmptyList() {
    allowRead();
    when(handler.listVersions(eq(APP_ID), eq("sensor.csv"))).thenReturn(Optional.of(List.of()));
    var r = resource.listVersions(APP_ID, "sensor.csv", securityContext);
    assertEquals(200, r.getStatus());
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
    when(handler.listLinkedDataObjects(eq(APP_ID))).thenReturn(Optional.of(List.of(doIo)));
    var r = resource.getLinkedDataObjects(APP_ID, securityContext);
    assertEquals(200, r.getStatus());
  }

  @Test
  void getLinkedDataObjects_returns200WithEmptyList() {
    allowRead();
    when(handler.listLinkedDataObjects(eq(APP_ID))).thenReturn(Optional.of(List.of()));
    var r = resource.getLinkedDataObjects(APP_ID, securityContext);
    assertEquals(200, r.getStatus());
  }

  @Test
  void getLinkedDataObjects_returns415WhenKindHasNoLinkedConcept() {
    allowRead();
    when(handler.kind()).thenReturn("hdf");
    when(handler.listLinkedDataObjects(eq(APP_ID))).thenReturn(Optional.empty());
    var r = resource.getLinkedDataObjects(APP_ID, securityContext);
    assertEquals(415, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
    ProblemJson body = (ProblemJson) r.getEntity();
    assertEquals("/problems/containers.linked-data-objects-unsupported", body.type());
    assertEquals(415, body.status());
  }

  @Test
  void getLinkedDataObjects_returns404WhenUnknown() {
    when(containersService.resolveByAppId(APP_ID)).thenReturn(Optional.empty());
    var r = resource.getLinkedDataObjects(APP_ID, securityContext);
    assertEquals(404, r.getStatus());
  }

  @Test
  void getLinkedDataObjects_returns403WhenNoRead() {
    when(containersService.resolveByAppId(APP_ID)).thenReturn(Optional.of(resolved()));
    when(permissionsService.isAccessTypeAllowedForUser(eq(CONTAINER_NEO_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(false);
    var r = resource.getLinkedDataObjects(APP_ID, securityContext);
    assertEquals(403, r.getStatus());
  }

  @Test
  void getLinkedDataObjects_returns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    var r = resource.getLinkedDataObjects(APP_ID, securityContext);
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
        "getThumbnail", String.class, String.class, Integer.class,
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
        "getThumbnail", String.class, String.class, Integer.class,
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
}
