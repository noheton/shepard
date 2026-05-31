package de.dlr.shepard.v2.structureddatacontainer.resources;

import de.dlr.shepard.data.structureddata.entities.StructuredDataContainer;
import de.dlr.shepard.data.structureddata.services.StructuredDataContainerService;
import de.dlr.shepard.v2.containers.io.ContainerCardinalityIO;
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
 * UI21-SIZEBAR-DATA — cheap cardinality summary for a StructuredDataContainer.
 *
 * <p>Route: {@code GET /v2/structured-data-containers/{containerId}/summary}
 *
 * <p>Returns the number of structured-data records stored in the container. The count is
 * read from the in-memory Neo4j relationship ({@code structuredDatas} list) loaded by
 * {@link StructuredDataContainerService#getContainer(long)}, so it is O(1) once the
 * container entity is loaded.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/structured-data-containers")
@RequestScoped
@Tag(name = "Structured data containers — cardinality summary (UI21-SIZEBAR-DATA)")
public class StructuredDataContainerSummaryRest {

  @Inject
  StructuredDataContainerService structuredDataContainerService;

  @GET
  @Path("/{containerId}/summary")
  @Operation(
    summary = "Cardinality summary for a StructuredDataContainer.",
    description =
      "Returns the number of structured-data records stored in this container. " +
      "Requires Read permission on the container."
  )
  @APIResponse(
    responseCode = "200",
    description = "Cardinality for the container.",
    content = @Content(schema = @Schema(implementation = ContainerCardinalityIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the container.")
  @APIResponse(responseCode = "404", description = "No StructuredDataContainer with that id.")
  public Response getSummary(@PathParam("containerId") long containerId) {
    StructuredDataContainer container = structuredDataContainerService.getContainer(containerId);
    int recordCount = container.getStructuredDatas() != null ? container.getStructuredDatas().size() : 0;
    return Response.ok(new ContainerCardinalityIO(recordCount)).build();
  }
}
