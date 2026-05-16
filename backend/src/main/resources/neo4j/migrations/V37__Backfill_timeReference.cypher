// V37 — Backfill timeReference = 'WALL_CLOCK' for all TimeseriesReference nodes
// missing the property.
//
// Context: TM1a adds three nullable time-reference fields to TimeseriesReference.
// timeReference encodes timestamp semantics. Pre-TM1a rows lack the property;
// the application treats NULL as 'WALL_CLOCK', but making the value explicit
// simplifies Neo4j queries and prevents surprises if the default ever changes.
//
// wallClockOffset and wallClockOffsetSource are intentionally left NULL on
// pre-TM1a rows because no meaningful value can be back-filled — they are only
// relevant when timeReference == 'EXPERIMENT_RELATIVE'.
//
// Idempotent: the WHERE clause ensures only NULL values are touched. Re-running
// after a partial run is safe.
//
// Operator runbook: run via cypher-shell or the Neo4j Browser — no project setup
// required. The CALL...IN TRANSACTIONS batching avoids heap pressure on large
// graphs.
//
// Rollback: V37_R__Rollback_Backfill_timeReference.cypher (strips the property).
MATCH (r:TimeseriesReference)
WHERE r.timeReference IS NULL
CALL { WITH r SET r.timeReference = 'WALL_CLOCK' } IN TRANSACTIONS OF 10000 ROWS;
