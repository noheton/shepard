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
    // C5b fix: bind containerId as a Cypher parameter rather than concatenating it.
    var query =
      "MATCH (c:StructuredDataContainer)-[:structureddata_in_container]->(s:StructuredData {oid: $oid}) WHERE ID(c)=$containerId %s".formatted(
          CypherQueryHelper.getReturnPart("s")
        );
    var results = findByQuery(query, Map.of("oid", oid, "containerId", containerId));
    return results.iterator().hasNext() ? results.iterator().next() : null;
  }

  @Override
  public Class<StructuredData> getEntityType() {
    return StructuredData.class;
  }
}
