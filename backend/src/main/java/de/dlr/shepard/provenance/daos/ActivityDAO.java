package de.dlr.shepard.provenance.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.provenance.entities.Activity;
import jakarta.enterprise.context.RequestScoped;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DAO for {@link Activity} provenance rows. Designed in
 * {@code aidocs/55 §6}.
 *
 * <p>Reads support filtering by agent / target / time window with a
 * default descending sort on {@code startedAtMillis} so the casual-user
 * dashboard's "most recent first" pattern is the cheap path.
 */
@RequestScoped
public class ActivityDAO extends GenericDAO<Activity> {

  /**
   * List activities matching the supplied filters. All filters are
   * optional; {@code null} means "no filter on this field".
   *
   * @param agentUsername filter to activities whose agent matches
   * @param targetKind    filter to activities whose target kind matches
   *                      (e.g. {@code "Collection"})
   * @param targetAppId   filter to activities targeting the given entity
   * @param sinceMillis   inclusive lower bound on {@code startedAtMillis};
   *                      ignored when {@code null}
   * @param untilMillis   inclusive upper bound on {@code startedAtMillis};
   *                      ignored when {@code null}
   * @param limit         maximum rows to return; capped at 1000
   */
  public List<Activity> list(
    String agentUsername,
    String targetKind,
    String targetAppId,
    Long sinceMillis,
    Long untilMillis,
    int limit
  ) {
    int capped = Math.min(Math.max(limit, 1), 1000);
    StringBuilder cypher = new StringBuilder("MATCH (a:Activity) WHERE 1=1");
    Map<String, Object> params = new HashMap<>();
    if (agentUsername != null) {
      cypher.append(" AND a.agentUsername = $agent");
      params.put("agent", agentUsername);
    }
    if (targetKind != null) {
      cypher.append(" AND a.targetKind = $kind");
      params.put("kind", targetKind);
    }
    if (targetAppId != null) {
      cypher.append(" AND a.targetAppId = $tappid");
      params.put("tappid", targetAppId);
    }
    if (sinceMillis != null) {
      cypher.append(" AND a.startedAtMillis >= $since");
      params.put("since", sinceMillis);
    }
    if (untilMillis != null) {
      cypher.append(" AND a.startedAtMillis <= $until");
      params.put("until", untilMillis);
    }
    cypher.append(" RETURN a ORDER BY a.startedAtMillis DESC LIMIT $limit");
    params.put("limit", capped);

    List<Activity> out = new ArrayList<>();
    findByQuery(cypher.toString(), params).forEach(out::add);
    return out;
  }

  /**
   * Delete activities older than {@code beforeMillis}. Returns the
   * number of rows removed. Used by the nightly retention job
   * (per {@code aidocs/55 §7}).
   */
  public long deleteOlderThan(long beforeMillis) {
    String cypher = "MATCH (a:Activity) WHERE a.startedAtMillis < $cutoff WITH a, count(a) AS c DETACH DELETE a RETURN c";
    var result = session.query(cypher, Map.of("cutoff", beforeMillis));
    long total = 0L;
    for (Map<String, Object> row : result.queryResults()) {
      Object c = row.get("c");
      if (c instanceof Number n) total += n.longValue();
    }
    return total;
  }

  /**
   * Count activities matching the optional filter set. Mirror of
   * {@link #list} without the row return — used for dashboard
   * summary tiles.
   */
  public long count(String agentUsername, String targetKind, String targetAppId, Long sinceMillis, Long untilMillis) {
    StringBuilder cypher = new StringBuilder("MATCH (a:Activity) WHERE 1=1");
    Map<String, Object> params = new HashMap<>();
    if (agentUsername != null) {
      cypher.append(" AND a.agentUsername = $agent");
      params.put("agent", agentUsername);
    }
    if (targetKind != null) {
      cypher.append(" AND a.targetKind = $kind");
      params.put("kind", targetKind);
    }
    if (targetAppId != null) {
      cypher.append(" AND a.targetAppId = $tappid");
      params.put("tappid", targetAppId);
    }
    if (sinceMillis != null) {
      cypher.append(" AND a.startedAtMillis >= $since");
      params.put("since", sinceMillis);
    }
    if (untilMillis != null) {
      cypher.append(" AND a.startedAtMillis <= $until");
      params.put("until", untilMillis);
    }
    cypher.append(" RETURN count(a) AS c");
    var result = session.query(cypher.toString(), params);
    var it = result.queryResults().iterator();
    if (!it.hasNext()) return 0L;
    Object c = it.next().get("c");
    return c instanceof Number n ? n.longValue() : 0L;
  }

  @Override
  public Class<Activity> getEntityType() {
    return Activity.class;
  }
}
