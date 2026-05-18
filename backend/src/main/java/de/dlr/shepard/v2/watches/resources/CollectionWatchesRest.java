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
    summary = "List containers this Collection is watching.",
    description = "Returns one row per :Watch attached to the Collection, " +
    "with the target container's name + availability inlined when the " +
    "caller has Read on the container. Availability tokens: available / " +
    "forbidden / deleted / error."
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
    summary = "Add a watch link to a target Container.",
    description = "Body: {containerKind, containerAppId}. containerKind is " +
    "TIMESERIES / FILE / STRUCTURED_DATA. Idempotent: a duplicate (collection, " +
    "container) pair returns the existing watch instead of 409."
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
    summary = "Remove a watch link by its appId.",
    description = "Idempotent: removing a non-existent watch returns 204."
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
