package de.dlr.shepard.v2.structureddatacontainer.resources;

import de.dlr.shepard.common.exceptions.ProblemJson;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * APISIMP-STRUCT-CONTAINER-STATS-UNIFY — 410 Gone tombstone.
 *
 * <p>This path has been merged into the kind-dispatcher. Use
 * {@code GET /v2/containers/{appId}/stats} instead (returns {@code entryCount}
 * for structured-data containers).
 */
@Produces(MediaType.APPLICATION_JSON)
@Path("/v2/structured-data-containers")
@RequestScoped
@Tag(name = "Structured data containers — stats (tombstoned, use /v2/containers/{appId}/stats)")
public class StructuredDataContainerStatsRest {

  @GET
  @Path("/{containerAppId}/stats")
  @Operation(
    operationId = "getStructuredDataContainerStats_gone",
    summary = "[Gone] Use GET /v2/containers/{appId}/stats",
    deprecated = true
  )
  @APIResponse(responseCode = "410", description = "Endpoint removed. Use GET /v2/containers/{appId}/stats.")
  public Response getStats(@PathParam("containerAppId") String containerAppId) {
    String newLocation = UriBuilder.fromPath("/v2/containers/{appId}/stats")
      .build(containerAppId)
      .toString();
    return Response.status(Response.Status.GONE)
      .type("application/problem+json")
      .header("Location", newLocation)
      .entity(new ProblemJson(
        "urn:shepard:error:gone",
        "Gone",
        Response.Status.GONE.getStatusCode(),
        "This path has been removed. Use GET " + newLocation + " instead.",
        null))
      .build();
  }
}
