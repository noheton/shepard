package de.dlr.shepard.v2.timeseriescontainer.io;

import de.dlr.shepard.data.timeseries.io.TimeseriesWithDataPoints;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * APISIMP-TIMESERIES-DATAPOINT-TS — v2 response wrapper for
 * {@code GET /v2/containers/{appId}/channels/{channelShepardId}/data}.
 *
 * <p>Mirrors {@link TimeseriesWithDataPoints} but uses {@link TimeseriesDataPointV2IO}
 * so timestamps appear as ISO 8601 UTC strings instead of nanosecond longs.
 * The v1 {@link TimeseriesWithDataPoints} shape on {@code /shepard/api/} is untouched.
 */
@Schema(
  name = "TimeseriesWithDataPointsV2",
  description = "Timeseries metadata plus its ISO 8601 timestamped data points (v2 surface)."
)
public record TimeseriesWithDataPointsV2IO(

  @Schema(description = "Timeseries 5-tuple identifying the channel.")
  Timeseries timeseries,

  @Schema(description = "Data points ordered by timestamp.")
  List<TimeseriesDataPointV2IO> points

) {

  public static TimeseriesWithDataPointsV2IO from(TimeseriesWithDataPoints src) {
    List<TimeseriesDataPointV2IO> v2Points = src.getPoints() == null
        ? List.of()
        : src.getPoints().stream().map(TimeseriesDataPointV2IO::from).toList();
    return new TimeseriesWithDataPointsV2IO(src.getTimeseries(), v2Points);
  }
}
