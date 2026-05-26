// V84_R — PROV1k rollback: drop the typedPredecessorsJson index.
//
// Reverses V84__TypedPredecessors.cypher.
// The typedPredecessorsJson property values are NOT removed — they are
// schema-free node properties that can be re-indexed by re-running V84.
//
// Idempotent: IF EXISTS is safe to re-run.

DROP INDEX dataobject_typed_predecessors_json IF EXISTS;
