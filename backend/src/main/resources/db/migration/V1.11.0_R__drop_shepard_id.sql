-- Rollback of V1.11.0__add_shepard_id_to_timeseries.sql.
--
-- Operator-run only — Flyway does not execute *_R__ files automatically.
-- Drops the unique index and the shepard_id column. Resolution by shepardId
-- is no longer possible after this runs; the 5-tuple stays the only key.
--
-- Operator runbook:
--   $ psql -U shepard -d shepard -f V1.11.0_R__drop_shepard_id.sql

DROP INDEX IF EXISTS idx_timeseries_shepard_id;

ALTER TABLE timeseries
    DROP COLUMN IF EXISTS shepard_id;
