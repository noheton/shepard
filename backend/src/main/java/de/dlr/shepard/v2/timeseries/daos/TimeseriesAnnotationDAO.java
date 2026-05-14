package de.dlr.shepard.v2.timeseries.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.v2.timeseries.model.TimeseriesAnnotation;
import jakarta.enterprise.context.RequestScoped;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

@RequestScoped
public class TimeseriesAnnotationDAO extends GenericDAO<TimeseriesAnnotation> {

  public List<TimeseriesAnnotation> findByTimeseriesReferenceAppId(String refAppId) {
    String query =
      "MATCH (r:TimeseriesReference {appId: $refAppId})-[:has_timeseries_annotation]->" +
      CypherQueryHelper.getObjectPart("a", "TimeseriesAnnotation", false) +
      " " +
      CypherQueryHelper.getReturnPart("a");
    return StreamSupport
      .stream(findByQuery(query, Map.of("refAppId", refAppId)).spliterator(), false)
      .toList();
  }

  public TimeseriesAnnotation findByAppId(String appId) {
    String query =
      "MATCH " +
      CypherQueryHelper.getObjectPart("a", "TimeseriesAnnotation", false) +
      " WHERE a.appId = $appId " +
      CypherQueryHelper.getReturnPart("a");
    var iter = findByQuery(query, Map.of("appId", appId)).iterator();
    return iter.hasNext() ? iter.next() : null;
  }

  /** Creates the `has_timeseries_annotation` edge from the reference to the annotation. */
  public void linkToReference(String refAppId, String annotationAppId) {
    session.query(
      "MATCH (r:TimeseriesReference {appId: $refAppId}), " +
      "(a:TimeseriesAnnotation {appId: $annId}) " +
      "MERGE (r)-[:has_timeseries_annotation]->(a)",
      Map.of("refAppId", refAppId, "annId", annotationAppId)
    );
  }

  /** Removes the `has_timeseries_annotation` edge and deletes the annotation node. */
  public void unlinkAndDelete(String refAppId, TimeseriesAnnotation annotation) {
    session.query(
      "MATCH (r:TimeseriesReference {appId: $refAppId})-[rel:has_timeseries_annotation]->" +
      "(a:TimeseriesAnnotation {appId: $annId}) DELETE rel",
      Map.of("refAppId", refAppId, "annId", annotation.getAppId())
    );
    deleteByNeo4jId(annotation.getId());
  }

  @Override
  public Class<TimeseriesAnnotation> getEntityType() {
    return TimeseriesAnnotation.class;
  }
}
