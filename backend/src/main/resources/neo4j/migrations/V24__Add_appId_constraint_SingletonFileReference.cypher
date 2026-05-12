// FR1b — uniqueness constraint on the new :SingletonFileReference
// label. Mirrors V11 / V22 idiom: one CREATE CONSTRAINT IF NOT EXISTS
// per @NodeEntity label, uniqueness only — nulls are tolerated so a
// dev stack with no singletons yet doesn't trip on the constraint.
//
// The :SingletonFileReference label is introduced by FR1b's
// FileReference entity (de.dlr.shepard.context.references.file.entities.FileReference).
// V23 (opt-in) backfills the label onto pre-FR1b singleton-shaped
// bundles; the OGM writes it on every freshly-created singleton via
// /v2/files/... POST.
//
// Idempotent + fail-fast per CLAUDE.md.

CREATE CONSTRAINT appId_unique_SingletonFileReference IF NOT EXISTS
  FOR (n:SingletonFileReference)
  REQUIRE n.appId IS UNIQUE;
