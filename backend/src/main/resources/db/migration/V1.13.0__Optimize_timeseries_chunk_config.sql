-- V1.13.0__Optimize_timeseries_chunk_config.sql
--
-- Configure the timeseries_data_points hypertable for high-rate MFFD sensor ingest.
--
-- WHY: The default TimescaleDB chunk interval of 7 days is wrong for sub-second
-- sensor data. AFP tapelaying runs at 180 Hz for ~6 seconds per track; a single
-- track produces ~1 080 rows and sits in one 7-day chunk alongside thousands of
-- other tracks from different sessions — terrible temporal locality and a bloated
-- active chunk. 1-hour chunks give ~6 000 track runs per chunk and match the
-- typical "show me one test run" query pattern.
--
-- Space partitioning on timeseries_id distributes inserts across 4 independent
-- partitions so that 4 importer workers can write in parallel without row-level
-- lock contention on the same chunk.
--
-- IDEMPOTENCY: Both blocks check current state before making any change and emit
-- a NOTICE reporting what they did (or skipped). Safe to re-run on existing
-- instances; Flyway's checksum validation ensures it is only executed once in
-- normal operation.
--
-- OPERATOR NOTES:
--   * This migration does NOT retroactively repack existing chunks. Existing data
--     remains in its current chunk layout; only new data is written into 1-hour
--     chunks going forward.
--   * Space partitions are additive. Existing data and indexes are unaffected.
--   * If you need to repack existing chunks for full locality benefit, run:
--       SELECT recompress_chunk(c) FROM show_chunks('timeseries_data_points') c;
--     (expensive — run outside peak hours on large datasets)
--   * Rollback: space partitions cannot be removed via SQL once added. Rolling
--     back the chunk interval is possible:
--       SELECT set_chunk_time_interval('timeseries_data_points', INTERVAL '7 days');
--     but existing 1-hour chunks remain until dropped.

-- ---------------------------------------------------------------------------
-- 1) Set chunk time interval to 1 hour.
--
-- Default is 7 days; 1 hour fits ~6 000 AFP track runs per chunk and matches
-- the "one session" query pattern. Only applies to chunks created after this
-- migration runs.
-- ---------------------------------------------------------------------------
DO $$
DECLARE
  current_interval INTERVAL;
BEGIN
  SELECT chunk_time_interval
    INTO current_interval
    FROM timescaledb_information.hypertables
    WHERE hypertable_name = 'timeseries_data_points';

  IF current_interval IS NULL OR current_interval <> INTERVAL '1 hour' THEN
    PERFORM set_chunk_time_interval('timeseries_data_points', INTERVAL '1 hour');
    RAISE NOTICE 'set chunk_time_interval to 1 hour (was %)', current_interval;
  ELSE
    RAISE NOTICE 'chunk_time_interval already 1 hour, skipping';
  END IF;
END $$;

-- ---------------------------------------------------------------------------
-- 2) Add 4 space partitions on timeseries_id.
--
-- 4 partitions allow 4 parallel importer workers to write to distinct physical
-- partitions without row-level lock contention. The partition count matches the
-- worker pool size configured in the MFFD ingest pipeline.
-- ---------------------------------------------------------------------------
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
      FROM timescaledb_information.dimensions
      WHERE hypertable_name = 'timeseries_data_points'
        AND dimension_type = 'Space'
  ) THEN
    PERFORM add_space_partitions('timeseries_data_points', 'timeseries_id', 4);
    RAISE NOTICE 'added 4 space partitions on timeseries_id';
  ELSE
    RAISE NOTICE 'space partitions already present, skipping';
  END IF;
END $$;
