package de.dlr.shepard.provenance.services;

import de.dlr.shepard.provenance.daos.ActivityDAO;
import de.dlr.shepard.provenance.daos.ContentCensusDAO;
import de.dlr.shepard.v2.provenance.io.ProvenanceStatsIO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * On-demand provenance stats roll-up per {@code aidocs/55 §6}. Built
 * around the existing {@link ActivityDAO} aggregation methods.
 *
 * <p>v1 of PROV1c does the aggregation per request — fast enough for
 * casual installs (< 10k activities / day). A future PROV1c2 will
 * add a pre-aggregated {@code :ActivityRollup} table for sub-second
 * dashboard queries on big installs.
 */
@RequestScoped
public class ProvenanceStatsService {

  public static final String SCOPE_INSTANCE = "instance";
  public static final String SCOPE_COLLECTION = "collection";
  public static final String SCOPE_USER = "user";

  static final long DAY_MILLIS = 86_400_000L;
  static final long WEEK_MILLIS = 7L * DAY_MILLIS;

  /**
   * Switch from daily to weekly buckets above 90 days of window —
   * keeps the sparkline payload bounded.
   */
  static final long DAILY_TO_WEEKLY_THRESHOLD_DAYS = 90L;

  @Inject
  ActivityDAO activityDAO;

  @Inject
  ContentCensusDAO censusDAO;

  public ProvenanceStatsIO compute(String scope, String id, long sinceMillis, long untilMillis) {
    if (sinceMillis > untilMillis) {
      throw new IllegalArgumentException("since must be <= until");
    }
    long windowDays = (untilMillis - sinceMillis) / DAY_MILLIS;
    long bucketMillis = windowDays > DAILY_TO_WEEKLY_THRESHOLD_DAYS ? WEEK_MILLIS : DAY_MILLIS;

    String targetAppId = null;
    String agentUsername = null;
    switch (scope) {
      case SCOPE_COLLECTION -> targetAppId = id;
      case SCOPE_USER -> agentUsername = id;
      case SCOPE_INSTANCE -> {
        // No narrowing — instance-wide.
      }
      default -> throw new IllegalArgumentException(
        "Unknown scope: " + scope + " (valid: instance, collection, user)"
      );
    }

    // Single Cypher round-trip across the matched rows; buckets +
    // per-actionKind totals + distinct-agent count derived in Java
    // from the same matched set (was three separate Cypher round-
    // trips in PROV1c v1).
    var snap = activityDAO.aggregateStats(targetAppId, agentUsername, sinceMillis, untilMillis, bucketMillis);

    // For scope=user, "distinct contributors" is meaningless; report
    // 1 when the user has activity, 0 when they don't.
    long distinctAgents = SCOPE_USER.equals(scope)
      ? (snap.totalCount > 0 ? 1L : 0L)
      : snap.distinctAgents;

    List<long[]> cumulative = cumulativeIntegral(snap.buckets);

    // Content census: not window-filtered (NOT a "captured in this
    // window" count — that needs the FB1 byte-size field and is
    // tracked as PROV1-content-stats-2). v1 reports the at-query-time
    // "what's in here" totals so the dashboard tiles render now.
    Map<String, Long> census = switch (scope) {
      case SCOPE_COLLECTION -> censusDAO.censusForCollection(id);
      case SCOPE_INSTANCE -> censusDAO.censusInstanceWide();
      default -> null; // user-scope census is meaningless
    };

    return new ProvenanceStatsIO(
      scope,
      id,
      sinceMillis,
      untilMillis,
      bucketMillis,
      snap.totalCount,
      distinctAgents,
      snap.totalsByActionKind,
      snap.buckets,
      cumulative,
      census
    );
  }

  /**
   * Build the cumulative-integral list from per-bucket counts. Each
   * output entry is {@code [bucketStart, runningTotal]} — the sum of
   * every count up to and including the bucket. Same bucket
   * alignment as the input.
   */
  static List<long[]> cumulativeIntegral(List<long[]> buckets) {
    List<long[]> out = new ArrayList<>(buckets.size());
    long running = 0L;
    for (long[] b : buckets) {
      running += b[1];
      out.add(new long[] { b[0], running });
    }
    return out;
  }
}
