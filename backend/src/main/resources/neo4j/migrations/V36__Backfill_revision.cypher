// V36 — Backfill revision = 1 for all VersionableEntity nodes missing the property.
//
// Context: V2a (revision counter) of the aidocs/41 snapshots design adds a
// monotonically-increasing write counter to every VersionableEntity. New nodes
// get revision=1 at creation; this migration seeds the same value for existing
// nodes (Collection, DataObject, BasicReference and all their subclasses).
//
// Idempotent: the WHERE clause ensures only NULL values are touched. Re-running
// after a partial run is safe.
//
// Operator runbook: run via cypher-shell or the Neo4j Browser — no project setup
// required. The CALL…IN TRANSACTIONS batching avoids heap pressure on large
// graphs.
MATCH (n:VersionableEntity)
WHERE n.revision IS NULL
CALL { WITH n SET n.revision = 1 } IN TRANSACTIONS OF 10000 ROWS;
