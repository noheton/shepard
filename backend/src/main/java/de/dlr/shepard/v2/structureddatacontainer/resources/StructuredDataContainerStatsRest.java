package de.dlr.shepard.v2.structureddatacontainer.resources;

import de.dlr.shepard.data.structureddata.entities.StructuredDataContainer;
import de.dlr.shepard.data.structureddata.services.StructuredDataContainerService;
import de.dlr.shepard.v2.structureddatacontainer.io.StructuredDataContainerStatsIO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * UI21-SIZEBAR-DATA — cardinality stats for a StructuredDataContainer.
 *
 * <p>Route: {@code GET /v2/structured-data-containers/{containerId}/stats}
 *
 * <p>Returns the number of {@link de.dlr.shepard.data.structureddata.entities.StructuredData} nodes
 * attached to the container. Used by the /containers list sizebar to display a
 * domain-meaningful scale indicator (entry count) instead of the CC1e referenced-by proxy.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/structured-data-containers")
@RequestScoped
@Tag(name = "Structured data containers — cardinality stats (UI21-SIZEBAR-DATA)")
public class StructuredDataContainerStatsRest {

  @Inject
  StructuredDataContainerService structuredDataContainerService;

  @GET
  @Path("/{containerId}/stats")
  @Operation(
    summary = "Entry count for a StructuredDataContainer.",
    description = "Returns the number of structured-data entries stored in this container. " +
      "Requires Read permission on the container. " +
      "Used by the /containers list sizebar to show a domain-meaningful scale indicator."
  )
  @APIResponse(
    responseCode = "200",
    description = "Cardinality stats for the container.",
    content = @Content(schema = @Schema(implementation = StructuredDataContainerStatsIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the container.")
  @APIResponse(responseCode = "404", description = "No StructuredDataContainer with that id.")
  public Response getStats(@PathParam("containerId") long containerId) {
    // Permission check — throws 403/404 if not accessible.
    StructuredDataContainer container = structuredDataContainerService.getContainer(containerId);

    long entryCount = container.getStructuredDatas() != null ? container.getStructuredDatas().size() : 0L;

    return Response.ok(new StructuredDataContainerStatsIO(entryCount)).build();
  }
}
