// ROR1 — Uniqueness constraint on :InstanceRorConfig.appId
//
// Runbook pointer: aidocs/34-upstream-upgrade-path.md §ROR1
//
// This migration is additive and idempotent (CREATE CONSTRAINT IF NOT EXISTS).
// The :InstanceRorConfig label is new as of this commit — no pre-existing rows
// exist in any production graph, so there is no backfill needed.
//
// After this migration, InstanceRorConfigService.seedIfNeeded() mints the
// singleton on first startup with GenericDAO.createOrUpdate (which assigns a
// UUID v7 appId); any attempt to insert a second :InstanceRorConfig node with
// the same appId is rejected at the database boundary.
//
// No rollback file is needed — the constraint is on a new label with no data.
// To roll back manually: DROP CONSTRAINT instance_ror_config_appId_unique IF EXISTS;
CREATE CONSTRAINT instance_ror_config_appId_unique IF NOT EXISTS
FOR (n:InstanceRorConfig) REQUIRE n.appId IS UNIQUE;
