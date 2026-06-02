// TEMPLATE-ICONS-1 — seed iconKey on the 10 shipped MFFD process-type templates.
//
// Neo4j is schema-less, so the iconKey property is additive on existing
// :ShepardTemplate nodes. Nodes without iconKey read as null in the OGM
// (ShepardTemplateIO.from() maps null through; the UI falls back to the
// per-kind default per aidocs/integrations/122 §3).
//
// Idempotency: each SET uses coalesce(t.iconKey, $icon) so a re-run is a
// no-op — if iconKey is already set the coalesce returns the existing value
// and the SET is a no-op. MATCH (instead of MERGE) + OPTIONAL MATCH means
// missing template names are silently skipped; the migration does not fail
// if a template has not yet been seeded.
//
// Operator runbook:
//   Apply via the standard MigrationsRunner (Flyway-style ordering).
//   Manual run: cypher-shell -u neo4j -p <password> -f V107__Add_iconKey_to_ShepardTemplate.cypher
//   Verify:     MATCH (t:ShepardTemplate)
//               WHERE t.iconKey IS NOT NULL
//               RETURN t.name, t.iconKey ORDER BY t.name;
//               → up to 10 rows for the templates that exist on this instance.
//
// Rollback: V107_R__Add_iconKey_to_ShepardTemplate.cypher
// aidocs/16 TEMPLATE-ICONS-1; aidocs/34 V107 row.

OPTIONAL MATCH (t:ShepardTemplate {name: 'MFFDStepRoot'})
WHERE t IS NOT NULL
SET t.iconKey = coalesce(t.iconKey, 'mdi-factory');

OPTIONAL MATCH (t:ShepardTemplate {name: 'MFFDLayer'})
WHERE t IS NOT NULL
SET t.iconKey = coalesce(t.iconKey, 'mdi-layers');

OPTIONAL MATCH (t:ShepardTemplate {name: 'MFFDPlyGroup'})
WHERE t IS NOT NULL
SET t.iconKey = coalesce(t.iconKey, 'mdi-format-list-group');

OPTIONAL MATCH (t:ShepardTemplate {name: 'MFFDTrack'})
WHERE t IS NOT NULL
SET t.iconKey = coalesce(t.iconKey, 'mdi-vector-polyline');

OPTIONAL MATCH (t:ShepardTemplate {name: 'MFFDExecution'})
WHERE t IS NOT NULL
SET t.iconKey = coalesce(t.iconKey, 'mdi-play-circle-outline');

OPTIONAL MATCH (t:ShepardTemplate {name: 'MFFDBridgeWeldExecution'})
WHERE t IS NOT NULL
SET t.iconKey = coalesce(t.iconKey, 'mdi-flash-outline');

OPTIONAL MATCH (t:ShepardTemplate {name: 'MFFDSpotWeld'})
WHERE t IS NOT NULL
SET t.iconKey = coalesce(t.iconKey, 'mdi-dots-circle');

OPTIONAL MATCH (t:ShepardTemplate {name: 'MFFDNDTScan'})
WHERE t IS NOT NULL
SET t.iconKey = coalesce(t.iconKey, 'mdi-radar');

OPTIONAL MATCH (t:ShepardTemplate {name: 'MFFDCell'})
WHERE t IS NOT NULL
SET t.iconKey = coalesce(t.iconKey, 'mdi-floor-plan');

OPTIONAL MATCH (t:ShepardTemplate {name: 'MFFDLayerOverview'})
WHERE t IS NOT NULL
SET t.iconKey = coalesce(t.iconKey, 'mdi-view-dashboard-variant');
