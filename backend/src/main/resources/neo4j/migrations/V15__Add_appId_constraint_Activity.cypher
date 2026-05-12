// PROV1a — activity-log overhaul (PROV-O alignment). Adds the same
// uniqueness-only `appId` constraint to the new `Activity` label that
// V11 added to the protected node-entity labels (see aidocs/25 §2
// and the preceding constraint additions in V11 + V13).
//
// Single statement, idempotent — safe to re-run on stacks where it
// already applied. Mirrors the V13 (Role) shape.
//
// Activity is born here; no backfill of legacy data needed. The
// capture filter (see ProvenanceCaptureFilter) lands rows from
// the next 2xx mutation onward.
CREATE CONSTRAINT appId_unique_Activity IF NOT EXISTS FOR (n:Activity) REQUIRE n.appId IS UNIQUE;
