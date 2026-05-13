// KIP1d — uniqueness constraint on the new singleton
// :DataciteMinterConfig label. Mirrors the V11 / V22 / V25 / V27 /
// V30 idiom (one `CREATE CONSTRAINT IF NOT EXISTS` per @NodeEntity
// label, uniqueness only — nulls tolerated so pre-KIP1d stacks
// upgrading in don't trip on the constraint).
//
// The singleton invariant (exactly one :DataciteMinterConfig node
// ever exists) is held by the service-layer seed in
// DataciteMinterConfigService#seedIfNeeded() at startup; this
// constraint is the database-side belt-and-braces guarantee.
//
// V31 is intentionally skipped (KIP1d numbering with breathing room
// for in-flight PRs that might land V31 / V32 first). V32 is the
// PM1e PluginRuntimeOverride constraint already on main.
//
// Operator runbook: aidocs/34-upstream-upgrade-path.md row KIP1d.
// Reference manual: docs/reference/minter-datacite.md.
// Idempotent + fail-fast per CLAUDE.md.

CREATE CONSTRAINT appId_unique_DataciteMinterConfig IF NOT EXISTS FOR (n:DataciteMinterConfig) REQUIRE n.appId IS UNIQUE;
