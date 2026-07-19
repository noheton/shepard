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
   * NEO-AUDIT-2026-07-18-ACTIVITY-SUPERNODE — the exact Cypher issued for the
   * time-bucketed agent edge. Held as a constant so the unit test can assert on
   * a byte-identical string (spacing drift would silently break the assertion).
   */
  // NEO-AUDIT-SUPERNODE: MERGE on the ~2.87M-degree service :User would scan
  // the supernode's rels on every write (O(degree)) — pathological on the hot
  // path (ProvenanceCaptureFilter creates an Activity per mutation). The Activity
  // is brand-new, so we CREATE (O(1)); the `WHERE NOT` existence guard is checked
  // from the Activity side (`a`'s incoming agent_acted_in_month degree is ≤1, so
  // O(1)) to stay idempotent if wireEdges is ever re-invoked for the same Activity.
  static final String AGENT_ACTED_IN_MONTH_CYPHER =
    "MATCH (u:User {username: $username}) " +
    "MATCH (a:Activity {appId: $activityAppId}) " +
    "WHERE NOT (a)<-[:agent_acted_in_month]-(u) " +
    "CREATE (u)-[:agent_acted_in_month {ym: $ym}]->(a)";

  /**
   * Wire the PROV-O edges (plus the time-bucketed agent index) for a freshly
   * saved {@link Activity}.
   *
   * <ul>
   *   <li>{@code (:Activity)-[:WAS_ASSOCIATED_WITH]->(:User)} — agent who
   *       performed the action (PROV-O canonical OUTGOING direction).</li>
   *   <li>{@code (:Activity)-[:GENERATED]->(:BasicEntity)} — target entity
   *       created by a {@code CREATE} action.</li>
   *   <li>{@code (:Activity)-[:USED]->(:BasicEntity)} — target entity
   *       read / mutated by a {@code READ / UPDATE / DELETE / EXECUTE} action.</li>
   *   <li>{@code (:User)-[:agent_acted_in_month {ym:"YYYYMM"}]->(:Activity)} —
   *       NEO-AUDIT-2026-07-18-ACTIVITY-SUPERNODE. A time-bucketed agent index
   *       mirroring {@code DataObjectDAO.writeCreatedInMonth} (NEO-AUDIT-004).
   *       The service {@code :User} carries ~2.87M incoming
   *       {@code WAS_ASSOCIATED_WITH} edges (~28× Neo4j's dense-node threshold);
   *       agent+time provenance queries can label-scan this bounded, ym-indexed
   *       rel instead of walking the supernode. See
   *       {@code V121__add_agent_acted_in_month_index.cypher}.</li>
   * </ul>
   *
   * <p>All MERGEs are idempotent. Only the edges relevant to this
   * activity's {@code actionKind} and available identifiers are executed.
   * Failures are logged and suppressed — provenance edges are observability,
   * never contract (see {@code aidocs/55 §4}); each edge write sits in its own
   * try/catch so one failing edge never suppresses the others.
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

    // Edge 3: (:User)-[:agent_acted_in_month {ym}]->(:Activity) — NEO-AUDIT-SUPERNODE.
    // Time-bucketed agent index so agent+time provenance queries avoid walking the
    // ~2.87M-degree service :User supernode. Best-effort, in its own try/catch.
    writeAgentActedInMonth(saved, agentUsername);
  }

  /**
   * NEO-AUDIT-2026-07-18-ACTIVITY-SUPERNODE — write the time-bucketed
   * {@code (:User)-[:agent_acted_in_month {ym:"YYYYMM"}]->(:Activity)} edge.
   *
   * <p>Mirrors {@code DataObjectDAO.writeCreatedInMonth}: the {@code ym} is
   * derived from the Activity's {@code startedAtMillis} (epoch-millis), formatted
   * UTC-explicitly as a 6-char {@code "YYYYMM"} string so it aligns with the
   * Cypher backfill migration (which uses {@code datetime({epochMillis: x})}, UTC).
   * Using the JVM-default timezone would diverge for activities near midnight in
   * timezones east of UTC.
   *
   * <p>Uses MERGE so the call is idempotent. Best-effort — skips (WARN) when the
   * username or {@code startedAtMillis} is missing, and swallows any query
   * failure, so a write here never blocks the primary provenance capture
   * (CLAUDE.md fire-and-forget rule).
   *
   * @param saved         the freshly persisted Activity (non-null appId asserted
   *                      by the caller)
   * @param agentUsername the acting agent's username (may be {@code null}/blank)
   */
  void writeAgentActedInMonth(Activity saved, String agentUsername) {
    if (agentUsername == null || agentUsername.isBlank()) return;
    Long startedAtMillis = saved.getStartedAtMillis();
    if (startedAtMillis == null) {
      io.quarkus.logging.Log.debugf(
        "NEO-AUDIT-SUPERNODE: skipping agent_acted_in_month for Activity %s — null startedAtMillis",
        saved.getAppId()
      );
      return;
    }
    var utc = java.time.Instant.ofEpochMilli(startedAtMillis).atZone(java.time.ZoneOffset.UTC);
    String ym = String.format("%04d%02d", utc.getYear(), utc.getMonthValue());
    try {
      session.query(
        AGENT_ACTED_IN_MONTH_CYPHER,
        Map.of("username", agentUsername, "activityAppId", saved.getAppId(), "ym", ym)
      );
    } catch (RuntimeException e) {
      io.quarkus.logging.Log.debugf(e,
        "NEO-AUDIT-SUPERNODE: agent_acted_in_month edge failed for Activity %s (user=%s, ym=%s)",
        saved.getAppId(), agentUsername, ym);
    }
  }

  @Override
  public Class<Activity> getEntityType() {
    return Activity.class;
  }
}
