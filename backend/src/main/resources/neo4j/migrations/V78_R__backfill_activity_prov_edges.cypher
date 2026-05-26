// ROLLBACK for V77__backfill_activity_prov_edges.cypher
//
// Removes the three PROV-O relationship types wired by V77 from all
// :Activity nodes.  Does NOT touch the two indexes added by V77
// (Activity_targetAppId_idx, Activity_agentUsername_idx) — dropping
// an index on a live instance mid-stream is more disruptive than
// leaving two inexpensive RANGE indexes in place.  Drop them manually
// if required:
//
//   DROP INDEX Activity_targetAppId_idx IF EXISTS;
//   DROP INDEX Activity_agentUsername_idx IF EXISTS;
//
// IMPORTANT: After running this rollback, revert ProvenanceService.java
// and ActivityDAO.java to stop forward-wiring new edges.
//
// Idempotent: MATCH + DELETE, safe to re-run on an already-rolled-back graph.

CALL {
  MATCH (a:Activity)-[r:WAS_ASSOCIATED_WITH]->()
  DELETE r
} IN TRANSACTIONS OF 1000 ROWS;

CALL {
  MATCH (a:Activity)-[r:GENERATED]->()
  DELETE r
} IN TRANSACTIONS OF 1000 ROWS;

CALL {
  MATCH (a:Activity)-[r:USED]->()
  DELETE r
} IN TRANSACTIONS OF 1000 ROWS;
