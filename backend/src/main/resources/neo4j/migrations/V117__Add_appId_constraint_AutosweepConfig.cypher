// FTOGGLE-AUTOSWEEP-1 — unique appId constraint for the :AutosweepConfig singleton.
// Operator runbook: none — additive index, no data change.
// Rollback: V117_R__Add_appId_constraint_AutosweepConfig.cypher
CREATE CONSTRAINT AutosweepConfig_appId_unique IF NOT EXISTS
  FOR (n:AutosweepConfig) REQUIRE n.appId IS UNIQUE;
