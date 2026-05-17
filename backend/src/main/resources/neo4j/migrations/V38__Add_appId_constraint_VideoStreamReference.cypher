// V38 — unique constraint on VideoStreamReference.appId
//
// VID1a: adds the :VideoStreamReference node label to the existing
// per-label appId unique-constraint set (V11 pattern). Safe to re-run:
// CREATE CONSTRAINT … IF NOT EXISTS is idempotent.
//
// Operator runbook: this migration runs automatically on backend startup
// via MigrationsRunner; no manual step required. Can also be applied
// manually via cypher-shell or the Neo4j Browser without setting up the
// project:
//
//   cypher-shell -u neo4j -p <password> \
//     "CREATE CONSTRAINT VideoStreamReference_appId_unique IF NOT EXISTS \
//      FOR (n:VideoStreamReference) REQUIRE n.appId IS UNIQUE;"
CREATE CONSTRAINT VideoStreamReference_appId_unique IF NOT EXISTS
FOR (n:VideoStreamReference) REQUIRE n.appId IS UNIQUE;
