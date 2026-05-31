package de.dlr.shepard.v2.timeseriescontainer.resources;

import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.v2.containers.io.ContainerCardinalityIO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
 * UI21-SIZEBAR-DATA — cheap cardinality summary for a TimeseriesContainer.
 *
 * <p>Route: {@code GET /v2/timeseries-containers/{containerId}/summary}
 *
 * <p>Returns the number of distinct channels (timeseries rows) in the container.
 * This is a single-aggregate SQL query against TimescaleDB — cheaper than the full
 * {@link TimeseriesContainerStatsRest} which also counts data points and estimates
 * ingest rates.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/timeseries-containers")
@RequestScoped
@Tag(name = "Timeseries containers — cardinality summary (UI21-SIZEBAR-DATA)")
public class TimeseriesContainerSummaryRest {

  @Inject
  TimeseriesContainerService timeseriesContainerService;

  @PersistenceContext
  EntityManager entityManager;

  @GET
  @Path("/{containerId}/summary")
  @Operation(
    summary = "Cardinality summary for a TimeseriesContainer.",
    description =
      "Returns the number of distinct channels (timeseries measurement rows) in this container. " +
      "Cheaper than /stats — single COUNT aggregate, no data-point scan. " +
      "Requires Read permission on the container."
  )
  @APIResponse(
    responseCode = "200",
    description = "Cardinality for the container.",
    content = @Content(schema = @Schema(implementation = ContainerCardinalityIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the container.")
  @APIResponse(responseCode = "404", description = "No TimeseriesContainer with that id.")
  public Response getSummary(@PathParam("containerId") long containerId) {
    // Permission / existence check — throws 403/404 if not accessible.
    timeseriesContainerService.getContainer(containerId);

    Number channelCount = (Number) entityManager.createNativeQuery(
      "SELECT COUNT(*) FROM timeseries t WHERE t.container_id = :cid"
    ).setParameter("cid", containerId).getSingleResult();

    return Response.ok(new ContainerCardinalityIO(channelCount.intValue())).build();
  }
}
