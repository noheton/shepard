// V2b — unique constraint on Snapshot.appId
// Safe to re-run: CREATE CONSTRAINT … IF NOT EXISTS is idempotent.
// SnapshotEntry.appId is also unique (each entry is minted with a fresh UUID v7)
// but we do not add a separate constraint for entries: they are internal
// junction nodes consumed only via their parent Snapshot and do not need an
// independent lookup path by appId.
// Operator runbook: this migration runs automatically on backend startup
// via MigrationsRunner; no manual step required.
// Cross-reference: aidocs/16 V2b, aidocs/34 V2b row, aidocs/41 §8.
CREATE CONSTRAINT Snapshot_appId_unique IF NOT EXISTS
FOR (n:Snapshot) REQUIRE n.appId IS UNIQUE;
