// AAS1l — unique constraint on :AasConfig.appId + singleton seed guard
//
// Safe to re-run: CREATE CONSTRAINT … IF NOT EXISTS is idempotent.
//
// :AasConfig is the runtime-mutable config singleton for the AAS plugin.
// The appId uniqueness constraint ensures the singleton invariant is
// enforced at the database boundary — a duplicate row minted by a
// concurrent startup sequence will fail on appId collision rather than
// creating a second config node.
//
// The node itself is seeded by AasConfigService.seedIfNeeded() on first
// startup; this migration only adds the guard constraint.
//
// Operator runbook: runs automatically on backend startup via MigrationsRunner;
// no manual step required.
//
// Rollback: V88_R__aas_config.cypher — drops the constraint.
//
// Cross-reference: aidocs/16 AAS1l, aidocs/34 AAS1l row, aidocs/44 AAS1l row.

CREATE CONSTRAINT AasConfig_appId_unique IF NOT EXISTS
FOR (n:AasConfig) REQUIRE n.appId IS UNIQUE;
