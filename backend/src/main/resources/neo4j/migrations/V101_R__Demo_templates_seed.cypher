// V99 rollback — TPL-SEED-DEMO-1 demo templates.
//
// Removes ONLY the six system-seeded demo templates (source = 'V99-builtin').
// User-authored templates with matching names (e.g. an admin who minted their
// own "Generic Test Run" via the UI) are intentionally left untouched —
// they lack the source marker.
//
// Run ONLY if you need to roll back V99.
//
// Operator runbook:
//   Run via: cypher-shell -u neo4j -p <password> -f V99_R__Demo_templates_seed.cypher
//   Verify:  MATCH (t:ShepardTemplate {source: 'V99-builtin'}) RETURN count(t);
//            → should return 0 after rollback.

MATCH (t:ShepardTemplate {source: 'V99-builtin'})
DETACH DELETE t;
