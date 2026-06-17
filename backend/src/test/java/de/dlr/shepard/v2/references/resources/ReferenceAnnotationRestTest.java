package de.dlr.shepard.v2.references.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.v2.references.services.ReferencesV2Service;
import de.dlr.shepard.v2.references.services.ReferencesV2Service.ResolvedReference;
import de.dlr.shepard.v2.references.spi.ReferenceKindHandler;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * APISIMP-ANNOTATION-SUBRESOURCE-COLLISION — unit tests for the unified
 * {@link ReferenceAnnotationRest} resource.
 */
class ReferenceAnnotationRestTest {

  static final String REF_ID = "ref-appid-1";
  static final String ANN_ID = "ann-appid-1";
  static final String DO_APP_ID = "do-appid-99";
  static final long DO_OGM_ID = 99L;
  static final String CALLER = "alice";

  @Mock ReferencesV2Service referencesService;
  @Mock PermissionsService permissionsService;
  @Mock ReferenceKindHandler handler;
  @Mock BasicReference reference;
  @Mock SecurityContext sc;
  @Mock Principal principal;

  ReferenceAnnotationRest resource;
  DataObject dataObject;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new ReferenceAnnotationRest();
    resource.referencesService = referencesService;
    resource.permissionsService = permissionsService;

    dataObject = new DataObject();
    dataObject.setId(DO_OGM_ID);
    dataObject.setAppId(DO_APP_ID);

    when(sc.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
    when(handler.supportsAnnotations()).thenReturn(true);
    when(handler.kind()).thenReturn("timeseries");
    when(reference.getDataObject()).thenReturn(dataObject);
    when(referencesService.resolveByAppId(REF_ID))
      .thenReturn(Optional.of(new ResolvedReference(handler, reference)));
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(DO_APP_ID), any(AccessType.class), eq(CALLER)))
      .thenReturn(true);
  }

  // ── 401 / 404 / unsupported ─────────────────────────────────────────────

  @Test
  void list_returns401WhenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);
    assertThat(resource.list(REF_ID, sc).getStatus()).isEqualTo(401);
  }

  @Test
  void list_returns404WhenRefMissing() {
    when(referencesService.resolveByAppId(REF_ID)).thenReturn(Optional.empty());
    assertThat(resource.list(REF_ID, sc).getStatus()).isEqualTo(404);
  }

  @Test
  void list_returns404WhenKindDoesNotSupportAnnotations() {
    when(handler.supportsAnnotations()).thenReturn(false);
    assertThat(resource.list(REF_ID, sc).getStatus()).isEqualTo(404);
  }

  @Test
  void list_returns403WhenNoReadPermission() {
    when(permissionsService.isAccessAllowedForDataObjectAppId(DO_APP_ID, AccessType.Read, CALLER))
      .thenReturn(false);
    assertThat(resource.list(REF_ID, sc).getStatus()).isEqualTo(403);
  }

  // ── list ────────────────────────────────────────────────────────────────

  @Test
  void list_returns200WithAnnotations() {
    Map<String, Object> ann = Map.of("appId", ANN_ID, "label", "spike");
    when(handler.listAnnotations(REF_ID)).thenReturn(List.of(ann));

    var r = resource.list(REF_ID, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    var rows = (List<Map<String, Object>>) r.getEntity();
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).get("label")).isEqualTo("spike");
  }

  @Test
  void list_returns200WithEmptyList() {
    when(handler.listAnnotations(REF_ID)).thenReturn(Collections.emptyList());
    var r = resource.list(REF_ID, sc);
    assertThat(r.getStatus()).isEqualTo(200);
  }

  // ── create ──────────────────────────────────────────────────────────────

  @Test
  void create_returns201() {
    Map<String, Object> body = Map.of("startNs", 1000L, "label", "spike");
    Map<String, Object> created = Map.of("appId", ANN_ID, "label", "spike");
    when(handler.createAnnotation(REF_ID, body)).thenReturn(created);

    var r = resource.create(REF_ID, body, sc);
    assertThat(r.getStatus()).isEqualTo(201);
    assertThat(r.getEntity()).isEqualTo(created);
  }

  @Test
  void create_returns400WhenHandlerThrowsBadRequest() {
    Map<String, Object> body = Map.of("label", "oops");
    when(handler.createAnnotation(REF_ID, body)).thenThrow(new BadRequestException("startNs required"));

    var r = resource.create(REF_ID, body, sc);
    assertThat(r.getStatus()).isEqualTo(400);
  }

  @Test
  void create_returns403WhenNoWritePermission() {
    when(permissionsService.isAccessAllowedForDataObjectAppId(DO_APP_ID, AccessType.Write, CALLER))
      .thenReturn(false);
    var r = resource.create(REF_ID, Map.of("startNs", 1L, "label", "x"), sc);
    assertThat(r.getStatus()).isEqualTo(403);
    verify(handler, never()).createAnnotation(any(), any());
  }

  // ── get ─────────────────────────────────────────────────────────────────

  @Test
  void get_returns200() {
    Map<String, Object> ann = Map.of("appId", ANN_ID, "label", "interval");
    when(handler.getAnnotation(REF_ID, ANN_ID)).thenReturn(ann);

    var r = resource.get(REF_ID, ANN_ID, sc);
    assertThat(r.getStatus()).isEqualTo(200);
  }

  @Test
  void get_returns404WhenAnnotationMissing() {
    when(handler.getAnnotation(REF_ID, ANN_ID)).thenThrow(new NotFoundException("not found"));
    assertThat(resource.get(REF_ID, ANN_ID, sc).getStatus()).isEqualTo(404);
  }

  // ── patch ───────────────────────────────────────────────────────────────

  @Test
  void patch_returns200() {
    Map<String, Object> body = Map.of("label", "new");
    Map<String, Object> patched = Map.of("appId", ANN_ID, "label", "new");
    when(handler.patchAnnotation(REF_ID, ANN_ID, body)).thenReturn(patched);

    var r = resource.patch(REF_ID, ANN_ID, body, sc);
    assertThat(r.getStatus()).isEqualTo(200);
  }

  @Test
  void patch_returns400WhenHandlerThrowsBadRequest() {
    when(handler.patchAnnotation(eq(REF_ID), eq(ANN_ID), any()))
      .thenThrow(new BadRequestException("blank label"));
    assertThat(resource.patch(REF_ID, ANN_ID, Map.of("label", "  "), sc).getStatus()).isEqualTo(400);
  }

  @Test
  void patch_returns404WhenAnnotationMissing() {
    when(handler.patchAnnotation(eq(REF_ID), eq(ANN_ID), any()))
      .thenThrow(new NotFoundException("not found"));
    assertThat(resource.patch(REF_ID, ANN_ID, Map.of("label", "x"), sc).getStatus()).isEqualTo(404);
  }

  // ── delete ──────────────────────────────────────────────────────────────

  @Test
  void delete_returns204() {
    var r = resource.delete(REF_ID, ANN_ID, sc);
    assertThat(r.getStatus()).isEqualTo(204);
    verify(handler).deleteAnnotation(REF_ID, ANN_ID);
  }

  @Test
  void delete_returns404WhenAnnotationMissing() {
    when(handler.supportsAnnotations()).thenReturn(true);
    // handler.deleteAnnotation throws
    org.mockito.Mockito.doThrow(new NotFoundException("not found"))
      .when(handler).deleteAnnotation(REF_ID, ANN_ID);
    assertThat(resource.delete(REF_ID, ANN_ID, sc).getStatus()).isEqualTo(404);
  }
}
