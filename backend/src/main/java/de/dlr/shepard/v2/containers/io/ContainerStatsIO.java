package de.dlr.shepard.v2.containers.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * APISIMP-CONT-NS-COLLAPSE-1 — generic storage statistics returned by
 * {@code GET /v2/containers/{appId}/stats} for all container kinds.
 *
 * <p>Fields are kind-specific; null fields are omitted from the JSON response:
 * <ul>
 *   <li>{@code timeseries} — {@code pointCount}, {@code channelCount},
 *       {@code estimatedSizeBytes}, {@code recentPointsLast10s}, {@code ingestRateBytesPerSec}</li>
 *   <li>{@code file} — {@code fileCount}</li>
 *   <li>{@code structured-data} — {@code entryCount}</li>
 * </ul>
 *
 * <p>{@code estimatedSizeBytes} is an uncompressed estimate: {@code pointCount × 28} bytes
 * (8 timestamp + 8 double_value + 4 timeseries_id + 8 tuple overhead). Compressed chunks on
 * disk are typically 5–10× smaller; the uncompressed figure is reported because it is stable
 * and derivable without scanning TimescaleDB chunk metadata.
 *
 * <p>{@code ingestRateBytesPerSec} is based on data points inserted in the last 10 seconds.
 * When no recent data exists it is zero. Divide by 1 048 576 for MB/s.
 */
@Schema(name = "ContainerStats")
@JsonInclude(Include.NON_NULL)
public record ContainerStatsIO(

  @Schema(description = "Total data points across all channels in this container. Present for timeseries containers only.")
  Long pointCount,

  @Schema(description = "Number of distinct channels. Present for timeseries containers only.")
  Long channelCount,

  @Schema(description = "Uncompressed size estimate in bytes (pointCount × 28). Present for timeseries containers only.")
  Long estimatedSizeBytes,

  @Schema(description = "Data points inserted in the last 10 seconds. Present for timeseries containers only.")
  Long recentPointsLast10s,

  @Schema(description = "Estimated ingest rate in bytes per second (recentPointsLast10s × 28 / 10). Present for timeseries containers only.")
  Long ingestRateBytesPerSec,

  @Schema(description = "Number of files stored in this container. Present for file containers only.")
  Long fileCount,

  @Schema(description = "Number of structured-data entries stored in this container. Present for structured-data containers only.")
  Long entryCount

) {}
