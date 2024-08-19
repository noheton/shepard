package de.dlr.shepard.neo4Core.dao;

import de.dlr.shepard.neo4Core.entities.SemanticAnnotation;
import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.CypherQueryHelper;
import de.dlr.shepard.util.CypherQueryHelper.Neighborhood;
import jakarta.enterprise.context.RequestScoped;
import java.util.Collections;
import java.util.List;
import java.util.stream.StreamSupport;

@RequestScoped
public class SemanticAnnotationDAO extends GenericDAO<SemanticAnnotation> {

  public List<SemanticAnnotation> findAllSemanticAnnotationsByNeo4jId(long entityId) {
    String query;
    query = String.format(
      "MATCH (e)-[ha:has_annotation]->(a:SemanticAnnotation) WHERE ID(e)=%d WITH a %s",
      entityId,
      CypherQueryHelper.getReturnPart("a", Neighborhood.OUTGOING)
    );
    var queryResult = findByQuery(query, Collections.emptyMap());
    var ret = StreamSupport.stream(queryResult.spliterator(), false).toList();
    return ret;
  }

  public List<SemanticAnnotation> findAllSemanticAnnotationsByShepardId(long shepardId) {
    String query;
    query = String.format(
      "MATCH (e)-[ha:has_annotation]->(a:SemanticAnnotation) WHERE e." + Constants.SHEPARD_ID + "=%d WITH a %s",
      shepardId,
      CypherQueryHelper.getReturnPart("a", Neighborhood.OUTGOING)
    );
    var queryResult = findByQuery(query, Collections.emptyMap());
    var ret = StreamSupport.stream(queryResult.spliterator(), false).toList();
    return ret;
  }

  @Override
  public Class<SemanticAnnotation> getEntityType() {
    return SemanticAnnotation.class;
  }
}
