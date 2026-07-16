package de.dlr.shepard.v2.notifications.transport.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.v2.notifications.transport.entities.NotificationTransport;
import jakarta.enterprise.context.RequestScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;

/**
 * NTF1-BACKEND-TRANSPORT-MODEL — DAO for {@link NotificationTransport} nodes.
 *
 * <p>List-shaped (unlike the {@code :JupyterConfig} / {@code :UnhideConfig}
 * singleton DAOs); admins can create N transports addressable by
 * {@link NotificationTransport#getAppId() appId}. {@code @RequestScoped}
 * mirrors {@code NotificationDAO} since every entry point is an admin
 * REST call inside a request context.
 */
@RequestScoped
public class NotificationTransportDAO extends GenericDAO<NotificationTransport> {

  /**
   * Find a transport by its appId. Returns {@link Optional#empty()} when
   * no row matches.
   */
  public Optional<NotificationTransport> findByAppId(String appId) {
    if (appId == null || appId.isBlank()) {
      return Optional.empty();
    }
    Filter f = new Filter("appId", ComparisonOperator.EQUALS, appId);
    var hits = session.loadAll(getEntityType(), f, DEPTH_ENTITY);
    if (hits == null || hits.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(hits.iterator().next());
  }

  /**
   * Delete a transport by its appId. Returns {@code true} when a row
   * was found and removed, {@code false} when none matched.
   *
   * <p><b>Note.</b> No cascade — there is no foreign-key edge from
   * {@code :Notification} (the message envelope) to
   * {@code :NotificationTransport}. Historical {@code :Activity} rows
   * referencing the deleted transport's {@code appId} are preserved per
   * the CLAUDE.md "audit trail is a graph" rule.
   */
  public boolean deleteByAppId(String appId) {
    Optional<NotificationTransport> existing = findByAppId(appId);
    if (existing.isEmpty()) {
      return false;
    }
    session.delete(existing.get());
    return true;
  }

  // ─── APISIMP-NOTIF-TRANSPORT-INMEM: DB-side pagination ─────────────────

  static final String COUNT_ALL_CYPHER =
    "MATCH (t:NotificationTransport) RETURN count(t) AS c";

  static final String LIST_PAGED_CYPHER =
    "MATCH (t:NotificationTransport) " +
    "RETURN t ORDER BY toLower(coalesce(t.name, '')) ASC " +
    "SKIP $skip LIMIT $limit";

  /**
   * Total count of :NotificationTransport nodes, pushed to the DB.
   * Used to populate X-Total-Count without loading all nodes.
   */
  public long countAll() {
    var result = session.query(COUNT_ALL_CYPHER, Map.of());
    for (var row : result) {
      Object c = row.get("c");
      return c instanceof Number n ? n.longValue() : 0L;
    }
    return 0L;
  }

  /**
   * Returns a bounded, name-ASC page of :NotificationTransport nodes.
   * SKIP/LIMIT are pushed to the DB — no full list materialised.
   *
   * @param skip  rows to skip (0-based)
   * @param limit maximum rows to return
   */
  public List<NotificationTransport> listPaged(long skip, int limit) {
    List<NotificationTransport> out = new ArrayList<>();
    for (NotificationTransport t : findByQuery(LIST_PAGED_CYPHER,
        Map.of("skip", skip, "limit", limit))) {
      out.add(t);
    }
    return out;
  }

  @Override
  public Class<NotificationTransport> getEntityType() {
    return NotificationTransport.class;
  }
}
