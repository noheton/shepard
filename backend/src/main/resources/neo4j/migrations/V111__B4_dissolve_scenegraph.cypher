// V2CONV-B4 — dissolve the bespoke scene-graph stored graph.
//
// The bespoke `/v2/scene-graphs/*` namespace and its stored frames/joints
// graph converge into the generic MAPPING_RECIPE mechanism (decision #2 of
// aidocs/platform/191-v2-surface-convergence.md). The URDF FileReference is now
// the single source of truth for the kinematic tree; the tree is parsed on
// demand by the SceneGraphPlay TransformExecutor (vis-trace3d plugin), never
// materialised into Neo4j. So the stored scene graph is removed.
//
// What this migration does (idempotent, fail-fast):
//   1. DETACH DELETE every :DigitalTwinScene / :CoordinateFrame / :Joint node —
//      this also removes their edges [:HAS_FRAME], [:HAS_PARENT_FRAME],
//      [:JOINT_PARENT], [:JOINT_CHILD], [:HAS_JOINT].
//   2. Clear the :Collection.sceneGraphAppId hero-link property where it still
//      points at a (now-deleted) scene. Collections whose hero link already
//      points at a MAPPING_RECIPE template appId are left untouched — the
//      property is reused unchanged by V2CONV-B4 (see CollectionHeroViewLinkDAO).
//      We cannot distinguish "scene appId" from "template appId" purely by
//      shape (both UUID v7), so we clear ONLY the values that matched a scene
//      we just deleted; to do that safely we capture the deleted scene appIds
//      first.
//
// There was never a [:HERO_SCENE] relationship edge — the hero link is and
// always was a scalar :Collection.sceneGraphAppId property (V104 NOOP note), so
// no edge removal is needed.
//
// Idempotent: re-running after the nodes are gone matches nothing and is a
// no-op. DROP CONSTRAINT ... IF EXISTS is a no-op when already dropped.
//
// Fail-fast: aborts startup on error per MigrationsRunner's post-A1e posture.
//
// Operator runbook: no action required. To verify post-migration:
//   MATCH (n:DigitalTwinScene) RETURN count(n);  // expect 0
//   MATCH (n:CoordinateFrame)  RETURN count(n);  // expect 0
//   MATCH (n:Joint)            RETURN count(n);  // expect 0
//
// Rollback: V111_R__B4_dissolve_scenegraph.cypher (re-creates the appId
// uniqueness constraints only — the deleted scene DATA is not recoverable from
// the graph, which is the intended one-way convergence; operators who must
// retain old scenes should snapshot before upgrading).
//
// BREAKING (v2 surface): the `/v2/scene-graphs/*` CRUD + export endpoints and
// `POST /v2/scene-graphs/from-urdf/{appId}` are removed. See aidocs/34.

// 1 — capture the scene appIds we are about to delete, then clear any
//     Collection hero link that still points at one of them.
MATCH (s:DigitalTwinScene)
WITH collect(s.appId) AS deletedSceneAppIds
MATCH (c:Collection)
WHERE c.sceneGraphAppId IS NOT NULL
  AND c.sceneGraphAppId IN deletedSceneAppIds
REMOVE c.sceneGraphAppId;

// 2 — DETACH DELETE the stored scene graph (nodes + their edges).
MATCH (n:DigitalTwinScene) DETACH DELETE n;
MATCH (n:CoordinateFrame)  DETACH DELETE n;
MATCH (n:Joint)            DETACH DELETE n;

// 3 — drop the appId uniqueness constraints those entities added in V95.
DROP CONSTRAINT appId_unique_DigitalTwinScene IF EXISTS;
DROP CONSTRAINT appId_unique_CoordinateFrame IF EXISTS;
DROP CONSTRAINT appId_unique_Joint IF EXISTS;
