// IMP-LOCK — Add :ImportLock uniqueness constraints.
//
// Each :ImportLock node represents one import-in-progress lock attempt,
// persisted across backend restarts. Client importers acquire, heartbeat,
// and release locks via /v2/import/lock/... REST endpoints.
//
// This migration adds two uniqueness constraints:
//   1. lockId — the public UUID-v7 lock identifier used in REST paths
//   2. appId  — the HasAppId stable identifier minted by GenericDAO
//
// Both follow the V11/V22/V25/V27/V30/V59/V63 pattern:
//   CREATE CONSTRAINT ... IF NOT EXISTS  → idempotent; safe to re-run.
//
// To roll back manually:
//   DROP CONSTRAINT ImportLock_lockId_unique IF EXISTS;
//   DROP CONSTRAINT ImportLock_appId_unique IF EXISTS;
//   MATCH (l:ImportLock) DETACH DELETE l;
//
// Operator runbook: docs/reference/import-lock.md
//
// No data migration required — no :ImportLock nodes exist before this migration.

CREATE CONSTRAINT ImportLock_lockId_unique IF NOT EXISTS
FOR (l:ImportLock) REQUIRE l.lockId IS UNIQUE;

CREATE CONSTRAINT ImportLock_appId_unique IF NOT EXISTS
FOR (l:ImportLock) REQUIRE l.appId IS UNIQUE;
