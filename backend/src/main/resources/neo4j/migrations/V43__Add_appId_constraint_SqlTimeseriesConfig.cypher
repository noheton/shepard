// P10c — unique constraint on SqlTimeseriesConfig.appId
// Safe to re-run: CREATE CONSTRAINT … IF NOT EXISTS is idempotent.
// The singleton invariant (one :SqlTimeseriesConfig node) is enforced by:
//   (1) SqlTimeseriesConfigService.seedIfNeeded() minting exactly one node on startup
//   (2) this constraint rejecting any accidental duplicates at the DB boundary
// Note: V42 (InstanceRorConfig) was referenced in the ROR1 commit message but the
// migration file was not shipped. That gap is a known issue — the constraint runner
// tolerates version gaps. V43 is the next available version in this file set.
// Operator runbook: this migration runs automatically on backend startup via
// MigrationsRunner; no manual step required.
// Cross-reference: aidocs/16 P10c, aidocs/34 P10c row, aidocs/44 P10 row.
CREATE CONSTRAINT SqlTimeseriesConfig_appId_unique IF NOT EXISTS
FOR (n:SqlTimeseriesConfig) REQUIRE n.appId IS UNIQUE;
