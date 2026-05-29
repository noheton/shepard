// DT1-PHASE-0 — entity scaffold for :DigitalTwinScene + :CoordinateFrame + :Joint.
//
// Adds uniqueness constraints on appId for the three new node entities
// shipped by DT1-PHASE-0. The constraints both:
//   1. Prevent accidental duplicate nodes (e.g. a race between two
//      application pods seeding the same scene on first parse).
//   2. Create implicit indexes that make appId-keyed lookups fast in
//      the not-yet-shipped SCENEGRAPH-REST-1 read path.
//
// No filterable property indexes ship in V95. The design doc
// `aidocs/data/85 §9` proposes `INDEX CoordinateFrame_name`; that index
// is deferred until SCENEGRAPH-REST-1 introduces the list endpoint that
// actually filters by name — a `CREATE INDEX IF NOT EXISTS` is cheap to
// add later in V96+ as an additive layer.
//
// Idempotent: CREATE CONSTRAINT ... IF NOT EXISTS is a no-op when the
// constraint already exists. Safe to re-run.
//
// Rollback: V95_R__DT1_Phase0_rollback.cypher
//
// Operator runbook: see the PR body for `dt1-phase-0-scaffold` (no
// runbook page yet — the operator-visible surface ships with
// SCENEGRAPH-REST-1).
//
// Planned graph edges (NOT created in this migration — the entity
// scaffold ships pure-data; relationship-edge writes belong to
// SCENEGRAPH-REST-1's service layer):
//   (:DigitalTwinScene)-[:HAS_FRAME]->(:CoordinateFrame)
//   (:DigitalTwinScene)-[:HAS_JOINT]->(:Joint)
//   (:CoordinateFrame)-[:HAS_PARENT_FRAME]->(:CoordinateFrame)
//   (:Joint)-[:JOINT_PARENT]->(:CoordinateFrame)
//   (:Joint)-[:JOINT_CHILD]->(:CoordinateFrame)
// The entities carry the scalar appId pointers (rootFrameAppId,
// parentFrameAppId, parentFrameAppId/childFrameAppId) so the edges can
// be reconstructed lazily by the consumer row.
//
// Aborts startup on error per MigrationsRunner's fail-fast posture.

CREATE CONSTRAINT appId_unique_DigitalTwinScene IF NOT EXISTS
  FOR (n:DigitalTwinScene)
  REQUIRE n.appId IS UNIQUE;

CREATE CONSTRAINT appId_unique_CoordinateFrame IF NOT EXISTS
  FOR (n:CoordinateFrame)
  REQUIRE n.appId IS UNIQUE;

CREATE CONSTRAINT appId_unique_Joint IF NOT EXISTS
  FOR (n:Joint)
  REQUIRE n.appId IS UNIQUE;
