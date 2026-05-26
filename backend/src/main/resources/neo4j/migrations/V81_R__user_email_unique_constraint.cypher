// V81_R — Rollback for V81__user_email_unique_constraint.cypher
//
// Drops the user_email_unique constraint.
//
// NOTE: This rollback CANNOT restore the merged duplicate :User nodes.
// apoc.refactor.mergeNodes is a one-way operation — the duplicate node is
// deleted and its edges are transferred to the canonical node. If you need to
// restore the pre-migration graph (including the duplicate user nodes), you must
// restore from a database backup taken before V81 was applied.
//
// This rollback is safe to apply after:
//   1. Restoring from backup (to undo the node merges and remove new data added
//      to deduplicated users since the migration ran), OR
//   2. As an isolated rollback of the constraint only (if you intend to recreate
//      the duplicate nodes manually before re-applying the migration).
//
// Operator runbook:
//   Manual run from cypher-shell:
//     cypher-shell -u neo4j -p <pwd> -f V81_R__user_email_unique_constraint.cypher
//   Verify: SHOW CONSTRAINTS WHERE name = 'user_email_unique'; → 0 rows.

DROP CONSTRAINT user_email_unique IF EXISTS;
