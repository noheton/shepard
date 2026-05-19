package de.dlr.shepard.v2.timeseriescontainer.io;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * TS_STATS1 — storage and ingestion stats for a TimeseriesContainer.
 *
 * <p>{@code estimatedSizeBytes} is an uncompressed estimate: {@code pointCount × 28} bytes
 * (8 timestamp + 8 double_value + 4 timeseries_id + 8 tuple overhead). Compressed chunks
 * on disk are typically 5-10× smaller; the uncompressed figure is reported because it is
 * stable and derivable without scanning TimescaleDB chunk metadata.
 *
 * <p>{@code ingestRateBytesPerSec} is based on data points inserted in the last 10 seconds.
 * When no recent data exists it is zero. Divide by 1 048 576 for MB/s.
 */
@Schema(name = "TimeseriesContainerStats")
public record TimeseriesContainerStatsIO(
  @Schema(description = "Total data points across all channels in this container.", required = true)
  long pointCount,

  @Schema(description = "Number of distinct channels (measurement × device × location × field tuples).", required = true)
  long channelCount,

  @Schema(description = "Uncompressed size estimate in bytes (pointCount × 28).", required = true)
  long estimatedSizeBytes,

  @Schema(description = "Data points inserted in the last 10 seconds.", required = true)
  long recentPointsLast10s,

  @Schema(description = "Estimated ingest rate in bytes per second (recentPointsLast10s × 28 / 10).", required = true)
  long ingestRateBytesPerSec
) {}
