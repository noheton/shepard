package de.dlr.shepard.v2.timeseriescontainer.io;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * TS-OPT3-COPY — request body for the high-throughput COPY-protocol ingest endpoint.
 *
 * <p>Route: {@code POST /v2/containers/{appId}/channels/{appId}/data/ingest}
 *
 * <p>COPY is 3–5× faster than the VALUES INSERT path for bulk loads. No ON CONFLICT
 * handling is applied; timestamps must be unique within the batch and must not
 * collide with rows that already exist. Use the regular endpoint for upsert semantics.
 *
 * <p>APISIMP-TIMESERIES-DATAPOINT-TS — timestamps are ISO 8601 UTC strings; the handler
 * converts to nanosecond longs before passing to the COPY-protocol repository method.
 */
@Schema(
  name = "CopyIngestRequest",
  description = "Request body for high-throughput COPY-protocol ingest into a single channel. " +
    "Timestamps must be globally unique for the channel — no ON CONFLICT handling. " +
    "Timestamps are ISO 8601 UTC strings (e.g. '2024-06-01T08:00:00.123456789Z')."
)
public record CopyIngestRequestIO(

  @NotEmpty
  @Schema(
    description = "Data points to ingest. Each timestamp (ISO 8601 UTC string) must be unique " +
      "within the batch and must not already exist for this channel. The value type must match " +
      "the channel's declared value type (Double, Integer, String, or Boolean).",
    required = true
  )
  List<@NotNull TimeseriesDataPointIngestIO> dataPoints

) {}
