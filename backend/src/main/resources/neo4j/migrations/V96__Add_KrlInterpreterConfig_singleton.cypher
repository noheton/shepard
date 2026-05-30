// KRL-CONFIG-1 — add uniqueness constraint on :KrlInterpreterConfigEntity.appId
// and seed one node if none exists.
//
// The KRL interpreter admin-config runtime singleton is a single node seeded
// by KrlInterpreterConfigService.seedIfNeeded() on first startup.
// Adding a UNIQUE constraint on appId:
//   1. Prevents accidental duplicate :KrlInterpreterConfigEntity nodes (e.g. from a
//      race between two application pods on first startup).
//   2. Creates a Neo4j index that makes findSingleton() fast without
//      an explicit index migration.
//
// Idempotent: CREATE CONSTRAINT ... IF NOT EXISTS is a no-op when the
// constraint already exists. Safe to re-run.
//
// Rollback: V96_R__Add_KrlInterpreterConfig_singleton.cypher
//
// Operator runbook: docs/admin/runbooks/krl-interpreter-config.md
//
// Aborts startup on error per MigrationsRunner's fail-fast posture.

CREATE CONSTRAINT appId_unique_KrlInterpreterConfigEntity IF NOT EXISTS
  FOR (n:KrlInterpreterConfigEntity)
  REQUIRE n.appId IS UNIQUE;
