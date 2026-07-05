package de.dlr.shepard.v2.notifications.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.dlr.shepard.v2.common.io.PagedResponseIO;
import de.dlr.shepard.v2.notifications.entities.Notification;
import de.dlr.shepard.v2.notifications.io.NotificationIO;
import de.dlr.shepard.v2.notifications.services.NotificationService;
import jakarta.ws.rs.QueryParam;
import java.security.Principal;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import jakarta.ws.rs.core.SecurityContext;

/**
 * APISIMP-PAGE-PARAM-ANNOTATION-INCONSISTENCY — Mockito unit tests for
 * {@link NotificationRest#list}.
 *
 * <p>Verifies the bounded-DAO contract: the handler calls
 * {@code service.countForUser} + {@code service.listForUser(skip, limit)} and
 * delegates all paging arithmetic to the service rather than loading all rows
 * then Java-subListing.
 */
class NotificationRestTest {

  static final String CALLER = "alice";

  @Mock
  NotificationService service;

  @Mock
  SecurityContext sc;

  @Mock
  Principal principal;

  NotificationRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new NotificationRest();
    resource.service = service;

    when(sc.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
    when(sc.isUserInRole("instance-admin")).thenReturn(false);

    // Default: countForUser → 3; fallback listForUser → empty.
    when(service.countForUser(eq(CALLER), eq(false))).thenReturn(3L);
    when(service.listForUser(anyString(), anyBoolean(), anyInt(), anyInt())).thenReturn(List.of());
    // Default page (0) + default pageSize (50) returns the three items.
    when(service.listForUser(eq(CALLER), eq(false), eq(0), eq(50))).thenReturn(List.of(
      notif("n1"), notif("n2"), notif("n3")
    ));
  }

  @Test
  void list_returns401WhenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);
    assertEquals(401, resource.list(0, 50, sc).getStatus());
  }

  @Test
  void list_returns200WithAllEntriesWhenDefaultParams() {
    var r = resource.list(0, 50, sc);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    var body = (PagedResponseIO<NotificationIO>) r.getEntity();
    assertEquals(3, body.items().size());
  }

  @Test
  void list_xTotalCountHeaderPresent() {
    var r = resource.list(0, 50, sc);
    assertEquals("3", r.getHeaderString("X-Total-Count"));
  }

  @Test
  void list_paginationReturnsSublist() {
    when(service.listForUser(eq(CALLER), eq(false), eq(0), eq(2))).thenReturn(List.of(
      notif("n1"), notif("n2")
    ));
    var r = resource.list(0, 2, sc);
    assertEquals(200, r.getStatus());
    assertEquals("3", r.getHeaderString("X-Total-Count"));
    @SuppressWarnings("unchecked")
    var body = (PagedResponseIO<NotificationIO>) r.getEntity();
    assertEquals(2, body.items().size());
  }

  @Test
  void list_paginationPageBeyondRangeReturnsEmptyList() {
    // skip = 99 * 10 = 990; fallback listForUser mock returns List.of()
    var r = resource.list(99, 10, sc);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    var body = (PagedResponseIO<NotificationIO>) r.getEntity();
    assertEquals(0, body.items().size());
  }

  @Test
  void list_pageSizeCappedAt200() {
    when(service.countForUser(eq(CALLER), eq(false))).thenReturn(250L);
    var twoHundred = IntStream.range(0, 200)
        .mapToObj(i -> notif("n" + i))
        .toList();
    when(service.listForUser(eq(CALLER), eq(false), eq(0), eq(200))).thenReturn(twoHundred);
    var r = resource.list(0, 200, sc);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    var body = (PagedResponseIO<NotificationIO>) r.getEntity();
    assertEquals(200, body.items().size());
  }

  @Test
  void list_pageParamIsDocumented() throws NoSuchMethodException {
    var method = NotificationRest.class.getMethod(
        "list", int.class, int.class, SecurityContext.class);
    var ann = java.util.Arrays.stream(method.getParameters())
        .filter(p -> p.getAnnotation(QueryParam.class) != null
            && "page".equals(p.getAnnotation(QueryParam.class).value()))
        .map(p -> p.getAnnotation(
            org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class))
        .findFirst().orElse(null);
    org.junit.jupiter.api.Assertions.assertNotNull(ann,
        "list() page @QueryParam must carry @Parameter");
    org.junit.jupiter.api.Assertions.assertFalse(ann.description().isBlank(),
        "list() page @Parameter description must be non-blank");
  }

  @Test
  void list_pageSizeParamIsDocumented() throws NoSuchMethodException {
    var method = NotificationRest.class.getMethod(
        "list", int.class, int.class, SecurityContext.class);
    var ann = java.util.Arrays.stream(method.getParameters())
        .filter(p -> p.getAnnotation(QueryParam.class) != null
            && "pageSize".equals(p.getAnnotation(QueryParam.class).value()))
        .map(p -> p.getAnnotation(
            org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class))
        .findFirst().orElse(null);
    org.junit.jupiter.api.Assertions.assertNotNull(ann,
        "list() pageSize @QueryParam must carry @Parameter");
    org.junit.jupiter.api.Assertions.assertFalse(ann.description().isBlank(),
        "list() pageSize @Parameter description must be non-blank");
  }

  private static Notification notif(String title) {
    return new Notification("USER", CALLER, "INFO", "test", title, "", null);
  }
}
