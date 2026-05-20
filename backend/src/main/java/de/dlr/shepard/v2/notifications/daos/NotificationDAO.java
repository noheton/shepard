package de.dlr.shepard.v2.notifications.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.v2.notifications.entities.Notification;
import jakarta.enterprise.context.RequestScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DAO for {@link Notification} nodes. All list queries are ordered most-recent-first
 * and cap at 200 rows per caller to keep the UI snappy.
 */
@RequestScoped
public class NotificationDAO extends GenericDAO<Notification> {

  private static final int LIST_LIMIT = 200;

  /**
   * Returns unread + recent read notifications visible to {@code username}.
   * Includes USER-audience rows addressed to that user, plus INSTANCE_ADMIN rows
   * (when {@code isAdmin} is true), plus ALL-audience rows. Expired nodes are filtered out.
   */
  public List<Notification> listForUser(String username, boolean isAdmin) {
    long now = System.currentTimeMillis();
    String cypher =
      "MATCH (n:Notification) WHERE (" +
      "  (n.audience = 'USER' AND n.targetUsername = $username) OR" +
      "  (n.audience = 'ALL') OR" +
      "  (n.audience = 'INSTANCE_ADMIN' AND $isAdmin)" +
      ") AND (n.expiresAtMillis IS NULL OR n.expiresAtMillis > $now)" +
      " RETURN n ORDER BY n.createdAtMillis DESC LIMIT $limit";
    Map<String, Object> params = Map.of(
      "username", username,
      "isAdmin", isAdmin,
      "now", now,
      "limit", LIST_LIMIT
    );
    List<Notification> out = new ArrayList<>();
    findByQuery(cypher, params).forEach(out::add);
    return out;
  }

  /**
   * Count unread notifications visible to {@code username}. Used by the badge in the bell icon.
   */
  public long countUnread(String username, boolean isAdmin) {
    long now = System.currentTimeMillis();
    String cypher =
      "MATCH (n:Notification) WHERE (" +
      "  (n.audience = 'USER' AND n.targetUsername = $username) OR" +
      "  (n.audience = 'ALL') OR" +
      "  (n.audience = 'INSTANCE_ADMIN' AND $isAdmin)" +
      ") AND n.read = false" +
      " AND (n.expiresAtMillis IS NULL OR n.expiresAtMillis > $now)" +
      " RETURN count(n) AS c";
    Map<String, Object> params = Map.of(
      "username", username,
      "isAdmin", isAdmin,
      "now", now
    );
    var result = session.query(cypher, params);
    var it = result.queryResults().iterator();
    if (!it.hasNext()) return 0L;
    Object c = it.next().get("c");
    return c instanceof Number n ? n.longValue() : 0L;
  }

  /**
   * Find a single notification by appId, checking that it is visible to the caller.
   */
  public Optional<Notification> findByAppIdForUser(String appId, String username, boolean isAdmin) {
    long now = System.currentTimeMillis();
    String cypher =
      "MATCH (n:Notification) WHERE n.appId = $appId AND (" +
      "  (n.audience = 'USER' AND n.targetUsername = $username) OR" +
      "  (n.audience = 'ALL') OR" +
      "  (n.audience = 'INSTANCE_ADMIN' AND $isAdmin)" +
      ") AND (n.expiresAtMillis IS NULL OR n.expiresAtMillis > $now)" +
      " RETURN n LIMIT 1";
    Map<String, Object> params = Map.of(
      "appId", appId,
      "username", username,
      "isAdmin", isAdmin,
      "now", now
    );
    List<Notification> results = new ArrayList<>();
    findByQuery(cypher, params).forEach(results::add);
    return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
  }

  /** Delete expired notifications older than {@code beforeMillis}. Returns count deleted. */
  public long deleteExpiredBefore(long beforeMillis) {
    String cypher =
      "MATCH (n:Notification) WHERE n.expiresAtMillis IS NOT NULL AND n.expiresAtMillis < $cutoff" +
      " WITH n DETACH DELETE n RETURN count(n) AS c";
    var result = session.query(cypher, Map.of("cutoff", beforeMillis));
    var it = result.queryResults().iterator();
    if (!it.hasNext()) return 0L;
    Object c = it.next().get("c");
    return c instanceof Number n ? n.longValue() : 0L;
  }

  @Override
  public Class<Notification> getEntityType() {
    return Notification.class;
  }
}
