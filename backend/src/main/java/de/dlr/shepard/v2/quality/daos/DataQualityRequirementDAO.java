package de.dlr.shepard.v2.quality.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.v2.quality.entities.DataQualityRequirement;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * TPL10 — data-access layer for {@link DataQualityRequirement} nodes.
 *
 * <p>DQRs are assigned to Collections via an {@code APPLIES_TO} relationship.
 * The DAO exposes collection-scoped list/lookup methods for the service layer.
 */
@ApplicationScoped
public class DataQualityRequirementDAO extends GenericDAO<DataQualityRequirement> {

  @Override
  public Class<DataQualityRequirement> getEntityType() {
    return DataQualityRequirement.class;
  }

  /**
   * List all {@link DataQualityRequirement} nodes assigned to a given Collection.
   *
   * @param collectionAppId the Collection's appId
   * @return ordered list (by creation order, appId ascending); empty list when none
   */
  public List<DataQualityRequirement> findByCollectionAppId(String collectionAppId) {
    String query =
      "MATCH (d:DataQualityRequirement)-[:APPLIES_TO]->(c:Collection {appId: $collectionAppId}) " +
      "RETURN d ORDER BY d.appId ASC";
    List<DataQualityRequirement> result = new ArrayList<>();
    for (var d : findByQuery(query, Map.of("collectionAppId", collectionAppId))) {
      result.add(d);
    }
    return result;
  }

  /**
   * Look up a DQR by its own {@code appId}.
   *
   * @param appId the DQR's appId
   * @return the matching entity, or {@code null} when not found
   */
  public DataQualityRequirement findByAppId(String appId) {
    String query =
      "MATCH (d:DataQualityRequirement) WHERE d.appId = $appId RETURN d LIMIT 1";
    var iter = findByQuery(query, Map.of("appId", appId)).iterator();
    return iter.hasNext() ? iter.next() : null;
  }

  /**
   * Resolve whether a DQR is actually assigned to the given Collection.
   * Prevents cross-collection manipulation of DQR records.
   *
   * @param dqrAppId        the DQR's appId
   * @param collectionAppId the Collection's appId
   * @return {@code true} when the APPLIES_TO relationship exists
   */
  public boolean isAssignedToCollection(String dqrAppId, String collectionAppId) {
    String query =
      "MATCH (d:DataQualityRequirement {appId: $dqrAppId})" +
      "-[:APPLIES_TO]->(c:Collection {appId: $collectionAppId}) " +
      "RETURN count(d) > 0 AS assigned LIMIT 1";
    var result = session.query(query, Map.of("dqrAppId", dqrAppId, "collectionAppId", collectionAppId));
    var it = result.queryResults().iterator();
    if (!it.hasNext()) return false;
    Object val = it.next().get("assigned");
    return Boolean.TRUE.equals(val);
  }

  /**
   * Create an {@code APPLIES_TO} relationship from the DQR node to the Collection.
   * The DQR node must already exist (created via {@code createOrUpdate} first).
   *
   * @param dqrAppId        the DQR's appId
   * @param collectionAppId the Collection's appId
   */
  public void assignToCollection(String dqrAppId, String collectionAppId) {
    String query =
      "MATCH (d:DataQualityRequirement {appId: $dqrAppId}), " +
      "      (c:Collection {appId: $collectionAppId}) " +
      "MERGE (d)-[:APPLIES_TO]->(c)";
    session.query(query, Map.of("dqrAppId", dqrAppId, "collectionAppId", collectionAppId));
  }

  /**
   * Remove the {@code APPLIES_TO} relationship and delete the DQR node.
   *
   * @param dqrNeo4jId internal OGM id of the DQR node to delete
   */
  public void deleteWithRelationships(Long dqrNeo4jId) {
    String query =
      "MATCH (d:DataQualityRequirement) WHERE id(d) = $id " +
      "DETACH DELETE d";
    session.query(query, Map.of("id", dqrNeo4jId));
  }

  /**
   * Fetch all DataObject appIds for a Collection.
   *
   * <p>Used as the baseline dataset for evaluation: returns every non-deleted
   * DataObject's {@code appId}.
   *
   * @param collectionAppId the Collection's appId
   * @return list of DataObject appId strings; empty when the collection has no DataObjects
   */
  public List<String> findDataObjectAppIds(String collectionAppId) {
    String query =
      "MATCH (c:Collection {appId: $collectionAppId})-[:has_dataobject]->(do:DataObject) " +
      "WHERE (do.deleted IS NULL OR do.deleted = false) " +
      "RETURN do.appId AS doAppId";
    List<String> ids = new ArrayList<>();
    var result = session.query(query, Map.of("collectionAppId", collectionAppId));
    for (var row : result.queryResults()) {
      Object id = row.get("doAppId");
      if (id != null) ids.add(id.toString());
    }
    return ids;
  }

  /**
   * For ANNOTATION_REQUIRED evaluation: return the appIds of DataObjects in the
   * given Collection that have a non-null, non-blank value for the specified
   * attribute key.
   *
   * <p>DataObject attributes are stored by the Neo4j OGM
   * {@code @Properties(delimiter = "||")} convention as individual node properties
   * with the key {@code attributes||<attributeKey>}. Cypher requires backtick-quoting
   * of property names that contain the {@code |} character.
   *
   * @param collectionAppId the Collection's appId
   * @param attributeKey    the attribute key name (e.g. {@code "status"})
   * @return set of DataObject appIds that pass the check (attribute present and non-blank)
   */
  public java.util.Set<String> findDataObjectsHavingAttribute(String collectionAppId, String attributeKey) {
    // Backtick-quote the dynamic property key to handle the "||" characters safely.
    // The key is built server-side from the DQR's ruleParam — we sanitise in the service.
    String propKey = "`attributes||" + attributeKey + "`";
    String query =
      "MATCH (c:Collection {appId: $collectionAppId})-[:has_dataobject]->(do:DataObject) " +
      "WHERE (do.deleted IS NULL OR do.deleted = false) " +
      "  AND do." + propKey + " IS NOT NULL " +
      "  AND do." + propKey + " <> '' " +
      "RETURN do.appId AS doAppId";
    java.util.Set<String> passing = new java.util.HashSet<>();
    var result = session.query(query, Map.of("collectionAppId", collectionAppId));
    for (var row : result.queryResults()) {
      Object id = row.get("doAppId");
      if (id != null) passing.add(id.toString());
    }
    return passing;
  }
}
