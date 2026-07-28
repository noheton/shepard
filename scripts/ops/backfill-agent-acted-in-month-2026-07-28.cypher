// =============================================================================
// OPERATOR OPS SCRIPT — NOT a startup migration. Run MANUALLY, offline.
// ACTIVITY-SUPERNODE-BACKFILL  (aidocs/16)  —  2026-07-28
// =============================================================================
//
// WHAT: Backfill the historical (:User)-[:agent_acted_in_month {ym}]->(:Activity)
//       edges for Activities created BEFORE the write-path started minting them
//       (commit e7d2c7219). New Activities already get this edge via an O(1)
//       guarded CREATE in ActivityDAO.writeAgentActedInMonth().
//
// WHY NOT A MIGRATION: On 2026-07-19 the naive in-CALL `MATCH…MERGE` form of
//       this backfill hung backend startup for 7 min with 0 commits — MERGE on
//       the ~3.3M-degree service :User supernode is O(degree)/row (it scans the
//       node's outgoing agent_acted_in_month rels to check existence) AND takes
//       an exclusive lock on :User per row. Heavy backfills DO NOT belong in the
//       fail-fast startup MigrationsRunner. This runs as a standalone, offline,
//       batched, resumable operation. V121 shipped the INDEX only (index-only,
//       write-path CREATE); this file is the deferred historical edge backfill.
//
// GROUND TRUTH (measured 2026-07-28, substrate-direct):
//       Activities total ............................. 3,332,648
//       Activities WITH agent edge (write-path) ......   313,567
//       Activities WITH WAS_ASSOCIATED_WITH ..........  3,320,027
//       BACKFILLABLE (has WAS_ASSOCIATED_WITH, no agent edge) .. 3,008,314
//       (~12,621 Activities have NO user association at all — importer
//        write-path "bare" rows — they are correctly SKIPPED: no agent to
//        attribute. This is why the driving MATCH requires WAS_ASSOCIATED_WITH.)
//
// SAFETY PROOF (PROFILE, read-only proxy, 2026-07-28):
//       Activity-side guard `WHERE NOT (a)<-[:agent_acted_in_month]-(u)`:
//         200 candidate rows  ->  1,208 DbHits  (~6/row)
//         2,000 candidate rows -> 12,008 DbHits  (~6/row)   => LINEAR, O(1)/row.
//       Contrast the 07-19 TRAP: one :User already has 313,560 outgoing
//         agent_acted_in_month edges = 313,628 DbHits to scan them ONCE. A MERGE
//         that expands from the :User side pays that PER ROW -> O(degree).
//
// HARD PREREQUISITES:
//   1. INGEST MUST BE PAUSED / QUIESCED. The per-row exclusive lock on the
//      :User supernode is uncontended only with a single writer. Under the
//      8-worker ingest this deadlocks (AGENT-EDGE-DEADLOCK).
//   2. V121 index `agent_acted_in_month_ym_idx` must exist (it does; verify below).
//   3. APOC available (5.26.26 confirmed) — used only for `apoc.temporal.format`.
//
// IDEMPOTENT: the `WHERE NOT (a)<-[:agent_acted_in_month]-(u)` guard makes every
//   re-run skip already-linked Activities. Safe to re-run after an ON ERROR FAIL
//   abort — committed batches persist, the next run resumes from the remainder.
//
// FAIL-FAST: `CALL {} IN TRANSACTIONS` defaults to ON ERROR FAIL — the failing
//   batch rolls back and execution stops; prior committed batches survive.
//
// ROLLBACK: additive edge-only. To fully undo, run V121_R__ (drops the index and
//   batch-deletes all agent_acted_in_month rels) — but note that also removes the
//   write-path edges, so only do so if reverting the whole feature.
//
// RUN:
//   NEOPW=$(docker inspect infrastructure-neo4j-1 | grep -oE 'NEO4J_AUTH=[^"]+' | head -1 | cut -d/ -f2)
//   docker exec -i infrastructure-neo4j-1 cypher-shell -u neo4j -p "$NEOPW" \
//     -f /path/to/backfill-agent-acted-in-month-2026-07-28.cypher
//
// EXPECTED WALL TIME (offline, single writer): ~3.0M edges / batches of 10k =
//   ~300 batches. Rough est. minutes-to-low-tens-of-minutes on this box; monitor
//   with the progress query at the foot of this file from a second shell.
// =============================================================================

// --- Preflight: verify the index exists (do not proceed without it) ----------
SHOW INDEXES YIELD name WHERE name = 'agent_acted_in_month_ym_idx'
RETURN name AS indexPresent;

// --- The backfill ------------------------------------------------------------
// Drive from the BOUNDED Activity side (a's incoming agent-edge degree <= 1).
// CREATE (never MERGE) so we never scan the :User supernode's edge list.
// ym derived exactly as the Java write-path: UTC year-month, 6-char "yyyyMM".
//
// !! BATCHING NOTE !! The driving MATCH is OUTSIDE the CALL so `CALL {} IN
// TRANSACTIONS` receives ~3M input rows and commits every 10,000. If the MATCH
// were INSIDE the CALL, the subquery would get ONE implicit row -> run ONCE ->
// all ~3M CREATEs in a single transaction = the exact non-streaming, 0-commits
// shape of the 07-19 hang. Keep the MATCH before the CALL.
// !! PLANNER NOTE (corrected 2026-07-28 after PROFILE on live data) !!
// The single-pattern form `MATCH (a:Activity)-[:WAS_ASSOCIATED_WITH]->(u:User)`
// did NOT drive from the Activity side despite the intent above: the COST
// planner chose `NodeByLabelScan(u:User)` + `Expand(All)` over the :User
// supernode's ~3.3M WAS_ASSOCIATED_WITH edges, then an `AntiSemiApply` with
// `Expand(Into)` for the guard. Measured: 2,183,547 db-hits to yield 200 rows
// (and 2,197,047 for 2,000) — a ~2.18M FIXED cost, i.e. the very supernode-driven
// shape this script exists to avoid.
// Splitting the pattern — filter Activities FIRST, then expand to the user —
// forces the bounded drive: 995 db-hits @200 rows, 9,067 @2,000 (~4.5/row,
// linear). That is a ~2,200x reduction on the driving query.
// The guard uses the anonymous `-()` form: verified 0 Activities have more than
// one WAS_ASSOCIATED_WITH user, so it is semantically identical and cheaper.
MATCH (a:Activity)
WHERE a.startedAtMillis IS NOT NULL
  AND NOT (a)<-[:agent_acted_in_month]-()
WITH a
MATCH (a)-[:WAS_ASSOCIATED_WITH]->(u:User)
WITH u, a, apoc.temporal.format(datetime({epochMillis: a.startedAtMillis}), 'yyyyMM') AS ym
CALL {
  WITH u, a, ym
  CREATE (u)-[:agent_acted_in_month {ym: ym}]->(a)
} IN TRANSACTIONS OF 10000 ROWS ON ERROR FAIL;

// --- Post-check: backfillable remainder should be 0 --------------------------
MATCH (a:Activity)
WHERE a.startedAtMillis IS NOT NULL AND NOT (a)<-[:agent_acted_in_month]-()
WITH a MATCH (a)-[:WAS_ASSOCIATED_WITH]->(:User)
RETURN count(a) AS remainingBackfillable;   // expect 0

// -----------------------------------------------------------------------------
// PROGRESS MONITOR (run from a SECOND shell while the backfill is in flight):
//   MATCH (:User)-[r:agent_acted_in_month]->(:Activity) RETURN count(r);
//   -- climbs from 313,567 toward ~3,321,881 (313,567 + 3,008,314).
//
// APOC ALTERNATIVE (equivalent; parallel:false is MANDATORY — parallel:true
// deadlocks N threads on the one :User exclusive lock = AGENT-EDGE-DEADLOCK):
//   CALL apoc.periodic.iterate(
//     "MATCH (a:Activity)-[:WAS_ASSOCIATED_WITH]->(u:User)
//      WHERE a.startedAtMillis IS NOT NULL AND NOT (a)<-[:agent_acted_in_month]-(u)
//      RETURN u, a",
//     "CREATE (u)-[:agent_acted_in_month {ym: apoc.temporal.format(datetime({epochMillis: a.startedAtMillis}),'yyyyMM')}]->(a)",
//     {batchSize:10000, parallel:false}
//   );
// =============================================================================
