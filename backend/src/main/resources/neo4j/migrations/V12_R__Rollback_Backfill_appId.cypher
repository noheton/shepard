// L2b — rollback for V12__Backfill_appId.cypher (aidocs/25 §4 Phase 2 Rollback).
//
// Removes the backfilled `appId` from every node that has one. Idempotent:
// re-running on an already-rolled-back graph is a no-op.
//
// IMPORTANT — operator-run only:
//   The neo4j-migrations runner does NOT pick up `_R` suffixes
//   automatically; this file is not in the V## sequence. There is no
//   precedent for a `_R` rollback file in this project (this is the first
//   one), so the operator runs it manually:
//
//     cypher-shell -u $NEO4J_USER -p $NEO4J_PW \
//       -f V12_R__Rollback_Backfill_appId.cypher
//
//   or, paste the block below into the Neo4j Browser.
//
// Side effect on subsequent app starts:
//   - V11's per-label `REQUIRE n.appId IS UNIQUE` constraint stays in
//     place; uniqueness on null is permitted in Neo4j 5, so the rolled-
//     back graph is constraint-valid.
//   - The next backend start will re-run V12 (it's idempotent), repopulating
//     `appId` on every node — to keep the rollback durable, the operator
//     must also pin the deployed version below 12 (or remove V12) before
//     restarting the backend.
//
// Chunked the same way V12 chunks: `CALL { … } IN TRANSACTIONS` so the
// REMOVE doesn't lock millions of nodes at once.

MATCH (n) WHERE n.appId IS NOT NULL
CALL { WITH n REMOVE n.appId } IN TRANSACTIONS OF 10000 ROWS;
