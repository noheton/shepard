package de.dlr.shepard.context.references.spatialdata.daos;

import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.context.references.spatialdata.entities.SpatialDataReference;
import de.dlr.shepard.context.version.daos.VersionableEntityDAO;
import jakarta.enterprise.context.RequestScoped;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

@RequestScoped
public class SpatialDataReferenceDAO extends VersionableEntityDAO<SpatialDataReference> {

  public List<SpatialDataReference> findByDataObjectShepardId(long dataObjectShepardId) {
    String query =
      "MATCH (d:DataObject {shepardId: %d})-[hr:has_reference]->%s".formatted(
          dataObjectShepardId,
          CypherQueryHelper.getObjectPart("r", "SpatialDataReference", false)
        ) +
      CypherQueryHelper.getReturnPart("r");
    var queryResult = findByQuery(query, Collections.emptyMap());

    List<SpatialDataReference> result = StreamSupport.stream(queryResult.spliterator(), false)
      .filter(r -> r.getDataObject() != null && r.getDataObject().getShepardId().equals(dataObjectShepardId))
      .toList();

    return result;
  }

  /**
   * SPATIAL-UNIFY-002 — list all non-deleted {@link SpatialDataReference}
   * nodes attached to the DataObject identified by its {@code appId}. The
   * appId-keyed read path the unified {@code /v2/references?kind=spatial}
   * surface needs (mirrors {@code VideoStreamReferenceDAO}).
   *
   * @param dataObjectAppId parent DataObject's UUID v7 appId
   * @return all non-deleted references attached to that DataObject
   */
  public List<SpatialDataReference> findByDataObjectAppId(String dataObjectAppId) {
    String query =
      "MATCH (d:DataObject)-[hr:has_reference]->%s WHERE d.appId = $dataObjectAppId ".formatted(
          CypherQueryHelper.getObjectPart("r", "SpatialDataReference", false)
        ) +
      CypherQueryHelper.getReturnPart("r");
    var queryResult = findByQuery(query, Map.of("dataObjectAppId", dataObjectAppId));

    return StreamSupport.stream(queryResult.spliterator(), false)
      .filter(r -> r != null && !r.isDeleted())
      .filter(r -> r.getDataObject() != null && dataObjectAppId.equals(r.getDataObject().getAppId()))
      .toList();
  }

  /**
   * SPATIAL-UNIFY-002 — find one {@link SpatialDataReference} by its
   * {@code appId} (UUID v7). Returns {@code null} when not found.
   *
   * @param appId the reference's appId
   * @return the reference, or {@code null} if not found
   */
  public SpatialDataReference findByAppId(String appId) {
    String query =
      "MATCH " +
      CypherQueryHelper.getObjectPart("r", "SpatialDataReference", false) +
      " WHERE r.appId = $appId " +
      CypherQueryHelper.getReturnPart("r");
    var iter = findByQuery(query, Map.of("appId", appId)).iterator();
    return iter.hasNext() ? iter.next() : null;
  }

  @Override
  public Class<SpatialDataReference> getEntityType() {
    return SpatialDataReference.class;
  }
}
