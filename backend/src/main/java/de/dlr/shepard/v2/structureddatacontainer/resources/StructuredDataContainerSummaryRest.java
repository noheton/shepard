package de.dlr.shepard.v2.structureddatacontainer.resources;

import de.dlr.shepard.data.structureddata.entities.StructuredDataContainer;
import de.dlr.shepard.data.structureddata.services.StructuredDataContainerService;
import de.dlr.shepard.v2.containers.io.ContainerCardinalitySummaryIO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * UI21-SIZEBAR-DATA — per-kind cardinality summary for a StructuredDataContainer.
 *
 * <p>Route: {@code GET /v2/structured-data-containers/{containerId}/summary}
 *
 * <p>Returns the number of structured-data payloads currently linked to the
 * container. The payload list is loaded from the Neo4j graph via
 * {@link StructuredDataContainerService#getContainer(long)}, which eagerly
 * hydrates the {@code structuredDatas} relationship at depth 1; the count
 * is then a simple Java {@code List.size()} call — no extra query.
 *
 * <p>This drives the sizebar metric in the frontend container list
 * (UI21-SIZEBAR-DATA).
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
    description = "Returns the payload count (number of StructuredData nodes linked to this container) " +
      "and the current timestamp. Used by the frontend container-list sizebar (UI21-SIZEBAR-DATA). " +
      "The payload list is loaded from Neo4j at depth-1 (already cached by the permission check) — " +
      "no additional query. Requires Read permission on the container."
  )
  @APIResponse(
    responseCode = "200",
    description = "Cardinality summary for the container.",
    content = @Content(schema = @Schema(implementation = ContainerCardinalitySummaryIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the container.")
  @APIResponse(responseCode = "404", description = "No StructuredDataContainer with that id.")
  public Response getSummary(@PathParam("containerId") long containerId) {
    // getContainer performs the read-permission check and loads structuredDatas at depth 1.
    StructuredDataContainer container = structuredDataContainerService.getContainer(containerId);

    long payloadCount = container.getStructuredDatas() != null
      ? (long) container.getStructuredDatas().size()
      : 0L;

    return Response.ok(new ContainerCardinalitySummaryIO(
      payloadCount,
      Instant.now().toString()
    )).build();
  }
}
