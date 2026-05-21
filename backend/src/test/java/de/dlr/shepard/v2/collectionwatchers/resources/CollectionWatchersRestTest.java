package de.dlr.shepard.v2.collectionwatchers.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.v2.collectionwatchers.io.CollectionWatcherIO;
import de.dlr.shepard.v2.collectionwatchers.resources.CollectionWatchersRest;
import de.dlr.shepard.v2.collectionwatchers.services.CollectionWatcherService;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class CollectionWatchersRestTest {

  static final String COLL_APP_ID = "019e3c96-0000-7000-a000-000000000001";
  static final String ALICE = "alice";

  @Mock
  CollectionWatcherService service;

  @Mock
  SecurityContext securityContext;

  @Mock
  Principal principal;

  CollectionWatchersRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new CollectionWatchersRest();
    resource.service = service;
    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(ALICE);
  }

  // ─── GET /watches ─────────────────────────────────────────────────────────

  @Test
  void listReturns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    Response r = resource.list(COLL_APP_ID, securityContext);
    assertEquals(401, r.getStatus());
    verify(service, never()).list(anyString(), anyString());
  }

  @Test
  void listReturns200WithWatchers() {
    CollectionWatcherIO io = makeIO("app-1", ALICE, COLL_APP_ID);
    when(service.list(COLL_APP_ID, ALICE)).thenReturn(List.of(io));

    Response r = resource.list(COLL_APP_ID, securityContext);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    List<CollectionWatcherIO> body = (List<CollectionWatcherIO>) r.getEntity();
    assertEquals(1, body.size());
  }

  @Test
  void listPropagates403FromService() {
    when(service.list(eq(COLL_APP_ID), eq(ALICE))).thenThrow(new ForbiddenException());
    org.junit.jupiter.api.Assertions.assertThrows(ForbiddenException.class,
      () -> resource.list(COLL_APP_ID, securityContext));
  }

  // ─── GET /watches/me ──────────────────────────────────────────────────────

  @Test
  void getMeReturns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    Response r = resource.getMe(COLL_APP_ID, securityContext);
    assertEquals(401, r.getStatus());
  }

  @Test
  void getMeReturns200WhenWatching() {
    CollectionWatcherIO io = makeIO("app-1", ALICE, COLL_APP_ID);
    when(service.getMe(COLL_APP_ID, ALICE)).thenReturn(Optional.of(io));

    Response r = resource.getMe(COLL_APP_ID, securityContext);

    assertEquals(200, r.getStatus());
  }

  @Test
  void getMeReturns404WhenNotWatching() {
    when(service.getMe(COLL_APP_ID, ALICE)).thenReturn(Optional.empty());

    Response r = resource.getMe(COLL_APP_ID, securityContext);

    assertEquals(404, r.getStatus());
  }

  // ─── POST /watches ────────────────────────────────────────────────────────

  @Test
  void watchReturns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    Response r = resource.watch(COLL_APP_ID, securityContext);
    assertEquals(401, r.getStatus());
    verify(service, never()).watch(anyString(), anyString());
  }

  @Test
  void watchReturns200WithWatcherRecord() {
    CollectionWatcherIO io = makeIO("app-1", ALICE, COLL_APP_ID);
    when(service.watch(COLL_APP_ID, ALICE)).thenReturn(io);

    Response r = resource.watch(COLL_APP_ID, securityContext);

    assertEquals(200, r.getStatus());
    assertEquals(io, r.getEntity());
  }

  @Test
  void watchPropagates404FromService() {
    when(service.watch(eq(COLL_APP_ID), eq(ALICE))).thenThrow(new NotFoundException());
    org.junit.jupiter.api.Assertions.assertThrows(NotFoundException.class,
      () -> resource.watch(COLL_APP_ID, securityContext));
  }

  // ─── DELETE /watches/me ───────────────────────────────────────────────────

  @Test
  void unwatchReturns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    Response r = resource.unwatch(COLL_APP_ID, securityContext);
    assertEquals(401, r.getStatus());
    verify(service, never()).unwatch(anyString(), anyString());
  }

  @Test
  void unwatchReturns204OnSuccess() {
    Response r = resource.unwatch(COLL_APP_ID, securityContext);
    assertEquals(204, r.getStatus());
    verify(service).unwatch(COLL_APP_ID, ALICE);
  }

  // ─── Helpers ─────────────────────────────────────────────────────────────

  private CollectionWatcherIO makeIO(String appId, String username, String collectionAppId) {
    return new CollectionWatcherIO(appId, username, collectionAppId, System.currentTimeMillis());
  }
}
