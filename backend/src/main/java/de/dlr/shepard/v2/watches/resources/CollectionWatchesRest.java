package de.dlr.shepard.v2.watches.resources;

import de.dlr.shepard.v2.watches.io.CreateWatchIO;
import de.dlr.shepard.v2.watches.io.WatchIO;
import de.dlr.shepard.v2.watches.services.WatchService;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * WATCH1 — Collection "watched containers" REST surface.
 *
 * <p>A Collection can watch any Container it doesn't own (no
 * DataObject inside it has to reference the Container). The watch
 * shows up as a panel on the Collection detail page so visitors can
 * see live container data without drilling through DataObjects.
 *
 * <p>Auth: Read on the Collection to list / view; Write to add or
 * remove. The watch's TARGET container also needs Read for the
 * adder — checked at create time and reflected in the
 * `containerAvailability` token at list time.
 */
@Path("/v2/collections/{collectionAppId}/watched-containers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Authenticated
@Tag(name = "Collection watched containers (WATCH1)")
public class CollectionWatchesRest {

  @Inject
  WatchService service;

  @GET
  @Operation(
    summary = "List containers this Collection is watching (WATCH1).",
    description =
      "Returns one `WatchIO` row per `:Watch` edge attached to the " +
      "Collection identified by `collectionAppId`. A Watch is a link from " +
      "a Collection to a Container the Collection does NOT own through any " +
      "DataObject reference — useful for live-data Collections that surface " +
      "containers shared from elsewhere (e.g. the home-showcase demo " +
      "Collection watches three home-energy TimeseriesContainers it doesn't " +
      "own).\n\n" +
      "Each `WatchIO` carries:\n" +
      "  - `appId` (UUID v7 of the Watch edge itself).\n" +
      "  - `containerKind` (`TIMESERIES` / `FILE` / `STRUCTURED_DATA`).\n" +
      "  - `containerAppId`.\n" +
      "  - `containerName` (inlined when the caller has Read on the target, " +
      "else null).\n" +
      "  - `containerAvailability` (`available` / `forbidden` / `deleted` / " +
      "`error`) — tells the UI whether to render the row as clickable, " +
      "permission-denied, tombstoned, or error.\n" +
      "  - `since` (millis when the watch was added).\n" +
      "  - `addedBy` (username).\n\n" +
      "Auth: Read on the Collection. The per-container availability check " +
      "filters out container details the caller can't see, but the watch " +
      "row itself is always returned so the operator knows the link exists.\n\n" +
      "Next step: `POST /v2/collections/{collectionAppId}/watched-containers` " +
      "to add a watch, or click the target container's appId in the UI."
  )
  @APIResponse(
    responseCode = "200",
    description = "List of watches (may be empty).",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = WatchIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read on the Collection.")
  @APIResponse(responseCode = "404", description = "No Collection with that appId.")
  public Response list(@PathParam("collectionAppId") @NotBlank String collectionAppId) {
    List<WatchIO> result = service.list(collectionAppId);
    return Response.ok(result).build();
  }

  @POST
  @Operation(
    summary = "Add a Watch link from a Collection to a Container (WATCH1).",
    description =
      "Creates a `:Watch` edge from the Collection identified by " +
      "`collectionAppId` to the Container identified by the body. The " +
      "target container is NOT modified — only a new graph edge is added.\n\n" +
      "Body fields (all required):\n" +
      "  - `containerKind` (one of `TIMESERIES`, `FILE`, `STRUCTURED_DATA`).\n" +
      "  - `containerAppId` (UUID v7 of an existing container of that kind).\n\n" +
      "Example body: `{\"containerKind\": \"TIMESERIES\", \"containerAppId\": " +
      "\"019e3c96-…\"}`.\n\n" +
      "Idempotency: the `(collection, container)` pair is the dedupe key. A " +
      "second POST with the same pair returns 201 carrying the EXISTING " +
      "WatchIO instead of erroring with 409 — safe for re-running seed " +
      "scripts.\n\n" +
      "Auth: Write on the Collection AND Read on the target Container. The " +
      "Read check on the container is enforced at create time so a caller " +
      "can't slip a watch in to entities they can't see (otherwise the watch " +
      "panel would always show 'forbidden' for that row, which is just " +
      "noise).\n\n" +
      "Side effects: ProvenanceCaptureFilter records a `CREATE` Activity on " +
      "the new Watch's appId.\n\n" +
      "Next step: `GET /v2/collections/{collectionAppId}/watched-containers` " +
      "to confirm the list, or `DELETE ../watched-containers/{watchAppId}` " +
      "to remove."
  )
  @APIResponse(
    responseCode = "201",
    description = "Watch created (or already existed — idempotent).",
    content = @Content(schema = @Schema(implementation = WatchIO.class))
  )
  @APIResponse(responseCode = "400", description = "Bad request — missing kind or appId.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write on the Collection, or Read on the target Container.")
  @APIResponse(responseCode = "404", description = "No Collection with that appId.")
  public Response create(
    @PathParam("collectionAppId") @NotBlank String collectionAppId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = CreateWatchIO.class))
    ) @Valid CreateWatchIO body
  ) {
    if (body == null || body.containerKind() == null || body.containerAppId() == null
        || body.containerAppId().isBlank()) {
      return Response.status(Response.Status.BAD_REQUEST)
        .entity("{\"error\":\"containerKind and containerAppId are required\"}")
        .build();
    }
    WatchIO out = service.create(collectionAppId, body.containerKind(), body.containerAppId());
    return Response.status(Response.Status.CREATED).entity(out).build();
  }

  @DELETE
  @Path("{watchAppId}")
  @Operation(
    summary = "Remove a Watch link by its appId (WATCH1).",
    description =
      "Deletes the `:Watch` edge identified by `watchAppId` from the " +
      "Collection identified by `collectionAppId`. The target Container is " +
      "untouched — only the edge is removed.\n\n" +
      "Idempotency: deleting a non-existent or already-deleted Watch " +
      "returns 204 without error.\n\n" +
      "Sanity: the `watchAppId` must belong to a Watch attached to THIS " +
      "Collection. A Watch that exists but belongs to a different Collection " +
      "returns 404, not 403 (the caller has no way to tell whether the " +
      "Watch exists at all from another Collection's vantage).\n\n" +
      "Auth: Write on the Collection."
  )
  @APIResponse(responseCode = "204", description = "Watch removed (or wasn't there).")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write on the Collection.")
  @APIResponse(responseCode = "404", description = "Watch exists but belongs to a different Collection.")
  public Response delete(
    @PathParam("collectionAppId") @NotBlank String collectionAppId,
    @PathParam("watchAppId") @NotBlank String watchAppId
  ) {
    service.delete(collectionAppId, watchAppId);
    return Response.status(Response.Status.NO_CONTENT).build();
  }
}
