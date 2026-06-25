package de.dlr.shepard.v2.references.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.uri.entities.URIReference;
import de.dlr.shepard.v2.references.io.ReferenceV2IO;
import de.dlr.shepard.v2.references.services.ReferencesV2Service;
import de.dlr.shepard.v2.references.spi.ReferenceKindHandler;
import jakarta.ws.rs.core.SecurityContext;
import java.io.ByteArrayInputStream;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * V2CONV-A2 — plain-Mockito unit tests for {@link ReferencesV2Rest}. The
 * service dispatch + handler logic is covered separately; here we cover the
 * permission gating + status-code contract of the unified resource.
 */
class ReferencesV2RestTest {

  private static final String CALLER = "alice";
  private static final String DO_APP_ID = "do-app-1";
  private static final String REF_APP_ID = "ref-app-9";

  @Mock
  ReferencesV2Service referencesService;

  @Mock
  PermissionsService permissionsService;

  @Mock
  ReferenceKindHandler handler;

  @Mock
  SecurityContext securityContext;

  @Mock
  Principal principal;

  final ObjectMapper om = new ObjectMapper();
  ReferencesV2Rest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new ReferencesV2Rest();
    resource.referencesService = referencesService;
    resource.permissionsService = permissionsService;
    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
  }

  private URIReference refWithParent() {
    var parent = new DataObject(42L);
    parent.setAppId(DO_APP_ID);
    var ref = new URIReference(7L);
    ref.setAppId(REF_APP_ID);
    ref.setDataObject(parent);
    return ref;
  }

  private ReferencesV2Service.ResolvedReference resolved() {
    return new ReferencesV2Service.ResolvedReference(handler, refWithParent());
  }

  // ─── create ──────────────────────────────────────────────────────────────

  @Test
  void create_returns201WhenAllowed() throws Exception {
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(DO_APP_ID), eq(AccessType.Write), eq(CALLER)))
      .thenReturn(true);
    var io = new ReferenceV2IO();
    when(referencesService.create(eq("uri"), eq(DO_APP_ID), any())).thenReturn(io);

    var body = om.readTree("{\"uri\":\"https://x\"}");
    var r = resource.create("uri", DO_APP_ID, body, securityContext);
    assertEquals(201, r.getStatus());
  }

  @Test
  void create_returns400WhenKindMissing() throws Exception {
    var r = resource.create(null, DO_APP_ID, om.readTree("{}"), securityContext);
    assertEquals(400, r.getStatus());
  }

  @Test
  void create_returns403WhenNoWrite() throws Exception {
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(DO_APP_ID), eq(AccessType.Write), eq(CALLER)))
      .thenReturn(false);
    var r = resource.create("uri", DO_APP_ID, om.readTree("{}"), securityContext);
    assertEquals(403, r.getStatus());
  }

  @Test
  void create_returns401WhenUnauthenticated() throws Exception {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    var r = resource.create("uri", DO_APP_ID, om.readTree("{}"), securityContext);
    assertEquals(401, r.getStatus());
  }

  // ─── get ───────────────────────────────────────────────────────────────────

  @Test
  void get_returns200WhenAllowed() {
    when(referencesService.resolveByAppId(REF_APP_ID)).thenReturn(Optional.of(resolved()));
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(DO_APP_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(true);
    when(handler.toIO(any())).thenReturn(new ReferenceV2IO());

    var r = resource.get(REF_APP_ID, securityContext);
    assertEquals(200, r.getStatus());
  }

  @Test
  void get_returns404WhenUnknown() {
    when(referencesService.resolveByAppId(REF_APP_ID)).thenReturn(Optional.empty());
    var r = resource.get(REF_APP_ID, securityContext);
    assertEquals(404, r.getStatus());
  }

  @Test
  void get_returns403WhenNoRead() {
    when(referencesService.resolveByAppId(REF_APP_ID)).thenReturn(Optional.of(resolved()));
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(DO_APP_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(false);
    var r = resource.get(REF_APP_ID, securityContext);
    assertEquals(403, r.getStatus());
  }

  // ─── patch ─────────────────────────────────────────────────────────────────

  @Test
  void patch_returns200WhenAllowed() throws Exception {
    when(referencesService.resolveByAppId(REF_APP_ID)).thenReturn(Optional.of(resolved()));
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(DO_APP_ID), eq(AccessType.Write), eq(CALLER)))
      .thenReturn(true);
    when(referencesService.patchByAppId(eq(REF_APP_ID), any())).thenReturn(new ReferenceV2IO());

    var r = resource.patch(REF_APP_ID, om.readTree("{\"name\":\"n\"}"), securityContext);
    assertEquals(200, r.getStatus());
  }

  @Test
  void patch_returns400WhenBodyNotObject() throws Exception {
    var r = resource.patch(REF_APP_ID, om.readTree("[1,2,3]"), securityContext);
    assertEquals(400, r.getStatus());
  }

  // ─── delete ──────────────────────────────────────────────────────────────

  @Test
  void delete_returns204WhenAllowed() {
    when(referencesService.resolveByAppId(REF_APP_ID)).thenReturn(Optional.of(resolved()));
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(DO_APP_ID), eq(AccessType.Write), eq(CALLER)))
      .thenReturn(true);
    var r = resource.delete(REF_APP_ID, securityContext);
    assertEquals(204, r.getStatus());
    verify(referencesService).deleteByAppId(REF_APP_ID);
  }

  // ─── list ──────────────────────────────────────────────────────────────────

  @Test
  void list_returns200WithFileKindFilter() {
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(DO_APP_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(true);
    when(referencesService.listByDataObject(eq("file"), eq(DO_APP_ID), eq("urdf")))
      .thenReturn(List.of(new ReferenceV2IO()));

    var r = resource.list("file", DO_APP_ID, "urdf", securityContext);
    assertEquals(200, r.getStatus());
    verify(referencesService).listByDataObject("file", DO_APP_ID, "urdf");
  }

  @Test
  void list_returns400WhenMissingParams() {
    var r = resource.list("file", null, null, securityContext);
    assertEquals(400, r.getStatus());
  }

  // ─── uploadContent ─────────────────────────────────────────────────────────

  @Test
  void uploadContent_returns200WhenAllowed() {
    when(referencesService.resolveByAppId(REF_APP_ID)).thenReturn(Optional.of(resolved()));
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(DO_APP_ID), eq(AccessType.Write), eq(CALLER)))
      .thenReturn(true);
    when(handler.uploadContent(eq(REF_APP_ID), any(), eq("doc.pdf"), anyLong()))
      .thenReturn(new ReferenceV2IO());

    var body = new ByteArrayInputStream(new byte[] { 1, 2, 3 });
    var r = resource.uploadContent(REF_APP_ID, "doc.pdf", "3", body, securityContext);
    assertEquals(200, r.getStatus());
    verify(handler).uploadContent(eq(REF_APP_ID), any(), eq("doc.pdf"), eq(3L));
  }

  @Test
  void uploadContent_returns400WhenFilenameBlank() {
    var body = new ByteArrayInputStream(new byte[] { 1 });
    var r = resource.uploadContent(REF_APP_ID, "  ", null, body, securityContext);
    assertEquals(400, r.getStatus());
  }

  @Test
  void uploadContent_returns404WhenUnknown() {
    when(referencesService.resolveByAppId(REF_APP_ID)).thenReturn(Optional.empty());
    var body = new ByteArrayInputStream(new byte[] { 1 });
    var r = resource.uploadContent(REF_APP_ID, "doc.pdf", null, body, securityContext);
    assertEquals(404, r.getStatus());
  }

  @Test
  void uploadContent_returns403WhenNoWrite() {
    when(referencesService.resolveByAppId(REF_APP_ID)).thenReturn(Optional.of(resolved()));
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(DO_APP_ID), eq(AccessType.Write), eq(CALLER)))
      .thenReturn(false);
    var body = new ByteArrayInputStream(new byte[] { 1 });
    var r = resource.uploadContent(REF_APP_ID, "doc.pdf", null, body, securityContext);
    assertEquals(403, r.getStatus());
  }

  @Test
  void uploadContent_returns400WhenUnsupportedKind() {
    when(referencesService.resolveByAppId(REF_APP_ID)).thenReturn(Optional.of(resolved()));
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(DO_APP_ID), eq(AccessType.Write), eq(CALLER)))
      .thenReturn(true);
    when(handler.uploadContent(any(), any(), any(), anyLong()))
      .thenThrow(new UnsupportedOperationException("kind=uri does not support content upload"));

    var body = new ByteArrayInputStream(new byte[] { 1 });
    var r = resource.uploadContent(REF_APP_ID, "doc.pdf", null, body, securityContext);
    assertEquals(400, r.getStatus());
  }

  // ─── APISIMP-REFERENCES-PARAMS-1 reflection guards ───────────────────────

  @Test
  void create_kindParam_hasParameterAnnotationWithDescription() throws NoSuchMethodException {
    java.lang.reflect.Method m = ReferencesV2Rest.class.getMethod(
        "create", String.class, String.class, com.fasterxml.jackson.databind.JsonNode.class,
        jakarta.ws.rs.core.SecurityContext.class);
    java.lang.reflect.Parameter param = java.util.Arrays.stream(m.getParameters())
        .filter(p -> { var qp = p.getAnnotation(jakarta.ws.rs.QueryParam.class); return qp != null && "kind".equals(qp.value()); })
        .findFirst().orElse(null);
    assertNotNull(param, "create.kind must carry @QueryParam");
    var ann = param.getAnnotation(org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
    assertNotNull(ann, "create.kind must carry @Parameter annotation");
    assertTrue(ann.description() != null && !ann.description().isBlank(), "@Parameter.description must be non-blank for create.kind");
  }

  @Test
  void list_fileKindParam_hasParameterAnnotationWithDescription() throws NoSuchMethodException {
    java.lang.reflect.Method m = ReferencesV2Rest.class.getMethod(
        "list", String.class, String.class, String.class, jakarta.ws.rs.core.SecurityContext.class);
    java.lang.reflect.Parameter param = java.util.Arrays.stream(m.getParameters())
        .filter(p -> { var qp = p.getAnnotation(jakarta.ws.rs.QueryParam.class); return qp != null && "fileKind".equals(qp.value()); })
        .findFirst().orElse(null);
    assertNotNull(param, "list.fileKind must carry @QueryParam");
    var ann = param.getAnnotation(org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
    assertNotNull(ann, "list.fileKind must carry @Parameter annotation");
    assertTrue(ann.description() != null && !ann.description().isBlank(), "@Parameter.description must be non-blank for list.fileKind");
  }

  @Test
  void uploadContent_filenameParam_hasParameterAnnotationWithDescription() throws NoSuchMethodException {
    java.lang.reflect.Method m = ReferencesV2Rest.class.getMethod(
        "uploadContent", String.class, String.class, String.class,
        java.io.InputStream.class, jakarta.ws.rs.core.SecurityContext.class);
    java.lang.reflect.Parameter param = java.util.Arrays.stream(m.getParameters())
        .filter(p -> { var qp = p.getAnnotation(jakarta.ws.rs.QueryParam.class); return qp != null && "filename".equals(qp.value()); })
        .findFirst().orElse(null);
    assertNotNull(param, "uploadContent.filename must carry @QueryParam");
    var ann = param.getAnnotation(org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
    assertNotNull(ann, "uploadContent.filename must carry @Parameter annotation");
    assertTrue(ann.description() != null && !ann.description().isBlank(), "@Parameter.description must be non-blank for uploadContent.filename");
  }
}
