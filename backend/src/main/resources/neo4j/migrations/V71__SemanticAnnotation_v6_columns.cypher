// SEMA-V6-001 — column-add for :SemanticAnnotation v6 fields.
//
// Neo4j is schemaless: there is no ALTER TABLE here. This migration:
//   1. Creates optional performance indexes on the two highest-cardinality
//      lookup patterns (subjectAppId for ownership lookup, vocabularyId for
//      vocabulary scoping).
//   2. Does NOT backfill the 56 existing production rows — all new fields are
//      nullable and default to null, which is correct for legacy annotations.
//
// New fields (all nullable on existing rows):
//   subjectKind          — entity kind label (Collection/DataObject/…)
//   subjectAppId         — appId of the annotated entity
//   vocabularyId         — appId of the :Vocabulary node this predicate belongs to
//   sourceMode           — provenance mode: human | ai | collaborative
//   sourceActivityAppId  — appId of the :Activity that created this annotation
//   validFromMillis      — epoch-millis; null = "always valid from creation"
//   validUntilMillis     — epoch-millis; null = "no expiry"
//   confidence           — float in [0,1]; null = "not specified"
//
// Note: this is DISTINCT from the existing `source` @Property("source") field
// which carries the TPL4 "attributes-backfill" tag (see ANNOTATION_SOURCE_ATTRIBUTES_BACKFILL).
// `sourceMode` is the f(ai)²r provenance mode; `source` is the backfill tag.
//
// Safe to re-run: CREATE INDEX … IF NOT EXISTS is idempotent.
// Rollback: DROP INDEX SemanticAnnotation_subjectAppId_idx IF EXISTS;
//           DROP INDEX SemanticAnnotation_vocabularyId_idx IF EXISTS;
//
// Operator runbook: no schema change required; additive-nullable fields.
// Aborts startup on error per MigrationsRunner's fail-fast posture.

// ── Index on subjectAppId ─────────────────────────────────────────────────────
// Used by "give me all annotations for this DataObject" queries — the primary
// ownership lookup once the polymorphic /v2/annotations/* surface lands (SEMA-V6-004).

CREATE INDEX SemanticAnnotation_subjectAppId_idx IF NOT EXISTS
FOR (n:SemanticAnnotation) ON (n.subjectAppId);

// ── Index on vocabularyId ─────────────────────────────────────────────────────
// Used by vocabulary-scoped facet queries ("list all annotations in vocab X").

CREATE INDEX SemanticAnnotation_vocabularyId_idx IF NOT EXISTS
FOR (n:SemanticAnnotation) ON (n.vocabularyId);
