// V92 — FE-PROV-INSTANCE-REGISTRY
//
// Add a UNIQUE constraint on :InstanceRegistry(appId) to enforce the
// singleton invariant at the database boundary.
//
// This migration is IDEMPOTENT: if the constraint already exists
// (e.g. re-run after partial failure) Neo4j silently skips it.
//
// Operator runbook:
//   Run via: cypher-shell -u neo4j -p <password> -f V92__Add_appId_constraint_InstanceRegistry.cypher
//   Rollback: V92_R__Add_appId_constraint_InstanceRegistry.cypher (drops constraint)
//
// The :InstanceRegistry node is seeded lazily on first startup by
// InstanceRegistryService.seedIfNeeded(); no data migration is needed.

CREATE CONSTRAINT instance_registry_appid_unique IF NOT EXISTS
  FOR (n:InstanceRegistry)
  REQUIRE n.appId IS UNIQUE;
