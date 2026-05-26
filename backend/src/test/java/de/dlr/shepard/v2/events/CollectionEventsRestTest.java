package de.dlr.shepard.v2.events;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import java.security.Principal;
import jakarta.ws.rs.core.SecurityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * P13 — unit tests for {@link CollectionEventsRest}.
 *
 * <p>Plain Mockito (no {@code @QuarkusTest}) following the pattern of
 * {@link de.dlr.shepard.v2.collectionwatchers.resources.CollectionWatchersRestTest}.
 *
 * <p>The {@code subscribe()} method returns {@code void} and communicates
 * error conditions by calling {@link SseEventSink#close()} before returning.
 * Happy-path tests verify that {@link CollectionEventBus#subscribe} is called.
 * Error-path tests verify that the sink is closed and the bus is not subscribed.
 */
class CollectionEventsRestTest {

  static final String COLL_APP_ID = "019e3c96-0000-7000-a000-000000000001";
  static final long OGM_ID = 42L;
  static final String ALICE = "alice";

  @Mock
  CollectionEventBus eventBus;

  @Mock
  EntityIdResolver entityIdResolver;

  @Mock
  PermissionsService permissionsService;

  @Mock
  SecurityContext securityContext;

  @Mock
  Principal principal;

  @Mock
  SseEventSink sink;

  @Mock
  Sse sse;

  CollectionEventsRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new CollectionEventsRest();
    resource.eventBus = eventBus;
    resource.entityIdResolver = entityIdResolver;
    resource.permissionsService = permissionsService;

    // Default: authenticated alice with Read access on the collection.
    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(ALICE);
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(
      eq(OGM_ID), eq(AccessType.Read), eq(ALICE), anyLong()
    )).thenReturn(true);
    // Sink is open by default.
    when(sink.isClosed()).thenReturn(false);
  }

  // ─── 401: unauthenticated ─────────────────────────────────────────────────

  @Test
  void closesSinkWhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);

    resource.subscribe(COLL_APP_ID, sink, sse, securityContext);

    verify(sink).close();
    verify(eventBus, never()).subscribe(anyString(), any(), any());
  }

  // ─── 404: collection not found ────────────────────────────────────────────

  @Test
  void closesSinkWhenCollectionNotFound() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenThrow(new NotFoundException());

    resource.subscribe(COLL_APP_ID, sink, sse, securityContext);

    verify(sink).close();
    verify(eventBus, never()).subscribe(anyString(), any(), any());
  }

  // ─── 403: no Read permission ──────────────────────────────────────────────

  @Test
  void closesSinkWhenCallerLacksReadPermission() {
    when(permissionsService.isAccessTypeAllowedForUser(
      eq(OGM_ID), eq(AccessType.Read), eq(ALICE), anyLong()
    )).thenReturn(false);

    resource.subscribe(COLL_APP_ID, sink, sse, securityContext);

    verify(sink).close();
    verify(eventBus, never()).subscribe(anyString(), any(), any());
  }

  // ─── 200: happy path — bus subscribed ────────────────────────────────────

  @Test
  void subscribesOnBusWhenAuthorised() {
    resource.subscribe(COLL_APP_ID, sink, sse, securityContext);

    // Sink must NOT be closed on the happy path.
    verify(sink, never()).close();
    // Bus must be told to register the sink.
    verify(eventBus).subscribe(eq(COLL_APP_ID), eq(sink), eq(sse));
  }

  // ─── produces text/event-stream ──────────────────────────────────────────

  @Test
  void subscribeMethodAnnotatedWithSseMediaType() throws NoSuchMethodException {
    var method = CollectionEventsRest.class.getMethod(
      "subscribe", String.class, SseEventSink.class, Sse.class, SecurityContext.class
    );
    jakarta.ws.rs.Produces produces = method.getAnnotation(jakarta.ws.rs.Produces.class);
    org.junit.jupiter.api.Assertions.assertNotNull(produces, "@Produces annotation missing");
    boolean hasSseType = java.util.Arrays.asList(produces.value())
      .contains(jakarta.ws.rs.core.MediaType.SERVER_SENT_EVENTS);
    org.junit.jupiter.api.Assertions.assertTrue(
      hasSseType,
      "@Produces must include SERVER_SENT_EVENTS (text/event-stream)"
    );
  }
}
