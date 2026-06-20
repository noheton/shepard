package de.dlr.shepard.v2.collection.daos;

import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.common.util.Constants;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * COLL-TIMELINE-1 — single-purpose DAO that aggregates a Collection's
 * DataObjects into per-day, per-lane counts for the Timeline tab on the
 * Collection landing page.
 *
 * <p>Single Cypher round-trip. Each DataObject becomes one row in the
 * grouping projection: its anchor day (UTC midnight truncation of
 * {@code createdAt}), its lane key (distinct value of the
 * {@code urn:shepard:mffd:process-type} SemanticAnnotation, or
 * {@code "__unclassified__"} when absent), and its status.
 *
 * <p>The day-truncation lives in the DAO (Cypher native
 * {@code datetime().truncate}); the bin-window math (1/7/30/90/365 day
 * coarsening, NCR/REJECT bucketing, lane ordering) is the resource's
 * responsibility — that keeps the SQL-equivalent query a flat aggregate and
 * the Java side trivially testable with synthetic row sets.
 *
 * <p>Cross-references:
 * <ul>
 *   <li>Predicate IRI: {@code urn:shepard:mffd:process-type} (V100
 *       {@code MFFD_PROCESS_TEMPLATES} seed).</li>
 *   <li>Lane semantics: GAP-8 of
 *       {@code aidocs/agent-findings/mffd-feature-gaps-2026-06-02.md}.</li>
 *   <li>Status vocabulary (NCR/REJECT/CONCESSION_PENDING): AAA2 statuses
 *       — see {@code StatusTransitionGuard}.</li>
 * </ul>
 */
@ApplicationScoped
public class CollectionTimelineDAO {

  /**
   * Predicate IRI used to derive lane keys for the Timeline.
   * Defined by V100 MFFD_PROCESS_TEMPLATES seed; not yet hoisted into
   * {@code Constants} because no other code reads it as a literal — the
   * timeline endpoint is its only consumer at the moment.
   */
  public static final String PROCESS_TYPE_PREDICATE_IRI = "urn:shepard:mffd:process-type";

  /** Lane key for DataObjects without a process-type annotation. */
  public static final String UNCLASSIFIED_LANE_KEY = "__unclassified__";

  /**
   * Single-row record returned by {@link #aggregate(String)}. One row per
   * (lane, day) combination. {@code createdAtMillis} is null when the
   * DataObject has no recorded createdAt (legacy rows pre-PROV1 — coalesced
   * to epoch 0 in Cypher so they always sort first).
   */
  public record TimelineRow(
      String laneKey,
      long dayEpochMillis,
      long count,
      long ncrCount,
      long rejectCount) {
    public TimelineRow {
      Objects.requireNonNull(laneKey, "laneKey");
    }
  }

  /**
   * Result of {@link #aggregate(String)}.
   *
   * @param rows                  one entry per (lane, day) pair
   * @param totalDataObjects      total DataObjects observed (sum over rows)
   * @param minCreatedAtEpochMillis earliest createdAt across the Collection, null when empty
   * @param maxCreatedAtEpochMillis latest createdAt across the Collection, null when empty
   */
  public record TimelineAggregate(
      List<TimelineRow> rows,
      long totalDataObjects,
      Long minCreatedAtEpochMillis,
      Long maxCreatedAtEpochMillis) {}

  // The query truncates createdAt to UTC midnight, joins via subjectAppId
  // against the process-type SemanticAnnotation (left-join — DataObjects
  // without one fall into UNCLASSIFIED_LANE_KEY), and counts each status
  // bucket. The OPTIONAL MATCH uses the SEMA-V6-001 subjectAppId field
  // (indexed per V71) so the join is index-backed even at 8k+ DOs.
  private static final String CYPHER =
    "MATCH (coll:Collection {appId: $appId})" +
    "-[:" + Constants.HAS_DATAOBJECT + "]->(d:DataObject) " +
    "WHERE (d.deleted IS NULL OR d.deleted = false) " +
    "OPTIONAL MATCH (a:SemanticAnnotation { propertyIRI: $predicateIri, subjectAppId: d.appId }) " +
    "WITH d, coalesce(a.valueName, $unclassifiedKey) AS laneKey " +
    "WITH laneKey, " +
    "     CASE WHEN d.createdAt IS NULL THEN 0 ELSE d.createdAt END AS createdAtMs, " +
    "     d.status AS status " +
    "WITH laneKey, " +
    "     CASE WHEN createdAtMs = 0 THEN 0 ELSE " +
    "       datetime({epochMillis: createdAtMs}).truncate('day').epochMillis " +
    "     END AS dayMs, " +
    "     status " +
    "RETURN laneKey, dayMs, count(*) AS cnt, " +
    "       sum(CASE WHEN status IN ['NCR_OPEN','CONCESSION_PENDING'] THEN 1 ELSE 0 END) AS ncrCnt, " +
    "       sum(CASE WHEN status = 'REJECTED' THEN 1 ELSE 0 END) AS rejectCnt " +
    "ORDER BY dayMs ASC";

  // Companion query returns the global min/max createdAt for the Collection.
  // Single aggregation, no scan over the per-day grouping. Kept separate so
  // the main query's result schema stays compact (no per-row min/max
  // duplication).
  private static final String CYPHER_RANGE =
    "MATCH (coll:Collection {appId: $appId})" +
    "-[:" + Constants.HAS_DATAOBJECT + "]->(d:DataObject) " +
    "WHERE (d.deleted IS NULL OR d.deleted = false) " +
    "  AND d.createdAt IS NOT NULL " +
    "RETURN min(d.createdAt) AS minMs, max(d.createdAt) AS maxMs, count(d) AS total";

  /**
   * Run both the per-bin aggregation and the campaign range probe.
   *
   * @param collectionAppId Collection appId (caller has already authorised Read)
   * @return aggregate result; rows ordered by day ascending, totalDataObjects
   *         summed across all status buckets, range bounds nullable when the
   *         Collection has zero non-deleted DataObjects
   */
  public TimelineAggregate aggregate(String collectionAppId) {
    if (collectionAppId == null || collectionAppId.isBlank()) {
      return new TimelineAggregate(List.of(), 0L, null, null);
    }
    var session = NeoConnector.getInstance().getNeo4jSession();
    if (session == null) {
      return new TimelineAggregate(List.of(), 0L, null, null);
    }

    Map<String, Object> params = new LinkedHashMap<>();
    params.put("appId", collectionAppId);
    params.put("predicateIri", PROCESS_TYPE_PREDICATE_IRI);
    params.put("unclassifiedKey", UNCLASSIFIED_LANE_KEY);

    List<TimelineRow> rows = new ArrayList<>();
    long total = 0L;
    var binResult = session.query(CYPHER, params);
    for (var row : binResult) {
      String laneKey = stringOf(row.get("laneKey"));
      long dayMs = longOf(row.get("dayMs"));
      long cnt = longOf(row.get("cnt"));
      long ncrCnt = longOf(row.get("ncrCnt"));
      long rejectCnt = longOf(row.get("rejectCnt"));
      if (laneKey == null) laneKey = UNCLASSIFIED_LANE_KEY;
      rows.add(new TimelineRow(laneKey, dayMs, cnt, ncrCnt, rejectCnt));
      total += cnt;
    }

    Long minMs = null;
    Long maxMs = null;
    var rangeResult = session.query(CYPHER_RANGE, Map.of("appId", collectionAppId));
    for (var row : rangeResult) {
      Object rawMin = row.get("minMs");
      Object rawMax = row.get("maxMs");
      if (rawMin instanceof Number n) minMs = toEpochMillis(n);
      if (rawMax instanceof Number n) maxMs = toEpochMillis(n);
      // If the count-from-range disagrees with the per-bin count, prefer the
      // range total (per-bin can drop the createdAt-null bucket when dayMs=0).
      Object rawTotal = row.get("total");
      if (rawTotal instanceof Number n && total == 0L) total = n.longValue();
      break;
    }

    return new TimelineAggregate(rows, total, minMs, maxMs);
  }

  // ── helpers ────────────────────────────────────────────────────────────

  private static long longOf(Object raw) {
    if (raw instanceof Number n) return n.longValue();
    return 0L;
  }

  private static String stringOf(Object raw) {
    return raw == null ? null : raw.toString();
  }

  /**
   * Neo4j OGM hands `createdAt` (a {@code java.util.Date} property in OGM
   * model) back as either an epoch-millis Long or a wrapped Date depending
   * on driver/codec config. Normalise to epoch millis. Numeric values are
   * already in millis (no scaling needed — the OGM `@Convert(DateLong.class)`
   * default stores millis since epoch).
   */
  private static long toEpochMillis(Number n) {
    return n.longValue();
  }

  // Cypher for multi-collection (IN-list) aggregation; identical in shape to
  // CYPHER but joins via WHERE coll.appId IN $appIds instead of a property filter.
  private static final String CYPHER_MULTI =
    "MATCH (coll:Collection)-[:" + Constants.HAS_DATAOBJECT + "]->(d:DataObject) " +
    "WHERE coll.appId IN $appIds " +
    "  AND (d.deleted IS NULL OR d.deleted = false) " +
    "OPTIONAL MATCH (a:SemanticAnnotation { propertyIRI: $predicateIri, subjectAppId: d.appId }) " +
    "WITH d, coalesce(a.valueName, $unclassifiedKey) AS laneKey " +
    "WITH laneKey, " +
    "     CASE WHEN d.createdAt IS NULL THEN 0 ELSE d.createdAt END AS createdAtMs, " +
    "     d.status AS status " +
    "WITH laneKey, " +
    "     CASE WHEN createdAtMs = 0 THEN 0 ELSE " +
    "       datetime({epochMillis: createdAtMs}).truncate('day').epochMillis " +
    "     END AS dayMs, " +
    "     status " +
    "RETURN laneKey, dayMs, count(*) AS cnt, " +
    "       sum(CASE WHEN status IN ['NCR_OPEN','CONCESSION_PENDING'] THEN 1 ELSE 0 END) AS ncrCnt, " +
    "       sum(CASE WHEN status = 'REJECTED' THEN 1 ELSE 0 END) AS rejectCnt " +
    "ORDER BY dayMs ASC";

  private static final String CYPHER_RANGE_MULTI =
    "MATCH (coll:Collection)-[:" + Constants.HAS_DATAOBJECT + "]->(d:DataObject) " +
    "WHERE coll.appId IN $appIds " +
    "  AND (d.deleted IS NULL OR d.deleted = false) " +
    "  AND d.createdAt IS NOT NULL " +
    "RETURN min(d.createdAt) AS minMs, max(d.createdAt) AS maxMs, count(d) AS total";

  /**
   * Multi-collection variant — aggregates DataObjects from all listed Collections
   * into a single swimlane envelope (same shape as {@link #aggregate(String)}).
   *
   * <p>Callers are responsible for verifying Read access on each Collection in
   * {@code collectionAppIds} before invoking this method.
   *
   * @param collectionAppIds non-null, non-empty list of Collection appIds
   * @return aggregate result; rows ordered by day ascending
   */
  public TimelineAggregate aggregateMulti(List<String> collectionAppIds) {
    if (collectionAppIds == null || collectionAppIds.isEmpty()) {
      return new TimelineAggregate(List.of(), 0L, null, null);
    }
    if (collectionAppIds.size() == 1) {
      return aggregate(collectionAppIds.get(0));
    }
    var session = NeoConnector.getInstance().getNeo4jSession();
    if (session == null) {
      return new TimelineAggregate(List.of(), 0L, null, null);
    }

    Map<String, Object> params = new LinkedHashMap<>();
    params.put("appIds", collectionAppIds);
    params.put("predicateIri", PROCESS_TYPE_PREDICATE_IRI);
    params.put("unclassifiedKey", UNCLASSIFIED_LANE_KEY);

    List<TimelineRow> rows = new ArrayList<>();
    long total = 0L;
    var binResult = session.query(CYPHER_MULTI, params);
    for (var row : binResult) {
      String laneKey = stringOf(row.get("laneKey"));
      long dayMs = longOf(row.get("dayMs"));
      long cnt = longOf(row.get("cnt"));
      long ncrCnt = longOf(row.get("ncrCnt"));
      long rejectCnt = longOf(row.get("rejectCnt"));
      if (laneKey == null) laneKey = UNCLASSIFIED_LANE_KEY;
      rows.add(new TimelineRow(laneKey, dayMs, cnt, ncrCnt, rejectCnt));
      total += cnt;
    }

    Long minMs = null;
    Long maxMs = null;
    var rangeResult = session.query(CYPHER_RANGE_MULTI, Map.of("appIds", collectionAppIds));
    for (var row : rangeResult) {
      Object rawMin = row.get("minMs");
      Object rawMax = row.get("maxMs");
      if (rawMin instanceof Number n) minMs = toEpochMillis(n);
      if (rawMax instanceof Number n) maxMs = toEpochMillis(n);
      Object rawTotal = row.get("total");
      if (rawTotal instanceof Number n && total == 0L) total = n.longValue();
      break;
    }

    return new TimelineAggregate(rows, total, minMs, maxMs);
  }

  /** Visible for tests. */
  static String getProcessTypePredicateIri() {
    return PROCESS_TYPE_PREDICATE_IRI;
  }
}
