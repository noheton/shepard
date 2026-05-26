// AAS1l — rollback: drop :AasConfig.appId uniqueness constraint
//
// WARNING: this rollback removes the singleton guard. After running,
// concurrent startups could create duplicate :AasConfig nodes.
// Only run this rollback if you are also reverting the AAS1l code
// change (removing AasConfig.java, AasConfigService.java, etc.).
//
// If you only want to reset the config values, use
// PATCH /v2/admin/aas/config instead of running this rollback.
//
// Operator runbook:
//   cypher-shell -u neo4j -p <password> -f V88_R__aas_config.cypher
//
// Cross-reference: aidocs/16 AAS1l.

DROP CONSTRAINT AasConfig_appId_unique IF EXISTS;

// Optionally remove any seeded :AasConfig nodes:
// MATCH (c:AasConfig) DETACH DELETE c;
// (Commented out — non-destructive by default; uncomment if a full rollback is needed.)
