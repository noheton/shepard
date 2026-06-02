// TPL-VIEW-CROSS-PLY-TCP-1 — seed one VIEW_RECIPE ShepardTemplate.
//
// Seeds the canonical "Cross-ply TCP temperature" cross-DataObject small-multiples
// view recipe consumed by the frontend "Cross-track view" pane and by callers of
// POST /v2/timeseries/cross-data-object-bulk-data (TS-CROSS-DO-VIEW-1).
//
// The recipe body shape is the JSON DSL surface that the frontend pane reads.
// The outer keys `view` + `renderer` are required by TemplateBodyValidator's
// VIEW_RECIPE rule (`expectedKeys` = {view, shape, renderer}). The actual recipe
// fields live inside the `view` object so the validator is satisfied without
// over-fitting the shape.
//
// Inner `view` keys (consumed by frontend `CollectionCrossTrackViewPane.vue`):
//   * `kind`              — "small-multiples" (only kind supported in v1)
//   * `channelPredicate`  — canonical urn:shepard:* annotation predicate IRI to match
//   * `axisGrouping`      — secondary annotation predicate for grouping
//                           (e.g. `urn:shepard:mffd:ply-number`); informational
//   * `downsampleTo`      — LTTB target rows per series (server-side default 500
//                           when null, hard cap 5000 — see
//                           CrossDoBulkDataRest.HARD_MAX_DOWNSAMPLE)
//
// Pattern mirrors V100 / V101 (six built-in casual templates) — same merge key
// shape, same `createdBy='system'`, same `retired=false`, same source-rollback handle.
//
// Idempotency: MERGE on {name, version} — re-running is a no-op.
// Rollback: V102_R__ deletes only rows with `source = 'V102-builtin'`,
// so user-authored templates with colliding names are untouched.
//
// Operator runbook:
//   Run via: cypher-shell -u neo4j -p <password> -f V102__Cross_ply_tcp_temperature_view_recipe_seed.cypher
//   Rollback: V102_R__Cross_ply_tcp_temperature_view_recipe_seed.cypher
//   Verify:   MATCH (t:ShepardTemplate {source: 'V102-builtin'}) RETURN t.name, t.templateKind, t.retired;
//             → should return 1 row; retired=false.
//
// aidocs/16 TPL-VIEW-CROSS-PLY-TCP-1; aidocs/34 V102 row.

MERGE (t:ShepardTemplate {name: 'Cross-ply TCP temperature', version: 1})
ON CREATE SET
  t.appId         = randomUUID(),
  t.templateKind  = 'VIEW_RECIPE',
  t.description   = 'Cross-DataObject small-multiples view of TCP (tool-centre-point) temperature for the AFP layup process. Resolves each DataObject to its TimeseriesReference whose channel carries the `urn:shepard:afp:tcp-temperature-c` annotation predicate, then renders one downsampled trace per DO in a 4-column grid. Use to compare TCP temperature across plies, tracks, or full layup campaigns at a glance.',
  t.body          = '{"view":{"kind":"small-multiples","channelPredicate":"urn:shepard:afp:tcp-temperature-c","axisGrouping":"urn:shepard:mffd:ply-number","downsampleTo":500},"renderer":"small-multiples"}',
  t.tags          = ['view-recipe', 'small-multiples', 'cross-track', 'mffd', 'afp', 'tcp-temperature'],
  t.createdBy     = 'system',
  t.createdAt     = timestamp(),
  t.updatedAt     = timestamp(),
  t.retired       = false,
  t.source        = 'V102-builtin'
;
