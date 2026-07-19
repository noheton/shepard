// APPID-CHILD-MINT-REGRESSION — child-appId backfill DEFERRED (NOOP migration).
//
// The write-path fix ships in the Java layer (this deploy): `:ShepardFile` is
// minted in FileStorageService.storeFile and `:Timeseries`
// (ReferencedTimeseriesNodeEntity) in its value constructors, so every NEW node
// carries a v7 appId. That is the structural fix and it is live.
//
// The one-time HISTORICAL backfill of the existing NULL-appId rows (DB-AP2,
// 2026-07-18: 0/198 :Timeseries + ~567k :ShepardFile and growing) is
// DELIBERATELY NOT run here. Per the 2026-07-19 startup-hang incident
// (V121's in-startup backfill hung the backend), a large backfill must never be
// a fail-fast, synchronous startup migration. It is deferred to a separate,
// tuned, monitored operation run offline against a paused-ingest window —
// tracked as CHILD-APPID-BACKFILL in aidocs/16. The query is a simple label
// scan + SET (no MERGE, not pathological), but it is kept out of startup on
// principle and because it is otherwise unvalidated at scale.
//
// This file is intentionally a NOOP so the V122 slot stays recorded-applied and
// consistent with the released migration numbering. Rollback: V122_R__ (no-op).

RETURN 1;
