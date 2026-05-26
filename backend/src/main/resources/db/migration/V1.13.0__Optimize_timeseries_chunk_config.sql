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
-- The timeseries_data_points hypertable uses an integer time column (bigint
-- nanoseconds since epoch), so set_chunk_time_interval expects a bigint, not
-- a Postgres INTERVAL. 1 hour = 3_600_000_000_000 ns.
-- Current default is 24 hours (86_400_000_000_000 ns). Only applies to
-- chunks created after this migration runs.
-- ---------------------------------------------------------------------------
DO $$
DECLARE
  current_interval BIGINT;
  target_interval  BIGINT := 3600000000000; -- 1 hour in nanoseconds
BEGIN
  SELECT integer_interval
    INTO current_interval
    FROM timescaledb_information.dimensions
    WHERE hypertable_name = 'timeseries_data_points'
      AND dimension_type = 'Time';

  IF current_interval IS NULL OR current_interval <> target_interval THEN
    PERFORM set_chunk_time_interval('timeseries_data_points', target_interval);
    RAISE NOTICE 'set chunk_time_interval to 1h (3600000000000 ns); was %', current_interval;
  ELSE
    RAISE NOTICE 'chunk_time_interval already 1h, skipping';
  END IF;
END $$;

-- ---------------------------------------------------------------------------
-- 2) Add space partitions dimension on timeseries_id (4 partitions).
--
-- 4 partitions allow 4 parallel importer workers to write to distinct physical
-- partitions without row-level lock contention. The partition count matches the
-- worker pool size configured in the MFFD ingest pipeline.
--
-- Uses add_dimension(..., if_not_exists => true) so the block is safe to re-run
-- on an instance that already has the dimension; it emits a NOTICE either way.
-- Note: set_number_partitions() only modifies an *existing* space dimension;
-- add_dimension() is the correct call to add a new one.
-- ---------------------------------------------------------------------------
DO $$
DECLARE
  already_present BOOLEAN;
BEGIN
  SELECT EXISTS (
    SELECT 1
      FROM timescaledb_information.dimensions
      WHERE hypertable_name = 'timeseries_data_points'
        AND dimension_type = 'Space'
  ) INTO already_present;

  PERFORM add_dimension('timeseries_data_points', 'timeseries_id',
                        number_partitions => 4, if_not_exists => true);

  IF already_present THEN
    RAISE NOTICE 'space dimension on timeseries_id already present, skipping';
  ELSE
    RAISE NOTICE 'added 4 space partitions on timeseries_id';
  END IF;
END $$;
