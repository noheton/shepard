// APPID-CHILD-MINT-REGRESSION ROLLBACK — Remove backfilled appId from
//   :Timeseries and :ShepardFile cascade-child nodes.
//
// WARNING: this rollback removes `appId` from ALL :Timeseries and :ShepardFile
//   nodes that currently carry one — it CANNOT distinguish a V122-backfilled
//   value from a value minted by the (now-fixed) write path. Run this ONLY on an
//   instance where you know no such node had an appId before V122 executed (e.g.
//   to fully revert V122 on a fresh/test install). On a production instance where
//   new files/timeseries have been written since V122, this rollback would strip
//   legitimate write-path-minted appIds and re-open the DB-AP2 identity gap — do
//   not run it there.
//
// After rollback, rows that were appId-less before V122 are appId-less again.
//
// Batched + idempotent, mirroring V82_R__mint_timeseries_appids.cypher: the
//   `WHERE n.appId IS NOT NULL` guard makes a re-run a safe no-op, and the
//   `IN TRANSACTIONS` batching bounds the transaction footprint on the ~505k-row
//   :ShepardFile population.

CALL {
  MATCH (t:Timeseries)
  WHERE t.appId IS NOT NULL
  REMOVE t.appId
} IN TRANSACTIONS OF 1000 ROWS;

CALL {
  MATCH (f:ShepardFile)
  WHERE f.appId IS NOT NULL
  REMOVE f.appId
} IN TRANSACTIONS OF 1000 ROWS;
