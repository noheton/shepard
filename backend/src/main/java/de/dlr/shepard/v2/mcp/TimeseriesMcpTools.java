package de.dlr.shepard.v2.mcp;

import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPointsQueryParams;
import de.dlr.shepard.data.timeseries.model.TimeseriesEntity;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import de.dlr.shepard.data.timeseries.util.Lttb;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Phase 1 — Timeseries container tools (aidocs/88 §3.1).
 *
 * <p>Two tools:
 * <ul>
 *   <li>{@code list_channels} — channel discovery (5-tuple identifiers)</li>
 *   <li>{@code get_channel_data} — actual point retrieval, with LTTB
 *       downsampling so an agent can ask for "a chart-friendly view" of
 *       a multi-million-sample window without blowing the context.</li>
 * </ul>
 */
@ApplicationScoped
public class TimeseriesMcpTools {

  /** Cap on points returned in a single call to keep response bounded. */
  private static final int MAX_POINTS_CAP = 5000;

  /** Default downsample target if the caller omits {@code maxPoints}. */
  private static final int DEFAULT_MAX_POINTS = 2000;

  @Inject
  TimeseriesService timeseriesService;

  @Inject
  McpContextBridge contextBridge;

  @Inject
  McpToolSupport support;

  @Tool(
    name = "list_channels",
    description =
      "Enumerate every channel inside a TimeseriesContainer. Use this to discover " +
      "which sensors / signals a DataObject's timeseries payload contains before " +
      "deciding what to plot, summarise, or compare.\n\n" +
      "A channel in shepard is identified by a 5-tuple, NOT a single id:\n" +
      "  measurement   — what was sampled (e.g. \"vibration\", \"thermal\")\n" +
      "  device        — instrument or DUT subsystem (e.g. \"turbopump\")\n" +
      "  location      — where on the device (e.g. \"turbopump_bearing\")\n" +
      "  symbolicName  — instance tag (e.g. \"TB1\")\n" +
      "  field         — quantity / unit-bound column (e.g. \"rms_g\")\n" +
      "All five together uniquely address a channel inside one container; one " +
      "tuple element on its own is ambiguous. (A future migration — aidocs/87 — " +
      "will collapse this 5-tuple to a single channel appId.)\n\n" +
      "Response: JSON array of {measurement, device, location, symbolicName, field}.\n\n" +
      "Use `get_channel_data` next to fetch actual timestamp/value samples for a " +
      "specific channel."
  )
  public String listChannels(
    @ToolArg(description = "UUID v7 of the TimeseriesContainer. Get this from `get_data_object → containers.timeseries[].containerAppId`. NOT the DataObject appId, NOT a FileContainer/StructuredDataContainer appId.") String containerAppId
  ) {
    return support.run("list_channels", () -> {
      contextBridge.bind();
      long containerOgmId = support.resolveOfType(containerAppId, "TimeseriesContainer", "containerAppId");

      List<TimeseriesEntity> channels = timeseriesService.getTimeseriesAvailable(containerOgmId);
      List<Map<String, Object>> result = new ArrayList<>(channels.size());
      for (TimeseriesEntity ch : channels) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("measurement", ch.getMeasurement());
        row.put("device", ch.getDevice());
        row.put("location", ch.getLocation());
        row.put("symbolicName", ch.getSymbolicName());
        row.put("field", ch.getField());
        result.add(row);
      }
      return support.toJson(result);
    });
  }

  @Tool(
    name = "get_channel_data",
    description =
      "Fetch raw timestamp/value samples for one channel inside a TimeseriesContainer, " +
      "with LTTB downsampling so the response stays bounded even when the underlying " +
      "channel has millions of samples.\n\n" +
      "Identify the channel by passing the same 5-tuple {measurement, device, location, " +
      "symbolicName, field} returned by `list_channels`. Optionally bound the window " +
      "with `startNanos` / `endNanos`; if omitted, all available samples in the channel " +
      "are queried.\n\n" +
      "Response shape:\n" +
      "{\n" +
      "  \"channel\":     {measurement, device, location, symbolicName, field},\n" +
      "  \"window\":      {startNanos, endNanos},\n" +
      "  \"raw_count\":   <samples available in the window>,\n" +
      "  \"returned_count\": <samples in the response after downsampling>,\n" +
      "  \"downsampled\": true|false,\n" +
      "  \"algorithm\":   \"LTTB\" | \"none\",\n" +
      "  \"points\":      [{timestamp: <ns since epoch>, value: <number|string|bool>}, ...]\n" +
      "}\n\n" +
      "Time unit is **nanoseconds since the Unix epoch** throughout (shepard's native " +
      "timeseries resolution). To convert to milliseconds divide by 1_000_000.\n\n" +
      "`maxPoints` caps the response size (default 2000, hard ceiling " + MAX_POINTS_CAP + "). " +
      "LTTB (Largest-Triangle-Three-Buckets) preserves visual shape — peaks, troughs and " +
      "trends survive even at aggressive 100× compression. For exact analysis (e.g. " +
      "anomaly threshold counts) pass a narrow window so raw_count ≤ maxPoints and the " +
      "response carries every sample (downsampled=false)."
  )
  public String getChannelData(
    @ToolArg(description = "UUID v7 of the TimeseriesContainer (from `get_data_object → containers.timeseries[].containerAppId`).") String containerAppId,
    @ToolArg(description = "Channel measurement (from `list_channels` rows).") String measurement,
    @ToolArg(description = "Channel device.") String device,
    @ToolArg(description = "Channel location.") String location,
    @ToolArg(description = "Channel symbolicName.") String symbolicName,
    @ToolArg(description = "Channel field.") String field,
    @ToolArg(required = false, description = "Window start in nanoseconds since Unix epoch. Omit for open-start.") Long startNanos,
    @ToolArg(required = false, description = "Window end in nanoseconds since Unix epoch. Omit for open-end.") Long endNanos,
    @ToolArg(required = false, description = "Max points in response. Default 2000, capped at " + MAX_POINTS_CAP + ".") Integer maxPoints
  ) {
    return support.run("get_channel_data", () -> {
      contextBridge.bind();
      long containerOgmId = support.resolveOfType(containerAppId, "TimeseriesContainer", "containerAppId");

      if (measurement == null || measurement.isBlank()
        || device == null || device.isBlank()
        || location == null || location.isBlank()
        || symbolicName == null || symbolicName.isBlank()
        || field == null || field.isBlank()) {
        throw McpToolSupport.invalidParams(
          "All five channel-identity fields are required (measurement, device, location, symbolicName, field). " +
          "Get them from a `list_channels` row."
        );
      }

      long start = startNanos != null ? startNanos : Long.MIN_VALUE;
      long end = endNanos != null ? endNanos : Long.MAX_VALUE;
      int cap = maxPoints != null ? Math.min(Math.max(maxPoints, 1), MAX_POINTS_CAP) : DEFAULT_MAX_POINTS;

      Timeseries channel = new Timeseries(measurement, device, location, symbolicName, field);
      var params = new TimeseriesDataPointsQueryParams(start, end, null, null, null);

      List<TimeseriesDataPoint> raw = timeseriesService.getDataPointsByTimeseries(containerOgmId, channel, params);
      int rawCount = raw.size();

      List<TimeseriesDataPoint> shown;
      boolean downsampled;
      if (rawCount <= cap) {
        shown = raw;
        downsampled = false;
      } else {
        shown = Lttb.downsample(raw, cap);
        downsampled = true;
      }

      Map<String, Object> body = new LinkedHashMap<>();
      Map<String, Object> ch = new LinkedHashMap<>();
      ch.put("measurement", measurement);
      ch.put("device", device);
      ch.put("location", location);
      ch.put("symbolicName", symbolicName);
      ch.put("field", field);
      body.put("channel", ch);
      Map<String, Object> window = new LinkedHashMap<>();
      window.put("startNanos", startNanos);
      window.put("endNanos", endNanos);
      body.put("window", window);
      body.put("raw_count", rawCount);
      body.put("returned_count", shown.size());
      body.put("downsampled", downsampled);
      body.put("algorithm", downsampled ? "LTTB" : "none");
      if (rawCount == 0) {
        // Make "channel exists but is empty" explicit so the agent does not
        // mistake an empty response for an error and start retrying.
        body.put(
          "note",
          "No samples in this window. The channel descriptor matched a known " +
          "TimeseriesContainer slot but no points were written for it in the queried range " +
          "(startNanos..endNanos). Try widening the window, or confirm via `list_channels` " +
          "that the 5-tuple is spelled exactly as listed."
        );
      }

      List<Map<String, Object>> points = new ArrayList<>(shown.size());
      for (TimeseriesDataPoint p : shown) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("timestamp", p.getTimestamp());
        row.put("value", p.getValue());
        points.add(row);
      }
      body.put("points", points);
      return support.toJson(body);
    });
  }

  /** Test-visible delegate — the real impl lives in {@link Lttb}. */
  static List<TimeseriesDataPoint> lttb(List<TimeseriesDataPoint> points, int target) {
    return Lttb.downsample(points, target);
  }
}
