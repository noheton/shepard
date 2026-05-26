-- V1.18.0__add_ts_datapoints_value_check.sql
--
-- TS-AUDIT-2026-05-24-003: enforce exactly-one-non-null value column per row.
--
-- BACKGROUND
-- ----------
-- timeseries_data_points carries four value columns (double_value, int_value,
-- string_value, boolean_value) but has no constraint enforcing that exactly one
-- is non-null per row.  The DAO's getColumnName(valueType) switch always reads
-- the column declared by the parent timeseries.value_type; a misbehaving writer
-- (future Influx-line-protocol shim, OPC-UA bridge, or direct SQL insert) could
-- write to the wrong column and the DAO would silently return null.
--
-- SAFETY ON LIVE DATA
-- -------------------
-- All 81M+ live rows are Double; the three other columns are always null.
-- num_nonnulls(double_value, int_value, string_value, boolean_value) = 1
-- is satisfied by every existing row.  The constraint scan is O(n) over the
-- hypertable chunks but is non-blocking (AccessShareLock) on TimescaleDB PG16.
-- Estimated scan time on 81M rows: ~30–60 s on cold buffers; under 10 s warm.
--
-- IDEMPOTENT
-- ----------
-- The DO block checks pg_constraint before issuing ALTER TABLE so re-running
-- this migration after a partial failure is safe.
--
-- Operator verification (after migration):
--   SELECT conname FROM pg_constraint
--   WHERE conname = 'chk_one_value_column'
--     AND conrelid = 'timeseries_data_points'::regclass;
--   -- returns 1 row if constraint is present
--
-- Rollback (if needed):
--   ALTER TABLE timeseries_data_points DROP CONSTRAINT IF EXISTS chk_one_value_column;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'chk_one_value_column'
      AND conrelid = 'timeseries_data_points'::regclass
  ) THEN
    ALTER TABLE timeseries_data_points
      ADD CONSTRAINT chk_one_value_column
      CHECK (num_nonnulls(double_value, int_value, string_value, boolean_value) = 1);
    RAISE NOTICE 'chk_one_value_column constraint added to timeseries_data_points';
  ELSE
    RAISE NOTICE 'chk_one_value_column already exists on timeseries_data_points, skipping';
  END IF;
END $$;
