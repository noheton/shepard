package de.dlr.shepard.v2.timeseriescontainer.io;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * TS-OPT2 (bulk-trace variant) — request body for
 * {@code POST /v2/timeseries-containers/{id}/channels/bulk}.
 *
 * <p>Reduces N parallel single-channel requests (as the Trace3D renderer
 * previously issued) to a single call. Each channel entry carries its own
 * role label which is echoed in the response.
 *
 * <p>When {@code downsample=lttb}, LTTB is applied per channel using the
 * TS-OPT1 pre-aggregation path ({@code TimeseriesService#getDataPointsLttbOptimised}).
 */
@Schema(
  name = "BulkTraceRequest",
  description = "Request body for multi-channel bulk fetch with optional LTTB downsampling."
)
public record BulkTraceRequestIO(

  @NotNull
  @PositiveOrZero
  @Schema(description = "Window start, nanoseconds since epoch.", required = true)
  Long start,

  @NotNull
  @PositiveOrZero
  @Schema(description = "Window end, nanoseconds since epoch.", required = true)
  Long end,

  @Schema(description = "Downsample algorithm. Pass \"lttb\" to enable per-channel LTTB reduction.")
  String downsample,

  @PositiveOrZero
  @Schema(description = "Target point count per channel when downsample=lttb. Capped at 5000. Defaults to 2000.")
  Integer maxPoints,

  @NotEmpty
  @Size(max = 20)
  @Schema(
    description = "Channels to fetch. Each entry has a role label + 5-tuple selector. Max 20 per call.",
    required = true
  )
  List<@NotNull @Valid BulkTraceChannelIO> channels
) {}
