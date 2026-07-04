// V2CONV-B5 rollback — relabel the converged KRL trajectory activities back to
// the bespoke :KrlInterpretActivity label.
//
// Rollback twin of V112__B5_dissolve_krl.cypher.
//
// Because the forward migration is a PURE LABEL RENAME (no node or property
// deletion), this rollback fully restores the original shape: it relabels
// :KrlTrajectoryActivity nodes back to :KrlInterpretActivity. Every property
// and PROV-O edge is untouched and therefore preserved across both directions.
//
// CAVEAT: a fresh post-V112 KRL interpret (run through the MAPPING_RECIPE
// materialize path) also writes :KrlTrajectoryActivity. Rolling back will
// relabel THOSE too — there is no way to distinguish a pre-migration interpret
// from a post-migration one purely by label. Operators rolling back should
// accept that all KRL trajectory activities (old + new) revert to the bespoke
// label; the audit data itself is never lost.
//
// Idempotent: re-running after the relabel matches no :KrlTrajectoryActivity
// nodes and is a no-op.

MATCH (a:KrlTrajectoryActivity)
SET a:KrlInterpretActivity
REMOVE a:KrlTrajectoryActivity;
