package de.dlr.shepard.data.timeseries.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.common.util.CypherQueryHelper.Neighborhood;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import jakarta.enterprise.context.RequestScoped;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RequestScoped
public class TimeseriesContainerDAO extends GenericDAO<TimeseriesContainer> {

  public List<TimeseriesContainer> findAllTimeseriesContainers(QueryParamHelper params, String username) {
    String query;
    Map<String, Object> paramsMap = new HashMap<>();
    paramsMap.put("name", params.getName());
    if (params.hasPagination()) {
      paramsMap.put("offset", params.getPagination().getOffset());
      paramsMap.put("size", params.getPagination().getSize());
    }

    query = "MATCH %s WHERE %s WITH c".formatted(
        CypherQueryHelper.getObjectPart("c", "TimeseriesContainer", params.hasName()),
        CypherQueryHelper.getReadableByQuery("c", username)
      );
    if (params.hasOrderByAttribute()) {
      query += " " + CypherQueryHelper.getOrderByPart("c", params.getOrderByAttribute(), params.getOrderDesc());
    }
    if (params.hasPagination()) {
      query += " " + CypherQueryHelper.getPaginationPart();
    }
    query += " " + CypherQueryHelper.getReturnPart("c", Neighborhood.ESSENTIAL);
    var result = new ArrayList<TimeseriesContainer>();
    for (var container : findByQuery(query, paramsMap)) {
      if (matchName(container, params.getName())) {
        result.add(container);
      }
    }
    return result;
  }

  private boolean matchName(TimeseriesContainer container, String name) {
    return name == null || container.getName().equalsIgnoreCase(name);
  }

  public Optional<TimeseriesContainer> findByAppId(String appId) {
    String query = "MATCH (c:TimeseriesContainer {appId: $appId}) " + CypherQueryHelper.getReturnPart("c");
    var iter = findByQuery(query, Map.of("appId", appId));
    var it = iter.iterator();
    return it.hasNext() ? Optional.of(it.next()) : Optional.empty();
  }

  /**
   * CC1b — find all DataObjects that reference this TimeseriesContainer via a
   * TimeseriesReference.
   *
   * <p>The relationship path is:
   * {@code DataObject -[:has_reference]-> TimeseriesReference -[:is_in_container]-> TimeseriesContainer}
   *
   * @param containerAppId the appId of the TimeseriesContainer
   * @return distinct non-deleted DataObjects linked to this container
   */
  public List<DataObject> findLinkedDataObjectsByContainerAppId(String containerAppId) {
    String query =
      "MATCH (do:DataObject)-[:has_reference]->()-[:is_in_container]->(c:TimeseriesContainer) " +
      "WHERE c.appId = $containerAppId " +
      "  AND (do.deleted IS NULL OR do.deleted = false) " +
      "RETURN DISTINCT do";
    var result = new ArrayList<DataObject>();
    for (var dataObject : session.query(DataObject.class, query, Map.of("containerAppId", containerAppId))) {
      result.add(dataObject);
    }
    return result;
  }

  @Override
  public Class<TimeseriesContainer> getEntityType() {
    return TimeseriesContainer.class;
  }
}
