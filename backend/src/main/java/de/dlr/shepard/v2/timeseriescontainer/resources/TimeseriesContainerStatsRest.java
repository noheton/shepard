package de.dlr.shepard.v2.timeseriescontainer.resources;

import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.v2.timeseriescontainer.io.TimeseriesContainerStatsIO;
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
 * TS_STATS1 — storage and ingestion statistics for a TimeseriesContainer.
 *
 * <p>Route: {@code GET /v2/timeseries-containers/{containerId}/stats}
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/timeseries-containers")
@RequestScoped
@Tag(name = "Timeseries containers — storage stats (TS_STATS1)")
public class TimeseriesContainerStatsRest {

  private static final int BYTES_PER_POINT = 28; // 8 time + 8 double_value + 4 id + 8 overhead
  private static final long WINDOW_NS = 10_000_000_000L; // 10 seconds in nanoseconds

  @Inject
  TimeseriesContainerService timeseriesContainerService;

  @PersistenceContext
  EntityManager entityManager;

  @GET
  @Path("/{containerId}/stats")
  @Operation(
    summary = "Storage and ingestion stats for a TimeseriesContainer.",
    description = "Returns point count, channel count, estimated uncompressed size, and recent " +
      "ingest rate. Requires Read permission on the container. The size is an uncompressed " +
      "estimate (pointCount × 28 bytes); compressed chunks on disk are typically 5–10× smaller."
  )
  @APIResponse(
    responseCode = "200",
    description = "Stats for the container.",
    content = @Content(schema = @Schema(implementation = TimeseriesContainerStatsIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the container.")
  @APIResponse(responseCode = "404", description = "No TimeseriesContainer with that id.")
  public Response getStats(@PathParam("containerId") long containerId) {
    // Permission check — throws 403/404 if not accessible.
    timeseriesContainerService.getContainer(containerId);

    Object[] totals = (Object[]) entityManager.createNativeQuery(
      "SELECT COUNT(dp.timeseries_id) AS point_count, COUNT(DISTINCT dp.timeseries_id) AS channel_count " +
      "FROM timeseries_data_points dp " +
      "JOIN timeseries t ON dp.timeseries_id = t.id " +
      "WHERE t.container_id = :cid"
    ).setParameter("cid", containerId).getSingleResult();

    long pointCount = ((Number) totals[0]).longValue();
    long channelCount = ((Number) totals[1]).longValue();

    long nowNs = System.currentTimeMillis() * 1_000_000L;
    Number recent = (Number) entityManager.createNativeQuery(
      "SELECT COUNT(*) " +
      "FROM timeseries_data_points dp " +
      "JOIN timeseries t ON dp.timeseries_id = t.id " +
      "WHERE t.container_id = :cid AND dp.time > :windowStart"
    )
      .setParameter("cid", containerId)
      .setParameter("windowStart", nowNs - WINDOW_NS)
      .getSingleResult();

    long recentPoints = recent.longValue();
    long ingestRate = recentPoints * BYTES_PER_POINT / 10;

    return Response.ok(new TimeseriesContainerStatsIO(
      pointCount,
      channelCount,
      pointCount * BYTES_PER_POINT,
      recentPoints,
      ingestRate
    )).build();
  }
}
