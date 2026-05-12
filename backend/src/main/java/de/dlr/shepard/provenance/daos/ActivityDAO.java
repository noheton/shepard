package de.dlr.shepard.provenance.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.provenance.entities.Activity;
import jakarta.enterprise.context.RequestScoped;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

  /**
   * Bucket activity counts by time-window for the dashboard sparkline.
   * Returns a list of {@code [bucketStartMillis, count]} pairs sorted
   * ascending. Empty buckets within the window are NOT filled — the
   * caller fills gaps client-side (per {@code aidocs/55 §6}).
   *
   * @param targetAppId    optional — filter to one entity (sparkline for one Collection)
   * @param agentUsername  optional — filter to one Agent
   * @param sinceMillis    inclusive lower bound
   * @param untilMillis    inclusive upper bound
   * @param bucketMillis   bucket width in millis (e.g. 86_400_000 for daily)
   */
  public List<long[]> aggregateBuckets(String targetAppId, String agentUsername, long sinceMillis, long untilMillis, long bucketMillis) {
    StringBuilder cypher = new StringBuilder(
      "MATCH (a:Activity) WHERE a.startedAtMillis >= $since AND a.startedAtMillis <= $until"
    );
    Map<String, Object> params = new HashMap<>();
    params.put("since", sinceMillis);
    params.put("until", untilMillis);
    params.put("bucket", bucketMillis);
    if (targetAppId != null) {
      cypher.append(" AND a.targetAppId = $tappid");
      params.put("tappid", targetAppId);
    }
    if (agentUsername != null) {
      cypher.append(" AND a.agentUsername = $agent");
      params.put("agent", agentUsername);
    }
    cypher.append(" WITH (a.startedAtMillis / $bucket) * $bucket AS bucketStart")
      .append(" RETURN bucketStart, count(*) AS c ORDER BY bucketStart ASC");
    var result = session.query(cypher.toString(), params);
    List<long[]> out = new ArrayList<>();
    for (Map<String, Object> row : result.queryResults()) {
      long bucket = row.get("bucketStart") instanceof Number bn ? bn.longValue() : 0L;
      long count = row.get("c") instanceof Number cn ? cn.longValue() : 0L;
      out.add(new long[] { bucket, count });
    }
    return out;
  }

  /**
   * Returns per-{@code actionKind} totals for the same filter set as
   * {@link #aggregateBuckets}.
   */
  public Map<String, Long> totalsByActionKind(String targetAppId, String agentUsername, long sinceMillis, long untilMillis) {
    StringBuilder cypher = new StringBuilder(
      "MATCH (a:Activity) WHERE a.startedAtMillis >= $since AND a.startedAtMillis <= $until"
    );
    Map<String, Object> params = new HashMap<>();
    params.put("since", sinceMillis);
    params.put("until", untilMillis);
    if (targetAppId != null) {
      cypher.append(" AND a.targetAppId = $tappid");
      params.put("tappid", targetAppId);
    }
    if (agentUsername != null) {
      cypher.append(" AND a.agentUsername = $agent");
      params.put("agent", agentUsername);
    }
    cypher.append(" RETURN coalesce(a.actionKind, 'UNKNOWN') AS kind, count(*) AS c");
    var result = session.query(cypher.toString(), params);
    Map<String, Long> out = new java.util.LinkedHashMap<>();
    for (Map<String, Object> row : result.queryResults()) {
      String kind = Objects.toString(row.get("kind"), "UNKNOWN");
      long c = row.get("c") instanceof Number n ? n.longValue() : 0L;
      out.put(kind, c);
    }
    return out;
  }

  /**
   * Distinct count of agent usernames matching the filter set. Used by
   * the dashboard's "N contributors active" tile.
   */
  public long distinctAgentCount(String targetAppId, long sinceMillis, long untilMillis) {
    StringBuilder cypher = new StringBuilder(
      "MATCH (a:Activity) WHERE a.startedAtMillis >= $since AND a.startedAtMillis <= $until"
    );
    Map<String, Object> params = new HashMap<>();
    params.put("since", sinceMillis);
    params.put("until", untilMillis);
    if (targetAppId != null) {
      cypher.append(" AND a.targetAppId = $tappid");
      params.put("tappid", targetAppId);
    }
    cypher.append(" RETURN count(DISTINCT a.agentUsername) AS c");
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
