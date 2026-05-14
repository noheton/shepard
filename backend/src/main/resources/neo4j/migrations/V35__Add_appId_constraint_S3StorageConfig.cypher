// V35 — FS1b: add uniqueness constraint on :S3StorageConfig.appId.
//
// Operator runbook: backend/src/main/resources/neo4j/migrations/
// Idempotent: the IF NOT EXISTS guard makes this safe to replay.
// Fail-fast: MigrationsRunner propagates MigrationsException on error,
//            aborting startup.
//
// This migration is paired with the S3StorageConfigService.seedIfNeeded()
// call that mints the singleton on first startup. The constraint enforces
// the singleton invariant at the database boundary so accidental duplicates
// (e.g. two pods racing at first-boot before quorum) fail hard rather than
// silently producing two :S3StorageConfig nodes.
//
// Rollback: V35_R__Rollback_S3StorageConfig_constraint.cypher (see below).
CREATE CONSTRAINT IF NOT EXISTS FOR (n:S3StorageConfig) REQUIRE n.appId IS UNIQUE;
