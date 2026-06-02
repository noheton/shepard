// COLL-SCENE-1 — INTENTIONALLY EMPTY (NOOP).
//
// :Collection gains a new optional `sceneGraphAppId` string property
// linking the Collection to a :DigitalTwinScene that renders as the
// hero scene-graph on its detail page (MFFD robot cell, LUMEN test
// bench, etc. — see aidocs/agent-findings/mffd-feature-gaps-2026-06-02.md
// GAP-6).
//
// Neo4j is schema-less on properties, so the additive nullable
// property needs no DDL change. Existing :Collection rows simply lack
// the property; OGM reads the absence as null. A null value means
// "no hero scene linked"; the frontend renders a "Link scene-graph"
// affordance to writers instead of the URDF viewer.
//
// The link is intentionally scalar (app-level appId pointer), not an
// OGM graph edge — keeping :Collection loose-coupled with the v2
// scene-graph package. The scene's permission walk
// (SCENEGRAPH-PERMS-1, scene → FileReference → DataObject →
// Collection) stays the source-of-truth for who can read the scene;
// the Collection→Scene back-pointer surfaces only a render affordance
// on the Collection detail page, and does NOT widen scene
// readability (cf. agent advisor note 2026-06-02: hand-built scenes
// must NOT become viewable by anyone who can write to a Collection
// they happen to be linked from).
//
// REST surface:
//   GET    /v2/collections/{appId}/scene-graph   — resolve linked scene
//   PUT    /v2/collections/{appId}/scene-graph   — link / replace
//   DELETE /v2/collections/{appId}/scene-graph   — unlink (does NOT
//                                                    delete the scene)
//
// Permission gate:
//   GET    — AccessType.Read on Collection
//   PUT    — AccessType.Write on Collection AND
//            SceneGraphPermissionService.isAllowed(Read) on the target
//            scene (two-sided gate per the advisor 2026-06-02 — a
//            writer on Collection A must not blind-link a private
//            scene B they cannot themselves read).
//   DELETE — AccessType.Write on Collection
//
// Operator runbook: no action required. To inspect Collections
// carrying a hero scene link:
//   MATCH (c:Collection) WHERE c.sceneGraphAppId IS NOT NULL
//   RETURN c.appId, c.name, c.sceneGraphAppId
//   ORDER BY c.name LIMIT 50;
//
// To unlink in bulk (e.g. after wiping the scene catalogue):
//   MATCH (c:Collection) WHERE c.sceneGraphAppId IS NOT NULL
//   REMOVE c.sceneGraphAppId;
//
// Rollback: V102_R__Collection_scene_graph_link.cypher
RETURN 1;
