package de.dlr.shepard.v2.timeseries.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.v2.timeseries.model.TimeseriesAnnotation;
import jakarta.enterprise.context.RequestScoped;
import java.util.HashMap;
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

  public List<TimeseriesAnnotation> findByTimeseriesReferenceAppId(String refAppId, int skip, int limit) {
    String query =
      "MATCH (r:TimeseriesReference {appId: $refAppId})-[:has_timeseries_annotation]->" +
      CypherQueryHelper.getObjectPart("a", "TimeseriesAnnotation", false) +
      " RETURN a ORDER BY a.appId SKIP $skip LIMIT $limit";
    Map<String, Object> params = new HashMap<>();
    params.put("refAppId", refAppId);
    params.put("skip", (long) skip);
    params.put("limit", (long) limit);
    return StreamSupport
      .stream(findByQuery(query, params).spliterator(), false)
      .toList();
  }

  public long countByTimeseriesReferenceAppId(String refAppId) {
    String query =
      "MATCH (r:TimeseriesReference {appId: $refAppId})-[:has_timeseries_annotation]->" +
      CypherQueryHelper.getObjectPart("a", "TimeseriesAnnotation", false) +
      " RETURN count(a) AS total";
    for (var row : session.query(query, Map.of("refAppId", refAppId)).queryResults()) {
      Object val = row.get("total");
      if (val instanceof Number n) return n.longValue();
    }
    return 0L;
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

  // ── container-level temporal annotations (TS-ANNOT-B) ────────────────────

  public List<TimeseriesAnnotation> findByContainerId(long containerId) {
    String query =
      "MATCH (c:TimeseriesContainer) WHERE id(c) = $cid " +
      "MATCH (c)-[:has_temporal_annotation]->" +
      CypherQueryHelper.getObjectPart("a", "TimeseriesAnnotation", false) +
      " " +
      CypherQueryHelper.getReturnPart("a");
    return StreamSupport
      .stream(findByQuery(query, Map.of("cid", containerId)).spliterator(), false)
      .toList();
  }

  public void linkToContainer(long containerId, String annotationAppId) {
    session.query(
      "MATCH (c:TimeseriesContainer) WHERE id(c) = $cid " +
      "MATCH (a:TimeseriesAnnotation {appId: $annId}) " +
      "MERGE (c)-[:has_temporal_annotation]->(a)",
      Map.of("cid", containerId, "annId", annotationAppId)
    );
  }

  public void unlinkAndDeleteFromContainer(long containerId, TimeseriesAnnotation annotation) {
    session.query(
      "MATCH (c:TimeseriesContainer)-[rel:has_temporal_annotation]->" +
      "(a:TimeseriesAnnotation {appId: $annId}) WHERE id(c) = $cid DELETE rel",
      Map.of("cid", containerId, "annId", annotation.getAppId())
    );
    deleteByNeo4jId(annotation.getId());
  }

  @Override
  public Class<TimeseriesAnnotation> getEntityType() {
    return TimeseriesAnnotation.class;
  }
}
