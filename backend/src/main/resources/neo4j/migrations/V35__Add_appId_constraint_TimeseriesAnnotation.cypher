// TA1a — unique constraint on TimeseriesAnnotation.appId
// Safe to re-run: CREATE CONSTRAINT … IF NOT EXISTS is idempotent.
// Operator runbook: this migration runs automatically on backend startup
// via MigrationsRunner; no manual step required.
CREATE CONSTRAINT TimeseriesAnnotation_appId_unique IF NOT EXISTS
FOR (n:TimeseriesAnnotation) REQUIRE n.appId IS UNIQUE;
