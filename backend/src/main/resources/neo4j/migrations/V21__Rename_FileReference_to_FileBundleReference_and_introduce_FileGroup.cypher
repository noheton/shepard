// FR1a — additive rename of FileReference → FileBundleReference plus
// the introduction of a default :FileGroup sub-node per bundle.
// See `aidocs/53 §1.5` for the full design rationale.
//
// Wire-shape compatibility (CLAUDE.md API-version policy):
//   * The legacy `:FileReference` label STAYS. The OGM and the
//     upstream `/shepard/api/.../fileReferences/...` REST surface keep
//     querying by it. We only ADD `:FileBundleReference` alongside.
//   * The legacy `(bundle)-[:HAS_PAYLOAD]->(file)` edges STAY in place
//     as a "compatibility shadow" so the upstream-API read path keeps
//     returning the flat files list with zero wire change. The new
//     edges live under the default `:FileGroup` sub-node.
//
// Idempotent + fail-fast per `CLAUDE.md`:
//   * Each step uses `WHERE NOT (...has...)` guards so re-running V21
//     against an already-migrated graph is a no-op.
//   * Cypher errors propagate via `MigrationsException` (post-A1e
//     `MigrationsRunner` translates that into a fail-fast startup
//     abort).
//
// Operator runbook (the boring path):
//   1. Take a Neo4j backup (per `aidocs/45 §2.1 W3`).
//   2. Restart shepard with the upgraded image. The Java side
//      (`MigrationsRunner`) drives this Cypher.
//   3. After startup, verify in `cypher-shell`:
//        MATCH (b:FileBundleReference)
//        RETURN count(b) AS bundles,
//               sum(CASE WHEN (b)-[:HAS_GROUP]->(:FileGroup) THEN 1 ELSE 0 END) AS bundles_with_group;
//      Both numbers should match.
//   4. Rollback (only if no user-created groups exist):
//      `V21_R__Split_FileBundleReference_into_FileReference.cypher`.
//
// MIGRATION BODY:

// 1. Tag every existing :FileReference with the new :FileBundleReference label.
//    Idempotent: SET label is a no-op if the label is already present.
//    The legacy :FileReference label is preserved for upstream-API reads.
MATCH (r:FileReference)
WHERE NOT r:FileBundleReference
SET r:FileBundleReference;

// 2. For each FileBundleReference still missing a :HAS_GROUP edge,
//    create a default :FileGroup with a fresh appId and attach it.
//    Re-run safe: bundles that already have a group are filtered out
//    by the WHERE clause.
//    `randomUUID()` produces UUID v4 in Cypher (vs. UUID v7 from
//    AppIdGenerator on the JVM side). For migration-minted defaults
//    this is acceptable — the V11 / V22 unique constraint on `appId`
//    is what matters; ordering by mint-time is not required for these
//    backfill rows.
MATCH (b:FileBundleReference)
WHERE NOT (b)-[:HAS_GROUP]->(:FileGroup)
CREATE (g:FileGroup:BasicEntity {
  appId: randomUUID(),
  name: 'default',
  description: 'Auto-created default group from V21 (FR1a) migration.',
  index: 0,
  deleted: false,
  legacyDefault: 'FR1a-V21'
})
CREATE (b)-[:HAS_GROUP {index: 0}]->(g);

// 3. Re-parent the bundle's existing files under the default group.
//    The legacy bundle->file HAS_PAYLOAD edges STAY (compatibility
//    shadow for the upstream API). We MERGE the new group->file edge
//    so re-running this step is a no-op.
//    Filter by `g.legacyDefault = 'FR1a-V21'` so we don't touch
//    user-created groups on re-run.
MATCH (b:FileBundleReference)-[:HAS_PAYLOAD]->(f:ShepardFile)
MATCH (b)-[:HAS_GROUP]->(g:FileGroup {legacyDefault: 'FR1a-V21'})
MERGE (g)-[:HAS_PAYLOAD]->(f);

// 4. Progress log (RETURN row — works without APOC). Cheap; bounded
//    to the count of bundles touched. Visible in the migration log.
MATCH (b:FileBundleReference)
RETURN count(b) AS file_bundle_references_total;

MATCH (g:FileGroup {legacyDefault: 'FR1a-V21'})
RETURN count(g) AS default_file_groups_created;
