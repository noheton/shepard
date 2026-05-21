package de.dlr.shepard.v2.timeseriescontainer.resources;

import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPointsQueryParams;
import de.dlr.shepard.data.timeseries.model.TimeseriesEntity;
import de.dlr.shepard.data.timeseries.model.enums.DataPointValueType;
import de.dlr.shepard.data.timeseries.repositories.TimeseriesDataPointRepository;
import de.dlr.shepard.data.timeseries.repositories.TimeseriesRepository;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.v2.timeseriescontainer.io.LiveWindowPointIO;
import de.dlr.shepard.v2.timeseriescontainer.io.LiveWindowResponseIO;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * TS_LIVE1 — live-window endpoint for timeseries channel data.
 *
 * <p>Route: {@code GET /v2/timeseries-containers/{containerAppId}/channels/live-window}
 *
 * <p>Returns the most recent {@code windowSeconds} of data for a single channel
 * in a TimeseriesContainer, with optional linearly-interpolated boundary points at
 * the window start and end. Timestamps in the response are epoch milliseconds.
 *
 * <p>Auth: {@code @Authenticated}; Read permission is enforced by
 * {@link TimeseriesContainerService#getContainerByAppId}.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/timeseries-containers/{containerAppId}/channels/live-window")
@RequestScoped
@Authenticated
@Tag(name = "Timeseries container live window (TS_LIVE1)")
public class TimeseriesLiveWindowRest {

  private static final long NS_PER_MS = 1_000_000L;

  @Inject
  TimeseriesContainerService containerService;

  @Inject
  TimeseriesRepository timeseriesRepository;

  @Inject
  TimeseriesDataPointRepository dataPointRepository;

  @GET
  @Operation(
    summary = "Fetch the most recent N seconds of a timeseries channel.",
    description =
      "Returns the last `windowSeconds` of data for a single channel in the specified " +
      "TimeseriesContainer, keyed by the 5-tuple channel address " +
      "(`measurement`, `device`, `location`, `symbolicName`, `field`). Only `measurement` " +
      "is required; the remaining fields default to the literal string `\"\"` when omitted " +
      "so that channels ingested without those dimensions can be addressed without noise.\n\n" +
      "If multiple channels match the non-null filter fields the endpoint returns 400 " +
      "(ambiguous channel); add more filter fields to narrow to one. If no channel matches " +
      "the filters the endpoint returns 404.\n\n" +
      "**Boundary points** (when `withBoundaryPoints=true`, the default): if the first raw " +
      "data point in the window is at T+Δ (not at the window start T), a linearly " +
      "interpolated point is inserted at exactly T using the last known point before T. " +
      "Same logic at the trailing edge. Boundary points carry `interpolated: true`. " +
      "Boundary points are only added for numeric channel types (Double, Integer); " +
      "Boolean and String channels never get boundary points.\n\n" +
      "**No extrapolation**: if no point exists before the window start, no start boundary " +
      "is added. If the window contains no data at all, `points` is empty.\n\n" +
      "**Timestamps**: all timestamps in the response are epoch milliseconds (UTC). " +
      "The `windowStart` and `windowEnd` fields reflect the actual computed window " +
      "boundaries regardless of whether any data fell in the window.\n\n" +
      "Auth: Read permission on the container (enforced by `containerAppId` lookup)."
  )
  @APIResponse(
    responseCode = "200",
    description = "Window data for the channel (may have empty `points` array when no data exists in the window).",
    content = @Content(schema = @Schema(implementation = LiveWindowResponseIO.class))
  )
  @APIResponse(responseCode = "400",
    description = "Channel address is ambiguous — multiple channels match the supplied filter fields.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the container.")
  @APIResponse(responseCode = "404",
    description = "No TimeseriesContainer with that appId, or no channel matches the supplied filter fields.")
  public Response getLiveWindow(
    @PathParam("containerAppId") String containerAppId,
    @QueryParam("measurement") String measurement,
    @QueryParam("device") String device,
    @QueryParam("location") String location,
    @QueryParam("symbolicName") String symbolicName,
    @QueryParam("field") String field,
    @QueryParam("windowSeconds") @DefaultValue("300") @Min(1) @Max(3600) int windowSeconds,
    @QueryParam("withBoundaryPoints") @DefaultValue("true") boolean withBoundaryPoints
  ) {
    // Resolves the container by appId and enforces Read permission.
    var container = containerService.getContainerByAppId(containerAppId);
    long containerId = container.getId();

    // Build the window boundaries in nanoseconds (storage unit) and milliseconds (response unit).
    long nowNs = System.currentTimeMillis() * NS_PER_MS;
    long windowNs = (long) windowSeconds * 1_000_000_000L;
    long startNs = nowNs - windowNs;
    long windowStartMs = startNs / NS_PER_MS;
    long windowEndMs = nowNs / NS_PER_MS;

    // Resolve the channel entity. measurement is required; the rest default to "" to match
    // channels ingested without those dimensions.
    String m = measurement != null ? measurement : "";
    String d = device != null ? device : "";
    String l = location != null ? location : "";
    String s = symbolicName != null ? symbolicName : "";
    String f = field != null ? field : "";

    // Fetch all channels for this container and filter.
    List<TimeseriesEntity> candidates = timeseriesRepository.list("containerId", containerId);

    // Apply supplied non-default filters (non-null query params narrow the match).
    List<TimeseriesEntity> matched = candidates.stream()
      .filter(e -> (measurement == null || m.equals(e.getMeasurement())))
      .filter(e -> (device == null || d.equals(e.getDevice())))
      .filter(e -> (location == null || l.equals(e.getLocation())))
      .filter(e -> (symbolicName == null || s.equals(e.getSymbolicName())))
      .filter(e -> (field == null || f.equals(e.getField())))
      .toList();

    if (matched.isEmpty()) {
      return Response.status(Response.Status.NOT_FOUND)
        .entity("No channel matches the supplied filter fields in container " + containerAppId)
        .build();
    }
    if (matched.size() > 1) {
      return Response.status(Response.Status.BAD_REQUEST)
        .entity("Channel address is ambiguous — " + matched.size() +
          " channels match. Provide more specific filter fields.")
        .build();
    }

    TimeseriesEntity entity = matched.get(0);
    int tsId = entity.getId();
    DataPointValueType valueType = entity.getValueType();

    // Fetch the raw data points in the window.
    var queryParams = new TimeseriesDataPointsQueryParams(startNs, nowNs, null, null, null);
    List<TimeseriesDataPoint> raw = dataPointRepository.queryDataPoints(tsId, valueType, queryParams);

    List<LiveWindowPointIO> points = buildPoints(
      raw, tsId, valueType, startNs, nowNs, withBoundaryPoints);

    return Response.ok(new LiveWindowResponseIO(windowStartMs, windowEndMs, points)).build();
  }

  /**
   * Assembles the final point list, optionally prepending/appending interpolated boundary points.
   */
  private List<LiveWindowPointIO> buildPoints(
    List<TimeseriesDataPoint> raw,
    int tsId,
    DataPointValueType valueType,
    long startNs,
    long endNs,
    boolean withBoundaryPoints
  ) {
    List<LiveWindowPointIO> result = new ArrayList<>(raw.size() + 2);

    boolean canInterpolate = withBoundaryPoints &&
      (valueType == DataPointValueType.Double || valueType == DataPointValueType.Integer);

    if (canInterpolate && !raw.isEmpty()) {
      // Start boundary: needed when the first point is strictly after startNs.
      TimeseriesDataPoint first = raw.get(0);
      if (first.getTimestamp() > startNs) {
        Optional<TimeseriesDataPoint> before = dataPointRepository.findLatestBefore(tsId, valueType, startNs);
        if (before.isPresent()) {
          double interpolated = lerp(before.get(), first, startNs);
          result.add(new LiveWindowPointIO(startNs / 1_000_000L, interpolated, true));
        }
      }
    }

    // Add raw points.
    for (TimeseriesDataPoint dp : raw) {
      result.add(new LiveWindowPointIO(dp.getTimestamp() / 1_000_000L, dp.getValue(), false));
    }

    if (canInterpolate && !raw.isEmpty()) {
      // End boundary: needed when the last point is strictly before endNs.
      TimeseriesDataPoint last = raw.get(raw.size() - 1);
      if (last.getTimestamp() < endNs) {
        Optional<TimeseriesDataPoint> after = dataPointRepository.findEarliestAfter(tsId, valueType, endNs);
        if (after.isPresent()) {
          double interpolated = lerp(last, after.get(), endNs);
          result.add(new LiveWindowPointIO(endNs / 1_000_000L, interpolated, true));
        }
      }
    }

    return result;
  }

  /**
   * Linear interpolation between two data points at a target nanosecond timestamp.
   *
   * <p>Both points must have numeric values (Double or Integer).
   * The result is a {@code double} regardless of input type.
   */
  private double lerp(TimeseriesDataPoint a, TimeseriesDataPoint b, long targetNs) {
    long tA = a.getTimestamp();
    long tB = b.getTimestamp();
    double vA = toDouble(a.getValue());
    double vB = toDouble(b.getValue());
    if (tA == tB) return vA;
    double ratio = (double) (targetNs - tA) / (tB - tA);
    return vA + ratio * (vB - vA);
  }

  private double toDouble(Object v) {
    return ((Number) v).doubleValue();
  }
}
