// TPL10 — Data Quality Requirements as first-class records.
//
// Adds a uniqueness constraint on :DataQualityRequirement.appId.
// The APPLIES_TO relationship ((:DataQualityRequirement)-[:APPLIES_TO]->(:Collection))
// is schema-free in Neo4j and requires no explicit constraint here.
//
// Safe to re-run: CREATE CONSTRAINT … IF NOT EXISTS is idempotent.
// Aborts startup on error per MigrationsRunner's fail-fast posture.
//
// Operator runbook: no pre-existing data to migrate.
// Rollback: DROP CONSTRAINT DataQualityRequirement_appId_unique IF EXISTS;

CREATE CONSTRAINT DataQualityRequirement_appId_unique IF NOT EXISTS
FOR (n:DataQualityRequirement) REQUIRE n.appId IS UNIQUE;
