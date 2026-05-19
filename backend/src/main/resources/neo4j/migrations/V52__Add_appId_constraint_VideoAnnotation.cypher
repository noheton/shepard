// VID1b-annotation — unique constraint on VideoAnnotation.appId
// Safe to re-run: CREATE CONSTRAINT … IF NOT EXISTS is idempotent.
CREATE CONSTRAINT VideoAnnotation_appId_unique IF NOT EXISTS
FOR (n:VideoAnnotation) REQUIRE n.appId IS UNIQUE;
