// SPATIAL-UNIFY-006 — Backfill NULL :SpatialDataReference / :SpatialDataContainer appIds
//
// Context (aidocs/integrations/124 §8):
//   The spatial unification surfaces spatial data through the unified
//   /v2/references?kind=spatial surface. That surface resolves nodes by their
//   `appId`. Every :SpatialDataReference already extends BasicReference (and
//   :SpatialDataContainer extends BasicContainer), so both carry an `appId`
//   minted by GenericDAO.createOrUpdate() on creation and the
//   appId_unique_SpatialDataReference / appId_unique_SpatialDataContainer
//   uniqueness constraints already exist (V11). The [:IS_IN_CONTAINER] edge
//   already mirrors FileReference→FileContainer, so legacy spatial nodes show
//   up in the new "Spatial (N)" tab automatically.
//
//   The only data touch is defensive: any legacy row that predates appId
//   minting (appId IS NULL) would be invisible to the appId-keyed surface.
//   This migration mints a UUID for those rows so they are addressable.
//
// NOTE: randomUUID() yields UUID-v4 (mirrors V82's timeseries backfill). New
//   rows receive UUID-v7 from AppIdGenerator on creation; both satisfy the
//   uniqueness constraint. The distinction only matters for sort-by-time
//   semantics, irrelevant for backfilled rows.
//
// Idempotent: the WHERE appId IS NULL guard skips already-minted rows.
// Batched to avoid heap pressure on large installs.
// Rollback: V113_R__SPATIAL_UNIFY_backfill_spatial_reference_appids.cypher
//
// Operator runbook: verify before/after with
//   MATCH (r:SpatialDataReference) WHERE r.appId IS NULL RETURN count(r)
//   MATCH (c:SpatialDataContainer)  WHERE c.appId IS NULL RETURN count(c)
//   Expected: N → 0 for both. On a fresh install the count is already 0
//   (this migration is a no-op) — that is the expected steady state.

// Plain SET (NOT `CALL { ... } IN TRANSACTIONS`): the neo4j-migrations runner
// executes each statement inside an explicit transaction, where batched
// `IN TRANSACTIONS` is illegal ("can only be executed in an implicit
// transaction"). The targeted set is only legacy NULL-appId rows — rare and
// small — so no batching is needed.
MATCH (r:SpatialDataReference)
WHERE r.appId IS NULL
SET r.appId = randomUUID();

MATCH (c:SpatialDataContainer)
WHERE c.appId IS NULL
SET c.appId = randomUUID();
