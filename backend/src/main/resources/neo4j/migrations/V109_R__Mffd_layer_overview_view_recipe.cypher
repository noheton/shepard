// V109 rollback — MFFD-LAYER-RECIPE view-recipe seed.
//
// Removes ONLY the single system-seeded VIEW_RECIPE template
// (source = 'V109-builtin'). User-authored templates with a matching
// name (an admin who minted their own "MFFDLayerOverview" via the UI)
// are intentionally left untouched — they lack the source marker.
//
// Run ONLY if you need to roll back V109.
//
// Operator runbook:
//   Run via: cypher-shell -u neo4j -p <password> -f V109_R__Mffd_layer_overview_view_recipe.cypher
//   Verify:  MATCH (t:ShepardTemplate {source: 'V109-builtin'}) RETURN count(t);
//            -> should return 0 after rollback.

MATCH (t:ShepardTemplate {source: 'V109-builtin'})
DETACH DELETE t;
