-- Rollback for V1.14.0: restore 5-tuple columns to timeseries from channel_metadata.
-- Run this BEFORE the application starts if you need to revert TS-CORE-SCHEMA-01.

-- Step 1: Re-add columns to timeseries (nullable for the backfill step)
ALTER TABLE timeseries
    ADD COLUMN IF NOT EXISTS measurement   TEXT,
    ADD COLUMN IF NOT EXISTS field         TEXT,
    ADD COLUMN IF NOT EXISTS device        TEXT,
    ADD COLUMN IF NOT EXISTS location      TEXT,
    ADD COLUMN IF NOT EXISTS symbolic_name TEXT;

-- Step 2: Backfill from channel_metadata
UPDATE timeseries t
SET measurement   = cm.measurement,
    field         = cm.field,
    device        = cm.device,
    location      = cm.location,
    symbolic_name = cm.symbolic_name
FROM channel_metadata cm
WHERE cm.timeseries_id = t.id;

-- Step 3: Tighten to NOT NULL
ALTER TABLE timeseries
    ALTER COLUMN measurement   SET NOT NULL,
    ALTER COLUMN field         SET NOT NULL,
    ALTER COLUMN device        SET NOT NULL,
    ALTER COLUMN location      SET NOT NULL,
    ALTER COLUMN symbolic_name SET NOT NULL;

-- Step 4: Restore the unique constraint
ALTER TABLE timeseries
    ADD CONSTRAINT timeseries_unique_b4a836fabc25
    UNIQUE (container_id, measurement, field, symbolic_name, device, location);

-- Step 5: Drop the side table
DROP TABLE IF EXISTS channel_metadata;
