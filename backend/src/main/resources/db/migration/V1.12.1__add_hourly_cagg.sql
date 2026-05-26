-- Continuous aggregate: one row per (channel, hour) with avg/min/max/count.
--
-- WHY: The primary query pattern for overview charts is "show me channel X
-- over the last 6 months at hourly resolution". Without a CAgg that query
-- must scan up to ~10M raw rows per channel; with the CAgg it reads ~4380
-- pre-aggregated rows. The CAgg is maintained incrementally by TimescaleDB
-- so staleness is bounded by the refresh interval (1 hour).
--
-- WITH NO DATA defers the initial materialization so startup is instant.
-- The refresh policy below fills data lazily going forward; existing data
-- older than 7 days is left un-materialized until the job runs its first
-- pass. Force a full backfill on demand with:
--   CALL refresh_continuous_aggregate('timeseries_hourly', NULL, NULL);
-- (expensive — ~132 M rows — run outside peak hours)
--
-- The time bucket is expressed in nanoseconds to match the timeseries_data_points
-- `time` column (BIGINT epoch-ns). 3_600_000_000_000 = 1 hour in nanoseconds.

CREATE MATERIALIZED VIEW IF NOT EXISTS timeseries_hourly
WITH (timescaledb.continuous) AS
SELECT
    timeseries_id,
    time_bucket(3600000000000::BIGINT, time) AS hour_bucket,
    avg(double_value)::double precision        AS avg_double,
    min(double_value)::double precision        AS min_double,
    max(double_value)::double precision        AS max_double,
    avg(int_value::double precision)           AS avg_int,
    min(int_value)                             AS min_int,
    max(int_value)                             AS max_int,
    count(*)::integer                          AS sample_count
FROM timeseries_data_points
GROUP BY timeseries_id, time_bucket(3600000000000::BIGINT, time)
WITH NO DATA;

-- Refresh policy: maintain last 25 hours every hour.
-- 25h window (not 24h) ensures the hot/compressed boundary is always covered.
-- Idempotent: skip if a policy already exists for this view.
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM timescaledb_information.jobs
    WHERE proc_name = 'policy_refresh_continuous_aggregate'
      AND hypertable_name = 'timeseries_hourly'
  ) THEN
    PERFORM add_continuous_aggregate_policy('timeseries_hourly',
        start_offset => BIGINT '90000000000000',  -- 25 hours in nanoseconds
        end_offset   => BIGINT '3600000000000',   -- 1 hour lag (let raw data settle)
        schedule_interval => INTERVAL '1 hour'
    );
  END IF;
END $$;

-- Enable compression on the CAgg for buckets older than 30 days.
-- Reduces WAL overhead on refresh and speeds up multi-month scans.
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM timescaledb_information.continuous_aggregates
    WHERE view_name = 'timeseries_hourly'
      AND compression_enabled = true
  ) THEN
    ALTER MATERIALIZED VIEW timeseries_hourly SET (timescaledb.compress = true);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM timescaledb_information.jobs
    WHERE proc_name = 'policy_compression'
      AND hypertable_name = 'timeseries_hourly'
  ) THEN
    PERFORM add_compression_policy('timeseries_hourly',
        compress_after => BIGINT '2592000000000000'  -- 30 days in nanoseconds
    );
  END IF;
END $$;
