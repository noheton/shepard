package de.dlr.shepard.v2.timeseriescontainer.io;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * TS-OPT2 — request body for the multi-channel bulk data endpoint.
 *
 * <p>Route: {@code POST /v2/containers/{appId}/channels/data/bulk}
 *
 * <p>Returns raw data points (no downsampling). Callers that need LTTB
 * should use the single-channel endpoint with {@code ?downsample=lttb}.
 *
 * <p>Unknown channelAppIds are silently skipped so a caller can pass a
 * stale channel list without getting a 404. Max 200 channels per call.
 *
 * <p>APISIMP-BULK-CHANNEL-REQ-NANOS-TO-ISO — {@code start} and {@code end}
 * are ISO 8601 UTC strings with optional nanosecond precision, e.g.
 * {@code "2024-06-01T08:00:00Z"} or {@code "2024-06-01T08:00:00.123456789Z"}.
 * Replaces the previous nanosecond-Long shape to align with
 * {@code GET /v2/containers/{appId}/channels/{channelId}/data} (fire-607).
 */
@Schema(
  name = "BulkChannelDataRequest",
  description = "Request body for fetching raw data across multiple channels in one call."
)
public record BulkChannelDataRequestIO(

  @NotEmpty
  @Size(max = 200)
  @Schema(
    description = "Channel channelAppIds to fetch. Unknown IDs are silently skipped. Max 200 per call.",
    required = true,
    example = "[\"01930a2b-fe4c-7e3c-9f1d-8a5b2c3d4e5f\",\"01930a2b-fe4c-7e3c-9f1d-8a5b2c3d4e60\"]"
  )
  List<@NotNull UUID> channelAppIds,

  @NotNull
  @Schema(
    description = "Window start as ISO 8601 UTC, nanosecond precision supported. E.g. '2024-06-01T08:00:00Z'.",
    required = true,
    example = "2024-06-01T08:00:00Z"
  )
  String start,

  @NotNull
  @Schema(
    description = "Window end as ISO 8601 UTC, nanosecond precision supported. Must be after start.",
    required = true,
    example = "2024-06-01T09:00:00Z"
  )
  String end
) {}
