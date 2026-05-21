package de.dlr.shepard.v2.collectionwatchers.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.v2.collectionwatchers.entities.CollectionWatcher;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CW1 — data-access layer for {@link CollectionWatcher} nodes.
 */
@ApplicationScoped
public class CollectionWatcherDAO extends GenericDAO<CollectionWatcher> {

  @Override
  public Class<CollectionWatcher> getEntityType() {
    return CollectionWatcher.class;
  }

  /** List every watcher record for a given collection (by appId). */
  public List<CollectionWatcher> findByCollectionAppId(String collectionAppId) {
    String query =
      "MATCH (w:CollectionWatcher) " +
      "WHERE w.collectionAppId = $collectionAppId " +
      "RETURN w ORDER BY w.since ASC";
    List<CollectionWatcher> result = new ArrayList<>();
    for (var w : findByQuery(query, Map.of("collectionAppId", collectionAppId))) {
      result.add(w);
    }
    return result;
  }

  /** Find the watcher record for a specific (username, collectionAppId) pair. */
  public CollectionWatcher findByUsernameAndCollection(String username, String collectionAppId) {
    String query =
      "MATCH (w:CollectionWatcher) " +
      "WHERE w.username = $username AND w.collectionAppId = $collectionAppId " +
      "RETURN w LIMIT 1";
    var iter = findByQuery(
      query,
      Map.of("username", username, "collectionAppId", collectionAppId)
    ).iterator();
    return iter.hasNext() ? iter.next() : null;
  }

  /** Find all watcher records for a given user. */
  public List<CollectionWatcher> findByUsername(String username) {
    String query =
      "MATCH (w:CollectionWatcher) " +
      "WHERE w.username = $username " +
      "RETURN w ORDER BY w.since ASC";
    List<CollectionWatcher> result = new ArrayList<>();
    for (var w : findByQuery(query, Map.of("username", username))) {
      result.add(w);
    }
    return result;
  }

  /** Look up a single watcher record by its own appId. */
  public CollectionWatcher findByAppId(String appId) {
    String query =
      "MATCH (w:CollectionWatcher) WHERE w.appId = $appId RETURN w LIMIT 1";
    var iter = findByQuery(query, Map.of("appId", appId)).iterator();
    return iter.hasNext() ? iter.next() : null;
  }

  /** All watcher records for a collection — usernames only (for notification dispatch). */
  public List<String> findWatcherUsernamesByCollectionAppId(String collectionAppId) {
    String query =
      "MATCH (w:CollectionWatcher) " +
      "WHERE w.collectionAppId = $collectionAppId " +
      "RETURN w.username AS username";
    List<String> usernames = new ArrayList<>();
    var result = session.query(query, Map.of("collectionAppId", collectionAppId));
    for (var row : result) {
      Object u = row.get("username");
      if (u != null) usernames.add(u.toString());
    }
    return usernames;
  }
}
