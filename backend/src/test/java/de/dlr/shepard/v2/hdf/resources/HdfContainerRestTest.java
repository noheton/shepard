package de.dlr.shepard.v2.hdf.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.data.hdf.entities.HdfContainer;
import de.dlr.shepard.data.hdf.io.HdfContainerIO;
import de.dlr.shepard.data.hdf.services.HdfContainerService;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
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
}
