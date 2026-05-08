// A0 / C3 — Rollback for V14__Backfill_orphan_permissions.cypher.
//
// **Operator-run only**. Detaches and deletes the Permissions nodes
// that V14 created for orphan entities (identified by their
// `legacyBackfill = 'A0-V14'` marker). Other Permissions nodes stay
// untouched. After this runs, the affected entities are orphans
// again — and on the next start, V14 will re-attach default
// Permissions if the JVM-seeded context node is in place.
//
// To run from a shell:
//   cypher-shell -u neo4j -p "$NEO4J_PASSWORD" \
//     -f backend/src/main/resources/neo4j/migrations/V14_R__Rollback_Backfill_orphan_permissions.cypher

MATCH (e:BasicEntity)-[r:has_permissions]->(p:Permissions {legacyBackfill: 'A0-V14'})
DETACH DELETE p
RETURN count(p) AS rolled_back;
