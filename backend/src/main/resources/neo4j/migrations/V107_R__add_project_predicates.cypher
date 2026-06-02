// PROJ-PREDICATES-1 — Rollback for V107__add_project_predicates.cypher.
//
// Removes the three :Predicate nodes seeded for the Project/Programme
// controlled vocabulary (urn:shepard:project, urn:shepard:partOf,
// urn:shepard:programme). The parent :Vocabulary node (V72) is NOT removed.
//
// Safe to run whether or not V107 was applied:
//   - If V107 was applied: deletes exactly 3 nodes.
//   - If V107 was not applied (nodes absent): no rows matched, no error.
//
// Operator runbook:
//   cypher-shell -u neo4j -p <password> -f V107_R__add_project_predicates.cypher
//   Verify:
//     MATCH (p:Predicate)
//     WHERE p.uri IN ['urn:shepard:project', 'urn:shepard:partOf', 'urn:shepard:programme']
//     RETURN count(p) AS remaining;
//     -- → 0 rows after rollback
//
// Note: this rollback does NOT remove :SemanticAnnotation nodes that may
// have been created using these predicates before rollback. Annotations
// referencing these predicate URIs become orphaned (predicate lookup
// returns null) but are not deleted — preserving data integrity per the
// "additive, no-backfill" policy. If annotation data must also be removed,
// run the companion cleanup manually before this rollback file.

MATCH (p:Predicate)
WHERE p.uri IN [
  'urn:shepard:project',
  'urn:shepard:partOf',
  'urn:shepard:programme'
]
DETACH DELETE p;
