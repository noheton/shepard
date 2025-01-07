package de.dlr.shepard.data.file.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.data.file.entities.ShepardFile;
import jakarta.enterprise.context.RequestScoped;
import java.util.Map;

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
    var query = String.format(
      "MATCH (c:FileContainer)-[:file_in_container]->(f:ShepardFile {oid: $oid}) WHERE ID(c)=%d %s",
      containerId,
      CypherQueryHelper.getReturnPart("f")
    );
    var results = findByQuery(query, Map.of("oid", oid));
    return results.iterator().hasNext() ? results.iterator().next() : null;
  }

  @Override
  public Class<ShepardFile> getEntityType() {
    return ShepardFile.class;
  }
}
