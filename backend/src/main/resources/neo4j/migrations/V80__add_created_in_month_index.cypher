// NEO-AUDIT-004 — Time-bucketed Agent index for the `:User` supernode
//
// Context:
//   The operator User node carries 33,368+ incoming created_by edges from DataObjects.
//   A PROFILE of a created_by traversal takes 66,794 dbHits to walk 17,086 DataObjects.
//   The NEO-AUDIT-001 Activity PROV-O edges will worsen this further as Activity nodes grow.
//
//   Fix: add a time-bucketed index relationship:
//     (:User)-[:created_in_month {ym:"YYYYMM"}]->(:DataObject)
//   written by DataObjectDAO.writeCreatedInMonth() on every new DataObject creation.
//
//   Queries like "list DataObjects this user created in May 2025" become a
//   label-scan on [:created_in_month] with a ym predicate — typically 100-1000x
//   fewer dbHits than walking the full supernode.
//
//   Citation: Neo4j supernode KB
//   https://neo4j.com/developer/kb/understanding-the-design-of-supernodes/
//
// ym format: "YYYYMM" (6-char string, e.g. "202605" for May 2026).
//   Matches the Java-side: String.format("%1$tY%1$tm", createdAt)
//
// Idempotent: MERGE is used throughout — safe to re-run.
//
// Performance:
//   The range index below must exist before the CALL block runs.
//   The backfill is batched in TRANSACTIONS OF 1000 ROWS.
//   On a 33k-row dataset expect ~33 transactions, each < 5 ms.
//   Index build is online in Neo4j 5.x (no downtime).
//
// Rollback: V80_R__add_created_in_month_index.cypher
//   (drops the index and removes all created_in_month relationships)
//
// Operator runbook: run from cypher-shell:
//   cypher-shell -u neo4j -p <pwd> -f V80__add_created_in_month_index.cypher
// Or let Flyway/Shepard MigrationsRunner pick it up on next startup.
// Monitor backfill progress:
//   MATCH (u:User)-[r:created_in_month]->(:DataObject) RETURN count(r);
// Should converge to approximately the number of non-deleted DataObjects.

// --- Range index on the relationship property ----------------------------
// Enables efficient ym-predicated queries:
//   MATCH (:User {username:$u})-[r:created_in_month]->(:DataObject) WHERE r.ym = "202605"

CREATE INDEX created_in_month_ym_idx IF NOT EXISTS
FOR ()-[r:created_in_month]-()
ON (r.ym);

// --- Backfill existing DataObjects ---------------------------------------
// For each DataObject with a non-null createdAt and a resolvable createdBy User,
// compute the ym value from the stored epoch-millis timestamp and MERGE the rel.
//
// createdAt is stored as Long epoch-millis by @DateLong (see AbstractEntity.java).
// Neo4j datetime({epochMillis: x}).year / .month extract the components.
//
// DataObjects with NULL createdAt are skipped (pre-provenance rows, if any).
// DataObjects whose createdBy User has been deleted are skipped silently.

CALL {
  MATCH (d:DataObject)
  WHERE d.createdAt IS NOT NULL
  MATCH (d)<-[:created_by]-(u:User)
  WITH u, d,
       right('000' + toString(datetime({epochMillis: d.createdAt}).year), 4) +
       right('0' + toString(datetime({epochMillis: d.createdAt}).month), 2) AS ym
  MERGE (u)-[:created_in_month {ym: ym}]->(d)
} IN TRANSACTIONS OF 1000 ROWS;
