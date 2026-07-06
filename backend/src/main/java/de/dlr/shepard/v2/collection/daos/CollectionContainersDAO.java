package de.dlr.shepard.v2.collection.daos;

import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.v2.collection.io.ContainerSummaryIO;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Single-purpose DAO: walks the
 * Collection → DataObject → Reference → Container
 * chain for a given collection appId and returns distinct container summaries.
 */
@ApplicationScoped
public class CollectionContainersDAO {

  private static final String BASE_MATCH =
    "MATCH (coll:Collection {appId: $appId})" +
    "-[:" + Constants.HAS_DATAOBJECT + "]->(do:DataObject)" +
    "-[:" + Constants.HAS_REFERENCE + "]->(ref)" +
    "-[:" + Constants.IS_IN_CONTAINER + "]->(cont) " +
    "WHERE (do.deleted IS NULL OR do.deleted = false) ";

  private static final String CYPHER_PAGED =
    BASE_MATCH +
    "RETURN DISTINCT cont.appId AS appId, cont.name AS name, " +
    "CASE " +
    "  WHEN cont:TimeseriesContainer     THEN 'TIMESERIES' " +
    "  WHEN cont:FileContainer           THEN 'FILE' " +
    "  WHEN cont:StructuredDataContainer THEN 'STRUCTUREDDATA' " +
    "  ELSE 'BASIC' " +
    "END AS containerType " +
    "ORDER BY cont.appId SKIP $skip LIMIT $limit";

  private static final String CYPHER_COUNT =
    BASE_MATCH +
    "RETURN count(DISTINCT cont) AS total";

  public long countByCollectionAppId(String appId) {
    if (appId == null || appId.isBlank()) return 0L;
    var session = NeoConnector.getInstance().getNeo4jSession();
    if (session == null) return 0L;
    var result = session.queryForObject(Long.class, CYPHER_COUNT, Map.of("appId", appId));
    return result != null ? result : 0L;
  }

  public List<ContainerSummaryIO> findByCollectionAppId(String appId, int skip, int limit) {
    if (appId == null || appId.isBlank()) return List.of();
    var session = NeoConnector.getInstance().getNeo4jSession();
    if (session == null) return List.of();

    var result = session.query(CYPHER_PAGED, Map.of("appId", appId, "skip", skip, "limit", limit));
    var items = new ArrayList<ContainerSummaryIO>();
    for (var row : result) {
      items.add(new ContainerSummaryIO(
        (String) row.get("appId"),
        (String) row.get("name"),
        (String) row.get("containerType")
      ));
    }
    return items;
  }
}
