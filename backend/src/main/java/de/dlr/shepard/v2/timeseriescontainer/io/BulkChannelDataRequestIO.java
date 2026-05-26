package de.dlr.shepard.v2.timeseriescontainer.io;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * TS-OPT2 — request body for the multi-channel bulk data endpoint.
 *
 * <p>Route: {@code POST /v2/timeseries-containers/{containerId}/channels/data/bulk}
 *
 * <p>Returns raw data points (no downsampling). Callers that need LTTB
 * should use the single-channel endpoint with {@code ?downsample=lttb}.
 *
 * <p>Unknown shepardIds are silently skipped so a caller can pass a
 * stale channel list without getting a 404. Max 200 channels per call.
 */
@Schema(
  name = "BulkChannelDataRequest",
  description = "Request body for fetching raw data across multiple channels in one call."
)
public record BulkChannelDataRequestIO(

  @NotEmpty
  @Size(max = 200)
  @Schema(
    description = "Channel shepardIds to fetch. Unknown IDs are silently skipped. Max 200 per call.",
    required = true
  )
  List<@NotNull UUID> shepardIds,

  @NotNull
  @PositiveOrZero
  @Schema(description = "Window start, nanoseconds since epoch.", required = true)
  Long start,

  @NotNull
  @PositiveOrZero
  @Schema(description = "Window end, nanoseconds since epoch.", required = true)
  Long end
) {}
