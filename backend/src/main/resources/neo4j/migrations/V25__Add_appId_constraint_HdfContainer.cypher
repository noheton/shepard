// A5a — uniqueness constraint on the new `:HdfContainer` label, mirroring
// the V11 / V22 idiom (one `CREATE CONSTRAINT IF NOT EXISTS` per
// @NodeEntity label, uniqueness only — nulls are tolerated so a
// pre-A5a stack wouldn't trip on the constraint).
//
// Phase 1 only ships :HdfContainer. :HdfReference (per-DataObject
// anchor) arrives in A5c and gets its own constraint migration when
// it lands.
//
// Operator runbook: aidocs/34-upstream-upgrade-path.md row A5a.
// Idempotent + fail-fast per CLAUDE.md.

CREATE CONSTRAINT appId_unique_HdfContainer IF NOT EXISTS FOR (n:HdfContainer) REQUIRE n.appId IS UNIQUE;
