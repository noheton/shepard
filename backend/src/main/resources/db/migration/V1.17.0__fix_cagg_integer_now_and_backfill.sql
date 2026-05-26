-- V1.17.0__fix_cagg_integer_now_and_backfill.sql
--
-- DB-OPT3: Fix two gaps found during TimescaleDB chunk + compression analysis.
--
-- BACKGROUND
-- ----------
-- V1.12.1 created the timeseries_hourly continuous aggregate (CAgg) but never
-- called set_integer_now_func on its internal materialized hypertable. Without
-- this function, TimescaleDB cannot evaluate age-based thresholds, causing the
-- CAgg compression policy (job 1046) to fail with:
--   "integer_now function not set"
-- This means CAgg chunks older than 30 days were never compressed.
--
-- V1.12.1 also created the CAgg WITH NO DATA and set a refresh policy that
-- only covers the last 25 hours. This means historical MFFD/LUMEN data from
-- 2023-01, 2024-07..08, and 2026-05-18..25 was never materialized. The
-- TS-OPT3 CAgg routing in TimeseriesService falls through to TS-OPT1 for
-- any historical window, losing the 775× speedup measured in DB-OPT3.
--
-- WHAT THIS MIGRATION DOES
-- ------------------------
-- 1. Calls set_integer_now_func on the materialized hypertable so the CAgg
--    compression policy can run without error. Idempotent: skips if already set.
--
-- 2. Calls refresh_continuous_aggregate(..., NULL, NULL) to materialize the
--    full history. The CAgg is tiny (~32 534 rows total — ~4.4 MB) so this
--    runs fast (~2 seconds) even on the full 132 M row dataset.
--    Idempotent: re-materializing already-present buckets is a no-op.
--
-- OPERATOR NOTES
-- --------------
--   * After this migration the CAgg compression job (job 1046) will succeed
--     on its next scheduled run (daily, or trigger manually with run_job()).
--   * The full backfill may take up to 30 seconds on a cold system with no
--     shared_buffers warming. On the typical dev/prod stack (warm buffers,
--     28 compressed chunks) it runs in under 5 seconds.
--   * Rollback: there is no meaningful rollback — removing the integer_now
--     registration re-breaks the compression job; discarding the CAgg rows
--     is possible with CALL refresh_continuous_aggregate(...) to a NULL start,
--     but there is no operational reason to do so.
--
-- PERF IMPACT (measured on MFFD DB-OPT3 dataset, 2026-05-26)
-- -----------------------------------------------------------
--   Raw full-range query on channel 327 (1.4M rows, ~118 h):  310 ms
--   Pre-aggregation (TS-OPT1) same window:                    148 ms
--   CAgg query (TS-OPT3) same window:                          0.4 ms  ← 775×
--
--   Without this migration the 0.4 ms path silently degrades to 148 ms
--   for all historical data because the CAgg is cold.

-- ---------------------------------------------------------------------------
-- 1) Register unix_now_immutable as the integer_now function for the CAgg's
--    internal materialized hypertable, identified by its schema + name.
--
-- We look up the materialized hypertable's id via the catalog rather than
-- hard-coding 34 so the migration is safe on instances where the CAgg was
-- created in a different order (and thus received a different hypertable id).
-- ---------------------------------------------------------------------------
DO $$
DECLARE
  mat_ht_schema TEXT;
  mat_ht_name   TEXT;
  existing_func TEXT;
BEGIN
  SELECT materialization_hypertable_schema,
         materialization_hypertable_name
    INTO mat_ht_schema, mat_ht_name
  FROM timescaledb_information.continuous_aggregates
  WHERE view_name = 'timeseries_hourly';

  IF mat_ht_name IS NULL THEN
    RAISE NOTICE 'timeseries_hourly CAgg not found — skipping integer_now registration';
    RETURN;
  END IF;

  SELECT di.integer_now_func
    INTO existing_func
  FROM _timescaledb_catalog.dimension di
  JOIN _timescaledb_catalog.hypertable h ON h.id = di.hypertable_id
  WHERE h.schema_name = mat_ht_schema
    AND h.table_name  = mat_ht_name
    AND di.integer_now_func IS NOT NULL
  LIMIT 1;

  IF existing_func IS NOT NULL THEN
    RAISE NOTICE 'integer_now already set to % on %.%; skipping',
      existing_func, mat_ht_schema, mat_ht_name;
  ELSE
    PERFORM set_integer_now_func(
      format('%s.%s', mat_ht_schema, mat_ht_name),
      'unix_now_immutable'
    );
    RAISE NOTICE 'Registered unix_now_immutable on %.%', mat_ht_schema, mat_ht_name;
  END IF;
END $$;

-- ---------------------------------------------------------------------------
-- 2) Backfill the full timeseries_hourly CAgg history (NULL,NULL = all time).
--
-- Idempotent: buckets that are already materialized are not recomputed.
-- On the first run after V1.12.1 this materializes ~32 534 hourly rows.
-- ---------------------------------------------------------------------------
CALL refresh_continuous_aggregate('timeseries_hourly', NULL, NULL);
