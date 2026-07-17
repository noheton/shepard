package de.dlr.shepard.data.file.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.data.file.entities.ShepardFile;
import jakarta.enterprise.context.RequestScoped;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

@RequestScoped
public class ShepardFileDAO extends GenericDAO<ShepardFile> {

  /**
   * Find a shepardFile by oid
   *
   * @param containerId FileContainer ID
   * @param oid         Identifies the shepardFile
   *
   * @return the found shepardFile or null
   */
  public ShepardFile find(long containerId, String oid) {
    // L2c read-path swap: query by appId rather than the deprecated id() function.
    // Public method signature stays long for caller-compat until L2d.
    var query =
      "MATCH (c:FileContainer {appId: $containerAppId})-[:file_in_container]->(f:ShepardFile {oid: $oid}) %s".formatted(
          CypherQueryHelper.getReturnPart("f")
        );
    var results = findByQuery(query, Map.of("oid", oid, "containerAppId", resolveAppIdOrEmpty(containerId)));
    return results.iterator().hasNext() ? results.iterator().next() : null;
  }

  /**
   * APISIMP-CONTAINER-STATS-OGM-COUNT — count all {@link ShepardFile} nodes in the
   * given {@link de.dlr.shepard.data.file.entities.FileContainer} using a single Cypher
   * COUNT query. Replaces the OGM lazy-load in {@code FileContainerKindHandler.getStats()}.
   *
   * @param containerAppId the container's appId
   * @return total number of files in that container
   */
  public long countByContainerAppId(String containerAppId) {
    String query = "MATCH (:FileContainer {appId: $cid})-[:file_in_container]->(f:ShepardFile) " +
        "RETURN count(f) AS total";
    var result = session.query(query, Map.of("cid", containerAppId));
    var iter = result.iterator();
    if (!iter.hasNext()) return 0L;
    Object v = iter.next().get("total");
    return v instanceof Number ? ((Number) v).longValue() : 0L;
  }

  /**
   * APISIMP-BUNDLE-KIND-TOIO-OGM — count {@link ShepardFile} nodes directly attached to the
   * given {@link de.dlr.shepard.context.references.file.entities.FileBundleReference} via
   * {@code [:has_payload]} using a single Cypher COUNT query. Replaces the OGM lazy-load
   * in {@code FileBundleReferenceKindHandler.toIO()}.
   *
   * @param bundleAppId the bundle's appId
   * @return total number of files directly on that bundle (not via FileGroup)
   */
  public long countByBundleReferenceAppId(String bundleAppId) {
    String query = "MATCH (:FileBundleReference {appId: $bid})-[:has_payload]->(f:ShepardFile) " +
        "RETURN count(f) AS total";
    var result = session.query(query, Map.of("bid", bundleAppId));
    var iter = result.iterator();
    if (!iter.hasNext()) return 0L;
    Object v = iter.next().get("total");
    return v instanceof Number ? ((Number) v).longValue() : 0L;
  }

  /**
   * Count all {@link ShepardFile} nodes attached to the given {@link de.dlr.shepard.context.references.file.entities.FileGroup}.
   * Pushes counting to Cypher — no OGM entity hydration.
   *
   * @param groupAppId the group's appId
   * @return total number of files in that group
   */
  public long countByGroupAppId(String groupAppId) {
    String query = "MATCH (:FileGroup {appId: $gid})-[:has_payload]->(f:ShepardFile) " +
        "RETURN count(f) AS total";
    var result = session.query(query, Map.of("gid", groupAppId));
    var iter = result.iterator();
    if (!iter.hasNext()) return 0L;
    Object v = iter.next().get("total");
    return v instanceof Number ? ((Number) v).longValue() : 0L;
  }

  /**
   * Return one page of {@link ShepardFile} nodes attached to the given group,
   * ordered by filename ascending. Pagination is pushed to Cypher (SKIP/LIMIT).
   *
   * @param groupAppId the group's appId
   * @param skip       0-based offset (pre-clamped by caller)
   * @param limit      maximum rows to return (pre-clamped by caller)
   * @return the requested page slice, possibly empty
   */
  public List<ShepardFile> findByGroupAppId(String groupAppId, int skip, int limit) {
    String query = "MATCH (:FileGroup {appId: $gid})-[:has_payload]->(f:ShepardFile) " +
        "WITH f ORDER BY f.filename ASC SKIP $skip LIMIT $limit " +
        CypherQueryHelper.getReturnPart("f");
    var result = findByQuery(query, Map.of("gid", groupAppId, "skip", (long) skip, "limit", (long) limit));
    return StreamSupport.stream(result.spliterator(), false).toList();
  }

  @Override
  public Class<ShepardFile> getEntityType() {
    return ShepardFile.class;
  }
}
