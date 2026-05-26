// NEO-AUDIT-003 — Backfill NULL :Timeseries.appId rows
//
// Context:
//   596 :Timeseries nodes have appId IS NULL (confirmed post-MFFD ingest 2026-05-24).
//   The appId_unique_Timeseries constraint + range index already exist (V-era migration).
//   Uniqueness constraints permit NULL; the constraint is satisfied but meaningless.
//   This migration mints random UUIDs (UUID-v4 via Cypher randomUUID()) for all NULL rows.
//
// NOTE: UUID-v4 (not UUID-v7) for backfill. New Timeseries rows receive UUID-v7 appIds
//   from the Minter service on creation; backfill uses randomUUID() which is UUID-v4.
//   Both satisfy the uniqueness constraint; the distinction matters only for sort-by-time
//   semantics which is irrelevant for backfilled rows.
//
// Idempotent: WHERE clause guards against re-processing already-minted rows.
// Batched to avoid heap pressure on large installs.
// Rollback: V82_R__mint_timeseries_appids.cypher
//
// Operator runbook: run `MATCH (t:Timeseries) WHERE t.appId IS NULL RETURN count(t)`
//   before and after to verify. Expected: 596 → 0.

CALL {
  MATCH (t:Timeseries)
  WHERE t.appId IS NULL
  SET t.appId = randomUUID()
} IN TRANSACTIONS OF 500 ROWS;
