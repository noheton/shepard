package de.dlr.shepard.v2.timeseriescontainer.io;

import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import java.time.Instant;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * APISIMP-TIMESERIES-DATAPOINT-TS — v2 wire shape for a single timeseries data point.
 *
 * <p>The internal {@link TimeseriesDataPoint} uses a {@code long} nanosecond epoch timestamp.
 * This v2 IO record emits {@code timestamp} as an ISO 8601 UTC string so callers
 * parse it with {@code Instant.parse()} rather than doing unit arithmetic. The v1
 * {@link TimeseriesDataPoint} wire shape (numeric long) is preserved unchanged on
 * {@code /shepard/api/} endpoints.
 */
@Schema(
  name = "TimeseriesDataPointV2",
  description = "A single timeseries data point with an ISO 8601 UTC timestamp."
)
public record TimeseriesDataPointV2IO(

  @Schema(
    description = "Timestamp as ISO 8601 UTC string with nanosecond precision where available.",
    example = "2024-06-01T08:00:00.123456789Z"
  )
  String timestamp,

  @Schema(description = "Value: a string, integer, double, or boolean.")
  Object value

) {

  static String nsToIso(long ns) {
    return Instant.ofEpochSecond(ns / 1_000_000_000L, ns % 1_000_000_000L).toString();
  }

  public static TimeseriesDataPointV2IO from(TimeseriesDataPoint dp) {
    return new TimeseriesDataPointV2IO(nsToIso(dp.getTimestamp()), dp.getValue());
  }

  /** Parse an ISO 8601 UTC string back to nanoseconds since the Unix epoch. */
  public static long isoToNs(String iso) {
    Instant i = Instant.parse(iso.trim());
    return i.getEpochSecond() * 1_000_000_000L + i.getNano();
  }
}
