package de.dlr.shepard.v2.filecontainer.resources;

import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * APISIMP-FILE-CONTAINER-STATS-UNIFY — 410 Gone tombstone.
 *
 * <p>This path has been merged into the kind-dispatcher. Use
 * {@code GET /v2/containers/{appId}/stats} instead (returns {@code fileCount}
 * for file containers).
 */
@Produces(MediaType.APPLICATION_JSON)
@Path("/v2/file-containers")
@RequestScoped
@Tag(name = "File containers — stats (tombstoned, use /v2/containers/{appId}/stats)")
public class FileContainerStatsRest {

  private static final String GONE_MSG =
    "This path has been removed. Use GET /v2/containers/{appId}/stats instead.";

  @GET
  @Path("/{containerAppId}/stats")
  @Operation(
    operationId = "getFileContainerStats_gone",
    summary = "[Gone] Use GET /v2/containers/{appId}/stats",
    deprecated = true
  )
  @APIResponse(responseCode = "410", description = "Endpoint removed. Use GET /v2/containers/{appId}/stats.")
  public Response getStats(@PathParam("containerAppId") String containerAppId) {
    return Response.status(Response.Status.GONE).entity(GONE_MSG).build();
  }
}
