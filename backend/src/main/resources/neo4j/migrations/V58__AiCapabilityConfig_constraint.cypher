// V58 — Uniqueness constraint for :AiCapabilityConfig nodes
// One config node per AiCapability slot (TEXT, FAST_TEXT, etc.).
// Constraint also creates an index on (appId) for singleton lookup.
// Idempotent: CREATE CONSTRAINT IF NOT EXISTS is safe to re-run.

CREATE CONSTRAINT AiCapabilityConfig_appId_unique IF NOT EXISTS
FOR (n:AiCapabilityConfig)
REQUIRE n.appId IS UNIQUE;
