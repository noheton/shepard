package de.dlr.shepard.v2.hdf.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.data.hdf.entities.HdfContainer;
import de.dlr.shepard.data.hdf.entities.HdfReference;
import de.dlr.shepard.data.hdf.io.HdfReferenceIO;
import de.dlr.shepard.data.hdf.io.HdfReferenceRequestIO;
import de.dlr.shepard.data.hdf.services.HdfReferenceService;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HdfReferenceRestTest {

  private HdfReferenceService service;
  private SecurityContext securityContext;
  private Principal principal;
  private HdfReferenceRest resource;

  private static final String DO_APP_ID = "do-app-id-001";
  private static final String REF_APP_ID = "ref-app-id-001";
  private static final String CONTAINER_APP_ID = "container-app-id-001";
  private static final String CALLER = "alice";

  @BeforeEach
  void setUp() {
    service = mock(HdfReferenceService.class);
    securityContext = mock(SecurityContext.class);
    principal = mock(Principal.class);
    when(principal.getName()).thenReturn(CALLER);
    when(securityContext.getUserPrincipal()).thenReturn(principal);

    resource = new HdfReferenceRest();
    resource.service = service;
  }

  // ─── Helper ───────────────────────────────────────────────────────────────

  private HdfReference makeRef(String appId, String path) {
    HdfReference ref = new HdfReference(1L);
    ref.setAppId(appId);
    ref.setDatasetPath(path);
    HdfContainer container = new HdfContainer(2L);
    container.setAppId(CONTAINER_APP_ID);
    ref.setHdfContainer(container);
    return ref;
  }

  // ─── GET (list) ───────────────────────────────────────────────────────────

  @Test
  void listReturns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    Response r = resource.list(DO_APP_ID, securityContext);
    assertEquals(401, r.getStatus());
    verify(service, never()).listForDataObject(any(), any());
  }

  @Test
  void listReturnsEmptyListWhenNonePresent() {
    when(service.listForDataObject(DO_APP_ID, CALLER)).thenReturn(List.of());
    Response r = resource.list(DO_APP_ID, securityContext);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    List<HdfReferenceIO> body = (List<HdfReferenceIO>) r.getEntity();
    assertNotNull(body);
    assertEquals(0, body.size());
  }

  @Test
  void listReturnsReferencesWhenPresent() {
    var ref1 = makeRef(REF_APP_ID, "/sensor/ch_a");
    var ref2 = makeRef("ref-002", "/pressure/channel");
    when(service.listForDataObject(DO_APP_ID, CALLER)).thenReturn(List.of(ref1, ref2));

    Response r = resource.list(DO_APP_ID, securityContext);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    List<HdfReferenceIO> body = (List<HdfReferenceIO>) r.getEntity();
    assertEquals(2, body.size());
    assertEquals(REF_APP_ID, body.get(0).getAppId());
    assertEquals("/sensor/ch_a", body.get(0).getDatasetPath());
    assertEquals(CONTAINER_APP_ID, body.get(0).getHdfContainerAppId());
  }

  @Test
  void listReturns404WhenDataObjectMissing() {
    when(service.listForDataObject(DO_APP_ID, CALLER))
      .thenThrow(new NotFoundException("not found"));
    Response r = resource.list(DO_APP_ID, securityContext);
    assertEquals(404, r.getStatus());
  }

  @Test
  void listReturns403WhenForbidden() {
    when(service.listForDataObject(DO_APP_ID, CALLER))
      .thenThrow(new ForbiddenException("no access"));
    Response r = resource.list(DO_APP_ID, securityContext);
    assertEquals(403, r.getStatus());
  }

  // ─── POST (create) ────────────────────────────────────────────────────────

  @Test
  void createReturns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    var body = new HdfReferenceRequestIO();
    Response r = resource.create(DO_APP_ID, body, securityContext);
    assertEquals(401, r.getStatus());
    verify(service, never()).create(any(), any(), any());
  }

  @Test
  void createReturns400WhenBodyNull() {
    Response r = resource.create(DO_APP_ID, null, securityContext);
    assertEquals(400, r.getStatus());
    verify(service, never()).create(any(), any(), any());
  }

  @Test
  void createReturns400WhenContainerAppIdMissing() {
    var body = new HdfReferenceRequestIO();
    body.setDatasetPath("/sensor/ch_a");
    Response r = resource.create(DO_APP_ID, body, securityContext);
    assertEquals(400, r.getStatus());
    verify(service, never()).create(any(), any(), any());
  }

  @Test
  void createReturns400WhenDatasetPathMissing() {
    var body = new HdfReferenceRequestIO();
    body.setHdfContainerAppId(CONTAINER_APP_ID);
    Response r = resource.create(DO_APP_ID, body, securityContext);
    assertEquals(400, r.getStatus());
    verify(service, never()).create(any(), any(), any());
  }

  @Test
  void createReturns400WhenDatasetPathBlank() {
    var body = new HdfReferenceRequestIO();
    body.setHdfContainerAppId(CONTAINER_APP_ID);
    body.setDatasetPath("   ");
    Response r = resource.create(DO_APP_ID, body, securityContext);
    assertEquals(400, r.getStatus());
  }

  @Test
  void createReturns201WithMintedAppId() {
    var body = new HdfReferenceRequestIO();
    body.setHdfContainerAppId(CONTAINER_APP_ID);
    body.setDatasetPath("/sensor/ch_a");

    var saved = makeRef(REF_APP_ID, "/sensor/ch_a");
    when(service.create(eq(DO_APP_ID), any(HdfReferenceRequestIO.class), eq(CALLER)))
      .thenReturn(saved);

    Response r = resource.create(DO_APP_ID, body, securityContext);
    assertEquals(201, r.getStatus());
    var io = (HdfReferenceIO) r.getEntity();
    assertNotNull(io);
    assertEquals(REF_APP_ID, io.getAppId());
    assertEquals("/sensor/ch_a", io.getDatasetPath());
    assertEquals(CONTAINER_APP_ID, io.getHdfContainerAppId());
  }

  @Test
  void createReturns404WhenDataObjectNotFound() {
    var body = new HdfReferenceRequestIO();
    body.setHdfContainerAppId(CONTAINER_APP_ID);
    body.setDatasetPath("/sensor/ch_a");
    when(service.create(eq(DO_APP_ID), any(), eq(CALLER)))
      .thenThrow(new NotFoundException("not found"));

    Response r = resource.create(DO_APP_ID, body, securityContext);
    assertEquals(404, r.getStatus());
  }

  @Test
  void createReturns404WhenContainerNotFound() {
    var body = new HdfReferenceRequestIO();
    body.setHdfContainerAppId("ghost-container");
    body.setDatasetPath("/sensor/ch_a");
    when(service.create(eq(DO_APP_ID), any(), eq(CALLER)))
      .thenThrow(new NotFoundException("container not found"));

    Response r = resource.create(DO_APP_ID, body, securityContext);
    assertEquals(404, r.getStatus());
  }

  @Test
  void createReturns403WhenForbidden() {
    var body = new HdfReferenceRequestIO();
    body.setHdfContainerAppId(CONTAINER_APP_ID);
    body.setDatasetPath("/sensor/ch_a");
    when(service.create(eq(DO_APP_ID), any(), eq(CALLER)))
      .thenThrow(new ForbiddenException("no write"));

    Response r = resource.create(DO_APP_ID, body, securityContext);
    assertEquals(403, r.getStatus());
  }

  // ─── DELETE ───────────────────────────────────────────────────────────────

  @Test
  void deleteReturns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    Response r = resource.delete(DO_APP_ID, REF_APP_ID, securityContext);
    assertEquals(401, r.getStatus());
    verify(service, never()).delete(any(), any(), any());
  }

  @Test
  void deleteReturns204OnSuccess() {
    Response r = resource.delete(DO_APP_ID, REF_APP_ID, securityContext);
    assertEquals(204, r.getStatus());
    verify(service).delete(DO_APP_ID, REF_APP_ID, CALLER);
  }

  @Test
  void deleteReturns404WhenReferenceNotFound() {
    doThrow(new NotFoundException("not found"))
      .when(service).delete(DO_APP_ID, "ghost-ref", CALLER);
    Response r = resource.delete(DO_APP_ID, "ghost-ref", securityContext);
    assertEquals(404, r.getStatus());
  }

  @Test
  void deleteReturns404WhenReferenceDoesNotBelongToDataObject() {
    doThrow(new NotFoundException("wrong owner"))
      .when(service).delete("other-do", REF_APP_ID, CALLER);
    Response r = resource.delete("other-do", REF_APP_ID, securityContext);
    assertEquals(404, r.getStatus());
  }

  @Test
  void deleteReturns403WhenForbidden() {
    doThrow(new ForbiddenException("no write"))
      .when(service).delete(DO_APP_ID, REF_APP_ID, CALLER);
    Response r = resource.delete(DO_APP_ID, REF_APP_ID, securityContext);
    assertEquals(403, r.getStatus());
  }
}
