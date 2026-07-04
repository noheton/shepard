// MFFD-LAYER-RECIPE — seed the MFFDLayerOverview VIEW_RECIPE ShepardTemplate.
//
// Per-Layer overview recipe: composes 5 panels (run summary, temperature
// cross-track, TPS line-scan, 3D spatial overlay, NDT frames) over the
// generic Project by-annotation roll-up (PROJ-REST-2:
// `GET /v2/projects/{appId}/by-annotation/urn:shepard:mffd:layer/{N}`).
//
// Per `aidocs/integrations/119 §2.2.3`:
//   - This recipe is an MFFD content artefact (no new infra, no
//     MFFD-namespaced endpoint, no MFFD-specific UI).
//   - Other Projects (PLUTO, LUMEN, BT-KVS) get the same affordance by
//     shipping their own VIEW_RECIPE pointing the generic by-annotation
//     route at their own join key. No new code per domain.
//
// The body shape satisfies TemplateBodyValidator's VIEW_RECIPE rule
// (one of {view, shape, renderer} must be present); `renderer` =
// "multi-panel" declares the composition; `view` carries the recipe
// surface that the generic `POST /v2/shapes/render` route consumes; the
// `panels` array enumerates the 5 panels composed from already-shipped
// building blocks (AAA3 cross-track TS, TPS line-scan, AAC1 spatial,
// Trace3D #142, AAC2 NDT heatmap).
//
// Building blocks (already-shipped, per §2.2.3 table):
//   * AAA3 / GAP-2 — cross-track TS chart
//   * AAC1 / GAP-5 — spatial container + viewer
//   * AAC2 / GAP-7 — NDT thermography heatmap
//   * #142 — Trace3D colour-mapped path
//   * MFFD-RERUN-ANOMALY-DETECT — re-run badge + filter chip
//   * POST /v2/shapes/render — generic render entrypoint
//
// Pattern mirrors V102 (Cross-ply TCP temperature) — same merge key
// shape, same `createdBy='system'`, same `retired=false`, same
// source-rollback handle.
//
// Idempotency: MERGE on {name, version}. Re-running is a no-op.
// Rollback: V109_R__ deletes only rows with `source = 'V109-builtin'`;
// user-authored templates with colliding names are untouched.
//
// Operator runbook:
//   Run via: cypher-shell -u neo4j -p <password> -f V109__Mffd_layer_overview_view_recipe.cypher
//   Rollback: V109_R__Mffd_layer_overview_view_recipe.cypher
//   Verify:  MATCH (t:ShepardTemplate {source: 'V109-builtin'})
//            RETURN t.name, t.templateKind, t.retired, t.iconKey;
//            -> should return 1 row; templateKind='VIEW_RECIPE'; retired=false;
//            iconKey='mdi-view-dashboard-variant'.
//
// aidocs/16: MFFD-LAYER-RECIPE; aidocs/34: V109 row; spec: aidocs/integrations/119 §2.2.3.

MERGE (t:ShepardTemplate {name: 'MFFDLayerOverview', version: 1})
ON CREATE SET
  t.appId         = randomUUID(),
  t.templateKind  = 'VIEW_RECIPE',
  t.description   = 'Per-Layer overview for the MFFD AFP layup process. Composes 5 panels (run summary, cross-track temperature, TPS line-scan, 3D spatial overlay, NDT frames) over the generic Project by-annotation roll-up (`GET /v2/projects/{appId}/by-annotation/urn:shepard:mffd:layer/{N}`). MFFD content artefact only — no MFFD-namespaced endpoint, no MFFD-specific UI. Other Projects (PLUTO mission-phase, LUMEN test-bench, …) get the same affordance by shipping their own VIEW_RECIPE.',
  t.body          = '{"renderer":"multi-panel","view":{"kind":"per-layer-overview","domain":"mffd","scopePredicate":"urn:shepard:mffd:layer","projectScopeAnnotation":"urn:shepard:partOf","panels":[{"key":"run-summary","title":"Run summary","kind":"summary","fields":["runCount","reRunBadgeBreakdown"],"source":{"endpoint":"/v2/projects/{projectAppId}/by-annotation/urn:shepard:mffd:layer/{value}","rollUp":"count","groupBy":"urn:shepard:mffd:run-order"}},{"key":"temperature","title":"Temperature","kind":"cross-track-timeseries","building-block":"AAA3","channelPredicate":"urn:shepard:afp:tcp-temperature-c","groupBy":"urn:shepard:mffd:run-order","source":{"endpoint":"/v2/timeseries/cross-data-object-bulk-data"}},{"key":"tps-line-scan","title":"TPS line-scan","kind":"stitched-image","filter":{"annotation":"urn:shepard:spatial:kind","equals":"brush-trace"},"layout":"cell-coords","source":{"endpoint":"/v2/projects/{projectAppId}/by-annotation/urn:shepard:mffd:layer/{value}"}},{"key":"spatial-overlay","title":"Spatial overlay","kind":"spatial-3d","building-blocks":["Trace3D","AAC1"],"channels":[{"predicate":"urn:shepard:spatial:kind","equals":"tps-pointcloud"},{"predicate":"urn:shepard:spatial:kind","equals":"fsd-pointcloud"},{"predicate":"urn:shepard:mffd:process-type","equals":"ndt-thermography"}],"source":{"endpoint":"/v2/projects/{projectAppId}/by-annotation/urn:shepard:mffd:layer/{value}"}},{"key":"ndt-frames","title":"NDT frames","kind":"gallery","building-block":"AAC2","filter":{"annotation":"urn:shepard:mffd:process-type","equals":"ndt-thermography"},"source":{"endpoint":"/v2/projects/{projectAppId}/by-annotation/urn:shepard:mffd:layer/{value}"}}]}}',
  t.tags          = ['view-recipe', 'multi-panel', 'mffd', 'afp', 'layer', 'overview'],
  t.iconKey       = 'mdi-view-dashboard-variant',
  t.createdBy     = 'system',
  t.createdAt     = timestamp(),
  t.updatedAt     = timestamp(),
  t.retired       = false,
  t.source        = 'V109-builtin'
;
