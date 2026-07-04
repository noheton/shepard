// SPATIAL-UNIFY-006 ROLLBACK — Remove backfilled :SpatialDataReference /
// :SpatialDataContainer appId values
//
// WARNING: this rollback removes appId from ALL :SpatialDataReference and
// :SpatialDataContainer nodes — it cannot distinguish V113-minted appIds from
// appIds set by normal node creation. Run ONLY on a fresh install where you
// know no spatial nodes had appId set before V113 ran. On any real install,
// removing appIds breaks the unified /v2/references?kind=spatial surface and
// the in-context promote flow — do not run there.
//
// Idempotent.

// Plain statements (NOT `CALL { ... } IN TRANSACTIONS`) — the migration runner
// executes in an explicit transaction where batched IN TRANSACTIONS is illegal.
MATCH (r:SpatialDataReference)
WHERE r.appId IS NOT NULL
REMOVE r.appId;

MATCH (c:SpatialDataContainer)
WHERE c.appId IS NOT NULL
REMOVE c.appId;
