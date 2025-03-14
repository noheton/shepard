package de.dlr.shepard.context.semantic.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.context.semantic.entities.SemanticRepository;
import jakarta.enterprise.context.RequestScoped;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequestScoped
public class SemanticRepositoryDAO extends GenericDAO<SemanticRepository> {

  public List<SemanticRepository> findAllSemanticRepositories(QueryParamHelper params) {
    Map<String, Object> paramsMap = new HashMap<>();
    if (params.hasName()) paramsMap.put("name", params.getName());
    var query = String.format(
      "MATCH %s WITH r",
      CypherQueryHelper.getObjectPart("r", "SemanticRepository", params.hasName())
    );
    if (params.hasOrderByAttribute()) {
      query += " " + CypherQueryHelper.getOrderByPart("r", params.getOrderByAttribute(), params.getOrderDesc());
    }
    if (params.hasPagination()) {
      paramsMap.put("offset", params.getPagination().getOffset());
      paramsMap.put("size", params.getPagination().getSize());
      query += " " + CypherQueryHelper.getPaginationPart();
    }
    query += " " + CypherQueryHelper.getReturnPart("r");
    var result = new ArrayList<SemanticRepository>();
    for (var rep : findByQuery(query, paramsMap)) {
      if (matchName(rep, params.getName())) {
        result.add(rep);
      }
    }

    return result;
  }

  private boolean matchName(SemanticRepository rep, String name) {
    return name == null || rep.getName().equalsIgnoreCase(name);
  }

  @Override
  public Class<SemanticRepository> getEntityType() {
    return SemanticRepository.class;
  }
}
