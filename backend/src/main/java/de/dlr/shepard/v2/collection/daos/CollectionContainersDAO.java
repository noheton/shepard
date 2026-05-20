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

  private static final String CYPHER =
    "MATCH (coll:Collection {appId: $appId})" +
    "-[:" + Constants.HAS_DATAOBJECT + "]->(do:DataObject)" +
    "-[:" + Constants.HAS_REFERENCE + "]->(ref)" +
    "-[:" + Constants.IS_IN_CONTAINER + "]->(cont) " +
    "WHERE (do.deleted IS NULL OR do.deleted = false) " +
    "RETURN DISTINCT id(cont) AS neoId, cont.appId AS appId, cont.name AS name, " +
    "CASE " +
    "  WHEN cont:TimeseriesContainer   THEN 'TIMESERIES' " +
    "  WHEN cont:FileContainer         THEN 'FILE' " +
    "  WHEN cont:StructuredDataContainer THEN 'STRUCTUREDDATA' " +
    "  ELSE 'BASIC' " +
    "END AS containerType";

  public List<ContainerSummaryIO> findByCollectionAppId(String appId) {
    if (appId == null || appId.isBlank()) return List.of();
    var session = NeoConnector.getInstance().getNeo4jSession();
    if (session == null) return List.of();

    var result = session.query(CYPHER, Map.of("appId", appId));
    var items = new ArrayList<ContainerSummaryIO>();
    for (var row : result) {
      var io = new ContainerSummaryIO(
        ((Number) row.get("neoId")).longValue(),
        (String) row.get("appId"),
        (String) row.get("name"),
        (String) row.get("containerType")
      );
      items.add(io);
    }
    return items;
  }
}
