// FTOGGLE-HDF-ENABLE-1 — uniqueness constraint on :HdfConfig.appId
// Mirrors V43 (:SqlTimeseriesConfig), V114 (:ThermographyConfig).
// Rollback: V115_R__Add_appId_constraint_HdfConfig.cypher
CREATE CONSTRAINT appId_unique_HdfConfig IF NOT EXISTS
  FOR (n:HdfConfig) REQUIRE n.appId IS UNIQUE;
