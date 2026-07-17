package de.dlr.shepard.data.structureddata.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.data.structureddata.entities.StructuredData;
import jakarta.enterprise.context.RequestScoped;
import java.util.Map;

@RequestScoped
public class StructuredDataDAO extends GenericDAO<StructuredData> {

  /**
   * Find a structuredData by oid
   *
   * @param containerId StructuredDataContainer ID
   * @param oid         Identifies the structuredData
   *
   * @return the found structuredData or null
   */
  public StructuredData find(long containerId, String oid) {
    // L2c read-path swap: query by appId rather than the deprecated id() function.
    // Public method signature stays long for caller-compat until L2d.
    var query =
      "MATCH (c:StructuredDataContainer {appId: $containerAppId})-[:structureddata_in_container]->(s:StructuredData {oid: $oid}) %s".formatted(
          CypherQueryHelper.getReturnPart("s")
        );
    var results = findByQuery(query, Map.of("oid", oid, "containerAppId", resolveAppIdOrEmpty(containerId)));
    return results.iterator().hasNext() ? results.iterator().next() : null;
  }

  /**
   * APISIMP-CONTAINER-STATS-OGM-COUNT — count all {@link StructuredData} nodes in the
   * given {@link de.dlr.shepard.data.structureddata.entities.StructuredDataContainer}
   * using a single Cypher COUNT query. Replaces the OGM lazy-load in
   * {@code StructuredDataContainerKindHandler.getStats()}.
   *
   * @param containerAppId the container's appId
   * @return total number of structured-data items in that container
   */
  public long countByContainerAppId(String containerAppId) {
    String query =
        "MATCH (:StructuredDataContainer {appId: $cid})-[:structureddata_in_container]->(s:StructuredData) " +
        "RETURN count(s) AS total";
    var result = session.query(query, Map.of("cid", containerAppId));
    var iter = result.iterator();
    if (!iter.hasNext()) return 0L;
    Object v = iter.next().get("total");
    return v instanceof Number ? ((Number) v).longValue() : 0L;
  }

  @Override
  public Class<StructuredData> getEntityType() {
    return StructuredData.class;
  }
}
