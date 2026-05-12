package de.dlr.shepard.data.hdf.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.common.util.CypherQueryHelper.Neighborhood;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.data.hdf.entities.HdfContainer;
import jakarta.enterprise.context.RequestScoped;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A5a Phase 1 — neo4j-ogm DAO over {@link HdfContainer}. Mirrors the
 * shape of {@code FileContainerDAO}; only the label changes.
 */
@RequestScoped
public class HdfContainerDAO extends GenericDAO<HdfContainer> {

  /**
   * Find all {@code HdfContainer} rows readable by {@code username},
   * honouring optional name / pagination / ordering.
   *
   * @param params   query hints (may be {@code null}).
   * @param username caller; the cypher filter enforces ACL.
   * @return a (possibly empty) list, never {@code null}.
   */
  public List<HdfContainer> findAllHdfContainers(QueryParamHelper params, String username) {
    QueryParamHelper effective = params == null ? new QueryParamHelper() : params;
    Map<String, Object> paramsMap = new HashMap<>();
    paramsMap.put("name", effective.getName());
    if (effective.hasPagination()) {
      paramsMap.put("offset", effective.getPagination().getOffset());
      paramsMap.put("size", effective.getPagination().getSize());
    }
    String query = "MATCH %s WHERE %s WITH c".formatted(
        CypherQueryHelper.getObjectPart("c", "HdfContainer", effective.hasName()),
        CypherQueryHelper.getReadableByQuery("c", username)
      );
    if (effective.hasOrderByAttribute()) {
      query += " " + CypherQueryHelper.getOrderByPart("c", effective.getOrderByAttribute(), effective.getOrderDesc());
    }
    if (effective.hasPagination()) {
      query += " " + CypherQueryHelper.getPaginationPart();
    }
    query += " " + CypherQueryHelper.getReturnPart("c", Neighborhood.ESSENTIAL);
    List<HdfContainer> result = new ArrayList<>();
    for (HdfContainer container : findByQuery(query, paramsMap)) {
      if (matchName(container, effective.getName())) {
        result.add(container);
      }
    }
    return result;
  }

  private boolean matchName(HdfContainer container, String name) {
    return name == null || (container.getName() != null && container.getName().equalsIgnoreCase(name));
  }

  /**
   * Look up an {@code HdfContainer} by its appId.
   *
   * @param appId the application-level identifier (UUID v7).
   * @return the row, or {@code null} if no match.
   */
  public HdfContainer findByAppId(String appId) {
    if (appId == null || appId.isBlank()) return null;
    String query =
      "MATCH %s WHERE c.appId = $appId ".formatted(CypherQueryHelper.getObjectPart("c", "HdfContainer", false)) +
      CypherQueryHelper.getReturnPart("c");
    Iterable<HdfContainer> iter = findByQuery(query, Map.of("appId", appId));
    java.util.Iterator<HdfContainer> it = iter.iterator();
    return it.hasNext() ? it.next() : null;
  }

  @Override
  public Class<HdfContainer> getEntityType() {
    return HdfContainer.class;
  }
}
