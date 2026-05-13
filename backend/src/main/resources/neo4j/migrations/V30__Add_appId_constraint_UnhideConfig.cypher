// UH1a — uniqueness constraint on the new singleton :UnhideConfig
// label. Mirrors the V11 / V22 / V25 / V27 idiom (one
// `CREATE CONSTRAINT IF NOT EXISTS` per @NodeEntity label,
// uniqueness only — nulls are tolerated so pre-UH1a stacks
// upgrading in don't trip on the constraint).
//
// The singleton invariant (exactly one :UnhideConfig node ever
// exists) is held by the service-layer seed in
// UnhideConfigService#seedIfNeeded() at startup; this constraint
// is the database-side belt-and-braces guarantee.
//
// V29 was taken to avoid collisions with N1c2 (V27 + V28). If
// merging into main later finds a collision, bump higher — these
// constraint-only migrations are independently rebasable.
//
// Operator runbook: aidocs/34-upstream-upgrade-path.md row UH1a.
// Idempotent + fail-fast per CLAUDE.md.

CREATE CONSTRAINT appId_unique_UnhideConfig IF NOT EXISTS FOR (n:UnhideConfig) REQUIRE n.appId IS UNIQUE;
