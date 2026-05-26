package de.dlr.shepard.v2.events;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * P13 — Server-Sent Events (SSE) change-feed for a Collection.
 *
 * <p>Clients open a persistent HTTP/1.1 connection to this endpoint to receive
 * real-time change notifications without polling. The stream carries
 * {@link CollectionEventIO} events serialised as JSON in the SSE {@code data:}
 * field, with the event name set to the {@code eventType} value.
 *
 * <p>An immediate {@code HEARTBEAT} event is sent on connect; subsequent
 * heartbeats arrive every 30 s via {@link CollectionEventBus#heartbeat()}.
 *
 * <p>Auth: {@code @Authenticated} + Read permission on the Collection.
 * The endpoint is intentionally NOT in {@code PublicEndpointRegistry} — it
 * must never be reachable without a valid JWT or API key.
 *
 * <p>Frontend note: native {@code EventSource} cannot attach an
 * {@code Authorization} header. Use the {@code fetch} + streaming-body
 * reader approach (same pattern as {@code useFetchNotifications.ts}) to carry
 * the Bearer token, or pass an {@code X-API-KEY} header if using API-key auth.
 *
 * @see CollectionEventBus
 * @see CollectionEventProducer
 */
@Path("/v2/collections/{collectionAppId}/events")
@RequestScoped
@Authenticated
@Tag(name = "Collection events (P13)")
public class CollectionEventsRest {

  @Inject
  CollectionEventBus eventBus;

  @Inject
  EntityIdResolver entityIdResolver;

  @Inject
  PermissionsService permissionsService;

  @GET
  @Produces(MediaType.SERVER_SENT_EVENTS)
  @Operation(
    summary = "Subscribe to the Collection change-feed (P13).",
    description =
      "Opens a persistent SSE stream. The server emits one event per change " +
      "to the Collection or its DataObjects, plus a HEARTBEAT every 30 s.\n\n" +
      "Event types: DATA_OBJECT_CREATED, DATA_OBJECT_UPDATED, DATA_OBJECT_DELETED, " +
      "COLLECTION_UPDATED, HEARTBEAT.\n\n" +
      "Each non-heartbeat event carries `eventType`, `entityAppId`, `entityKind`, " +
      "`collectionAppId`, `actorUsername`, and `timestamp` (epoch millis).\n\n" +
      "Auth: Read permission on the Collection. Native EventSource cannot send " +
      "Authorization headers — use fetch + streaming body with `Authorization: Bearer <token>` " +
      "or the `X-API-KEY` header."
  )
  @APIResponse(responseCode = "200", description = "SSE stream opened (Content-Type: text/event-stream).")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read on the Collection.")
  @APIResponse(responseCode = "404", description = "No Collection with that appId.")
  public void subscribe(
    @PathParam("collectionAppId") String collectionAppId,
    @Context SseEventSink sink,
    @Context Sse sse,
    @Context SecurityContext securityContext
  ) {
    // Auth: must be authenticated (enforced by @Authenticated) and have Read access.
    String caller = securityContext.getUserPrincipal() != null
      ? securityContext.getUserPrincipal().getName()
      : null;
    if (caller == null) {
      sink.close();
      return;
    }

    Long ogmId = resolveOrNull(collectionAppId);
    if (ogmId == null) {
      sink.close();
      return;
    }

    if (!permissionsService.isAccessTypeAllowedForUser(ogmId, AccessType.Read, caller, 0L)) {
      sink.close();
      return;
    }

    eventBus.subscribe(collectionAppId, sink, sse);
    // The sink stays open; the JAX-RS runtime holds the HTTP connection open
    // for the lifetime of the sink. The bus prunes closed sinks on next emit.
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private Long resolveOrNull(String appId) {
    try {
      return entityIdResolver.resolveLong(appId);
    } catch (NotFoundException nfe) {
      return null;
    }
  }
}
