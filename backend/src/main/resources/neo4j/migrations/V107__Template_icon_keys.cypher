// TEMPLATE-ICONS-1 — set iconKey on every shipped :ShepardTemplate
//
// Design: aidocs/integrations/122 §4 (the MFFD spec table) + the actual
// templates shipped through V93 / V100 / V101 / V102 / V103. The spec
// doc lists ten CamelCase logical names (MFFDStepRoot, MFFDLayer, …);
// the migrations have shipped a different set of human-readable names
// (`MFFD AFP Layup`, `MFFD Ultrasonic Welding`, …). This migration
// covers BOTH:
//
//   1. The actual shipped V93 / V100 / V101 / V102 / V103 names — so
//      icons are observable immediately on the current dataset.
//   2. The spec's CamelCase names — so when the 5-level hierarchy
//      templates (`MFFDStepRoot`, etc.) land later, their icons are
//      already wired with no follow-up migration.
//
// Shape:
//   * Idempotent: `t.iconKey = coalesce(t.iconKey, $newIcon)` so a
//     re-run is a no-op AND so an admin who has set a custom icon
//     via PATCH /v2/templates/{appId} is never overwritten.
//   * Fail-soft: MATCH that finds zero rows is fine (the row will
//     pick up its icon when the seed migration finally lands).
//
// Operator runbook:
//   Run via: cypher-shell -u neo4j -p <password> -f V107__Template_icon_keys.cypher
//   Rollback: V107_R__Template_icon_keys.cypher (unsets iconKey on the same set)
//   Verify:  MATCH (t:ShepardTemplate)
//            WHERE t.iconKey IS NOT NULL
//            RETURN t.name, t.iconKey ORDER BY t.name;
//
// aidocs/16 TEMPLATE-ICONS-1

// ───────────────────────────────────────────────────────────────────────────
// V100 shipped names — the actual MFFD process-chain templates
// ───────────────────────────────────────────────────────────────────────────
MATCH (t:ShepardTemplate {name: 'MFFD AFP Layup'})
SET   t.iconKey = coalesce(t.iconKey, 'mdi-layers');

MATCH (t:ShepardTemplate {name: 'MFFD Ultrasonic Welding'})
SET   t.iconKey = coalesce(t.iconKey, 'mdi-flash-outline');

MATCH (t:ShepardTemplate {name: 'MFFD Resistance Welding'})
SET   t.iconKey = coalesce(t.iconKey, 'mdi-flash-outline');

MATCH (t:ShepardTemplate {name: 'MFFD Stud Welding'})
SET   t.iconKey = coalesce(t.iconKey, 'mdi-dots-circle');

MATCH (t:ShepardTemplate {name: 'MFFD NDT Inspection'})
SET   t.iconKey = coalesce(t.iconKey, 'mdi-radar');

MATCH (t:ShepardTemplate {name: 'MFFD Frame Welding'})
SET   t.iconKey = coalesce(t.iconKey, 'mdi-flash-outline');

MATCH (t:ShepardTemplate {name: 'MFFD Stringer Connection'})
SET   t.iconKey = coalesce(t.iconKey, 'mdi-vector-polyline');

MATCH (t:ShepardTemplate {name: 'MFFD LBR Cleats Assembly'})
SET   t.iconKey = coalesce(t.iconKey, 'mdi-play-circle-outline');

// ───────────────────────────────────────────────────────────────────────────
// V93 / V101 / V102 / V103 — the other shipped templates
// ───────────────────────────────────────────────────────────────────────────
MATCH (t:ShepardTemplate {name: 'EquipmentItem'})
SET   t.iconKey = coalesce(t.iconKey, 'mdi-tools');

MATCH (t:ShepardTemplate {name: 'Generic Test Run'})
SET   t.iconKey = coalesce(t.iconKey, 'mdi-test-tube');

MATCH (t:ShepardTemplate {name: 'Wet Lab Sample'})
SET   t.iconKey = coalesce(t.iconKey, 'mdi-flask-outline');

MATCH (t:ShepardTemplate {name: 'Process Step (Manufacturing)'})
SET   t.iconKey = coalesce(t.iconKey, 'mdi-factory');

MATCH (t:ShepardTemplate {name: 'Quality Inspection / NCR'})
SET   t.iconKey = coalesce(t.iconKey, 'mdi-clipboard-check-outline');

MATCH (t:ShepardTemplate {name: 'Research Collection'})
SET   t.iconKey = coalesce(t.iconKey, 'mdi-folder-multiple-outline');

MATCH (t:ShepardTemplate {name: 'Citable Dataset'})
SET   t.iconKey = coalesce(t.iconKey, 'mdi-bookmark-outline');

MATCH (t:ShepardTemplate {name: 'Cross-ply TCP temperature'})
SET   t.iconKey = coalesce(t.iconKey, 'mdi-view-dashboard-variant');

MATCH (t:ShepardTemplate {name: 'Disposition record'})
SET   t.iconKey = coalesce(t.iconKey, 'mdi-clipboard-text-outline');

// ───────────────────────────────────────────────────────────────────────────
// aidocs/integrations/122 §4 — CamelCase names anticipated by the spec
// (no-op until the corresponding seed lands; ready when it does)
// ───────────────────────────────────────────────────────────────────────────
MATCH (t:ShepardTemplate {name: 'MFFDStepRoot'})
SET   t.iconKey = coalesce(t.iconKey, 'mdi-factory');

MATCH (t:ShepardTemplate {name: 'MFFDLayer'})
SET   t.iconKey = coalesce(t.iconKey, 'mdi-layers');

MATCH (t:ShepardTemplate {name: 'MFFDPlyGroup'})
SET   t.iconKey = coalesce(t.iconKey, 'mdi-format-list-group');

MATCH (t:ShepardTemplate {name: 'MFFDTrack'})
SET   t.iconKey = coalesce(t.iconKey, 'mdi-vector-polyline');

MATCH (t:ShepardTemplate {name: 'MFFDExecution'})
SET   t.iconKey = coalesce(t.iconKey, 'mdi-play-circle-outline');

MATCH (t:ShepardTemplate {name: 'MFFDBridgeWeldExecution'})
SET   t.iconKey = coalesce(t.iconKey, 'mdi-flash-outline');

MATCH (t:ShepardTemplate {name: 'MFFDSpotWeld'})
SET   t.iconKey = coalesce(t.iconKey, 'mdi-dots-circle');

MATCH (t:ShepardTemplate {name: 'MFFDNDTScan'})
SET   t.iconKey = coalesce(t.iconKey, 'mdi-radar');

MATCH (t:ShepardTemplate {name: 'MFFDCell'})
SET   t.iconKey = coalesce(t.iconKey, 'mdi-floor-plan');

MATCH (t:ShepardTemplate {name: 'MFFDLayerOverview'})
SET   t.iconKey = coalesce(t.iconKey, 'mdi-view-dashboard-variant');
