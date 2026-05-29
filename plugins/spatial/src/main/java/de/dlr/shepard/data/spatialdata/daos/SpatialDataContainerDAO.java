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
   * SPATIAL-V6-004 — look up a SpatialDataContainer by its UUID v7 {@code appId}.
   *
   * @param appId the container's appId
   * @return the matching entity, or {@code null} when not found / deleted
   */
  public SpatialDataContainer findByAppId(String appId) {
    if (appId == null || appId.isBlank()) {
      return null;
    }
    String query =
      "MATCH (c:SpatialDataContainer) WHERE c.appId = $appId AND (c.deleted IS NULL OR c.deleted = false) RETURN c LIMIT 1";
    var iter = findByQuery(query, Map.of("appId", appId)).iterator();
    return iter.hasNext() ? iter.next() : null;
  }

  @Override
  public Class<SpatialDataContainer> getEntityType() {
    return SpatialDataContainer.class;
  }
}
