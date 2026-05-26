// AI1a reconciliation — two uniqueness constraints missing from the V58
// AiCapabilityConfig_constraint that shipped with 63dcbf7c.
//
// V58 added only AiCapabilityConfig_appId_unique. The E-branch design
// (c2d2d377) required two additional constraints which are added here:
//
//   * AiGlobalConfig_appId_unique — the cross-capability posture singleton
//     (:AiGlobalConfig) introduced in plugins/ai/entities/AiGlobalConfig.java.
//     One row per instance, seeded on first startup.
//
//   * AiCapabilityConfig_capability_unique — enforces that the dispatcher
//     resolves a capability to exactly one slot. Without this, a duplicate
//     PATCH could create two :AiCapabilityConfig nodes for the same
//     capability and the service would pick one arbitrarily.
//
// Safe to re-run: CREATE CONSTRAINT … IF NOT EXISTS is idempotent.
// Aborts startup on error per MigrationsRunner's fail-fast posture.
//
// Operator runbook: see plugins/ai/docs/install.md.

CREATE CONSTRAINT AiGlobalConfig_appId_unique IF NOT EXISTS
FOR (n:AiGlobalConfig) REQUIRE n.appId IS UNIQUE;

CREATE CONSTRAINT AiCapabilityConfig_capability_unique IF NOT EXISTS
FOR (n:AiCapabilityConfig) REQUIRE n.capability IS UNIQUE;
