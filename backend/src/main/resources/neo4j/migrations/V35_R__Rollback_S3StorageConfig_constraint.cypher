// V35_R — FS1b rollback: drop the :S3StorageConfig.appId uniqueness constraint.
//
// Run this before downgrading to pre-FS1b if the constraint was applied.
// Idempotent (DROP CONSTRAINT IF EXISTS).
DROP CONSTRAINT IF EXISTS FOR (n:S3StorageConfig) REQUIRE n.appId IS UNIQUE;
