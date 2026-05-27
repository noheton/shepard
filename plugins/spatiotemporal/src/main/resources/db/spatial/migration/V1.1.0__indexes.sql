-- PLUGIN-SPATIAL-AUDIT-2026-05-24-002: cheapest perf wins per aidocs/data/82 §2.1+§2.3
--
-- Adds two missing performance indexes to the V1 spatial_data_points table:
--   1. BRIN on `time`        — monotonic-arrival data; O(1) block-range cost,
--                              matches the profile_time_brin pattern in V2.0.0.
--   2. GIN  on `measurements jsonb_path_ops`
--                            — JSONB containment/path queries; matches the
--                              profile_measurements_gin pattern in V2.0.0.
--
-- Both use CREATE INDEX IF NOT EXISTS so this migration is idempotent (safe to
-- re-run on an instance that already has the indexes from a manual hot-fix).
--
-- Note: CONCURRENTLY cannot be used inside a Flyway-managed transaction.
-- These are plain CREATE INDEX statements; on a live system with a large
-- spatial_data_points table an operator may prefer to run them manually with
-- CONCURRENTLY outside of migration before upgrading, then let this migration
-- detect them as already-existing via the IF NOT EXISTS guard.
--
-- Operator runbook: plugins/spatiotemporal/docs/install.md §"V1 Index Backfill"

-- §2.1 — Time range queries (monotonic-arrival pattern, cheap pages).
-- BRIN is ideal for time columns where values arrive in approximately
-- monotonic order — each block range stores only min/max, making it
-- dramatically cheaper than B-tree for range scans on large tables.
CREATE INDEX IF NOT EXISTS spatial_data_points_time_brin
    ON spatial_data_points USING BRIN (time)
    WITH (pages_per_range = 32);

-- §2.3 — Measurements JSONB path/containment queries.
-- jsonb_path_ops GIN operator class covers @>, @?, @@ operators used by
-- field-value filters ("give me all points where measurements.temperature > 20").
CREATE INDEX IF NOT EXISTS spatial_data_points_measurements_gin
    ON spatial_data_points USING GIN (measurements jsonb_path_ops);
