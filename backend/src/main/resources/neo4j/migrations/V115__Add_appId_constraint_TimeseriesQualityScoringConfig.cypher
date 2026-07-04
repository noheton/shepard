// FTOGGLE-QS-1 — unique constraint on TimeseriesQualityScoringConfig.appId
// Safe to re-run: CREATE CONSTRAINT … IF NOT EXISTS is idempotent.
// The singleton invariant (one :TimeseriesQualityScoringConfig node) is enforced by:
//   (1) TimeseriesQualityScoringConfigService.seedIfNeeded() minting exactly one node on startup
//   (2) this constraint rejecting any accidental duplicates at the DB boundary
// Mirrors V114 (:ThermographyConfig), V94 (:JupyterConfig), V43 (:SqlTimeseriesConfig).
// Operator runbook: runs automatically on backend startup via MigrationsRunner; no manual step.
// Cross-reference: aidocs/16 FTOGGLE-QS-1.
CREATE CONSTRAINT TimeseriesQualityScoringConfig_appId_unique IF NOT EXISTS
FOR (n:TimeseriesQualityScoringConfig) REQUIRE n.appId IS UNIQUE;
