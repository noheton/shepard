// FTOGGLE-PROV-1 — add uniqueness constraint on :ProvenanceConfig.appId.
//
// The provenance admin-config runtime singleton is a single node seeded
// by ProvenanceConfigService.seedIfNeeded() on first startup. Adding a
// UNIQUE constraint on appId:
//   1. Prevents accidental duplicate :ProvenanceConfig nodes (e.g. from a
//      race between two application pods on first startup).
//   2. Creates a Neo4j index that makes findSingleton() fast without
//      an explicit index migration.
//
// Idempotent: CREATE CONSTRAINT ... IF NOT EXISTS is a no-op when the
// constraint already exists. Safe to re-run.
//
// Rollback: V116_R__Add_appId_constraint_ProvenanceConfig.cypher
//
// Aborts startup on error per MigrationsRunner's fail-fast posture.

CREATE CONSTRAINT appId_unique_ProvenanceConfig IF NOT EXISTS
  FOR (n:ProvenanceConfig)
  REQUIRE n.appId IS UNIQUE;
