// COLL-SCENE-1 ROLLBACK — clear the :Collection.sceneGraphAppId
// property from every existing :Collection row.
//
// Companion to V102__Collection_scene_graph_link.cypher (NOOP forward
// migration documenting the additive nullable property). The rollback
// removes any values written via the new
// /v2/collections/{appId}/scene-graph PUT endpoint while the property
// was in service. Existing :Collection nodes without the property are
// silently no-op'd (REMOVE on an absent property is idempotent).
MATCH (c:Collection)
WHERE c.sceneGraphAppId IS NOT NULL
REMOVE c.sceneGraphAppId;

RETURN 1;
