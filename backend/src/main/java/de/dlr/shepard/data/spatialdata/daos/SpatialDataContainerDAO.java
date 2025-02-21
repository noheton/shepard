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

    query = String.format(
      "MATCH %s WHERE %s WITH c",
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

  @Override
  public Class<SpatialDataContainer> getEntityType() {
    return SpatialDataContainer.class;
  }
}
