package de.dlr.shepard.v2.collectionwatchers.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.v2.common.io.PagedResponseIO;
import de.dlr.shepard.v2.collectionwatchers.io.CollectionWatcherIO;
import de.dlr.shepard.v2.collectionwatchers.services.CollectionWatcherService;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.ArrayList;
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
    Response r = resource.list(COLL_APP_ID, 0, 50, securityContext);
    assertEquals(401, r.getStatus());
    verify(service, never()).count(anyString(), anyString());
    verify(service, never()).list(anyString(), anyString(), anyInt(), anyInt());
  }

  @Test
  void listReturns200WithWatchers() {
    CollectionWatcherIO io = makeIO("app-1", ALICE, COLL_APP_ID);
    when(service.count(COLL_APP_ID, ALICE)).thenReturn(1L);
    when(service.list(COLL_APP_ID, ALICE, 0, 50)).thenReturn(List.of(io));

    Response r = resource.list(COLL_APP_ID, 0, 50, securityContext);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<CollectionWatcherIO> body = (PagedResponseIO<CollectionWatcherIO>) r.getEntity();
    assertEquals(1, body.items().size());
  }

  @Test
  void listUsesCountAndBoundedServiceCall() {
    when(service.count(COLL_APP_ID, ALICE)).thenReturn(3L);
    when(service.list(COLL_APP_ID, ALICE, 0, 2)).thenReturn(List.of(
      makeIO("app-1", "user1", COLL_APP_ID),
      makeIO("app-2", "user2", COLL_APP_ID)
    ));

    Response r = resource.list(COLL_APP_ID, 0, 2, securityContext);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<CollectionWatcherIO> body = (PagedResponseIO<CollectionWatcherIO>) r.getEntity();
    assertEquals(2, body.items().size());
    // Verify the bounded call was made with correct skip+limit (not the unbounded 1-arg variant).
    verify(service).list(COLL_APP_ID, ALICE, 0, 2);
  }

  @Test
  void listSecondPagePassesCorrectSkip() {
    // page=1, pageSize=2 → skip=2
    when(service.count(COLL_APP_ID, ALICE)).thenReturn(3L);
    when(service.list(COLL_APP_ID, ALICE, 2, 2)).thenReturn(List.of(
      makeIO("app-3", "user3", COLL_APP_ID)
    ));

    Response r = resource.list(COLL_APP_ID, 1, 2, securityContext);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<CollectionWatcherIO> body = (PagedResponseIO<CollectionWatcherIO>) r.getEntity();
    assertEquals(1, body.items().size());
    assertEquals("app-3", body.items().get(0).watcherAppId());
    verify(service).list(COLL_APP_ID, ALICE, 2, 2);
  }

  @Test
  void listMaxPageSizePassesCorrectBounds() {
    List<CollectionWatcherIO> items = new ArrayList<>();
    for (int i = 0; i < 200; i++) items.add(makeIO("app-" + i, "user" + i, COLL_APP_ID));
    when(service.count(COLL_APP_ID, ALICE)).thenReturn(250L);
    when(service.list(COLL_APP_ID, ALICE, 0, 200)).thenReturn(items);

    Response r = resource.list(COLL_APP_ID, 0, 200, securityContext);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<CollectionWatcherIO> body = (PagedResponseIO<CollectionWatcherIO>) r.getEntity();
    assertEquals(200, body.items().size());
  }

  @Test
  void listPropagates403FromService() {
    when(service.count(eq(COLL_APP_ID), eq(ALICE))).thenThrow(new ForbiddenException());
    org.junit.jupiter.api.Assertions.assertThrows(ForbiddenException.class,
      () -> resource.list(COLL_APP_ID, 0, 50, securityContext));
  }

  @Test
  void listPageParamsCarryValidationAnnotations() throws NoSuchMethodException {
    java.lang.reflect.Method m = CollectionWatchersRest.class.getDeclaredMethod(
        "list", String.class, int.class, int.class, jakarta.ws.rs.core.SecurityContext.class);
    java.lang.reflect.Parameter page = m.getParameters()[1];
    java.lang.reflect.Parameter size = m.getParameters()[2];
    assertNotNull(page.getAnnotation(jakarta.validation.constraints.PositiveOrZero.class), "page: @PositiveOrZero");
    assertNotNull(size.getAnnotation(jakarta.validation.constraints.Min.class), "pageSize: @Min");
    assertNotNull(size.getAnnotation(jakarta.validation.constraints.Max.class), "pageSize: @Max");
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
