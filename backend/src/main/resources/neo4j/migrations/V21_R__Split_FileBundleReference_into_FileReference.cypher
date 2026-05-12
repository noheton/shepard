// FR1a — rollback for V21. See `aidocs/53 §1.5`.
//
// Safety guard: refuses to run if any user-created `:FileGroup` exists
// (i.e. any group whose `legacyDefault` property is NOT 'FR1a-V21').
// Such groups can only have been created via the FR1a /v2/bundles/...
// REST surface; a rollback that destroys them would silently lose user
// state, which is the kind of thing the CLAUDE.md migration policy
// flags as "not rollback-safe."
//
// The Cypher-side guard is implemented via a CALL { ... } subquery
// that throws (via assert + apoc.util.validate-equivalent assertion
// using `CASE WHEN ... THEN 1/0 ELSE 0 END` so we don't depend on
// APOC). The startup migration runner translates the resulting
// `MigrationsException` into a fail-fast abort.
//
// Operator runbook (the boring path):
//   1. Verify no user-created groups exist:
//        MATCH (g:FileGroup) WHERE g.legacyDefault <> 'FR1a-V21'
//          OR g.legacyDefault IS NULL RETURN count(g) AS user_groups;
//      If `user_groups > 0`, do NOT roll back — manually triage first.
//   2. Take a Neo4j backup.
//   3. Run this rollback script via `cypher-shell` or by setting
//      the migration target back to V15 and restarting shepard.
//   4. Verify post-rollback:
//        MATCH (n:FileBundleReference) RETURN count(n);  // should be 0
//        MATCH (g:FileGroup)            RETURN count(g);  // should be 0

// 1. Guard: division-by-zero if any user-created group is present.
//    The error message that surfaces will mention division by zero;
//    the operator runbook above explains the actual cause.
CALL {
  MATCH (g:FileGroup)
  WHERE g.legacyDefault IS NULL OR g.legacyDefault <> 'FR1a-V21'
  WITH count(g) AS userGroups
  // If userGroups > 0, force a hard error.
  RETURN 1 / (CASE WHEN userGroups = 0 THEN 1 ELSE 0 END) AS guard
};

// 2. Drop the synthetic group->file edges and the synthetic groups.
//    The legacy bundle->file HAS_PAYLOAD edges were left in place by
//    V21 as a compatibility shadow, so files survive this step.
MATCH (b:FileBundleReference)-[hg:HAS_GROUP]->(g:FileGroup {legacyDefault: 'FR1a-V21'})
OPTIONAL MATCH (g)-[gp:HAS_PAYLOAD]->(:ShepardFile)
DELETE gp, hg, g;

// 3. Strip the :FileBundleReference label, leaving the legacy
//    :FileReference label intact (which is where we started).
MATCH (r:FileReference:FileBundleReference)
REMOVE r:FileBundleReference;
