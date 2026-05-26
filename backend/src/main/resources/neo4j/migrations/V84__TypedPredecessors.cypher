// V84 — PROV1k: Add index on :DataObject(typedPredecessorsJson).
//
// The typedPredecessorsJson property is a schemaless JSON string added to
// :DataObject nodes by DataObjectService when a CreateDataObjectV2IO body
// carries a non-empty typedPredecessors list.
//
// This migration adds an informational index to support future export/query
// operations that scan for nodes where typed predecessors are present (e.g.
// "find all rework arcs"). The field is not on a hot lookup path; the index
// avoids full-graph scans on batch export queries only.
//
// Pre-PROV1k DataObjects have typedPredecessorsJson = null — those nodes are
// excluded from the index by design (Neo4j does not index null property values
// in the default b-tree index; they only appear in an EXISTS() predicate scan).
//
// Idempotent: IF NOT EXISTS is safe to re-run after a failed deployment.
//
// Operator runbook: index creation only — no data mutation.
//   Rollback: V84_R__TypedPredecessors.cypher
//   Regression test: planned in DataObjectTypedPredecessorTest (tracked PROV1k).

CREATE INDEX dataobject_typed_predecessors_json IF NOT EXISTS
FOR (n:DataObject)
ON (n.typedPredecessorsJson);
