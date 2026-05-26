// V90_R — Rollback for V90__Tighten_orphan_permissions_backfill.cypher.
//
// **This rollback is intentionally a no-op.**
//
// V90 removes Permissions nodes that were incorrectly attached to
// DataObject and BasicReference nodes by V14. Restoring those incorrect
// Permissions nodes would re-break the Collection-level inheritance
// walk inside PermissionsService.isAccessAllowedForDataObjectAppId
// (lines 321-338), which is the bug V90 corrects.
//
// If you need to revert V90 for diagnostic purposes:
//   - The affected DataObjects and BasicReferences are no longer
//     directly permissioned; they now correctly inherit from their
//     parent Collection.
//   - To temporarily recreate V14's incorrect state (DANGER: breaks
//     access-control inheritance), you would need to re-run V14 manually
//     with a configured :_ShepardMigrationContext node — then immediately
//     re-run V90 to clean up again.
//   - No automated rollback is provided because re-introducing incorrect
//     Permissions is always wrong; operator intent must be explicit.
//
// To run from a shell (confirms this is a no-op):
//   cypher-shell -u neo4j -p "$NEO4J_PASSWORD" \
//     -f backend/src/main/resources/neo4j/migrations/V90_R__Rollback_Tighten_orphan_permissions_backfill.cypher

// Intentional no-op: verify migration cleanup is in effect.
MATCH (e)-[:has_permissions]->(p:Permissions {legacyBackfill: 'A0-V14'})
WHERE e:DataObject OR e:BasicReference
RETURN count(p) AS remaining_incorrect_permissions;
