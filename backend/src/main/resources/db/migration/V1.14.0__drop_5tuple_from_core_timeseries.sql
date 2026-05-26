-- TS-CORE-SCHEMA-01: Move 5-tuple channel identity out of `timeseries` into `channel_metadata`.
--
-- After this migration, `timeseries` stores only: id, container_id, value_type, shepard_id.
-- The 5-tuple (measurement, device, location, symbolic_name, field) lives in `channel_metadata`,
-- joined 1:1 via channel_metadata.timeseries_id = timeseries.id.
--
-- WHY:
--   The 5-tuple was the legacy channel discriminator before TS-ID introduced shepard_id as the
--   canonical single-field identity.  Keeping it in the hot timeseries row bleeds measurement
--   semantics into the data-points storage layer and widens each row unnecessarily.  Moving it
--   to a side table:
--     - Shrinks the hot timeseries row (fewer bytes scanned per data-point lookup)
--     - Gives the 5-tuple its own UNIQUE index without bloating the TimescaleDB parent
--     - Enables future migration to the SemanticAnnotation/AnnotatableTimeseries Neo4j subsystem
--       (TS-SEMANTIC-01 in aidocs/16) without touching timeseries_data_points
--
-- FUTURE:
--   channel_metadata is a stepping stone.  TS-SEMANTIC-01 will dual-write these five fields as
--   SemanticAnnotation nodes linked via AnnotatableTimeseries in Neo4j, then drop this table.
--
-- IDEMPOTENT: all DDL uses IF (NOT) EXISTS guards; data migration wrapped in a DO block that
-- checks column existence first.  Safe to re-run from psql.
-- FAIL-FAST: integrity check in Step 3 aborts startup if row counts diverge.
--
-- Operator runbook:
--   $ psql -U shepard -d shepard -f V1.14.0__drop_5tuple_from_core_timeseries.sql
--   $ psql -U shepard -d shepard -c "SELECT count(*) FROM channel_metadata;"
--   → expect value = SELECT count(*) FROM timeseries
--
-- Rollback: V1.14.0_R__restore_5tuple_to_timeseries.sql (sibling file)

-- ---------------------------------------------------------------------------
-- Step 1: Create channel_metadata side table
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS channel_metadata (
    id            BIGSERIAL PRIMARY KEY,
    timeseries_id INT  NOT NULL UNIQUE REFERENCES timeseries(id) ON DELETE CASCADE,
    container_id  BIGINT NOT NULL,
    measurement   TEXT   NOT NULL,
    field         TEXT   NOT NULL,
    device        TEXT   NOT NULL,
    location      TEXT   NOT NULL,
    symbolic_name TEXT   NOT NULL,
    UNIQUE (container_id, measurement, field, symbolic_name, device, location)
);

-- ---------------------------------------------------------------------------
-- Step 2: Migrate existing 5-tuple data (conditional — skipped on re-run
--         when timeseries.measurement has already been dropped).
-- ---------------------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'timeseries' AND column_name = 'measurement'
    ) THEN
        INSERT INTO channel_metadata
            (timeseries_id, container_id, measurement, field, device, location, symbolic_name)
        SELECT id, container_id, measurement, field, device, location, symbolic_name
        FROM   timeseries
        ON CONFLICT DO NOTHING;
        RAISE NOTICE 'Migrated 5-tuple data to channel_metadata';
    ELSE
        RAISE NOTICE '5-tuple columns already absent from timeseries, skipping data migration';
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- Step 3: Integrity check (fail-fast if counts diverge)
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    ts_count BIGINT;
    cm_count BIGINT;
BEGIN
    SELECT count(*) INTO ts_count FROM timeseries;
    SELECT count(*) INTO cm_count FROM channel_metadata;
    IF ts_count <> cm_count THEN
        RAISE EXCEPTION
            'TS-CORE-SCHEMA-01 integrity check failed: timeseries=% rows but channel_metadata=% rows',
            ts_count, cm_count;
    END IF;
    RAISE NOTICE 'Integrity OK: % channel_metadata rows == % timeseries rows', cm_count, ts_count;
END $$;

-- ---------------------------------------------------------------------------
-- Step 4: Drop the old 5-tuple unique constraint from timeseries
-- ---------------------------------------------------------------------------
ALTER TABLE timeseries DROP CONSTRAINT IF EXISTS timeseries_unique_b4a836fabc25;

-- ---------------------------------------------------------------------------
-- Step 5: Drop the 5-tuple columns from timeseries
-- ---------------------------------------------------------------------------
ALTER TABLE timeseries
    DROP COLUMN IF EXISTS measurement,
    DROP COLUMN IF EXISTS field,
    DROP COLUMN IF EXISTS device,
    DROP COLUMN IF EXISTS location,
    DROP COLUMN IF EXISTS symbolic_name;
