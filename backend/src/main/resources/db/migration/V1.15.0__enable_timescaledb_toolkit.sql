-- V1.15.0__enable_timescaledb_toolkit.sql  (TS-OPT3)
--
-- PURPOSE:
--   Enable the timescaledb_toolkit extension, which provides enhanced aggregate
--   functions (percentile_agg, stats_agg, time_weight, LTTB, etc.) used by the
--   TS-OPT3 CAgg query-routing layer in Java.
--
-- DESIGN NOTE — no duplicate CAgg:
--   A 1-hour continuous aggregate (timeseries_hourly) was already created in
--   V1.12.1.  The Java routing layer (TS-OPT3) routes wide-window overview queries
--   directly to that view.  No second hourly aggregate is needed.
--
-- TOOLKIT AVAILABILITY:
--   timescaledb_toolkit ships with the `timescaledb-ha` image family but NOT with
--   the plain `timescale/timescaledb` image used in this project.  The DO block
--   below attempts the CREATE EXTENSION and catches the "extension not available"
--   error gracefully so that startup is not blocked on environments that run the
--   plain image.  A NOTICE is raised in both cases so operators can confirm state.
--
-- IDEMPOTENT: safe to re-run; the DO block checks current state before acting.
--
-- Operator verification:
--   SELECT extname FROM pg_extension WHERE extname = 'timescaledb_toolkit';
--   -- returns 1 row if installed, 0 rows if unavailable

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM pg_extension WHERE extname = 'timescaledb_toolkit'
  ) THEN
    RAISE NOTICE 'timescaledb_toolkit already installed, skipping';
  ELSE
    BEGIN
      CREATE EXTENSION timescaledb_toolkit;
      RAISE NOTICE 'timescaledb_toolkit installed successfully';
    EXCEPTION WHEN OTHERS THEN
      RAISE NOTICE 'timescaledb_toolkit not available in this image (%), skipping; TS-OPT3 CAgg routing still works via timeseries_hourly', SQLERRM;
    END;
  END IF;
END $$;
