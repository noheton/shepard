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

  /**
   * Single-pass aggregation — returns {@link StatsSnapshot} with the
   * per-bucket counts, per-actionKind totals, total count, and
   * distinct-agent count all derived from one Cypher round-trip.
   *
   * <p>Replaces three separate Cypher round-trips
   * ({@link #aggregateBuckets} + {@link #totalsByActionKind} +
   * {@link #distinctAgentCount}). Used by
   * {@code ProvenanceStatsService} to keep the dashboard query
   * cheap on big installs.
   *
   * <p>The Cypher pulls one bag of {@code {bucket, kind, agent}}
   * tuples and the caller computes the three aggregations in Java
   * — this trades one Neo4j round-trip for one O(n) Java pass,
   * which is the right tradeoff up to ~100k activities per query
   * window (and {@code list}'s 1000-row cap covers most reads).
   */
  public StatsSnapshot aggregateStats(String targetAppId, String agentUsername, long sinceMillis, long untilMillis, long bucketMillis) {
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
    cypher.append(
      " RETURN (a.startedAtMillis / $bucket) * $bucket AS bucket," +
      " coalesce(a.actionKind, 'UNKNOWN') AS kind," +
      " a.agentUsername AS agent"
    );
    var result = session.query(cypher.toString(), params);
    StatsSnapshot snap = new StatsSnapshot();
    snap.buckets = new ArrayList<>();
    snap.totalsByActionKind = new java.util.LinkedHashMap<>();
    java.util.TreeMap<Long, Long> bucketCounts = new java.util.TreeMap<>();
    java.util.Set<String> agents = new java.util.HashSet<>();
    for (Map<String, Object> row : result.queryResults()) {
      long b = row.get("bucket") instanceof Number bn ? bn.longValue() : 0L;
      bucketCounts.merge(b, 1L, Long::sum);
      String kind = Objects.toString(row.get("kind"), "UNKNOWN");
      snap.totalsByActionKind.merge(kind, 1L, Long::sum);
      Object a = row.get("agent");
      if (a != null) agents.add(a.toString());
      snap.totalCount++;
    }
    bucketCounts.forEach((b, c) -> snap.buckets.add(new long[] { b, c }));
    snap.distinctAgents = agents.size();
    return snap;
  }

  /** Snapshot returned by {@link #aggregateStats}. */
  public static final class StatsSnapshot {

    public List<long[]> buckets;
    public Map<String, Long> totalsByActionKind;
    public long distinctAgents;
    public long totalCount;
  }

  /**
   * Walk the PROV-O graph to find all activities that directly touched
   * {@code entityAppId} via a {@code GENERATED} or {@code USED} edge.
   *
   * <p>The depth parameter limits the result set size, not a graph traversal
   * depth — activities are direct neighbours of the target entity node.
   * We multiply by 50 to get a practical row cap without requiring a
   * second count query: depth=3 → cap=150 rows, depth=10 → cap=500 rows.
   * Results are ordered newest-first.
   *
   * @param entityAppId appId of the entity whose provenance to fetch
   * @param depth       clamped to [1, 10]; scales the result-set cap
   */
  public List<ActivityEdgeRow> findByEntityAppId(String entityAppId, int depth) {
    int capped = Math.max(1, Math.min(depth, 10));
    String cypher =
      "MATCH (a:Activity)-[r:GENERATED|USED]->(e {appId: $appId})" +
      " RETURN a, type(r) AS relation" +
      " ORDER BY a.startedAtMillis DESC" +
      " LIMIT " + (capped * 50);
    Map<String, Object> params = Map.of("appId", entityAppId);

    List<ActivityEdgeRow> out = new ArrayList<>();
    var result = session.query(cypher, params);
    for (Map<String, Object> row : result.queryResults()) {
      if (!(row.get("a") instanceof Activity act)) continue;
      String rel = Objects.toString(row.get("relation"), "USED");
      out.add(new ActivityEdgeRow(act, rel));
    }
    return out;
  }

  /** Activity node plus the PROV-O edge label that linked it to the queried entity. */
  public record ActivityEdgeRow(Activity activity, String relation) {}

  /**
   * List recent activities with MCP-friendly filter parameter names.
   * All filters are optional; null or blank means "no filter on this field".
   *
   * <p>Field mapping from MCP names to Neo4j properties:
   * {@code userId} → {@code agentUsername},
   * {@code resourcePath} → {@code path} (prefix match),
   * {@code httpMethod} → {@code method} (upper-cased before match).
   *
   * @param userId       optional agentUsername filter
   * @param resourcePath optional path prefix filter
   * @param httpMethod   optional HTTP method filter
   * @param limit        max rows; capped to [1, 100]
   */
  public List<Activity> listForMcp(String userId, String resourcePath, String httpMethod, int limit) {
    int capped = Math.max(1, Math.min(limit, 100));
    StringBuilder cypher = new StringBuilder("MATCH (a:Activity) WHERE 1=1");
    Map<String, Object> params = new HashMap<>();
    if (userId != null && !userId.isBlank()) {
      cypher.append(" AND a.agentUsername = $userId");
      params.put("userId", userId);
    }
    if (resourcePath != null && !resourcePath.isBlank()) {
      cypher.append(" AND a.path STARTS WITH $path");
      params.put("path", resourcePath);
    }
    if (httpMethod != null && !httpMethod.isBlank()) {
      cypher.append(" AND a.method = $method");
      params.put("method", httpMethod.toUpperCase());
    }
    cypher.append(" RETURN a ORDER BY a.startedAtMillis DESC LIMIT $limit");
    params.put("limit", capped);

    List<Activity> out = new ArrayList<>();
    findByQuery(cypher.toString(), params).forEach(out::add);
    return out;
  }

  /**
   * Wire the three PROV-O edges for a freshly saved {@link Activity}.
   *
   * <ul>
   *   <li>{@code (:Activity)-[:WAS_ASSOCIATED_WITH]->(:User)} — agent who
   *       performed the action (PROV-O canonical OUTGOING direction).</li>
   *   <li>{@code (:Activity)-[:GENERATED]->(:BasicEntity)} — target entity
   *       created by a {@code CREATE} action.</li>
   *   <li>{@code (:Activity)-[:USED]->(:BasicEntity)} — target entity
   *       read / mutated by a {@code READ / UPDATE / DELETE / EXECUTE} action.</li>
   * </ul>
   *
   * <p>All three MERGEs are idempotent. Only the edges relevant to this
   * activity's {@code actionKind} and available identifiers are executed.
   * Failures are logged and suppressed — provenance edges are observability,
   * never contract (see {@code aidocs/55 §4}).
   *
   * <p>Called from {@link de.dlr.shepard.provenance.services.ProvenanceService#record}
   * immediately after the activity node is saved, inside the same best-effort
   * try/catch envelope.
   */
  public void wireEdges(Activity saved, String agentUsername, String targetAppId, String actionKind) {
    if (saved == null || saved.getAppId() == null) return;

    // Edge 1: WAS_ASSOCIATED_WITH → User (always, when username known)
    if (agentUsername != null && !agentUsername.isBlank()) {
      String cypher =
        "MATCH (a:Activity {appId: $actAppId})" +
        " MATCH (u:User {username: $username})" +
        " MERGE (a)-[:WAS_ASSOCIATED_WITH]->(u)";
      try {
        session.query(cypher, Map.of("actAppId", saved.getAppId(), "username", agentUsername));
      } catch (RuntimeException e) {
        io.quarkus.logging.Log.debugf(e, "PROV-O WAS_ASSOCIATED_WITH edge failed for Activity %s", saved.getAppId());
      }
    }

    // Edge 2: GENERATED (CREATE) or USED (non-CREATE) → target BasicEntity
    if (targetAppId != null && !targetAppId.isBlank()) {
      String edgeLabel = "CREATE".equals(actionKind) ? "GENERATED" : "USED";
      String cypher =
        "MATCH (a:Activity {appId: $actAppId})" +
        " MATCH (e:BasicEntity {appId: $targetAppId})" +
        " MERGE (a)-[:" + edgeLabel + "]->(e)";
      try {
        session.query(cypher, Map.of("actAppId", saved.getAppId(), "targetAppId", targetAppId));
      } catch (RuntimeException e) {
        io.quarkus.logging.Log.debugf(e, "PROV-O %s edge failed for Activity %s → %s",
          edgeLabel, saved.getAppId(), targetAppId);
      }
    }
  }

  @Override
  public Class<Activity> getEntityType() {
    return Activity.class;
  }
}
