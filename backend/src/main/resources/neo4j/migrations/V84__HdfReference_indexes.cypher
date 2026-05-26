// V84 — HdfReference: uniqueness constraint on appId + index on datasetPath
//
// Operator runbook: this migration creates two index structures on the
// new :HdfReference node label (introduced by plugin A5c).
//
//   1. UNIQUE appId constraint — mirrors the pattern on every other
//      HasAppId entity (Collection, DataObject, HdfContainer, …).
//      Fail-fast: if any pre-existing :HdfReference node lacks an
//      appId, Neo4j will refuse to create the constraint. In practice,
//      the schema is additive (no pre-A5c :HdfReference nodes exist),
//      so this is safe on a clean upgrade.
//
//   2. Index on datasetPath — accelerates future query patterns like
//      "find all references to dataset /sensor_data/channel_A across
//      all DataObjects". Non-unique because multiple DataObjects may
//      pin the same path in different containers.
//
// Rollback: V84_R__HdfReference_indexes.cypher
// Idempotent: IF NOT EXISTS guards make both calls safe to re-run.

CREATE CONSTRAINT hdf_reference_appId_unique IF NOT EXISTS
  FOR (r:HdfReference)
  REQUIRE r.appId IS UNIQUE;

CREATE INDEX hdf_reference_datasetPath_idx IF NOT EXISTS
  FOR (r:HdfReference)
  ON (r.datasetPath);
