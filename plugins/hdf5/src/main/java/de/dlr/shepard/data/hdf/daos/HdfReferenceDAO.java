package de.dlr.shepard.data.hdf.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.data.hdf.entities.HdfReference;
import jakarta.enterprise.context.RequestScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A5c — neo4j-ogm DAO over {@link HdfReference}.
 *
 * <p>The primary read pattern is "list all HdfReferences for a given
 * DataObject appId" — the Cypher traverses the inherited
 * {@code HAS_REFERENCE} edge from the DataObject node. The secondary
 * pattern is a point lookup by {@link HdfReference#getAppId()}.
 */
@RequestScoped
public class HdfReferenceDAO extends GenericDAO<HdfReference> {

  /**
   * Return all non-deleted {@link HdfReference} nodes reachable from
   * the DataObject identified by {@code dataObjectAppId}.
   *
   * @param dataObjectAppId UUID v7 of the owning DataObject.
   * @return list of matching references; empty if none or DataObject
   *         not found.
   */
  public List<HdfReference> findByDataObjectAppId(String dataObjectAppId) {
    if (dataObjectAppId == null || dataObjectAppId.isBlank()) {
      return List.of();
    }
    // HdfReference uses hard-delete (deleteByNeo4jId) so no soft-delete filter is needed.
    String query =
      "MATCH (d:DataObject {appId: $appId, deleted: false})-[:has_reference]->(r:HdfReference) " +
      "RETURN r";
    List<HdfReference> result = new ArrayList<>();
    for (HdfReference ref : findByQuery(query, Map.of("appId", dataObjectAppId))) {
      result.add(ref);
    }
    return result;
  }

  /**
   * Look up an {@link HdfReference} by its appId.
   *
   * @param appId UUID v7 of the reference.
   * @return the row, or {@code null} if no match.
   */
  public HdfReference findByAppId(String appId) {
    if (appId == null || appId.isBlank()) return null;
    // Hard-delete model: no deleted filter needed.
    String query =
      "MATCH (r:HdfReference {appId: $appId}) " +
      "RETURN r";
    var iter = findByQuery(query, Map.of("appId", appId)).iterator();
    return iter.hasNext() ? iter.next() : null;
  }

  @Override
  public Class<HdfReference> getEntityType() {
    return HdfReference.class;
  }
}
