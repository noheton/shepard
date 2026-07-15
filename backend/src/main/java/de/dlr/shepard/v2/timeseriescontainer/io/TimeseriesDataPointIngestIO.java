package de.dlr.shepard.v2.timeseriescontainer.io;

import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import jakarta.validation.constraints.NotNull;
import java.time.format.DateTimeParseException;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * APISIMP-TIMESERIES-DATAPOINT-TS — ingest-side data point with ISO 8601 timestamp.
 *
 * <p>Used by {@link CopyIngestRequestIO} for
 * {@code POST /v2/containers/{appId}/channels/{channelShepardId}/data/ingest}.
 * The timestamp is an ISO 8601 UTC string; the handler converts to nanoseconds
 * before passing to the COPY-protocol repository method.
 */
@Schema(
  name = "TimeseriesDataPointIngest",
  description = "A single timeseries data point for COPY-protocol ingest."
)
public record TimeseriesDataPointIngestIO(

  @NotNull
  @Schema(
    description = "Timestamp as ISO 8601 UTC string with nanosecond precision where available.",
    example = "2024-06-01T08:00:00.123456789Z",
    required = true
  )
  String timestamp,

  @NotNull
  @Schema(
    description = "Value: a string, integer, double, or boolean. Must match the channel's declared value type.",
    required = true
  )
  Object value

) {

  /**
   * Convert to a {@link TimeseriesDataPoint} (nanosecond epoch long), as required by
   * {@code TimeseriesDataPointRepository.insertManyDataPointsWithCopyCommandBatched}.
   *
   * @throws jakarta.ws.rs.BadRequestException if {@code timestamp} is not valid ISO 8601
   */
  public TimeseriesDataPoint toDataPoint() {
    try {
      return new TimeseriesDataPoint(TimeseriesDataPointV2IO.isoToNs(timestamp), value);
    } catch (DateTimeParseException e) {
      throw new jakarta.ws.rs.BadRequestException(
        "Invalid ISO 8601 timestamp '" + timestamp + "': " + e.getMessage());
    }
  }
}
