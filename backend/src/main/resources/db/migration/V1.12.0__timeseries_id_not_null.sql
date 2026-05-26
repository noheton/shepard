-- timeseries_id is a required FK — every row is owned by a channel.
-- The original DDL omitted NOT NULL; no null rows have ever been inserted
-- (confirmed: SELECT COUNT(*) WHERE timeseries_id IS NULL = 0).
-- Adding the constraint makes the intent explicit and lets the planner
-- treat it as non-nullable, which benefits partial-index eligibility.
--
-- Safe to apply on a live hypertable: PostgreSQL validates the constraint
-- without a full table rewrite on already-null-free data (NOT VALID is not
-- needed here because the scan is fast compared to a rewrite, and a
-- compressed hypertable is already column-organised).
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name   = 'timeseries_data_points'
      AND column_name  = 'timeseries_id'
      AND is_nullable  = 'YES'
  ) THEN
    ALTER TABLE timeseries_data_points ALTER COLUMN timeseries_id SET NOT NULL;
  END IF;
END $$;
