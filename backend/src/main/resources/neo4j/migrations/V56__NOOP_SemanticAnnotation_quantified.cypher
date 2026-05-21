// QA-1 — :SemanticAnnotation gains two optional properties: numericValue (Double)
// and unitIRI (String). Neo4j properties are schema-free; no DDL change required.
//
// New properties are written on the first save of any SemanticAnnotation that
// carries a numeric rendering. Existing annotations without numeric values keep
// working; the properties are simply absent (not null-valued) until set.
//
// Operator runbook: none required. To inspect annotations that now carry numeric
// values after the upgrade:
//   MATCH (a:SemanticAnnotation) WHERE a.numericValue IS NOT NULL
//   RETURN a.appId, a.propertyName, a.numericValue, a.unitIRI LIMIT 50;
//
// Rollback (data-only, no schema): if you must revert, strip the new properties:
//   MATCH (a:SemanticAnnotation)
//   WHERE a.numericValue IS NOT NULL OR a.unitIRI IS NOT NULL
//   REMOVE a.numericValue, a.unitIRI;
RETURN 1;
