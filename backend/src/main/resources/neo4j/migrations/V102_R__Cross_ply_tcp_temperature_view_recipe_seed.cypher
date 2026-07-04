// V102 rollback — TPL-VIEW-CROSS-PLY-TCP-1 view-recipe seed.
//
// Removes ONLY the single system-seeded VIEW_RECIPE template
// (source = 'V102-builtin'). User-authored templates with a matching name
// (an admin who minted their own "Cross-ply TCP temperature" via the UI)
// are intentionally left untouched — they lack the source marker.
//
// Run ONLY if you need to roll back V102.
//
// Operator runbook:
//   Run via: cypher-shell -u neo4j -p <password> -f V102_R__Cross_ply_tcp_temperature_view_recipe_seed.cypher
//   Verify:  MATCH (t:ShepardTemplate {source: 'V102-builtin'}) RETURN count(t);
//           → should return 0 after rollback.

MATCH (t:ShepardTemplate {source: 'V102-builtin'})
DETACH DELETE t;
