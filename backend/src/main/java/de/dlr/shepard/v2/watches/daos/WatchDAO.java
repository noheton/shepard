package de.dlr.shepard.v2.watches.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.v2.watches.entities.Watch;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class WatchDAO extends GenericDAO<Watch> {

  @Override
  public Class<Watch> getEntityType() {
    return Watch.class;
  }

  /** List every :Watch attached to a given Collection (by appId). */
  public List<Watch> findByCollectionAppId(String collectionAppId) {
    String query =
      "MATCH (w:Watch) " +
      "WHERE w.collectionAppId = $collectionAppId " +
      "RETURN w " +
      "ORDER BY w.since ASC";
    List<Watch> result = new ArrayList<>();
    for (var w : findByQuery(query, Map.of("collectionAppId", collectionAppId))) {
      result.add(w);
    }
    return result;
  }

  /** Look up a single :Watch by its own appId. */
  public Watch findByAppId(String appId) {
    String query =
      "MATCH (w:Watch) WHERE w.appId = $appId RETURN w LIMIT 1";
    var iter = findByQuery(query, Map.of("appId", appId)).iterator();
    return iter.hasNext() ? iter.next() : null;
  }

  /** Idempotency probe: existing watch with the same (collection, container) pair? */
  public Watch findByCollectionAndContainer(String collectionAppId, String containerAppId) {
    String query =
      "MATCH (w:Watch) " +
      "WHERE w.collectionAppId = $collectionAppId " +
      "  AND w.containerAppId = $containerAppId " +
      "RETURN w LIMIT 1";
    var iter = findByQuery(
      query,
      Map.of("collectionAppId", collectionAppId, "containerAppId", containerAppId)
    ).iterator();
    return iter.hasNext() ? iter.next() : null;
  }
}
