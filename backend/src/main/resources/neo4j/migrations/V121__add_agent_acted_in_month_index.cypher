// NEO-AUDIT-2026-07-18-ACTIVITY-SUPERNODE — Time-bucketed Agent index for the
// `:Activity` → `:User` supernode.
//
// Context:
//   A single service `:User` (appId 019ed452-5740-7f08-89f3-54aebc605a6a)
//   carries ~2,874,776 incoming `WAS_ASSOCIATED_WITH` edges from `:Activity`
//   nodes — ~28× Neo4j's ~100k dense-node threshold, and growing ~6.6k/hr
//   during the live MFFD ingest (independently confirmed 2026-07-18,
//   `aidocs/agent-findings/db-antipattern-hunt-2026-07-18.md` finding #1).
//   This is exactly the escalation NEO-AUDIT-2026-05-24-004 predicted.
//
//   Fix (REUSE the shipped NEO-AUDIT-004 pattern; do NOT dump the node — it is
//   structural): add a time-bucketed agent index relationship:
//     (:User)-[:agent_acted_in_month {ym:"YYYYMM"}]->(:Activity)
//   written by ActivityDAO.wireEdges() on every new Activity, alongside the
//   existing WAS_ASSOCIATED_WITH edge. Provenance queries filtered by
//   agent + time then label-scan the bounded `[:agent_acted_in_month]` rel with
//   a `ym` predicate instead of walking the 2.87M-degree supernode — typically
//   100-1000x fewer dbHits.
//
//   Citation: Neo4j supernode KB
//   https://neo4j.com/developer/kb/understanding-the-design-of-supernodes/
//
// ym format: "YYYYMM" (6-char string, e.g. "202607" for July 2026).
//   Matches the Java-side (ActivityDAO.writeAgentActedInMonth):
//   Instant.ofEpochMilli(startedAtMillis).atZone(UTC) → String.format("%04d%02d", y, m).
//   Both sides use UTC so a rel written at write-time and a rel written by this
//   backfill for the same Activity carry an identical `ym` (MERGE deduplicates).
//
// Idempotent: MERGE + `IF NOT EXISTS` throughout — safe to re-run.
// Fail-fast: MigrationsRunner aborts startup on error (propagates MigrationsException).
//
// !!! HEAVY BACKFILL WARNING — read before running !!!
//   This migration backfills ~2.87M existing edges on a LIVE graph. The
//   `MATCH (u:User)<-[:WAS_ASSOCIATED_WITH]-(a:Activity)` clause expands the full
//   supernode once, but the per-row MERGE evaluates from the Activity side
//   (out-degree ~1) and the whole pass is batched in TRANSACTIONS OF 1000 ROWS,
//   so it stays tractable and never holds one giant transaction/lock. Expect on
//   the order of ~2,870 committed batches. Rough runtime at MFFD scale: minutes
//   (single-digit to low tens), dominated by the one-time supernode expansion;
//   subsequent startups re-run it as a near-no-op because every rel already
//   MERGEs to an existing edge. It is safe to run at startup for this reason.
//   If an operator prefers to keep first-boot fast, they may run this file
//   manually from cypher-shell BEFORE deploying the new backend (see runbook),
//   then let the framework record it as applied.
//
// Rollback: V121_R__add_agent_acted_in_month_index.cypher
//   (drops the index and removes all agent_acted_in_month relationships, batched)
//
// Operator runbook: run from cypher-shell:
//   cypher-shell -u neo4j -p <pwd> -f V121__add_agent_acted_in_month_index.cypher
// Or let the Shepard MigrationsRunner pick it up on next startup.
// Monitor backfill progress (in a second session):
//   MATCH (:User)-[r:agent_acted_in_month]->(:Activity) RETURN count(r);
// Should converge to approximately the number of Activities with a resolvable
// agent User and a non-null startedAtMillis.

// --- Range index on the relationship property ----------------------------
// Enables efficient ym-predicated queries:
//   MATCH (u:User {username:$u})-[r:agent_acted_in_month]->(:Activity) WHERE r.ym = "202607"

CREATE RANGE INDEX agent_acted_in_month_ym_idx IF NOT EXISTS
FOR ()-[r:agent_acted_in_month]-()
ON (r.ym);

// --- Backfill existing Activities ----------------------------------------
// For each Activity associated to a `:User` via WAS_ASSOCIATED_WITH with a
// non-null startedAtMillis, compute the ym value from the stored epoch-millis
// timestamp (UTC) and MERGE the bucketed rel from the User to the Activity.
//
// startedAtMillis is stored as Long epoch-millis. Neo4j
// datetime({epochMillis: x}).year / .month extract the UTC components.
//
// Activities with NULL startedAtMillis are skipped (pre-provenance rows / bare
// importer rows, if any) — matching the write-path skip-guard.

CALL {
  MATCH (u:User)<-[:WAS_ASSOCIATED_WITH]-(a:Activity)
  WHERE a.startedAtMillis IS NOT NULL
  WITH u, a,
       right('000' + toString(datetime({epochMillis: a.startedAtMillis}).year), 4) +
       right('0' + toString(datetime({epochMillis: a.startedAtMillis}).month), 2) AS ym
  MERGE (u)-[:agent_acted_in_month {ym: ym}]->(a)
} IN TRANSACTIONS OF 1000 ROWS;
