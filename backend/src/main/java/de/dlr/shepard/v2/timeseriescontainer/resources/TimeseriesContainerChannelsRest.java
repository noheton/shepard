package de.dlr.shepard.v2.timeseriescontainer.resources;

import de.dlr.shepard.data.timeseries.io.TimeseriesWithDataPoints;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPointsQueryParams;
import de.dlr.shepard.data.timeseries.model.TimeseriesEntity;
import de.dlr.shepard.data.timeseries.repositories.TsChannelResolver;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import de.dlr.shepard.v2.timeseriescontainer.io.BulkChannelDataRequestIO;
import de.dlr.shepard.v2.timeseriescontainer.io.BulkTraceChannelIO;
import de.dlr.shepard.v2.timeseriescontainer.io.BulkTraceRequestIO;
import de.dlr.shepard.v2.timeseriescontainer.io.BulkTraceResultIO;
import de.dlr.shepard.v2.timeseriescontainer.io.CopyIngestRequestIO;
import de.dlr.shepard.v2.timeseriescontainer.io.TimeseriesChannelV2IO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
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

  private static final int DEFAULT_PAGE_SIZE = 200;
  private static final int MAX_PAGE_SIZE     = 1000;

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
  public Response listChannels(
    @PathParam("containerId") long containerId,
    @QueryParam("page") @DefaultValue("0") @PositiveOrZero int page,
    @QueryParam("size") @DefaultValue("200") @PositiveOrZero int size
  ) {
    timeseriesContainerService.getContainer(containerId);

    int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    List<TimeseriesEntity> rows = tsChannelResolver.listPaged(containerId, page, safeSize);
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

    var points = downsample != null && "lttb".equalsIgnoreCase(downsample.trim())
      ? timeseriesService.getDataPointsLttbOptimised(
          containerId, tuple, start, end,
          maxPoints == null ? DEFAULT_MAX_POINTS : Math.min(Math.max(maxPoints, 1), HARD_MAX_POINTS))
      : timeseriesService.getDataPointsByTimeseries(
          containerId, tuple, new TimeseriesDataPointsQueryParams(start, end, null, null, null));

    return Response.ok(new TimeseriesWithDataPoints(tuple, points)).build();
  }

  // ── TS-OPT2: multi-channel bulk raw fetch ─────────────────────────────────

  @POST
  @Path("/{containerId}/channels/data/bulk")
  @Operation(
    summary = "Fetch raw data for multiple channels in one call (TS-OPT2).",
    description = "Accepts a list of shepardIds (max 200) plus a shared time window and returns " +
      "raw data points — one TimeseriesWithDataPoints entry per resolved channel. " +
      "Unknown IDs are silently skipped. No downsampling is applied; use the single-channel " +
      "endpoint with ?downsample=lttb when a reduced point count is needed."
  )
  @APIResponse(
    responseCode = "200",
    description = "Raw data for all resolved channels.",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = TimeseriesWithDataPoints.class))
  )
  @APIResponse(responseCode = "400", description = "Validation error on request body.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the container.")
  @APIResponse(responseCode = "404", description = "No TimeseriesContainer with that id.")
  public Response getBulkChannelData(
    @PathParam("containerId") long containerId,
    @NotNull @Valid BulkChannelDataRequestIO body
  ) {
    timeseriesContainerService.getContainer(containerId);

    // Single bulk query resolves all shepardIds → entities; unknown ids are silently absent.
    // Then fetch data points sequentially — avoids the parallelStream connection-pool storm.
    var entities = tsChannelResolver.bulkFindByShepardIds(body.shepardIds());
    List<TimeseriesWithDataPoints> results = timeseriesService.getManyDataPointsByEntities(
      containerId, entities,
      new TimeseriesDataPointsQueryParams(body.start(), body.end(), null, null, null));

    return Response.ok(results).build();
  }

  // ── TS-OPT3-COPY: high-throughput COPY-protocol single-channel ingest ────────

  @POST
  @Path("/{containerId}/channels/{shepardId}/data/ingest")
  @Operation(
    summary = "High-throughput COPY ingest for a single channel (TS-OPT3-COPY).",
    description = "Uses the PostgreSQL COPY protocol, which is 3–5× faster than the " +
      "VALUES INSERT path for bulk historical loads. The channel (identified by shepardId) " +
      "must already exist; create it first via the regular endpoint if needed. " +
      "No ON CONFLICT handling is applied: timestamps must be unique within the batch " +
      "and must not duplicate rows already stored for this channel. " +
      "Designed for high-throughput import scripts that batch thousands of points per call."
  )
  @APIResponse(responseCode = "204", description = "Data ingested successfully.")
  @APIResponse(responseCode = "400", description = "Validation error or duplicate timestamp.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the container.")
  @APIResponse(responseCode = "404", description = "No channel with that shepardId in this container.")
  public Response ingestChannelData(
    @PathParam("containerId") long containerId,
    @PathParam("shepardId") UUID shepardId,
    @NotNull @Valid CopyIngestRequestIO body
  ) {
    timeseriesContainerService.getContainer(containerId);

    var entity = tsChannelResolver.findByShepardId(shepardId)
      .filter(e -> e.getContainerId() == containerId)
      .orElse(null);
    if (entity == null) {
      return Response.status(Response.Status.NOT_FOUND)
        .entity("No channel with shepardId " + shepardId + " in container " + containerId)
        .build();
    }

    timeseriesService.ingestDataPointsCopy(containerId, entity, body.dataPoints());
    return Response.noContent().build();
  }
}
