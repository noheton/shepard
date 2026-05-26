// Rollback for V80__add_created_in_month_index.cypher
// NEO-AUDIT-004 — Time-bucketed Agent index for `:User` supernode
//
// Removes all created_in_month relationships and drops the range index.
// Safe to run multiple times (idempotent).
//
// Run from cypher-shell:
//   cypher-shell -u neo4j -p <pwd> -f V80_R__add_created_in_month_index.cypher
// After running this file, also revert DataObjectDAO.writeCreatedInMonth() and
// the DataObjectService.createDataObject() call site to prevent new relationships
// from being created.

// --- Remove the range index ----------------------------------------------

DROP INDEX created_in_month_ym_idx IF EXISTS;

// --- Remove all created_in_month relationships ---------------------------
// Batched to avoid heap pressure on large installs.

CALL {
  MATCH ()-[r:created_in_month]->()
  DELETE r
} IN TRANSACTIONS OF 1000 ROWS;
