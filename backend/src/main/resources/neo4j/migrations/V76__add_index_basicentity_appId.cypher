// DB-OPT2 — BasicEntity.appId range index (permissions hot path)
//
// Every authenticated API request executes PermissionsDAO.findByEntityNeo4jId():
//
//   MATCH (e:BasicEntity {appId: $appId})-[:has_permissions]->(p:Permissions) RETURN p
//
// Without a :BasicEntity-level index, this query does:
//   NodeByLabelScan p:Permissions (4,368 hits)
//   → Expand(All) backwards through has_permissions (4,231 traversals)
//   → Filter e.appId AND e:BasicEntity
//   Total: 21,571 DB hits per request — measured on MFFD live data 2026-05-26.
//
// With this RANGE index, the plan becomes:
//   NodeByIndexSeek e:BasicEntity(appId)      (1 hit)
//   → Expand(All) → return p
//   Total: ~12 DB hits — a 1,800× reduction.
//
// IMPORTANT: This is a plain RANGE index, NOT a UNIQUE constraint.
// :BasicEntity is a supertype label; subtypes (Collection, DataObject, …)
// each carry their own UNIQUE constraint on appId already.  Adding UNIQUE
// at the supertype level would conflict with those constraints.
//
// Idempotent (IF NOT EXISTS).  Online build in Neo4j 5.x.
// Rollback: DROP INDEX BasicEntity_appId_idx IF EXISTS.
//
// NOTE: this index already exists on live as of 2026-05-26 (created manually
// during DB-OPT2 verification pass).  Flyway will skip it safely via IF NOT EXISTS.

CREATE INDEX BasicEntity_appId_idx IF NOT EXISTS
FOR (n:BasicEntity) ON (n.appId);
