// P10c / KIP1c — unique constraint on EpicMinterConfig.appId
// Safe to re-run: CREATE CONSTRAINT … IF NOT EXISTS is idempotent.
// The singleton invariant (one :EpicMinterConfig node) is enforced by:
//   (1) EpicMinterConfigService.seedIfNeeded() minting exactly one node on startup
//   (2) this constraint rejecting any accidental duplicates at the DB boundary
// Operator runbook: this migration runs automatically on backend startup via
// MigrationsRunner; no manual step required.
// Cross-reference: aidocs/16 KIP1c, aidocs/34 KIP1c row, aidocs/44 KIP1c row.
CREATE CONSTRAINT EpicMinterConfig_appId_unique IF NOT EXISTS
FOR (n:EpicMinterConfig) REQUIRE n.appId IS UNIQUE;
