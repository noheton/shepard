// V69_R__TPL4_attributes_to_annotations_backfill.cypher
//
// ROLLBACK for V69__TPL4_attributes_to_annotations_backfill.cypher
//
// Deletes all :SemanticAnnotation nodes with source = 'attributes-backfill'
// (i.e. those created by the TPL4 dual-write path, whether via the V69
// forward migration or the service-layer dual-write).
//
// Safe to run multiple times — idempotent.
// Does NOT affect hand-authored annotations (those have source = null).
//
// Operator note: after running this rollback, also set
//   shepard.tpl4.dual-write.enabled=false
// in your application.properties (or env var) and restart the backend
// to prevent new backfill annotations from being created.

MATCH (sa:SemanticAnnotation {source: 'attributes-backfill'})
DETACH DELETE sa
RETURN count(sa) AS annotationsDeleted;
