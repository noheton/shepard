// V60 — RESERVED slot for the PR-5 SHACL predicate lift (non-TS
// scope, brief 2026-05-22). NO-OP today.
//
// Per the SHACL changeover plan, PR-5 will lift the non-TS-channel-
// touching predicates from the n10s-managed RDF subgraph onto first-
// class Cypher edges where there's a demonstrated query-performance
// or transactional-integrity need. Candidates (see brief decision #5):
//
//   - NCR fields (mffd:NonConformanceReport)
//   - Calibration certificate
//   - NDT gate result
//   - AFP layup metadata (minus the trace-channel reference, which
//     the sibling TS-identity migration agent owns per PR-9)
//   - Welding metadata (minus trace-channel)
//   - AI annotations
//   - Structured / file refs (semantic side only)
//
// EXPLICITLY OUT OF SCOPE: anything that references TS channels by
// the 5-tuple {measurement, device, location, symbolicName, field}.
// Those land in the sibling worktree's PR-9 when the 5-tuple →
// shepardId migration completes.
//
// Why ship the empty migration file rather than wait:
//   1. Reserves the V## slot so the lift PR doesn't collide with
//      whatever migration the next sibling lands next.
//   2. Makes the "this PR is structural foundation, not data lift"
//      story legible to anyone reading the migrations folder.
//   3. The MigrationsRunner is fail-fast (A1e); landing this NOOP
//      proves the file parses and the runner is happy with an empty
//      apply.
//
// Operator runbook: safe to re-run. No graph writes occur.
RETURN 1 AS noop;
