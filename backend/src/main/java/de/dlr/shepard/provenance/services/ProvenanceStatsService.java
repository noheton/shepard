package de.dlr.shepard.provenance.services;

import de.dlr.shepard.provenance.daos.ActivityDAO;
import de.dlr.shepard.v2.provenance.io.ProvenanceStatsIO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
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

    List<long[]> buckets = activityDAO.aggregateBuckets(targetAppId, agentUsername, sinceMillis, untilMillis, bucketMillis);
    long totalCount = buckets.stream().mapToLong(b -> b[1]).sum();
    Map<String, Long> totalsByActionKind = activityDAO.totalsByActionKind(targetAppId, agentUsername, sinceMillis, untilMillis);
    // For scope=user, "distinct contributors" is meaningless; report 1 (the user themselves) when they have activity.
    long distinctAgents = SCOPE_USER.equals(scope)
      ? (totalCount > 0 ? 1L : 0L)
      : activityDAO.distinctAgentCount(targetAppId, sinceMillis, untilMillis);

    return new ProvenanceStatsIO(scope, id, sinceMillis, untilMillis, bucketMillis, totalCount, distinctAgents, totalsByActionKind, buckets);
  }
}
