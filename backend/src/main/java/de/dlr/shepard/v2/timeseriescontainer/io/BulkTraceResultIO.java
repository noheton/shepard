package de.dlr.shepard.v2.timeseriescontainer.io;

import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * TS-OPT2 (bulk-trace variant) — per-channel result inside the response of
 * {@code POST /v2/timeseries-containers/{id}/channels/bulk}.
 *
 * <p>The {@code role} field echoes the caller-assigned label from the request,
 * allowing the frontend to dispatch each data series directly to its render
 * role without a secondary lookup.
 */
@Schema(name = "BulkTraceResult", description = "Data points for one channel, tagged with its caller-assigned role.")
public record BulkTraceResultIO(

  @Schema(description = "The role label supplied in the request (e.g. \"x\", \"y\", \"rot_a\").")
  String role,

  @Schema(description = "Data points for this channel. Empty list when the channel was not found.")
  List<TimeseriesDataPoint> points
) {}
