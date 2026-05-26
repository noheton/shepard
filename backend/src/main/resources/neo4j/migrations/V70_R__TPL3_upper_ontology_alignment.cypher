// TPL3a-lite — rollback for V67__TPL3_upper_ontology_alignment.cypher
//
// Removes all (:OntologyAlignment) nodes seeded by the forward migration and
// drops the composite node-key constraint.
//
// Safe to re-run: MATCH/DETACH DELETE is idempotent when no nodes exist;
// DROP CONSTRAINT IF EXISTS does not error when the constraint is absent.
//
// NOTE: this rollback removes ALL :OntologyAlignment nodes, including any
// created by operators after the migration ran.  Operators who have added
// custom alignment rows should export them first.

MATCH (n:OntologyAlignment)
DETACH DELETE n;

DROP CONSTRAINT OntologyAlignment_concept_uri_unique IF EXISTS;
