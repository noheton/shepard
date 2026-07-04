// V2CONV-B5 — dissolve the bespoke KRL interpret subsystem.
//
// The bespoke `POST /v2/krl/interpret` endpoint, its `KrlInterpretRequestIO` /
// `KrlInterpretResponseIO` shapes, and its `:KrlInterpretActivity` typed
// provenance node converge into the generic MAPPING_RECIPE mechanism
// (decision #2 of aidocs/platform/191-v2-surface-convergence.md, the
// transform-direction sibling of V2CONV-B4's scene-graph dissolution).
//
// KRL interpret is now a MAPPING_RECIPE template targeting the
// `KrlTrajectoryShape` IRI, materialized through
// `POST /v2/mappings/{templateAppId}/materialize` by the in-tree
// `KrlTrajectoryTransformExecutor`. The executor records a
// `:KrlTrajectoryActivity` (the converged successor label).
//
// What this migration does (idempotent, fail-fast):
//   1. RELABEL every existing `:KrlInterpretActivity` node to
//      `:KrlTrajectoryActivity`. The base `:Activity` label + all PROV-O edges
//      (WAS_ASSOCIATED_WITH / USED / GENERATED) + every KRL-specific property
//      (srcFileAppId, urdfFileAppId, interpreterVersion, ik*) are PRESERVED —
//      the audit trail is referenced data and must never be deleted
//      (CLAUDE.md "INTEGRITY: referenced data infinite retention"). This is a
//      pure label rename: the historical interpret activities keep their full
//      shape and stay queryable under the new discriminator label.
//
// The `:KrlInterpretActivity` had no appId-uniqueness constraint of its own
// (appId is inherited from `:Activity`), so there is no constraint to drop.
//
// Idempotent: re-running after the relabel matches no `:KrlInterpretActivity`
// nodes and is a no-op. SET a:KrlTrajectoryActivity is itself idempotent.
//
// Fail-fast: aborts startup on error per MigrationsRunner's post-A1e posture.
//
// Operator runbook: no action required. To verify post-migration:
//   MATCH (a:KrlInterpretActivity)  RETURN count(a);  // expect 0
//   MATCH (a:KrlTrajectoryActivity) RETURN count(a);  // expect >= prior count
//
// Rollback: V112_R__B5_dissolve_krl.cypher (relabels back to
// :KrlInterpretActivity — the historical data is fully recoverable because this
// is a pure label rename, not a deletion).
//
// BREAKING (v2 surface): the `POST /v2/krl/interpret` endpoint is removed.
// Callers migrate to the generic MAPPING_RECIPE materialize path. See aidocs/34.

// 1 — relabel the historical interpret activities to the converged label.
//     Pure additive rename; all properties + edges + the :Activity base survive.
MATCH (a:KrlInterpretActivity)
SET a:KrlTrajectoryActivity
REMOVE a:KrlInterpretActivity;
