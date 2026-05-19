package de.dlr.shepard.v2.admin.io;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * TS_STATS1 / AD_STORE1 — aggregated storage overview across all backends.
 *
 * <p>Used by {@code GET /v2/admin/storage-overview} to give admins a
 * single-glance view of which database is growing and whether any backend
 * needs attention.
 */
@Schema(name = "StorageOverview")
public record StorageOverviewIO(

  @Schema(description = "TimescaleDB (timeseries payload storage).", required = true)
  TimescaleStats timescaledb,

  @Schema(description = "MongoDB (files, avatars, structured data, HDF5 metadata).", required = true)
  MongoStats mongodb

) {

  @Schema(name = "TimescaleStats")
  public record TimescaleStats(
    @Schema(description = "Total on-disk bytes for the timeseries_data_points hypertable (all chunks, compressed + uncompressed).", required = true)
    long hypertableSizeBytes,

    @Schema(description = "Number of distinct timeseries channels registered across all containers.", required = true)
    long channelCount,

    @Schema(description = "Number of distinct containers that have at least one channel.", required = true)
    long containerCount,

    @Schema(description = "Approximate uncompressed size bytes (channelCount × avg-bytes-per-point estimate is not used here; "
      + "this is the sum of before_compression_total_bytes across all compressed chunks).", required = true)
    long uncompressedChunkBytes,

    @Schema(description = "Compression ratio estimate (uncompressedChunkBytes / hypertableSizeBytes). "
      + "Null when no chunks have been compressed yet.", nullable = true)
    Double compressionRatio
  ) {}

  @Schema(name = "MongoStats")
  public record MongoStats(
    @Schema(description = "Total bytes on disk (includes data, indexes, and free space in pre-allocated files).", required = true)
    long storageSizeBytes,

    @Schema(description = "Bytes occupied by all documents in the database (excludes indexes and free space).", required = true)
    long dataSizeBytes,

    @Schema(description = "Bytes occupied by all indexes.", required = true)
    long indexSizeBytes,

    @Schema(description = "Number of collections in the database.", required = true)
    int collectionCount
  ) {}
}
