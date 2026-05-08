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

  @Override
  public Class<StructuredData> getEntityType() {
    return StructuredData.class;
  }
}
