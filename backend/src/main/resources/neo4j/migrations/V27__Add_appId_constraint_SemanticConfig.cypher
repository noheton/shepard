// N1c2 — uniqueness constraint on the new `:SemanticConfig` label,
// mirroring the V11 / V22 / V25 idiom (one `CREATE CONSTRAINT IF
// NOT EXISTS` per @NodeEntity label, uniqueness only — nulls are
// tolerated so a pre-N1c2 stack wouldn't trip on the constraint).
//
// :SemanticConfig is a single-instance node (per the A3b
// FeatureToggleRegistry pattern) carrying the runtime-mutable
// ontology-preseed knobs (preseedEnabled, disabledBundles) for the
// internal n10s semantic repository. The singleton invariant is
// enforced at the service layer in OntologyConfigService — the
// constraint here is the L2a appId uniqueness pattern that every
// :HasAppId entity carries.
//
// Operator runbook: aidocs/34-upstream-upgrade-path.md row N1c2.
// Idempotent + fail-fast per CLAUDE.md.

CREATE CONSTRAINT appId_unique_SemanticConfig IF NOT EXISTS FOR (n:SemanticConfig) REQUIRE n.appId IS UNIQUE;
