package de.dlr.shepard.v2.timeseriescontainer.io;

import de.dlr.shepard.data.timeseries.io.TimeseriesWithDataPoints;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Response envelope for {@code POST /v2/containers/{appId}/channels/data/bulk}.
 *
 * <p>All resolved channels are returned in a single response — there is no
 * pagination. The request body's {@code channelAppIds} list bounds the result
 * set; unknown IDs are silently skipped.
 */
@Schema(
  name = "BulkChannelDataResponse",
  description =
    "Response envelope for the bulk-channel-data endpoint. All resolved channels " +
    "are returned in one response (no pagination). Unknown channelAppIds are silently skipped."
)
public record BulkChannelDataResponseIO(

  @Schema(description = "Resolved channel entries, one per recognised channelAppId.")
  List<TimeseriesWithDataPoints> items
) {}
