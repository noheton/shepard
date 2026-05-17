// AAS1-reg — unique constraint on AasRegistration.appId
// Safe to re-run: CREATE CONSTRAINT … IF NOT EXISTS is idempotent.
// AasRegistration is the outbox entity tracking per-shell registration state
// at an external IDTA AAS Registry; the appId constraint guards against
// duplicate outbox rows from concurrent startup sequences.
// Operator runbook: runs automatically on backend startup via MigrationsRunner;
// no manual step required.
// Cross-reference: aidocs/16 AAS1-reg, aidocs/34 AAS1-reg row, aidocs/44 AAS1-reg row.
CREATE CONSTRAINT AasRegistration_appId_unique IF NOT EXISTS
FOR (n:AasRegistration) REQUIRE n.appId IS UNIQUE;
