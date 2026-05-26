// NEO-AUDIT-003 ROLLBACK — Remove backfilled :Timeseries.appId values
//
// WARNING: V82_R removes appId from ALL :Timeseries nodes (cannot distinguish
// V82-minted from pre-existing). Run only on a fresh install where no Timeseries
// rows had appId set before V82 ran.
//
// After rollback, rows that had appId IS NULL before V82 will have appId IS NULL again.
// Any Timeseries rows that coincidentally had appId set before V82 will also lose
// their appId — there is no record of which rows those were.
//
// Only run this rollback if you need to fully revert V82 on an instance where you
// know that no Timeseries rows had appId set prior to V82 executing.
//
// Idempotent.

CALL {
  MATCH (t:Timeseries)
  WHERE t.appId IS NOT NULL
  REMOVE t.appId
} IN TRANSACTIONS OF 500 ROWS;
