package de.dlr.shepard.v2.importer.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.v2.importer.entities.ImportPlan;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * IMP1 — data-access layer for {@link ImportPlan} nodes.
 *
 * <p>Plans are write-once; this DAO provides lookups by {@code commitId}
 * (the plan seal used by the import-jobs endpoint) and by collection.
 */
@ApplicationScoped
public class ImportPlanDAO extends GenericDAO<ImportPlan> {

  @Override
  public Class<ImportPlan> getEntityType() {
    return ImportPlan.class;
  }

  /**
   * Look up a plan by its commitId (plan seal).
   *
   * @param commitId the plan seal to look up
   * @return the matching plan, or {@code null} if not found
   */
  public ImportPlan findByCommitId(String commitId) {
    String query =
      "MATCH (p:ImportPlan) WHERE p.commitId = $commitId RETURN p LIMIT 1";
    var iter = findByQuery(query, Map.of("commitId", commitId)).iterator();
    return iter.hasNext() ? iter.next() : null;
  }

  /**
   * List all plans targeting a given Collection (ordered newest-first).
   *
   * @param collectionAppId the appId of the target Collection
   * @return plans for that collection, most recent first
   */
  public List<ImportPlan> findByCollectionAppId(String collectionAppId) {
    String query =
      "MATCH (p:ImportPlan) " +
      "WHERE p.collectionAppId = $collectionAppId " +
      "RETURN p ORDER BY p.validatedAt DESC";
    List<ImportPlan> result = new ArrayList<>();
    for (var p : findByQuery(query, Map.of("collectionAppId", collectionAppId))) {
      result.add(p);
    }
    return result;
  }

  /**
   * Count of non-deleted DataObjects directly owned by the given Collection.
   *
   * <p>Used by the context endpoint so it can return {@code dataObjectCount}
   * without loading the full DataObject list.
   *
   * @param collectionAppId the target Collection's appId
   * @return number of non-deleted DataObjects in the Collection
   */
  public long countDataObjects(String collectionAppId) {
    String query =
      "MATCH (c:Collection {appId: $appId})-[:has_dataobject]->(d:DataObject) " +
      "WHERE d.deleted IS NULL OR d.deleted = false " +
      "RETURN count(d) AS cnt";
    var result = session.query(query, Map.of("appId", collectionAppId));
    var iter = result.iterator();
    if (!iter.hasNext()) return 0L;
    Object countObj = iter.next().get("cnt");
    return countObj != null ? ((Number) countObj).longValue() : 0L;
  }

  /**
   * Compute a fingerprint for the collection based on DataObject count and
   * maximum creation timestamp.
   *
   * <p>Returns the raw values as a {@code String} in the form
   * {@code count + "|" + maxCreatedAt} so the service can SHA-256 it.
   * Returns {@code "0|0"} when the collection has no DataObjects.
   *
   * @param collectionAppId the target Collection's appId
   * @return raw fingerprint input string
   */
  public String getRawCollectionFingerprintInput(String collectionAppId) {
    String query =
      "MATCH (c:Collection {appId: $appId}) " +
      "OPTIONAL MATCH (d:DataObject)-[:has_dataobject]-(c) " +
      "RETURN count(d) AS doCount, max(d.createdAt) AS lastCreated";
    var result = session.query(query, Map.of("appId", collectionAppId));
    var iter = result.iterator();
    if (!iter.hasNext()) {
      return "0|0";
    }
    var row = iter.next();
    Object countObj = row.get("doCount");
    Object lastObj = row.get("lastCreated");
    long count = countObj != null ? ((Number) countObj).longValue() : 0L;
    long last = lastObj != null ? ((Number) lastObj).longValue() : 0L;
    return count + "|" + last;
  }

  /**
   * Find DataObject names in the given Collection that clash with the
   * supplied list of candidate names.
   *
   * @param collectionAppId the target Collection's appId
   * @param names           candidate names to check for conflicts
   * @return subset of {@code names} that already exist in the Collection
   */
  public List<String> findExistingNames(String collectionAppId, List<String> names) {
    if (names == null || names.isEmpty()) {
      return List.of();
    }
    String query =
      "MATCH (c:Collection {appId: $appId})-[:has_dataobject]->(d:DataObject) " +
      "WHERE d.name IN $names AND (d.deleted IS NULL OR d.deleted = false) " +
      "RETURN d.name AS name";
    var result = session.query(query, Map.of("appId", collectionAppId, "names", names));
    List<String> existing = new ArrayList<>();
    for (var row : result) {
      Object n = row.get("name");
      if (n != null) existing.add(n.toString());
    }
    return existing;
  }
}
