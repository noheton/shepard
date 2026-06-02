// PROJ-PREDICATES-1 (rollback) — remove the three Project predicate seeds.
//
// Only the :Predicate registry nodes minted by V107 are removed. Existing
// :SemanticAnnotation rows that carry one of the three IRIs as their
// propertyIRI are *not* touched — the data they encode is independent of
// the registry node.

MATCH (p:Predicate {source: 'V107-project-predicates'}) DETACH DELETE p;
