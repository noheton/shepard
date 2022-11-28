package de.dlr.shepard.neo4Core.dao;

import java.util.Collections;
import java.util.List;
import java.util.stream.StreamSupport;

import de.dlr.shepard.neo4Core.entities.SemanticAnnotation;
import de.dlr.shepard.util.CypherQueryHelper;

public class SemanticAnnotationDAO extends GenericDAO<SemanticAnnotation> {

	public List<SemanticAnnotation> findAllSemanticAnnotations(long entityId) {
		String query;
		query = String.format("MATCH %s<-[ha:has_annotation]-(e) WHERE ID(e)=%d WITH a %s",
				CypherQueryHelper.getObjectPart("a", "SemanticAnnotation", false), entityId,
				CypherQueryHelper.getReturnPart("a", true));
		var queryResult = findByQuery(query, Collections.emptyMap());
		var ret = StreamSupport.stream(queryResult.spliterator(), false).toList();
		return ret;
	}

	@Override
	public Class<SemanticAnnotation> getEntityType() {
		return SemanticAnnotation.class;
	}

}
