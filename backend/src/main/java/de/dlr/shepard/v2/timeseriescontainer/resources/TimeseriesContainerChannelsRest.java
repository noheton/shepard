package de.dlr.shepard.v2.timeseriescontainer.resources;

import de.dlr.shepard.data.timeseries.io.TimeseriesWithDataPoints;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPointsQueryParams;
import de.dlr.shepard.data.timeseries.model.TimeseriesEntity;
import de.dlr.shepard.data.timeseries.repositories.TsChannelResolver;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import de.dlr.shepard.data.timeseries.util.Lttb;
import de.dlr.shepard.v2.timeseriescontainer.io.TimeseriesChannelV2IO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * TS-ID PR-2 — per-channel listing for a TimeseriesContainer with
 * {@code shepardId} as the canonical channel handle.
 *
 * <p>Route: {@code GET /v2/timeseries-containers/{containerId}/channels}
 *
 * <p>This is the first wire-side payoff of the TS-ID substrate (PR-1).
 * Existing v1 callers of {@code /shepard/api/timeseriesContainers/{id}/timeseries}
 * continue to receive byte-identical responses (no {@code shepardId} field) —
 * the {@code V1WireFidelityTest} regression test guards that contract.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/timeseries-containers")
@RequestScoped
@Tag(name = "Timeseries containers — channel listing (TS-ID PR-2)")
public class TimeseriesContainerChannelsRest {

  @Inject
  TimeseriesContainerService timeseriesContainerService;

  @Inject
  TsChannelResolver tsChannelResolver;

  @Inject
  TimeseriesService timeseriesService;

  @GET
  @Path("/{containerId}/channels")
  @Operation(
    summary = "List all channels of a TimeseriesContainer with shepardId.",
    description = "Returns one entry per channel in the container, each carrying its " +
      "stable single-field identity (shepardId) plus the legacy 5-tuple (measurement, " +
      "device, location, symbolicName, field). The 5-tuple stays valid as a lookup key " +
      "for one release cycle; new integrations should prefer shepardId."
  )
  @APIResponse(
    responseCode = "200",
    description = "Per-channel listing.",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = TimeseriesChannelV2IO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the container.")
  @APIResponse(responseCode = "404", description = "No TimeseriesContainer with that id.")
  public Response listChannels(@PathParam("containerId") long containerId) {
    // Permission check — throws 403/404 if not accessible.
    timeseriesContainerService.getContainer(containerId);

    List<TimeseriesEntity> rows = tsChannelResolver.list("containerId", containerId);
    List<TimeseriesChannelV2IO> body = rows.stream().map(TimeseriesChannelV2IO::from).toList();
    return Response.ok(body).build();
  }

  /** Default target when {@code max_points} is absent; mirrors the v1 default. */
  private static final int DEFAULT_MAX_POINTS = 2000;
  private static final int HARD_MAX_POINTS    = 5000;

  @GET
  @Path("/{containerId}/channels/{shepardId}/data")
  @Operation(
    summary = "Fetch data points for a channel by shepardId (TS-IDc).",
    description = "Resolves the single-field shepardId to the legacy 5-tuple internally " +
      "and returns data points for the requested time window. " +
      "Accepts optional LTTB downsampling via ?downsample=lttb&max_points=N. " +
      "This endpoint supersedes the 5-tuple query params on the v1 surface for new integrations."
  )
  @APIResponse(
    responseCode = "200",
    description = "Data points for the channel.",
    content = @Content(schema = @Schema(implementation = TimeseriesWithDataPoints.class))
  )
  @APIResponse(responseCode = "400", description = "start or end missing / negative.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the container.")
  @APIResponse(responseCode = "404", description = "No channel with that shepardId in this container.")
  public Response getChannelData(
    @PathParam("containerId") long containerId,
    @PathParam("shepardId") UUID shepardId,
    @QueryParam("start")      @NotNull @PositiveOrZero Long start,
    @QueryParam("end")        @NotNull @PositiveOrZero Long end,
    @QueryParam("downsample") String downsample,
    @QueryParam("max_points") Integer maxPoints
  ) {
    timeseriesContainerService.getContainer(containerId);

    Timeseries tuple = tsChannelResolver.resolveTuple(shepardId)
      .orElse(null);
    if (tuple == null) {
      return Response.status(Response.Status.NOT_FOUND)
        .entity("No channel with shepardId " + shepardId + " in container " + containerId)
        .build();
    }

    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
      start, end, null, null, null
    );
    var points = timeseriesService.getDataPointsByTimeseries(containerId, tuple, queryParams);

    if (points != null && downsample != null && "lttb".equalsIgnoreCase(downsample.trim())) {
      int target = maxPoints == null ? DEFAULT_MAX_POINTS : Math.min(Math.max(maxPoints, 1), HARD_MAX_POINTS);
      points = Lttb.downsample(points, target);
    }

    return Response.ok(new TimeseriesWithDataPoints(tuple, points)).build();
  }
}
