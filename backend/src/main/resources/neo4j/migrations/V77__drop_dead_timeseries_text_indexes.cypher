// NEO-AUDIT-011 — drop zero-read Timeseries TEXT indexes (V8 residuals)
//
// These three TEXT indexes were created in V8 for querying Timeseries by
// field/location/symbolicName. Post-MFFD audit (2026-05-26) shows lastRead=NULL
// on all three — queries use measurement + device only (idx_Timeseries_attr_measurement
// and idx_Timeseries_attr_device remain in place and are NOT touched here).
//
// Write-amplification cost at MFFD scale: 33k writes × 3 indexes × ~1ms ≈ 100s
// of pure index-update CPU per ingest pass.
//
// UNIQUE constraints are NOT touched — they protect entity identity.
// n10s-owned indexes are NOT touched — n10s rebuilds them on restart.
//
// Safe to re-run: DROP INDEX … IF EXISTS is idempotent.
// Rollback: see V77_R__restore_timeseries_text_indexes.cypher.
//
// Operator runbook: online index drop in Neo4j 5.x; no data loss; no restart needed.
// To verify post-drop: SHOW INDEXES WHERE labelsOrTypes = ['Timeseries'] AND name IN
//   ['idx_Timeseries_attr_field', 'idx_Timeseries_attr_location',
//    'idx_Timeseries_attr_symbolic_name'];
// Expected: 0 rows returned.

DROP INDEX idx_Timeseries_attr_field IF EXISTS;
DROP INDEX idx_Timeseries_attr_location IF EXISTS;
DROP INDEX idx_Timeseries_attr_symbolic_name IF EXISTS;
