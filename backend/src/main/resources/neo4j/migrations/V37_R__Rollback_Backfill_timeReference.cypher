// V37_R — Rollback: remove timeReference property from TimeseriesReference nodes
// that were set to 'WALL_CLOCK' by V37.
//
// Only strips nodes whose timeReference == 'WALL_CLOCK' to avoid removing
// values written after V37 ran (e.g. 'EXPERIMENT_RELATIVE' set by users).
// Idempotent: WHERE ensures only backfilled values are removed.
MATCH (r:TimeseriesReference)
WHERE r.timeReference = 'WALL_CLOCK'
CALL { WITH r REMOVE r.timeReference } IN TRANSACTIONS OF 10000 ROWS;
