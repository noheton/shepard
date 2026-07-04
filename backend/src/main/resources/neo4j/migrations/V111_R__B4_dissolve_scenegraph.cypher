// V2CONV-B4 rollback — re-create the scene-graph appId uniqueness constraints.
//
// Rollback twin of V111__B4_dissolve_scenegraph.cypher.
//
// IMPORTANT: this rollback restores only the SCHEMA (the three appId uniqueness
// constraints V95 added). It CANNOT restore the deleted :DigitalTwinScene /
// :CoordinateFrame / :Joint node DATA — the dissolution is a one-way
// convergence into the MAPPING_RECIPE mechanism (aidocs/platform/191
// decision #2), and the URDF FileReference is the source of truth. Operators
// who must retain old stored scenes should snapshot the graph BEFORE applying
// V111; rolling back only un-dissolves the constraint schema so a hand-restored
// dataset would re-satisfy the uniqueness invariant.
//
// Idempotent: CREATE CONSTRAINT ... IF NOT EXISTS is a no-op when present.

CREATE CONSTRAINT appId_unique_DigitalTwinScene IF NOT EXISTS
  FOR (n:DigitalTwinScene)
  REQUIRE n.appId IS UNIQUE;

CREATE CONSTRAINT appId_unique_CoordinateFrame IF NOT EXISTS
  FOR (n:CoordinateFrame)
  REQUIRE n.appId IS UNIQUE;

CREATE CONSTRAINT appId_unique_Joint IF NOT EXISTS
  FOR (n:Joint)
  REQUIRE n.appId IS UNIQUE;
