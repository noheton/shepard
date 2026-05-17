package de.dlr.shepard.v2.hdf.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.data.hdf.entities.HdfContainer;
import de.dlr.shepard.data.hdf.hsds.HsdsClient.ExportResponse;
import de.dlr.shepard.data.hdf.io.HdfContainerIO;
import de.dlr.shepard.data.hdf.services.HdfContainerService;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.io.InputStream;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HdfContainerRestTest {

  private HdfContainerService service;
  private SecurityContext securityContext;
  private Principal principal;
  private HdfContainerRest resource;

  @BeforeEach
  void setUp() {
    service = mock(HdfContainerService.class);
    securityContext = mock(SecurityContext.class);
    principal = mock(Principal.class);
    when(principal.getName()).thenReturn("alice");
    when(securityContext.getUserPrincipal()).thenReturn(principal);

    resource = new HdfContainerRest();
    resource.service = service;
  }

  // ─── GET /{appId} ──────────────────────────────────────────────────────

  @Test
  void getReturns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    Response r = resource.get("app-1", securityContext);
    assertEquals(401, r.getStatus());
    verify(service, never()).getContainerByAppId(any());
  }

  @Test
  void getReturnsContainerWhenPresent() {
    var c = new HdfContainer(1L);
    c.setAppId("app-1");
    c.setName("primary");
    c.setHsdsDomain("/shepard/app-1/");
    when(service.getContainerByAppId("app-1")).thenReturn(c);

    Response r = resource.get("app-1", securityContext);
    assertEquals(200, r.getStatus());
    var io = (HdfContainerIO) r.getEntity();
    assertNotNull(io);
    assertEquals("app-1", io.getAppId());
    assertEquals("/shepard/app-1/", io.getHsdsDomain());
  }

  @Test
  void getReturns404WhenServiceRaisesInvalidPath() {
    when(service.getContainerByAppId("ghost")).thenThrow(new InvalidPathException("nope"));
    Response r = resource.get("ghost", securityContext);
    assertEquals(404, r.getStatus());
  }

  // ─── POST / ────────────────────────────────────────────────────────────

  @Test
  void createReturns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    Response r = resource.create(new HdfContainerIO(), securityContext);
    assertEquals(401, r.getStatus());
    verify(service, never()).createContainer(any());
  }

  @Test
  void createReturns400WhenBodyNull() {
    Response r = resource.create(null, securityContext);
    assertEquals(400, r.getStatus());
    verify(service, never()).createContainer(any());
  }

  @Test
  void createReturns400WhenNameMissing() {
    var body = new HdfContainerIO();
    Response r = resource.create(body, securityContext);
    assertEquals(400, r.getStatus());
    verify(service, never()).createContainer(any());
  }

  @Test
  void createReturns400WhenNameBlank() {
    var body = new HdfContainerIO();
    body.setName("   ");
    Response r = resource.create(body, securityContext);
    assertEquals(400, r.getStatus());
  }

  @Test
  void createReturns201WithCreatedRow() {
    var body = new HdfContainerIO();
    body.setName("primary");
    var created = new HdfContainer(1L);
    created.setAppId("server-minted");
    created.setName("primary");
    created.setHsdsDomain("/shepard/server-minted/");
    when(service.createContainer(any(HdfContainerIO.class))).thenReturn(created);

    Response r = resource.create(body, securityContext);
    assertEquals(201, r.getStatus());
    var io = (HdfContainerIO) r.getEntity();
    assertEquals("server-minted", io.getAppId());
    assertEquals("/shepard/server-minted/", io.getHsdsDomain());
  }

  // ─── DELETE /{appId} ───────────────────────────────────────────────────

  @Test
  void deleteReturns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    Response r = resource.delete("app-1", securityContext);
    assertEquals(401, r.getStatus());
    verify(service, never()).deleteContainerByAppId(any());
  }

  @Test
  void deleteReturns204OnSuccess() {
    Response r = resource.delete("app-1", securityContext);
    assertEquals(204, r.getStatus());
    verify(service).deleteContainerByAppId("app-1");
  }

  @Test
  void deleteReturns404WhenServiceRaisesInvalidPath() {
    org.mockito.Mockito.doThrow(new InvalidPathException("nope")).when(service).deleteContainerByAppId("ghost");
    Response r = resource.delete("ghost", securityContext);
    assertEquals(404, r.getStatus());
  }

  // ─── GET /{appId}/file ─────────────────────────────────────────────────

  @Test
  void downloadFileReturns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    Response r = resource.downloadFile("app-1", null, securityContext);
    assertEquals(401, r.getStatus());
    verify(service, never()).downloadFile(any(HdfContainer.class), any());
  }

  @Test
  void downloadFileReturns404WhenContainerMissing() {
    when(service.getContainerByAppId("ghost")).thenThrow(new InvalidPathException("nope"));
    Response r = resource.downloadFile("ghost", null, securityContext);
    assertEquals(404, r.getStatus());
  }

  @Test
  void downloadFileReturns200WithHdf5ContentType() {
    var c = new HdfContainer(1L);
    c.setAppId("app-1");
    c.setName("primary");
    c.setHsdsDomain("/shepard/app-1/");
    when(service.getContainerByAppId("app-1")).thenReturn(c);
    var export = new ExportResponse(200, InputStream.nullInputStream(), 9L, null, "bytes");
    when(service.downloadFile(eq(c), isNull())).thenReturn(export);

    Response r = resource.downloadFile("app-1", null, securityContext);
    assertEquals(200, r.getStatus());
    assertEquals("application/x-hdf5", r.getMediaType().toString());
    String cd = (String) r.getHeaders().getFirst("Content-Disposition");
    assertNotNull(cd, "Content-Disposition must be set");
    assertTrue(cd.contains("attachment"), "must be attachment");
    assertTrue(cd.contains(".h5"), "filename must end in .h5");
  }

  @Test
  void downloadFileReturns206WithRangeHeaders() {
    var c = new HdfContainer(2L);
    c.setAppId("app-2");
    c.setName("run-data");
    c.setHsdsDomain("/shepard/app-2/");
    when(service.getContainerByAppId("app-2")).thenReturn(c);
    var export = new ExportResponse(206, InputStream.nullInputStream(), 4L, "bytes 0-3/9", "bytes");
    when(service.downloadFile(eq(c), eq("bytes=0-3"))).thenReturn(export);

    Response r = resource.downloadFile("app-2", "bytes=0-3", securityContext);
    assertEquals(206, r.getStatus());
    assertEquals("bytes 0-3/9", r.getHeaders().getFirst("Content-Range"));
    assertEquals("bytes", r.getHeaders().getFirst("Accept-Ranges"));
  }

  @Test
  void downloadFileContentDispositionEncodesNonAsciiName() {
    var c = new HdfContainer(3L);
    c.setAppId("app-3");
    c.setName("Messung ÄÖÜ");
    c.setHsdsDomain("/shepard/app-3/");
    when(service.getContainerByAppId("app-3")).thenReturn(c);
    var export = new ExportResponse(200, InputStream.nullInputStream(), 9L, null, "bytes");
    when(service.downloadFile(eq(c), isNull())).thenReturn(export);

    Response r = resource.downloadFile("app-3", null, securityContext);
    assertEquals(200, r.getStatus());
    String cd = (String) r.getHeaders().getFirst("Content-Disposition");
    assertTrue(cd.contains("filename*=UTF-8''"), "RFC 5987 encoded name must be present");
  }
}
