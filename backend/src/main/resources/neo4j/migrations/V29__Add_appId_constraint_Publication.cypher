// KIP1a — uniqueness constraint on the new `:Publication` label,
// mirroring the V11 / V25 idiom (one `CREATE CONSTRAINT IF NOT EXISTS`
// per @NodeEntity label, uniqueness only — nulls are tolerated so a
// pre-KIP1a stack wouldn't trip on the constraint).
//
// KIP1a only ships :Publication. The :HAS_PUBLICATION edge between
// shepard entities and Publication nodes carries no extra properties
// in this baseline — uniqueness is anchored on Publication.appId
// (every row has a UUID v7 minted by GenericDAO#createOrUpdate) and
// the @Index on Publication.pid keeps the .well-known/kip resolver
// lookup cheap without needing a full constraint.
//
// Operator runbook: aidocs/34-upstream-upgrade-path.md row KIP1a.
// Idempotent + fail-fast per CLAUDE.md.

CREATE CONSTRAINT appId_unique_Publication IF NOT EXISTS FOR (n:Publication) REQUIRE n.appId IS UNIQUE;
