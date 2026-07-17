package de.dlr.shepard.context.references.structureddata.daos;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.common.util.TraversalRules;
import de.dlr.shepard.context.references.structureddata.entities.StructuredDataReference;
import de.dlr.shepard.context.version.daos.VersionableEntityDAO;
import jakarta.enterprise.context.RequestScoped;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

@RequestScoped
public class StructuredDataReferenceDAO extends VersionableEntityDAO<StructuredDataReference> {

  /**
   * Searches the database for references.
   *
   * @param dataObjectId identifies the dataObject
   * @return a List of references
   */
  public List<StructuredDataReference> findByDataObjectNeo4jId(long dataObjectId) {
    // L2c read-path swap: query by appId rather than the deprecated id() function.
    // Public method signature stays long for caller-compat until L2d.
    String query =
      "MATCH (d:DataObject)-[hr:has_reference]->%s WHERE d.appId=$dataObjectAppId ".formatted(
          CypherQueryHelper.getObjectPart("r", "StructuredDataReference", false)
        ) +
      CypherQueryHelper.getReturnPart("r");
    var queryResult = findByQuery(query, Map.of("dataObjectAppId", resolveAppIdOrEmpty(dataObjectId)));
    List<StructuredDataReference> result = StreamSupport.stream(queryResult.spliterator(), false)
      .filter(r -> r.getDataObject() != null)
      .filter(r -> r.getDataObject().getId().equals(dataObjectId))
      .toList();
    return result;
  }

  public List<StructuredDataReference> findReachableReferencesByShepardId(
    TraversalRules traversalRule,
    long collectionShepardId,
    long startShepardId,
    String userName
  ) {
    String query = getSearchForReachableReferencesByShepardIdQuery(
      traversalRule,
      collectionShepardId,
      startShepardId,
      userName
    );
    var queryResult = findByQuery(query, Collections.emptyMap());
    List<StructuredDataReference> ret = StreamSupport.stream(queryResult.spliterator(), false).toList();
    return ret;
  }

  public List<StructuredDataReference> findReachableReferencesByNeo4jId(
    TraversalRules traversalRule,
    long collectionShepardId,
    long startShepardId,
    String userName
  ) {
    var query = getSearchForReachableReferencesByNeo4jIdQuery(
      traversalRule,
      collectionShepardId,
      startShepardId,
      userName
    );
    var queryResult = findByQuery(query.cypher(), query.params());
    List<StructuredDataReference> ret = StreamSupport.stream(queryResult.spliterator(), false).toList();
    return ret;
  }

  public List<StructuredDataReference> findReachableReferencesByNeo4jId(
    long collectionId,
    long startId,
    String userName
  ) {
    var query = getSearchForReachableReferencesQuery(collectionId, startId, userName);
    var queryResult = findByQuery(query.cypher(), query.params());
    List<StructuredDataReference> ret = StreamSupport.stream(queryResult.spliterator(), false).toList();
    return ret;
  }

  public List<StructuredDataReference> findReachableReferencesByShepardId(
    long collectionShepardId,
    long startShepardId,
    String userName
  ) {
    String query = getSearchForReachableReferencesByShepardIdQuery(collectionShepardId, startShepardId, userName);
    var queryResult = findByQuery(query, Collections.emptyMap());
    List<StructuredDataReference> ret = StreamSupport.stream(queryResult.spliterator(), false).toList();
    return ret;
  }

  public List<StructuredDataReference> findReachableReferencesByNeo4jId(long collectionId, String userName) {
    var query = getSearchForReachableReferencesQuery(collectionId, userName);
    var queryResult = findByQuery(query.cypher(), query.params());
    List<StructuredDataReference> ret = StreamSupport.stream(queryResult.spliterator(), false).toList();
    return ret;
  }

  public List<StructuredDataReference> findReachableReferencesByShepardId(long collectionShepardId, String userName) {
    String query = getSearchForReachableReferencesByShepardIdQuery(collectionShepardId, userName);
    var queryResult = findByQuery(query, Collections.emptyMap());
    List<StructuredDataReference> ret = StreamSupport.stream(queryResult.spliterator(), false).toList();
    return ret;
  }

  /**
   * APISIMP-STRUCTURED-DATA-KIND — look up a {@link StructuredDataReference} by
   * its application-level UUID v7 ({@code appId}).
   *
   * @param appId UUID v7 of the reference
   * @return the matching entity, or {@code null} when not found
   */
  public StructuredDataReference findByAppId(String appId) {
    String query =
      "MATCH %s WHERE r.appId = $appId ".formatted(
          CypherQueryHelper.getObjectPart("r", "StructuredDataReference", false)
        ) +
      CypherQueryHelper.getReturnPart("r");
    var iter = findByQuery(query, Map.of("appId", appId));
    var it = iter.iterator();
    return it.hasNext() ? it.next() : null;
  }

  public List<StructuredDataReference> findByDataObjectShepardId(long dataObjectShepardId) {
    String query =
      String.format(
        "MATCH (d:DataObject)-[hr:has_reference]->%s WHERE d." + Constants.SHEPARD_ID + "=%d ",
        CypherQueryHelper.getObjectPart("r", "StructuredDataReference", false),
        dataObjectShepardId
      ) +
      CypherQueryHelper.getReturnPart("r");
    var queryResult = findByQuery(query, Collections.emptyMap());
    List<StructuredDataReference> result = StreamSupport.stream(queryResult.spliterator(), false)
      .filter(r -> r.getDataObject() != null)
      .filter(r -> r.getDataObject().getShepardId().equals(dataObjectShepardId))
      .toList();
    return result;
  }

  /**
   * APISIMP-REFS-HANDLER-PAGING-TAIL — count of non-deleted StructuredDataReferences under a
   * DataObject (resolved by appId). Pushes the predicate to Neo4j so the caller never loads all
   * rows just to count them.
   *
   * @param dataObjectAppId the parent DataObject's appId.
   * @return total count of matching, non-deleted StructuredDataReferences.
   */
  public int countByDataObjectAppId(String dataObjectAppId) {
    String query =
      "MATCH (d:DataObject {appId: $aid})-[:has_reference]->(r:StructuredDataReference) " +
      "WHERE (r.deleted IS NULL OR r.deleted = false) " +
      "RETURN count(r) AS cnt";
    for (var row : session.query(query, Map.of("aid", dataObjectAppId))) {
      Object cnt = row.get("cnt");
      return cnt instanceof Number n ? n.intValue() : 0;
    }
    return 0;
  }

  /**
   * APISIMP-SDR-LIST-APPID-PATH — unbounded list of non-deleted StructuredDataReferences
   * under a DataObject, resolved purely by appId. Used by the non-paged SPI overload in
   * {@code StructuredDataReferenceKindHandler} (MCP path). Avoids loading the DataObject
   * numeric Neo4j id.
   *
   * @param dataObjectAppId the parent DataObject's appId.
   * @return all non-deleted StructuredDataReferences; never null.
   */
  public List<StructuredDataReference> findByDataObjectAppId(String dataObjectAppId) {
    String query =
      "MATCH (d:DataObject {appId: $aid})-[hr:has_reference]->(r:StructuredDataReference) " +
      "WHERE (r.deleted IS NULL OR r.deleted = false) " +
      "RETURN r, d, hr " +
      "ORDER BY r.createdAt ASC";
    return StreamSupport.stream(
      findByQuery(query, Map.of("aid", dataObjectAppId)).spliterator(), false
    ).toList();
  }

  /**
   * APISIMP-REFS-HANDLER-PAGING-TAIL — paginated list of non-deleted StructuredDataReferences
   * under a DataObject (resolved by appId). Pushes SKIP/LIMIT to Neo4j.
   *
   * @param dataObjectAppId the parent DataObject's appId.
   * @param skip 0-based offset.
   * @param limit maximum rows (must be &gt; 0).
   * @return the StructuredDataReferences for the requested page; never null.
   */
  public List<StructuredDataReference> findByDataObjectAppId(String dataObjectAppId, int skip, int limit) {
    String query =
      "MATCH (d:DataObject {appId: $aid})-[hr:has_reference]->(r:StructuredDataReference) " +
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
  public Class<StructuredDataReference> getEntityType() {
    return StructuredDataReference.class;
  }
}
