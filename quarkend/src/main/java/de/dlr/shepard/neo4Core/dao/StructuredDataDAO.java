package de.dlr.shepard.neo4Core.dao;

import de.dlr.shepard.mongoDB.StructuredData;
import de.dlr.shepard.util.CypherQueryHelper;
import java.util.Map;

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
    var query = String.format(
      "MATCH (c:StructuredDataContainer)-[:structureddata_in_container]->(s:StructuredData {oid: $oid}) WHERE ID(c)=%d %s",
      containerId,
      CypherQueryHelper.getReturnPart("s")
    );
    var results = findByQuery(query, Map.of("oid", oid));
    return results.iterator().hasNext() ? results.iterator().next() : null;
  }

  @Override
  public Class<StructuredData> getEntityType() {
    return StructuredData.class;
  }
}
