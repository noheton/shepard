// J1d — unique constraint on LabJournalEntryRevision.appId
// Safe to re-run: CREATE CONSTRAINT … IF NOT EXISTS is idempotent.
// Operator runbook: this migration runs automatically on backend startup
// via MigrationsRunner; no manual step required.
CREATE CONSTRAINT LabJournalEntryRevision_appId_unique IF NOT EXISTS
FOR (n:LabJournalEntryRevision) REQUIRE n.appId IS UNIQUE;
