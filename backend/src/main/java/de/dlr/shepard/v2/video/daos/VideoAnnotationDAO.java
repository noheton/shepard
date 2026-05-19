package de.dlr.shepard.v2.video.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.v2.video.model.VideoAnnotation;
import jakarta.enterprise.context.RequestScoped;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

@RequestScoped
public class VideoAnnotationDAO extends GenericDAO<VideoAnnotation> {

  public List<VideoAnnotation> findByVideoReferenceAppId(String refAppId) {
    String query =
      "MATCH (r:VideoStreamReference {appId: $refAppId})-[:has_video_annotation]->" +
      CypherQueryHelper.getObjectPart("a", "VideoAnnotation", false) +
      " " +
      CypherQueryHelper.getReturnPart("a");
    return StreamSupport
      .stream(findByQuery(query, Map.of("refAppId", refAppId)).spliterator(), false)
      .toList();
  }

  public VideoAnnotation findByAppId(String appId) {
    String query =
      "MATCH " +
      CypherQueryHelper.getObjectPart("a", "VideoAnnotation", false) +
      " WHERE a.appId = $appId " +
      CypherQueryHelper.getReturnPart("a");
    var iter = findByQuery(query, Map.of("appId", appId)).iterator();
    return iter.hasNext() ? iter.next() : null;
  }

  public void linkToReference(String refAppId, String annotationAppId) {
    session.query(
      "MATCH (r:VideoStreamReference {appId: $refAppId}), " +
      "(a:VideoAnnotation {appId: $annId}) " +
      "MERGE (r)-[:has_video_annotation]->(a)",
      Map.of("refAppId", refAppId, "annId", annotationAppId)
    );
  }

  public void unlinkAndDelete(String refAppId, VideoAnnotation annotation) {
    session.query(
      "MATCH (r:VideoStreamReference {appId: $refAppId})-[rel:has_video_annotation]->" +
      "(a:VideoAnnotation {appId: $annId}) DELETE rel",
      Map.of("refAppId", refAppId, "annId", annotation.getAppId())
    );
    deleteByNeo4jId(annotation.getId());
  }

  @Override
  public Class<VideoAnnotation> getEntityType() {
    return VideoAnnotation.class;
  }
}
