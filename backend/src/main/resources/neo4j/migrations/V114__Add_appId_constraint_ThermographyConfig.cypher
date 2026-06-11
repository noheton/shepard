// MFFD-NDT-ADMIN-CONFIG-1 — add uniqueness constraint on :ThermographyConfig.appId.
//
// The thermography admin-config runtime singleton is a single node seeded
// by ThermographyConfigService.seedIfNeeded() on first startup. Adding a
// UNIQUE constraint on appId:
//   1. Prevents accidental duplicate :ThermographyConfig nodes (e.g. from a
//      race between two application pods on first startup).
//   2. Creates a Neo4j index that makes findSingleton() fast without
//      an explicit index migration.
//
// Idempotent: CREATE CONSTRAINT ... IF NOT EXISTS is a no-op when the
// constraint already exists. Safe to re-run.
//
// Rollback: V111_R__Add_appId_constraint_ThermographyConfig.cypher
//
// Operator runbook: docs/admin/runbooks/thermography-config.md
//   (see also: aidocs/34-upstream-upgrade-path.md MFFD-NDT-ADMIN-CONFIG-1 row)
//
// Aborts startup on error per MigrationsRunner's fail-fast posture.

CREATE CONSTRAINT appId_unique_ThermographyConfig IF NOT EXISTS
  FOR (n:ThermographyConfig)
  REQUIRE n.appId IS UNIQUE;
