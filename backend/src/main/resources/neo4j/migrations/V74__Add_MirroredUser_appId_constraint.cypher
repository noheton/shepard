// V74 — PROV-USER-MIRROR-ENDPOINT: add uniqueness constraint + composite lookup
// index for :MirroredUser nodes.
//
// Operator runbook: this migration is idempotent (IF NOT EXISTS guards) and safe
// to re-run from cypher-shell:
//
//   cypher-shell -u neo4j -p <pass> \
//     "CREATE CONSTRAINT IF NOT EXISTS FOR (n:MirroredUser) REQUIRE n.appId IS UNIQUE"
//
// Purpose:
//   - The appId constraint mirrors the pattern used for every other HasAppId entity
//     (e.g. V11, V42 for :InstanceRorConfig). It ensures that GenericDAO#createOrUpdate
//     cannot accidentally mint two nodes with the same appId.
//   - The composite index on (sourceInstance, sourceUsername) supports the MERGE lookup
//     in MirroredUserDAO#findBySourceInstanceAndUsername and makes the uniqueness invariant
//     fast to enforce at query time.
//
// Rollback: DROP CONSTRAINT MirroredUser_appId_unique IF EXISTS;
//           DROP INDEX MirroredUser_sourceInstance_username_idx IF EXISTS;

CREATE CONSTRAINT MirroredUser_appId_unique IF NOT EXISTS
  FOR (n:MirroredUser)
  REQUIRE n.appId IS UNIQUE;

CREATE INDEX MirroredUser_sourceInstance_username_idx IF NOT EXISTS
  FOR (n:MirroredUser)
  ON (n.sourceInstance, n.sourceUsername);
