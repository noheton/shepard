// Rollback for V77__drop_dead_timeseries_text_indexes.cypher
//
// Restores the three Timeseries TEXT indexes dropped by V77.
// Safe to re-run: CREATE TEXT INDEX … IF NOT EXISTS is idempotent.
//
// Note: these indexes were confirmed zero-read (lastRead=NULL) at the time of
// the V77 drop. Restoring them will restore write-amplification cost.
// Only roll back if a query pattern genuinely requires TEXT search on these
// properties; if so, also add a corresponding test and update aidocs/16 NEO-AUDIT-011.

CREATE TEXT INDEX idx_Timeseries_attr_field IF NOT EXISTS
FOR (n:Timeseries) ON (n.field);

CREATE TEXT INDEX idx_Timeseries_attr_location IF NOT EXISTS
FOR (n:Timeseries) ON (n.location);

CREATE TEXT INDEX idx_Timeseries_attr_symbolic_name IF NOT EXISTS
FOR (n:Timeseries) ON (n.symbolicName);
