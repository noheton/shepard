package de.dlr.shepard.neo4Core.dao;

import de.dlr.shepard.neo4Core.entities.StructuredDataContainer;
import de.dlr.shepard.util.CypherQueryHelper;
import de.dlr.shepard.util.CypherQueryHelper.Neighborhood;
import de.dlr.shepard.util.QueryParamHelper;
import jakarta.enterprise.context.RequestScoped;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequestScoped
public class StructuredDataContainerDAO extends GenericDAO<StructuredDataContainer> {

  public List<StructuredDataContainer> findAllStructuredDataContainers(QueryParamHelper params, String username) {
    String query;
    Map<String, Object> paramsMap = new HashMap<>();
    paramsMap.put("name", params.getName());
    if (params.hasPagination()) {
      paramsMap.put("offset", params.getPagination().getOffset());
      paramsMap.put("size", params.getPagination().getSize());
    }
    query = String.format(
      "MATCH %s WHERE %s WITH c",
      CypherQueryHelper.getObjectPart("c", "StructuredDataContainer", params.hasName()),
      CypherQueryHelper.getReadableByQuery("c", username)
    );
    if (params.hasOrderByAttribute()) {
      query += " " + CypherQueryHelper.getOrderByPart("c", params.getOrderByAttribute(), params.getOrderDesc());
    }
    if (params.hasPagination()) {
      query += " " + CypherQueryHelper.getPaginationPart();
    }
    query += " " + CypherQueryHelper.getReturnPart("c", Neighborhood.ESSENTIAL);
    var result = new ArrayList<StructuredDataContainer>();
    for (var container : findByQuery(query, paramsMap)) {
      if (matchName(container, params.getName())) {
        result.add(container);
      }
    }

    return result;
  }

  private boolean matchName(StructuredDataContainer container, String name) {
    return name == null || container.getName().equalsIgnoreCase(name);
  }

  @Override
  public Class<StructuredDataContainer> getEntityType() {
    return StructuredDataContainer.class;
  }
}
