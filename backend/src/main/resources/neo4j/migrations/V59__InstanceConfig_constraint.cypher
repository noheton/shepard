// V59 — appId uniqueness on :InstanceConfig (PR-3 of the SHACL
// changeover, brief 2026-05-22). Idempotent + fail-fast per the
// MigrationsRunner contract (CLAUDE.md "Always: maintain the
// upstream upgrade path" §3).
//
// What this migration does:
//   - Creates the appId uniqueness constraint on :InstanceConfig
//     (the singleton entity carrying the per-instance HMAC signing
//     key for the provenance audit chain).
//
// What this migration does NOT do:
//   - It does NOT seed the singleton. Seeding runs at
//     application-startup via InstanceConfigService.onStart()
//     (StartupEvent observer) so the fresh ${SHEPARD_INSTANCE_SECRET}
//     can be read from environment rather than baked into Cypher.
//   - It does NOT lift any SHACL predicates to dedicated columns or
//     edges. That's PR-5 of the SHACL changeover scope — deferred
//     to a separate migration when the predicate-list and fixtures
//     are agreed.
//
// Operator runbook:
//   - This migration is safe to re-run. `IF NOT EXISTS` makes it
//     idempotent. Failure mode: the constraint creation succeeds or
//     the migration aborts startup (MigrationsException propagates
//     per A1e).
//
// Companion docs:
//   - aidocs/agent-findings/shacl-changeover-non-ts.md (this slice)
//   - aidocs/semantics/98-mffd-process-shapes.md §Changelog v2-3
//     (the user-facing surface that motivates the chain).
CREATE CONSTRAINT instance_config_appid_unique IF NOT EXISTS
FOR (n:InstanceConfig) REQUIRE n.appId IS UNIQUE;
