package de.dlr.shepard.v2.timeseriescontainer.resources;

import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.v2.containers.io.ContainerCardinalitySummaryIO;
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
import java.time.Instant;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * UI21-SIZEBAR-DATA — per-kind cardinality summary for a TimeseriesContainer.
 *
 * <p>Route: {@code GET /v2/timeseries-containers/{containerId}/summary}
 *
 * <p>Returns the number of distinct channels in the container (TimescaleDB
 * {@code COUNT(DISTINCT timeseries_id)} on the {@code timeseries_data_points}
 * table — or equivalently the number of rows in the {@code timeseries} table
 * with the given {@code container_id}). The channel count drives the sizebar
 * metric in the frontend container list (UI21-SIZEBAR-DATA).
 *
 * <p>The query is a single cheap {@code COUNT} with no joins beyond the
 * {@code timeseries} index scan; it is safe to call once per container row
 * in a fire-and-forget frontend loop.
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
    description = "Returns the channel count (number of distinct timeseries rows in the container) " +
      "and the current timestamp. Used by the frontend container-list sizebar (UI21-SIZEBAR-DATA). " +
      "The query is a single cheap TimescaleDB COUNT — safe for fire-and-forget per-row calls. " +
      "Requires Read permission on the container."
  )
  @APIResponse(
    responseCode = "200",
    description = "Cardinality summary for the container.",
    content = @Content(schema = @Schema(implementation = ContainerCardinalitySummaryIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the container.")
  @APIResponse(responseCode = "404", description = "No TimeseriesContainer with that id.")
  public Response getSummary(@PathParam("containerId") long containerId) {
    // Permission check — throws 403/404 if not accessible.
    timeseriesContainerService.getContainer(containerId);

    Number channelCount = (Number) entityManager.createNativeQuery(
      "SELECT COUNT(*) FROM timeseries WHERE container_id = :cid"
    ).setParameter("cid", containerId).getSingleResult();

    return Response.ok(new ContainerCardinalitySummaryIO(
      channelCount.longValue(),
      Instant.now().toString()
    )).build();
  }
}
