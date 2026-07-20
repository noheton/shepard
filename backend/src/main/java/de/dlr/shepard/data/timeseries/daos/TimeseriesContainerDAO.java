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
      "RETURN DISTINCT id(do) AS neo4jId";
    var result = new ArrayList<DataObject>();
    for (var row : session.query(query, Map.of("containerAppId", containerAppId))) {
      Long neo4jId = (Long) row.get("neo4jId");
      if (neo4jId == null) continue;
      DataObject loaded = loadLinkedDataObjectForPanel(neo4jId);
      if (loaded != null) result.add(loaded);
    }
    return result;
  }

  public long countLinkedDataObjectsByContainerAppId(String containerAppId) {
    String query =
      "MATCH (do:DataObject)-[:has_reference]->()-[:is_in_container]->(c:TimeseriesContainer) " +
      "WHERE c.appId = $containerAppId " +
      "  AND (do.deleted IS NULL OR do.deleted = false) " +
      "RETURN count(DISTINCT do) AS total";
    var iter = session.query(query, Map.of("containerAppId", containerAppId)).iterator();
    if (!iter.hasNext()) return 0L;
    Object val = iter.next().get("total");
    return val instanceof Number n ? n.longValue() : 0L;
  }

  public List<DataObject> findLinkedDataObjectsByContainerAppIdPaged(
      String containerAppId, int skip, int limit) {
    String query =
      "MATCH (do:DataObject)-[:has_reference]->()-[:is_in_container]->(c:TimeseriesContainer) " +
      "WHERE c.appId = $containerAppId " +
      "  AND (do.deleted IS NULL OR do.deleted = false) " +
      "RETURN DISTINCT id(do) AS neo4jId, do.appId AS appId " +
      "ORDER BY appId SKIP $skip LIMIT $limit";
    var result = new ArrayList<DataObject>();
    for (var row : session.query(query,
        Map.of("containerAppId", containerAppId, "skip", skip, "limit", limit))) {
      Long neo4jId = (Long) row.get("neo4jId");
      if (neo4jId == null) continue;
      DataObject loaded = loadLinkedDataObjectForPanel(neo4jId);
      if (loaded != null) result.add(loaded);
    }
    return result;
  }

  /**
   * SUPERNODE-F3-CONTAINER-LINKED-DO — load a single linked DataObject for the
   * container "referenced-by" panel WITHOUT hydrating its {@code has_reference}
   * fan-out. A linked DataObject on the MFFD tapelaying/TPS containers can hold
   * ~177k {@code has_reference} edges; a depth-1 {@code session.load} would
   * materialise every reference row into OGM (O(K²) {@code coerceCollection})
   * merely to map the DataObject to a {@code DataObjectIO} that only needs its
   * name/appId/status/collection.
   *
   * <p>A plain depth-0 load is NOT viable here: {@code DataObjectIO}'s
   * constructor reads {@code dataObject.getCollection().getShepardId()} (no null
   * guard) plus successors/predecessors/children, all of which are unhydrated at
   * depth 0 (→ NPE / empty arrays). We therefore reuse GETDO-DETAIL-ON2's
   * {@link CypherQueryHelper#getReturnPartForDetail(String)}, which keeps the
   * bounded structural neighbourhood (collection + successors + predecessors +
   * children + version) and excludes ONLY the {@code has_reference} supernode
   * edge. OGM then coerces ~6 rows, not 177k. The linked DO's own
   * {@code referenceIds}/counts come back empty — acceptable: the panel renders
   * name + status + owning-collection only (LinkedDataObjectRow.vue).
   */
  private DataObject loadLinkedDataObjectForPanel(long neo4jId) {
    String query =
      "MATCH (do:DataObject) WHERE id(do) = $id WITH do " +
      CypherQueryHelper.getReturnPartForDetail("do");
    var it = session.query(DataObject.class, query, Map.of("id", neo4jId)).iterator();
    return it.hasNext() ? it.next() : null;
  }

  /**
   * APISIMP-CONT-LIST-INMEM-PAGING — count containers matching the filter
   * without loading any entity graph. Runs a single Cypher COUNT query so
   * {@link de.dlr.shepard.v2.containers.handlers.TimeseriesContainerKindHandler#count}
   * avoids a full-table load.
   */
  public int countTimeseriesContainers(QueryParamHelper params, String username) {
    Map<String, Object> paramsMap = new HashMap<>();
    paramsMap.put("name", params.getName());
    String query = "MATCH %s WHERE %s RETURN count(c) AS total".formatted(
        CypherQueryHelper.getObjectPart("c", "TimeseriesContainer", params.hasName()),
        CypherQueryHelper.getReadableByQuery("c", username)
    );
    var iter = session.query(query, paramsMap).iterator();
    if (!iter.hasNext()) return 0;
    Object val = iter.next().get("total");
    return val instanceof Number n ? n.intValue() : 0;
  }

  @Override
  public Class<TimeseriesContainer> getEntityType() {
    return TimeseriesContainer.class;
  }
}
