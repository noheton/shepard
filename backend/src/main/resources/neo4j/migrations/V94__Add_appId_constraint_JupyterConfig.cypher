// J1e — add uniqueness constraint on :JupyterConfig.appId.
//
// The JupyterHub admin-config runtime singleton is a single node seeded
// by JupyterConfigService.seedIfNeeded() on first startup. Adding a
// UNIQUE constraint on appId:
//   1. Prevents accidental duplicate :JupyterConfig nodes (e.g. from a
//      race between two application pods on first startup).
//   2. Creates a Neo4j index that makes findSingleton() fast without
//      an explicit index migration.
//
// Idempotent: CREATE CONSTRAINT ... IF NOT EXISTS is a no-op when the
// constraint already exists. Safe to re-run.
//
// Rollback: V94_R__Add_appId_constraint_JupyterConfig.cypher
//
// Operator runbook: docs/admin/runbooks/jupyterhub-config.md
//
// Aborts startup on error per MigrationsRunner's fail-fast posture.

CREATE CONSTRAINT appId_unique_JupyterConfig IF NOT EXISTS
  FOR (n:JupyterConfig)
  REQUIRE n.appId IS UNIQUE;
