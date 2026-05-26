// VID1c — add uniqueness constraint on :VideoConfig.appId.
//
// The video plugin runtime config is a singleton node seeded by
// VideoConfigService.seedIfNeeded() on first startup.  Adding a
// UNIQUE constraint on appId:
//   1. Prevents accidental duplicate :VideoConfig nodes (e.g. from
//      a race between two application pods on first startup).
//   2. Creates a Neo4j index that makes findSingleton() fast
//      without an explicit index migration.
//
// Idempotent: CREATE CONSTRAINT ... IF NOT EXISTS is a no-op when
// the constraint already exists.  Safe to re-run.
//
// Rollback: V89_R__Add_appId_constraint_VideoConfig.cypher
//
// Operator runbook: no pre-existing data to migrate; the plugin
// seeds the singleton lazily on first startup after the migration runs.
// Aborts startup on error per MigrationsRunner's fail-fast posture.

CREATE CONSTRAINT appId_unique_VideoConfig IF NOT EXISTS
  FOR (n:VideoConfig)
  REQUIRE n.appId IS UNIQUE;
