// V1COMPAT.0 — Bootstrap the singleton :LegacyV1Config for the
// v1 compat marker plugin (per aidocs/platform/103a §2 row 2).
//
// One :LegacyV1Config node carries the runtime-mutable `enabled`
// toggle for the upstream-frozen /shepard/api/... surface.
// `enabled` defaults to `true` — the v1 sunset philosophy is
// "no fork-imposed timeline; operator decides when to flip".
//
// Using MERGE + ON CREATE makes this migration idempotent — if an
// admin already minted the node (via the JVM-layer
// `LegacyV1ConfigService.seedIfNeeded()` defence-in-depth path),
// this migration is a no-op. Subsequent admin PATCHes via
// `/v2/admin/legacy/v1/config` mutate the same row in place; the
// migration NEVER overwrites a runtime-mutated value (the
// ON CREATE clause means SET only fires on the first MERGE-insert).
//
// Two-statement migration:
//   1. CREATE CONSTRAINT — appId uniqueness for the singleton label,
//      mirrors the V11 / V22 / V25 / V27 / V30 (:UnhideConfig)
//      idiom. Database-side belt-and-braces against an accidental
//      duplicate row; the service-layer seed is the primary guard.
//   2. MERGE — seed the singleton row with enabled=true.
//
// To roll back manually (e.g. during Phase 1 dry-run):
//   MATCH (c:LegacyV1Config) DETACH DELETE c;
//   DROP CONSTRAINT LegacyV1Config_appId_unique IF EXISTS;
//
// Operator runbook: docs/reference/v1-deprecation.md (lands with PR-3).

CREATE CONSTRAINT LegacyV1Config_appId_unique IF NOT EXISTS
FOR (c:LegacyV1Config) REQUIRE c.appId IS UNIQUE;

MERGE (c:LegacyV1Config)
ON CREATE SET
  c.appId     = randomUUID(),
  c.enabled   = true,
  c.createdAt = timestamp(),
  c.updatedAt = timestamp();
