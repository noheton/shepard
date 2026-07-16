package de.dlr.shepard.context.references.dataobject.daos;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.context.references.dataobject.entities.DataObjectReference;
import de.dlr.shepard.context.version.daos.VersionableEntityDAO;
import jakarta.enterprise.context.RequestScoped;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

@RequestScoped
public class DataObjectReferenceDAO extends VersionableEntityDAO<DataObjectReference> {

  /**
   * Searches the database for references.
   *
   * @param dataObjectId identifies the dataObject
   * @return a List of references
   */
  public List<DataObjectReference> findByDataObjectNeo4jId(long dataObjectId) {
    // L2c read-path swap: query by appId rather than the deprecated id() function.
    // Public method signature stays long for caller-compat until L2d.
    String query =
      "MATCH (d:DataObject)-[hr:has_reference]->%s WHERE d.appId=$dataObjectAppId ".formatted(
          CypherQueryHelper.getObjectPart("r", "DataObjectReference", false)
        ) +
      CypherQueryHelper.getReturnPart("r");

    var queryResult = findByQuery(query, Map.of("dataObjectAppId", resolveAppIdOrEmpty(dataObjectId)));

    List<DataObjectReference> result = StreamSupport.stream(queryResult.spliterator(), false)
      .filter(r -> r.getDataObject() != null)
      .filter(r -> r.getDataObject().getId().equals(dataObjectId))
      .toList();

    return result;
  }

  public List<DataObjectReference> findByDataObjectShepardId(long dataObjectShepardId, UUID versionUID) {
    String query =
      String.format(
        "MATCH (d:DataObject)-[hr:has_reference]->%s WHERE d." + Constants.SHEPARD_ID + "=%d ",
        CypherQueryHelper.getObjectPart("r", "DataObjectReference", false),
        dataObjectShepardId
      ) +
      CypherQueryHelper.getReturnPart("r");
    StringBuffer queryBuffer = new StringBuffer();
    queryBuffer.append("MATCH (v:Version)<-[:has_version]-(d:DataObject)-[hr:has_reference]->");
    queryBuffer.append(CypherQueryHelper.getObjectPart("r", "DataObjectReference", false));
    queryBuffer.append(" WHERE d." + Constants.SHEPARD_ID + "=" + dataObjectShepardId + " AND ");
    if (versionUID == null) queryBuffer.append(CypherQueryHelper.getVersionHeadPart("v"));
    else queryBuffer.append(CypherQueryHelper.getVersionPart("v", versionUID));
    queryBuffer.append(" " + CypherQueryHelper.getReturnPart("r"));
    query = queryBuffer.toString();
    var queryResult = findByQuery(query, Collections.emptyMap());
    List<DataObjectReference> result = StreamSupport.stream(queryResult.spliterator(), false)
      .filter(r -> r.getDataObject() != null)
      .filter(r -> r.getDataObject().getShepardId().equals(dataObjectShepardId))
      .toList();
    return result;
  }

  /**
   * Looks up a DataObjectReference by its application-level UUID v7 ({@code appId}).
   * V2-SWEEP-004-1: required by {@code DataObjectReferenceKindHandler}.
   *
   * @param appId UUID v7 of the reference
   * @return the matching {@link DataObjectReference}, or {@code null} when not found
   */
  public DataObjectReference findByAppId(String appId) {
    String query =
      "MATCH %s WHERE r.appId = $appId ".formatted(
          CypherQueryHelper.getObjectPart("r", "DataObjectReference", false)
        ) +
      CypherQueryHelper.getReturnPart("r");
    var iter = findByQuery(query, Map.of("appId", appId));
    var it = iter.iterator();
    return it.hasNext() ? it.next() : null;
  }

  /**
   * APISIMP-REFS-HANDLER-PAGING-TAIL — count of non-deleted DataObjectReferences under a
   * DataObject (resolved by appId). Pushes the predicate to Neo4j so the caller never loads all
   * rows just to count them.
   *
   * @param dataObjectAppId the parent DataObject's appId.
   * @return total count of matching, non-deleted DataObjectReferences.
   */
  public int countByDataObjectAppId(String dataObjectAppId) {
    String query =
      "MATCH (d:DataObject {appId: $aid})-[:has_reference]->(r:DataObjectReference) " +
      "WHERE (r.deleted IS NULL OR r.deleted = false) " +
      "RETURN count(r) AS cnt";
    for (var row : session.query(query, Map.of("aid", dataObjectAppId))) {
      Object cnt = row.get("cnt");
      return cnt instanceof Number n ? n.intValue() : 0;
    }
    return 0;
  }

  /**
   * APISIMP-REFS-HANDLER-PAGING-TAIL — paginated list of non-deleted DataObjectReferences under a
   * DataObject (resolved by appId). Pushes SKIP/LIMIT to Neo4j.
   *
   * @param dataObjectAppId the parent DataObject's appId.
   * @param skip 0-based offset.
   * @param limit maximum rows (must be &gt; 0).
   * @return the DataObjectReferences for the requested page; never null.
   */
  public List<DataObjectReference> findByDataObjectAppId(String dataObjectAppId, int skip, int limit) {
    String query =
      "MATCH (d:DataObject {appId: $aid})-[hr:has_reference]->(r:DataObjectReference) " +
      "WHERE (r.deleted IS NULL OR r.deleted = false) " +
      "RETURN r, d, hr " +
      "ORDER BY r.createdAt ASC " +
      "SKIP $skip LIMIT $limit";
    var queryResult = findByQuery(
      query,
      Map.of("aid", dataObjectAppId, "skip", (long) skip, "limit", (long) limit)
    );
    return StreamSupport.stream(queryResult.spliterator(), false).toList();
  }

  @Override
  public Class<DataObjectReference> getEntityType() {
    return DataObjectReference.class;
  }
}
