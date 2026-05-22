-- TS-ID PR-1: substrate for the 5-tuple → shepardId migration.
--
-- Adds a stable, single-field channel identity (shepard_id UUID) to every
-- row of the existing `timeseries` metadata table. Resolution from the
-- shepardId UUID to the 5-tuple (measurement, device, location,
-- symbolicName, field) happens via `TsChannelResolver` (a JDBC service).
--
-- Supersedes aidocs/platform/87 §3 TS-IDa which assumed a `:Timeseries`
-- Neo4j node — that node never existed; the channel is a Postgres row.
--
-- Idempotent: `ADD COLUMN IF NOT EXISTS` + `CREATE UNIQUE INDEX IF NOT
-- EXISTS`. Safe to re-run from psql.
-- Fail-fast: any uniqueness collision aborts startup (should never
-- happen — gen_random_uuid() collisions are astronomically unlikely).
--
-- Operator runbook:
--   $ psql -U shepard -d shepard -f V1.11.0__add_shepard_id_to_timeseries.sql
--   $ psql -U shepard -d shepard -c "SELECT count(*) FROM timeseries WHERE shepard_id IS NULL;"
--   → expect 0
--
-- Rollback: V1.11.0_R__drop_shepard_id.sql (sibling file).

-- pgcrypto provides gen_random_uuid(); enable if not already loaded.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Step 1: add the column NULLABLE first so the backfill expression can populate it.
ALTER TABLE timeseries
    ADD COLUMN IF NOT EXISTS shepard_id UUID;

-- Step 2: backfill all NULLs with a fresh UUID per row (idempotent — only updates
-- rows that don't have one yet). Re-running this on a fully-backfilled table is a no-op.
UPDATE timeseries
    SET shepard_id = gen_random_uuid()
    WHERE shepard_id IS NULL;

-- Step 3: tighten the column to NOT NULL now that every row has a value.
ALTER TABLE timeseries
    ALTER COLUMN shepard_id SET NOT NULL;

-- Step 4: ensure new rows auto-mint a shepard_id on insert when the caller omits it.
ALTER TABLE timeseries
    ALTER COLUMN shepard_id SET DEFAULT gen_random_uuid();

-- Step 5: enforce uniqueness. Concurrent table creation is safe because TS-ID is
-- single-tenant per shepard instance (no second writer racing this migration).
CREATE UNIQUE INDEX IF NOT EXISTS idx_timeseries_shepard_id
    ON timeseries(shepard_id);
