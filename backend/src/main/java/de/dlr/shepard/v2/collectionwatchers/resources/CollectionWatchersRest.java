package de.dlr.shepard.v2.collectionwatchers.resources;

import de.dlr.shepard.v2.collectionwatchers.io.CollectionWatcherIO;
import de.dlr.shepard.v2.collectionwatchers.services.CollectionWatcherService;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.List;
import java.util.Optional;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * CW1 — Collection watching (user subscribes to a Collection) REST surface.
 *
 * <p>A user can "watch" a Collection to receive in-app notifications when
 * new top-level DataObjects are added. This is distinct from the
 * {@code /v2/collections/{appId}/watched-containers} surface (WATCH1),
 * which tracks Collections-watching-Containers for live-data dashboards.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET  /v2/collections/{collectionAppId}/watches} — list all watchers (Read gate).</li>
 *   <li>{@code GET  /v2/collections/{collectionAppId}/watches/me} — 200 if watching, 404 if not.</li>
 *   <li>{@code POST /v2/collections/{collectionAppId}/watches} — start watching (idempotent).</li>
 *   <li>{@code DELETE /v2/collections/{collectionAppId}/watches/me} — stop watching (idempotent).</li>
 * </ul>
 */
@Path("/v2/collections/{collectionAppId}/watches")
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
@Authenticated
@Tag(name = "Collection watches (CW1)")
public class CollectionWatchersRest {

  @Inject
  CollectionWatcherService service;

  @GET
  @Operation(
    summary = "List all watchers for this Collection (CW1).",
    description =
      "Returns the complete list of users currently watching this Collection. Each " +
      "item carries `username` and `since` (epoch millis when the watch was registered). " +
      "The list is unordered and is not paginated — the number of watchers is expected " +
      "to be small relative to the DataObject count.\n\n" +
      "Auth: Read permission on the Collection."
  )
  @APIResponse(
    responseCode = "200",
    description = "List of CollectionWatcherIO records (may be empty). Each item has `username` and `since` (epoch millis).",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = CollectionWatcherIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required (no JWT and no X-API-KEY).")
  @APIResponse(responseCode = "403", description = "Caller lacks Read on the Collection.")
  @APIResponse(responseCode = "404", description = "No Collection with that appId.")
  public Response list(
    @PathParam("collectionAppId") String collectionAppId,
    @Context SecurityContext securityContext
  ) {
    String caller = caller(securityContext);
    if (caller == null) return unauthorized();
    List<CollectionWatcherIO> result = service.list(collectionAppId, caller);
    return Response.ok(result).build();
  }

  @GET
  @Path("me")
  @Operation(
    summary = "Check whether the caller is watching this Collection (CW1).",
    description =
      "Returns 200 with the caller's watch record when they are subscribed to " +
      "this Collection, or 404 when they are not. Use this endpoint to determine " +
      "the initial state of the Watch button in the UI without fetching the full " +
      "watcher list.\n\n" +
      "No Collection-level permission is required beyond authentication — any " +
      "authenticated user can check their own watch status even for Collections " +
      "they no longer have Read access to."
  )
  @APIResponse(
    responseCode = "200",
    description = "The caller is watching. Body is a CollectionWatcherIO with `username` and `since` (epoch millis).",
    content = @Content(schema = @Schema(implementation = CollectionWatcherIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required (no JWT and no X-API-KEY).")
  @APIResponse(responseCode = "404", description = "The caller is not currently watching this Collection.")
  public Response getMe(
    @PathParam("collectionAppId") String collectionAppId,
    @Context SecurityContext securityContext
  ) {
    String caller = caller(securityContext);
    if (caller == null) return unauthorized();
    Optional<CollectionWatcherIO> result = service.getMe(collectionAppId, caller);
    return result.map(Response::ok).orElse(Response.status(Response.Status.NOT_FOUND))
      .build();
  }

  @POST
  @Operation(
    summary = "Start watching this Collection (CW1).",
    description =
      "Subscribes the caller to notifications for this Collection. " +
      "Idempotent: a second POST returns 200 carrying the existing record " +
      "rather than erroring with 409.\n\n" +
      "Auth: Read permission on the Collection."
  )
  @APIResponse(
    responseCode = "200",
    description = "Watch record (existing or newly created).",
    content = @Content(schema = @Schema(implementation = CollectionWatcherIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read on the Collection.")
  @APIResponse(responseCode = "404", description = "No Collection with that appId.")
  public Response watch(
    @PathParam("collectionAppId") String collectionAppId,
    @Context SecurityContext securityContext
  ) {
    String caller = caller(securityContext);
    if (caller == null) return unauthorized();
    CollectionWatcherIO result = service.watch(collectionAppId, caller);
    return Response.ok(result).build();
  }

  @DELETE
  @Path("me")
  @Operation(
    summary = "Stop watching this Collection (CW1).",
    description =
      "Unsubscribes the caller from notifications for this Collection. " +
      "Idempotent: if the caller is not watching, 204 is returned without error."
  )
  @APIResponse(responseCode = "204", description = "Watch removed (or was never there — idempotent).")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response unwatch(
    @PathParam("collectionAppId") String collectionAppId,
    @Context SecurityContext securityContext
  ) {
    String caller = caller(securityContext);
    if (caller == null) return unauthorized();
    service.unwatch(collectionAppId, caller);
    return Response.noContent().build();
  }

  // ─── Helpers ─────────────────────────────────────────────────────────────

  private static String caller(SecurityContext sc) {
    return sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
  }

  private static Response unauthorized() {
    return Response.status(Response.Status.UNAUTHORIZED).build();
  }
}
