// WATCH1 — Uniqueness constraint on :Watch.appId
//
// Runbook pointer: aidocs/34-upstream-upgrade-path.md §WATCH1
//
// Additive + idempotent (CREATE CONSTRAINT IF NOT EXISTS). The :Watch
// label is new as of this commit — no pre-existing rows, no backfill.
//
// Service-side uniqueness on (collectionAppId, containerAppId) is enforced
// by WatchService.create's idempotency probe, not a Cypher constraint —
// the (collectionAppId, containerAppId) pair is not the entity's primary
// key (appId is), and a composite Cypher constraint would require schema
// version bumps if we ever want to add a third dimension.
//
// To roll back: DROP CONSTRAINT watch_appId_unique IF EXISTS;
CREATE CONSTRAINT watch_appId_unique IF NOT EXISTS
FOR (n:Watch) REQUIRE n.appId IS UNIQUE;
