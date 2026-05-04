-- Performance optimisation for the timeseries_data_points hypertable.
--
-- Three fixes, all targeting query/ingest paths that became sluggish after the
-- aggressive compression setup introduced in V1.4.0.
--
-- ---------------------------------------------------------------------------
-- 1) Restore the composite (timeseries_id, time DESC) index.
--
-- V1.3.0 added an index on timeseries_id; V1.4.0 dropped it with the comment
-- "useless with compression". That is only true for compressed chunks, where
-- TimescaleDB indexes the segmentby column automatically. For UNCOMPRESSED
-- chunks (the active chunk and, after fix #2 below, the entire 7-day hot
-- window) TimescaleDB only auto-creates an index on the time dimension, so
-- WHERE timeseries_id = ? AND time BETWEEN ? AND ? has nothing to filter
-- timeseries_id on and degenerates into a chunk scan.
--
-- Composite (timeseries_id, time DESC) is the canonical layout for
-- "give me one series in a time range" queries and is also what TimescaleDB
-- recommends for hypertables that share chunks across many series.
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS timeseries_data_points_id_time_idx
    ON timeseries_data_points (timeseries_id, time DESC);

-- ---------------------------------------------------------------------------
-- 2) Relax the compression policy from 1 day to 7 days.
--
-- Chunk interval is 1 day. The previous policy compressed chunks as soon as
-- they aged out, leaving only the active chunk uncompressed. Two problems:
--   * INSERT ... ON CONFLICT DO UPDATE against a compressed chunk forces a
--     segment decompression. Any backfill or out-of-order point pays this.
--   * Queries that touch data older than ~1 day pay decompression overhead.
-- A 7-day delay keeps a hot write/read window matching the TimescaleDB best
-- practice of "delay >= ~7 chunks". Older data still gets compressed.
-- ---------------------------------------------------------------------------
SELECT remove_compression_policy('timeseries_data_points', if_exists => true);
SELECT add_compression_policy('timeseries_data_points', BIGINT '604800000000000'); -- 7 days in ns

-- ---------------------------------------------------------------------------
-- 3) Enable chunk skipping on timeseries_id (TimescaleDB 2.16+).
--
-- Maintains per-chunk min/max statistics for timeseries_id so the planner can
-- skip chunks that don't contain the requested series when a query spans many
-- chunks (e.g. "this series over the last year"). Without this, every chunk
-- in the time range is opened.
-- ---------------------------------------------------------------------------
SELECT enable_chunk_skipping('timeseries_data_points', 'timeseries_id');
