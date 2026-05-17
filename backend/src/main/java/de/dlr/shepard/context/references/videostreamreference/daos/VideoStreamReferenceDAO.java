package de.dlr.shepard.context.references.videostreamreference.daos;

import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.context.references.videostreamreference.model.VideoStreamReference;
import de.dlr.shepard.context.version.daos.VersionableEntityDAO;
import jakarta.enterprise.context.RequestScoped;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * VID1a — DAO for {@link VideoStreamReference} Neo4j nodes.
 */
@RequestScoped
public class VideoStreamReferenceDAO extends VersionableEntityDAO<VideoStreamReference> {

  @Override
  public Class<VideoStreamReference> getEntityType() {
    return VideoStreamReference.class;
  }

  /**
   * List all non-deleted {@link VideoStreamReference} nodes whose parent
   * {@code :DataObject} carries the given OGM id.
   *
   * <p>Uses the L2c appId-based read path (same pattern as
   * {@link de.dlr.shepard.context.references.timeseriesreference.daos.TimeseriesReferenceDAO#findByDataObjectNeo4jId}).
   *
   * @param dataObjectId the Neo4j internal Long id of the parent DataObject
   * @return all non-deleted references attached to that DataObject
   */
  public List<VideoStreamReference> findByDataObjectNeo4jId(long dataObjectId) {
    String query =
      "MATCH (d:DataObject)-[hr:has_reference]->%s WHERE d.appId=$dataObjectAppId ".formatted(
          CypherQueryHelper.getObjectPart("r", "VideoStreamReference", false)
        ) +
      CypherQueryHelper.getReturnPart("r");

    var queryResult = findByQuery(query, Map.of("dataObjectAppId", resolveAppIdOrEmpty(dataObjectId)));

    return StreamSupport.stream(queryResult.spliterator(), false)
      .filter(r -> r.getDataObject() != null)
      .filter(r -> r.getDataObject().getId().equals(dataObjectId))
      .collect(Collectors.toList());
  }

  /**
   * Find a single {@link VideoStreamReference} by its {@code appId}.
   *
   * @param appId the UUID v7 application-level identifier
   * @return the reference, or {@code null} if not found
   */
  public VideoStreamReference findByAppId(String appId) {
    String query =
      "MATCH " +
      CypherQueryHelper.getObjectPart("r", "VideoStreamReference", false) +
      " WHERE r.appId = $appId " +
      CypherQueryHelper.getReturnPart("r");
    var iter = findByQuery(query, Map.of("appId", appId)).iterator();
    return iter.hasNext() ? iter.next() : null;
  }
}
