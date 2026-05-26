// SEMA-V6-003 — idempotent property-existence pass for the :SemanticConfig
// singleton's four v6 fields.
//
// The :SemanticConfig node is a schemaless Neo4j singleton: no ALTER TABLE
// exists. This migration sets each new field to a safe default on any
// existing node that does not already have it, so that first reads from the
// application layer see a non-null value for the boolean flags.
//
// New fields:
//   defaultVocabularyAppId  — nullable String; null = "no default vocabulary"
//   annotationMode          — String; "PERMISSIVE" (default) | "STRICT"
//   suggestionEnabled       — boolean (stored as bool); default false
//   suggestionModelId       — nullable String; null = "server default"
//
// MERGE key: uses the SemanticConfig node as found; SET only fires when the
// property is currently absent (coalesce null check).
//
// Safe to re-run: SET only changes properties that are currently null.
// Rollback (removes only the new fields; preserves existing ones):
//   MATCH (c:SemanticConfig)
//   REMOVE c.defaultVocabularyAppId, c.annotationMode, c.suggestionEnabled, c.suggestionModelId;
//
// Note: this migration sets defaults on existing rows only. New rows seeded
// by OntologyConfigService.loadSingleton() carry the field defaults from the
// Java entity class (annotationMode = "PERMISSIVE", suggestionEnabled = false).
//
// Aborts startup on error per MigrationsRunner's fail-fast posture.

MATCH (c:SemanticConfig)
SET
  c.annotationMode    = coalesce(c.annotationMode,    "PERMISSIVE"),
  c.suggestionEnabled = coalesce(c.suggestionEnabled, false);
