// V83 — IMP2: Add index on :ImportPlan(commitId) and document manifestJson property.
//
// The manifestJson property itself is written by ImportValidationService (Java) from
// IMP2 onwards; this migration only ensures the commitId lookup index exists so that
// the execute leg (POST /v2/import/jobs) can resolve plans efficiently.
//
// Plans persisted before this migration was deployed have manifestJson = null.
// The execute endpoint treats null as a "pre-IMP2 plan" and rejects with HTTP 410.
//
// Idempotent: CREATE INDEX IF NOT EXISTS is safe to re-run.
//
// Operator runbook: no data mutation — index creation only.
// Rollback: V83_R__ImportPlan_manifestJson.cypher

CREATE INDEX import_plan_commit_id IF NOT EXISTS
FOR (p:ImportPlan)
ON (p.commitId);
