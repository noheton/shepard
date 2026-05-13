// PM1e — uniqueness constraint on the new :PluginRuntimeOverride
// label. Mirrors the V11 / V22 / V25 / V27 / V30 idiom (one
// `CREATE CONSTRAINT IF NOT EXISTS` per @NodeEntity label,
// uniqueness only — nulls are tolerated so pre-PM1e stacks
// upgrading in don't trip on the constraint).
//
// :PluginRuntimeOverride is a per-plugin row carrying the
// persisted enabled flip (admin override of the deploy-time
// `shepard.plugins.<id>.enabled` install default). The
// uniqueness key is `pluginId` — one override row per plugin
// id at most. The service-layer seed in
// `PluginRegistry.onStart` reads every row at startup and
// populates the in-memory cache; subsequent writes mutate the
// row in place (when overriding the default) or DELETE it (when
// resetting to the default). The constraint here is the
// database-side belt-and-braces guarantee.
//
// V31 was reserved for KIP1h (`claude/kip1h-localminter-versioned-pids`)
// per the orchestrator's V## coordination convention; PM1e
// takes V32. If a sibling slice has also taken V32 by rebase
// time, this file is independently rebasable to V33 — the
// constraint is idempotent and order-independent against the
// other constraint-only migrations.
//
// Operator runbook: aidocs/34-upstream-upgrade-path.md row PM1e.
// Idempotent + fail-fast per CLAUDE.md.

CREATE CONSTRAINT pluginId_unique_PluginRuntimeOverride IF NOT EXISTS FOR (n:PluginRuntimeOverride) REQUIRE n.pluginId IS UNIQUE;
