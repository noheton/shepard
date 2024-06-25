MATCH (a:SemanticAnnotation {deleted: True}) DETACH DELETE a;

MATCH (a:SemanticAnnotation {deleted: False}) REMOVE a.createdAt, a.createdBy, a.updatedAt, a.updatedBy, a.deleted;

MATCH (a:SemanticAnnotation)-[r:has_annotation]->(b:SemanticAnnotation) DELETE r;

DROP INDEX idx_SemanticAnnotation_deleted IF EXISTS;
