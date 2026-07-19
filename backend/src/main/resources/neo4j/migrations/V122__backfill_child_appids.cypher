// APPID-CHILD-MINT-REGRESSION — Re-backfill NULL appId on cascade-child nodes
//   (:Timeseries and :ShepardFile)
//
// Context (aidocs/agent-findings/db-ap2-cross-cutting-2026-07-18.md, findings #1/#2):
//   Every persisted Shepard entity must carry a single stable shepardId (`appId`,
//   UUID v7) — the only key that crosses substrate boundaries. Two node kinds
//   violated this AT SCALE on the live graph (DB-AP2, 2026-07-18):
//     * :ShepardFile  — 0 of 505,759 carried an appId
//     * :Timeseries   — 0 of 198     carried an appId
//   (:Timeseries here is the ReferencedTimeseriesNodeEntity payload node hanging
//    off a :TimeseriesReference; NOT the Postgres `timeseries` row.)
//
//   Root cause: both are ONLY ever persisted as CASCADED CHILDREN of a parent
//   (`FileGroup`/`FileBundleReference`/`SingletonFileReference` for :ShepardFile,
//   `TimeseriesReference` for :Timeseries) via
//   `GenericDAO.createOrUpdate(parent)` → `session.save(parent, DEPTH_ENTITY)`.
//   `createOrUpdate` mints appId only on its TOP-LEVEL argument, so the cascaded
//   children were persisted un-minted. Earlier backfills (V82 for :Timeseries,
//   V12 for the file layer) fixed a point-in-time snapshot, but the un-fixed
//   write path re-manufactured NULLs on every re-ingest — the identical shape of
//   the providerId backfill-without-write-path-fix that V34/V79 documented.
//
//   The write path is now fixed IN THE SAME PR as this migration:
//     * :ShepardFile — minted at the single choke point
//       `FileStorageService.storeFile` (beside the providerId stamp), plus the
//       existing mints in `FileContainerService.createFileImpl`/`commitUpload`.
//     * :Timeseries  — minted in both value constructors of
//       `ReferencedTimeseriesNodeEntity` (OGM hydrates loaded rows through the
//       no-arg ctor + reflection, so value-ctor minting is write-only-safe).
//   NEW rows therefore carry a v7 appId; this migration mops up the LEGACY NULLs.
//
// UUID VERSION CAVEAT (v4, not v7 — deliberate, documented):
//   Cypher `randomUUID()` produces UUID **v4**; the fork mints **v7** via the
//   Java `AppIdGenerator`. A pure-Cypher backfill cannot mint v7. We accept v4
//   for these ~505,957 legacy backfilled rows — identical to the V82 (:Timeseries)
//   and V113 (:SpatialData*) precedent. Both v4 and v7 are globally-unique and
//   satisfy the appId uniqueness constraint; the v7 mandate only buys time-sortable
//   locality, irrelevant for a one-shot legacy backfill. The v7 invariant HOLDS
//   for all NEW rows via the write-path fix above. (A wire-visible v4→v7 re-mint of
//   the channel identity is tracked separately — DB-AP2 finding #5 — as it needs
//   its own migration window.)
//
// Safety on a LIVE graph (~505k rows, heavy):
//   Batched via `CALL { ... } IN TRANSACTIONS OF 1000 ROWS` so no single
//   transaction holds all 505k writes — bounds heap + lock footprint and keeps
//   the operation safe to run at startup. This runner uses
//   TransactionMode.PER_STATEMENT (see MigrationsRunner); neo4j-migrations 4.1.2
//   executes `IN TRANSACTIONS` statements in autocommit, exactly as the applied
//   sibling V82__mint_timeseries_appids.cypher does.
//
// Idempotent: the `WHERE n.appId IS NULL` guard skips already-minted rows, so a
//   re-run (or a fresh install where the count is already 0) is a safe no-op.
// Fail-fast: MigrationsRunner propagates MigrationsException → startup aborts on
//   any error (per CLAUDE.md migration rules).
// Rollback: V122_R__backfill_child_appids.cypher
//
// Operator runbook — verify before/after with cypher-shell:
//   MATCH (t:Timeseries)  WHERE t.appId IS NULL RETURN count(t);   // expect 198 → 0
//   MATCH (f:ShepardFile) WHERE f.appId IS NULL RETURN count(f);   // expect 505759 → 0
//   On a fresh install both counts are already 0 — the migration is then a no-op.

CALL {
  MATCH (t:Timeseries)
  WHERE t.appId IS NULL
  SET t.appId = randomUUID()
} IN TRANSACTIONS OF 1000 ROWS;

CALL {
  MATCH (f:ShepardFile)
  WHERE f.appId IS NULL
  SET f.appId = randomUUID()
} IN TRANSACTIONS OF 1000 ROWS;
