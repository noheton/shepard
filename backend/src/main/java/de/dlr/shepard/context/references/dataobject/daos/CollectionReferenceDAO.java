package de.dlr.shepard.context.references.dataobject.daos;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.context.references.dataobject.entities.CollectionReference;
import de.dlr.shepard.context.version.daos.VersionableEntityDAO;
import jakarta.enterprise.context.RequestScoped;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

@RequestScoped
public class CollectionReferenceDAO extends VersionableEntityDAO<CollectionReference> {

  /**
   * Searches the database for references.
   *
   * @param dataObjectId identifies the dataObject
   * @return a List of references
   */
  public List<CollectionReference> findByDataObjectNeo4jId(long dataObjectId) {
    // L2c read-path swap: query by appId rather than the deprecated id() function.
    // Public method signature stays long for caller-compat until L2d.
    String query =
      "MATCH (d:DataObject)-[hr:has_reference]->%s WHERE d.appId=$dataObjectAppId ".formatted(
          CypherQueryHelper.getObjectPart("r", "CollectionReference", false)
        ) +
      CypherQueryHelper.getReturnPart("r");

    var queryResult = findByQuery(query, Map.of("dataObjectAppId", resolveAppIdOrEmpty(dataObjectId)));

    List<CollectionReference> result = StreamSupport.stream(queryResult.spliterator(), false)
      .filter(r -> r.getDataObject() != null)
      .filter(r -> r.getDataObject().getId().equals(dataObjectId))
      .toList();

    return result;
  }

  public List<CollectionReference> findByDataObjectShepardId(long dataObjectShepardId) {
    String query =
      String.format(
        "MATCH (d:DataObject)-[hr:has_reference]->%s WHERE d." + Constants.SHEPARD_ID + "=%d ",
        CypherQueryHelper.getObjectPart("r", "CollectionReference", false),
        dataObjectShepardId
      ) +
      CypherQueryHelper.getReturnPart("r");
    var queryResult = findByQuery(query, Collections.emptyMap());

    List<CollectionReference> result = StreamSupport.stream(queryResult.spliterator(), false)
      .filter(r -> r.getDataObject() != null)
      .filter(r -> r.getDataObject().getShepardId().equals(dataObjectShepardId))
      .toList();

    return result;
  }

  /**
   * Looks up a CollectionReference by its application-level UUID v7 ({@code appId}).
   * V2-SWEEP-004-1: required by {@code CollectionReferenceKindHandler}.
   *
   * @param appId UUID v7 of the reference
   * @return the matching {@link CollectionReference}, or {@code null} when not found
   */
  public CollectionReference findByAppId(String appId) {
    String query =
      "MATCH %s WHERE r.appId = $appId ".formatted(
          CypherQueryHelper.getObjectPart("r", "CollectionReference", false)
        ) +
      CypherQueryHelper.getReturnPart("r");
    var iter = findByQuery(query, Map.of("appId", appId));
    var it = iter.iterator();
    return it.hasNext() ? it.next() : null;
  }

  /**
   * APISIMP-REFS-HANDLER-PAGING-TAIL — count of non-deleted CollectionReferences under a
   * DataObject (resolved by appId). Pushes the predicate to Neo4j so the caller never loads all
   * rows just to count them.
   *
   * @param dataObjectAppId the parent DataObject's appId.
   * @return total count of matching, non-deleted CollectionReferences.
   */
  public int countByDataObjectAppId(String dataObjectAppId) {
    String query =
      "MATCH (d:DataObject {appId: $aid})-[:has_reference]->(r:CollectionReference) " +
      "WHERE (r.deleted IS NULL OR r.deleted = false) " +
      "RETURN count(r) AS cnt";
    for (var row : session.query(query, Map.of("aid", dataObjectAppId))) {
      Object cnt = row.get("cnt");
      return cnt instanceof Number n ? n.intValue() : 0;
    }
    return 0;
  }

  /**
   * APISIMP-URI-COLL-DOREF-NONPAGED-APPID — unbounded list of non-deleted CollectionReferences
   * under a DataObject, resolved purely by appId. Used by the non-paged SPI overload in
   * {@code CollectionReferenceKindHandler}. Avoids loading the DataObject numeric Neo4j id.
   *
   * @param dataObjectAppId the parent DataObject's appId.
   * @return all non-deleted CollectionReferences; never null.
   */
  public List<CollectionReference> findByDataObjectAppId(String dataObjectAppId) {
    String query =
      "MATCH (d:DataObject {appId: $aid})-[hr:has_reference]->(r:CollectionReference) " +
      "WHERE (r.deleted IS NULL OR r.deleted = false) " +
      "RETURN r, d, hr " +
      "ORDER BY r.createdAt ASC";
    return StreamSupport.stream(
      findByQuery(query, Map.of("aid", dataObjectAppId)).spliterator(), false
    ).toList();
  }

  /**
   * APISIMP-REFS-HANDLER-PAGING-TAIL — paginated list of non-deleted CollectionReferences under a
   * DataObject (resolved by appId). Pushes SKIP/LIMIT to Neo4j.
   *
   * @param dataObjectAppId the parent DataObject's appId.
   * @param skip 0-based offset.
   * @param limit maximum rows (must be &gt; 0).
   * @return the CollectionReferences for the requested page; never null.
   */
  public List<CollectionReference> findByDataObjectAppId(String dataObjectAppId, int skip, int limit) {
    String query =
      "MATCH (d:DataObject {appId: $aid})-[hr:has_reference]->(r:CollectionReference) " +
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
  public Class<CollectionReference> getEntityType() {
    return CollectionReference.class;
  }
}
