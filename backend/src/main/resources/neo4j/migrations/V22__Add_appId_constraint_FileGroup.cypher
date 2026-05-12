// FR1a — uniqueness constraint on the new `:FileGroup` label, mirroring
// the V11 idiom (one `CREATE CONSTRAINT IF NOT EXISTS` per @NodeEntity
// label, uniqueness only — nulls are tolerated so a pre-V21 dev stack
// wouldn't trip on the constraint).
//
// `:FileBundleReference` doesn't need its own constraint here: it shares
// the existing `:FileReference` label (V21 added the second label
// alongside, so the V11 `appId_unique_FileReference` constraint already
// covers every node).
//
// Idempotent + fail-fast per `CLAUDE.md`.

CREATE CONSTRAINT appId_unique_FileGroup IF NOT EXISTS FOR (n:FileGroup) REQUIRE n.appId IS UNIQUE;
