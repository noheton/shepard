// V92 rollback — FE-PROV-INSTANCE-REGISTRY
//
// Drops the UNIQUE constraint on :InstanceRegistry(appId).
// Run this ONLY if you need to roll back V92.
//
// Operator runbook:
//   Run via: cypher-shell -u neo4j -p <password> -f V92_R__Add_appId_constraint_InstanceRegistry.cypher

DROP CONSTRAINT instance_registry_appid_unique IF EXISTS;
