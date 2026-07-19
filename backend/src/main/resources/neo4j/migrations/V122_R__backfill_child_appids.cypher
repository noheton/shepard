// Rollback twin for V122 — NOOP.
//
// V122 is now a NOOP (the historical child-appId backfill was deferred out of
// startup after the 2026-07-19 hang incident; see V122__). There is nothing to
// roll back. Crucially this must NOT strip appIds: the Java write-path fix mints
// v7 appIds on all NEW :ShepardFile / :Timeseries, and a blanket REMOVE would
// destroy them. If the deferred CHILD-APPID-BACKFILL is later run and must be
// undone, do it as its own scoped, monitored operation — not here.

RETURN 1;
