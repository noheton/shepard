// DT1-PHASE-0 rollback — drops the three appId uniqueness constraints
// AND destructively removes every :DigitalTwinScene, :CoordinateFrame,
// and :Joint node introduced by DT1-PHASE-0.
//
// IRREVERSIBLE FOR DATA. Run only when downgrading to a version prior
// to DT1-PHASE-0. The v1 rollback policy here is "we never shipped any
// production data" — DT1-PHASE-0 ships scaffold only; no service-layer
// caller exists yet (SCENEGRAPH-REST-1 has not landed). If real scene
// data exists in this graph, this is the wrong rollback path — abort
// the downgrade and ship a forward migration instead.
//
// Operator runbook: see the DT1-PHASE-0 PR body. The full operator-
// facing runbook lands with SCENEGRAPH-REST-1.
//
// Safe to re-run: DROP CONSTRAINT ... IF EXISTS is a no-op when the
// constraint is already absent; the DETACH DELETE branches are
// idempotent (zero rows after the first run).
//
// Order matters: drop constraints first so the subsequent node deletes
// do not fail on a stale unique-index reference.

DROP CONSTRAINT appId_unique_DigitalTwinScene IF EXISTS;
DROP CONSTRAINT appId_unique_CoordinateFrame IF EXISTS;
DROP CONSTRAINT appId_unique_Joint IF EXISTS;

MATCH (s:DigitalTwinScene) DETACH DELETE s;
MATCH (f:CoordinateFrame) DETACH DELETE f;
MATCH (j:Joint) DETACH DELETE j;
