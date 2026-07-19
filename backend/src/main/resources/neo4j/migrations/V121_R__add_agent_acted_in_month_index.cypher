// Rollback for V121__add_agent_acted_in_month_index.cypher
// NEO-AUDIT-2026-07-18-ACTIVITY-SUPERNODE — Time-bucketed Agent index for the
// `:Activity` → `:User` supernode.
//
// Removes all agent_acted_in_month relationships and drops the range index.
// Safe to run multiple times (idempotent). The relationship delete is batched
// in TRANSACTIONS OF 1000 ROWS to avoid heap pressure on the ~2.87M-rel set.
//
// Run from cypher-shell:
//   cypher-shell -u neo4j -p <pwd> -f V121_R__add_agent_acted_in_month_index.cypher
// After running this file, also revert ActivityDAO.writeAgentActedInMonth()
// (and its call from wireEdges) to prevent new relationships from being created.

// --- Remove the range index ----------------------------------------------

DROP INDEX agent_acted_in_month_ym_idx IF EXISTS;

// --- Remove all agent_acted_in_month relationships -----------------------
// Batched to avoid heap pressure on large installs.

CALL {
  MATCH ()-[r:agent_acted_in_month]->()
  DELETE r
} IN TRANSACTIONS OF 1000 ROWS;
