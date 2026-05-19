// PV1a — unique constraint on PayloadVersion.appId
// Safe to re-run: CREATE CONSTRAINT … IF NOT EXISTS is idempotent.
// PayloadVersion nodes are append-only junction records created by
// FileContainerService.createFile every time a file is uploaded. Each
// node is minted with a fresh UUID v7 appId via GenericDAO.createOrUpdate.
// Operator runbook: this migration runs automatically on backend startup
// via MigrationsRunner; no manual step required.
// Cross-reference: aidocs/16 PV1a, aidocs/34 PV1a row, aidocs/data/46.
CREATE CONSTRAINT PayloadVersion_appId_unique IF NOT EXISTS
FOR (n:PayloadVersion) REQUIRE n.appId IS UNIQUE;
