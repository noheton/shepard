// REF1c — uniqueness constraint on the new :DbpediaDatabusReference
// label. Mirrors the V11 / V22 / V25 / V27 / V30 / V32 idiom (one
// `CREATE CONSTRAINT IF NOT EXISTS` per @NodeEntity label, uniqueness
// only — nulls are tolerated so pre-REF1c stacks upgrading in don't
// trip on the constraint).
//
// :DbpediaDatabusReference rows are created by the plugin's
// DbpediaDatabusReferenceDAO (via VersionableEntityDAO.createOrUpdate)
// which mints a ULID appId on first persist. The uniqueness constraint
// is the database-side belt-and-braces guarantee against accidental
// duplicates.
//
// V32 was taken by PM1e (:PluginRuntimeOverride pluginId constraint);
// V33 is the next available slot. If a sibling slice has also taken
// V33 by rebase time this file is independently rebasable to V34 —
// the constraint is idempotent and order-independent against other
// constraint-only migrations.
//
// Operator runbook: aidocs/34-upstream-upgrade-path.md row REF1c.
// Idempotent + fail-fast per CLAUDE.md.

CREATE CONSTRAINT appId_unique_DbpediaDatabusReference IF NOT EXISTS FOR (n:DbpediaDatabusReference) REQUIRE n.appId IS UNIQUE;
