// NEO-AUDIT-001 — Backfill PROV-O edges on existing `:Activity` nodes
//
// Context:
//   284,530 Activity nodes exist with ZERO incoming or outgoing edges.
//   Activity.java documented (:User)-[:WAS_ASSOCIATED_WITH]->(:Activity) +
//   :USED / :GENERATED as "not yet wired" — they were never wired.
//   This migration backfills all three edges for existing rows.
//   ProvenanceService.record() + ActivityDAO.wireEdges() now wire them
//   for all new Activity rows going forward (NEO-AUDIT-001 forward-fix).
//
// PROV-O canonical directions (OUTGOING from Activity — W3C spec):
//   (:Activity)-[:WAS_ASSOCIATED_WITH]->(:User)
//   (:Activity)-[:GENERATED]->(:BasicEntity)  — for CREATE actions
//   (:Activity)-[:USED]->(:BasicEntity)        — for READ/UPDATE/DELETE/EXECUTE
//
// Idempotent: all three CALL blocks use MERGE, safe to re-run.
//
// Performance:
//   Index prerequisites (created below) must exist before the CALL blocks run.
//   Each CALL block is batched in TRANSACTIONS OF 1000 ROWS to avoid heap
//   pressure on large installs. On a 284k-row dataset expect ~285 transactions
//   per block, each taking < 5 ms on warmed Neo4j 5.x → ~6s total runtime.
//
// Rollback: V77_R__backfill_activity_prov_edges.cypher
//   (removes all three relationship types from :Activity nodes)
//
// Operator runbook: index build is online in Neo4j 5.x (no downtime).
//   Run from cypher-shell:
//     cypher-shell -u neo4j -p <pwd> -f V77__backfill_activity_prov_edges.cypher
//   Or let Flyway pick it up automatically on next startup.
//   Monitor progress: MATCH (a:Activity) WHERE NOT (a)-[:WAS_ASSOCIATED_WITH]->()
//                     RETURN count(a);

// --- Index prerequisites -----------------------------------------------
// Activity.targetAppId: needed by the GENERATED/USED lookup below.
// (Activity.startedAtMillis is already indexed by V75;
//  BasicEntity.appId is already indexed by V76.)

CREATE INDEX Activity_targetAppId_idx IF NOT EXISTS
FOR (a:Activity) ON (a.targetAppId);

CREATE INDEX Activity_agentUsername_idx IF NOT EXISTS
FOR (a:Activity) ON (a.agentUsername);

// --- Edge 1: WAS_ASSOCIATED_WITH → User --------------------------------
// Wire for every Activity whose agentUsername maps to a known :User node.
// Activities predating User cleanup or anonymous actions (no username) are
// silently skipped by the MATCH guard.

CALL {
  MATCH (a:Activity)
  WHERE a.agentUsername IS NOT NULL
    AND NOT (a)-[:WAS_ASSOCIATED_WITH]->()
  MATCH (u:User {username: a.agentUsername})
  MERGE (a)-[:WAS_ASSOCIATED_WITH]->(u)
} IN TRANSACTIONS OF 1000 ROWS;

// --- Edge 2: GENERATED → target BasicEntity (CREATE actions only) ------
// Wire for every Activity with actionKind = 'CREATE' and a non-null
// targetAppId that resolves to a living :BasicEntity node.

CALL {
  MATCH (a:Activity)
  WHERE a.actionKind = 'CREATE'
    AND a.targetAppId IS NOT NULL
    AND NOT (a)-[:GENERATED]->()
  MATCH (e:BasicEntity {appId: a.targetAppId})
  MERGE (a)-[:GENERATED]->(e)
} IN TRANSACTIONS OF 1000 ROWS;

// --- Edge 3: USED → target BasicEntity (non-CREATE actions) ------------
// Wire for READ, UPDATE, DELETE, EXECUTE where targetAppId is known.

CALL {
  MATCH (a:Activity)
  WHERE a.actionKind <> 'CREATE'
    AND a.targetAppId IS NOT NULL
    AND NOT (a)-[:USED]->()
  MATCH (e:BasicEntity {appId: a.targetAppId})
  MERGE (a)-[:USED]->(e)
} IN TRANSACTIONS OF 1000 ROWS;
