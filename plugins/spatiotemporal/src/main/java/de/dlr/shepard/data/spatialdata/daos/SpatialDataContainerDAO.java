package de.dlr.shepard.data.spatialdata.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.common.util.CypherQueryHelper.Neighborhood;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.data.spatialdata.model.SpatialDataContainer;
import jakarta.enterprise.context.RequestScoped;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequestScoped
public class SpatialDataContainerDAO extends GenericDAO<SpatialDataContainer> {

  public List<SpatialDataContainer> findAllSpatialContainers(QueryParamHelper params, String username) {
    String query;
    Map<String, Object> paramsMap = new HashMap<>();
    paramsMap.put("name", params.getName());
    if (params.hasPagination()) {
      paramsMap.put("offset", params.getPagination().getOffset());
      paramsMap.put("size", params.getPagination().getSize());
    }

    query = "MATCH %s WHERE %s WITH c".formatted(
        CypherQueryHelper.getObjectPart("c", "SpatialDataContainer", params.hasName()),
        CypherQueryHelper.getReadableByQuery("c", username)
      );
    if (params.hasOrderByAttribute()) {
      query += " " + CypherQueryHelper.getOrderByPart("c", params.getOrderByAttribute(), params.getOrderDesc());
    }
    if (params.hasPagination()) {
      query += " " + CypherQueryHelper.getPaginationPart();
    }
    query += " " + CypherQueryHelper.getReturnPart("c", Neighborhood.ESSENTIAL);
    var result = new ArrayList<SpatialDataContainer>();
    for (var container : findByQuery(query, paramsMap)) {
      if (matchName(container, params.getName())) {
        result.add(container);
      }
    }
    return result;
  }

  private boolean matchName(SpatialDataContainer container, String name) {
    return name == null || container.getName().equalsIgnoreCase(name);
  }

  /**
   * SPATIAL-UNIFY-002/004 — find one non-deleted {@link SpatialDataContainer}
   * by its {@code appId} (UUID v7). Returns {@code null} when not found. The
   * appId-keyed read path the unified surface + the promote endpoint need;
   * the container appId never appears on the wire as a first-class user
   * concept, but the unified create-by-container path resolves it here.
   *
   * @param appId the container's appId
   * @return the container, or {@code null} if not found or deleted
   */
  public SpatialDataContainer findByAppId(String appId) {
    String query =
      "MATCH " +
      CypherQueryHelper.getObjectPart("c", "SpatialDataContainer", false) +
      " WHERE c.appId = $appId " +
      CypherQueryHelper.getReturnPart("c");
    var iter = findByQuery(query, Map.of("appId", appId)).iterator();
    var container = iter.hasNext() ? iter.next() : null;
    return (container == null || container.isDeleted()) ? null : container;
  }

  /**
   * SPATIAL-UNIFY-004 — find the non-deleted {@link SpatialDataContainer} minted
   * by promoting the FileReference with the given {@code appId}, or {@code null}
   * when none exists. Drives the promote endpoint's idempotency.
   *
   * @param sourceFileReferenceAppId the source FileReference's appId
   * @return the existing container, or {@code null}
   */
  public SpatialDataContainer findBySourceFileReferenceAppId(String sourceFileReferenceAppId) {
    String query =
      "MATCH " +
      CypherQueryHelper.getObjectPart("c", "SpatialDataContainer", false) +
      " WHERE c.sourceFileReferenceAppId = $sourceFileReferenceAppId " +
      CypherQueryHelper.getReturnPart("c");
    var iter = findByQuery(query, Map.of("sourceFileReferenceAppId", sourceFileReferenceAppId)).iterator();
    var container = iter.hasNext() ? iter.next() : null;
    return (container == null || container.isDeleted()) ? null : container;
  }

  @Override
  public Class<SpatialDataContainer> getEntityType() {
    return SpatialDataContainer.class;
  }
}
