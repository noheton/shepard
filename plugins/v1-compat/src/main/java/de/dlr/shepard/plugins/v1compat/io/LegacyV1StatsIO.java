package de.dlr.shepard.plugins.v1compat.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Date;
import java.util.List;

/**
 * V1COMPAT.0 — JSON shape returned by
 * {@code GET /v2/admin/legacy/v1/stats}. In-memory counters per the
 * design's clarification 3 lean D (hybrid): the WARN log is
 * once-per-(path, sub)-per-process; the durable audit trail is
 * write-only via the {@code :Activity} infrastructure; this endpoint
 * surfaces the running totals an operator uses to decide whether
 * the v1 surface still has live callers worth waking up.
 *
 * @param totalHits        total v1 hits since process start
 * @param byEndpoint       per-endpoint hit counts, sorted by count
 *                          desc; entries beyond the top N are not
 *                          surfaced (configurable; default 50)
 * @param byPrincipal      per-principal hit counts, sorted by count
 *                          desc; same top-N cap
 * @param firstHitAt       wall-clock time of the first v1 hit since
 *                          process start; null when zero hits seen
 * @param mostRecentHitAt  wall-clock time of the most recent v1
 *                          hit; null when zero hits seen
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LegacyV1StatsIO(
  long totalHits,
  List<EndpointCount> byEndpoint,
  List<PrincipalCount> byPrincipal,
  Date firstHitAt,
  Date mostRecentHitAt
) {
  /** One row of the per-endpoint breakdown. */
  public record EndpointCount(String pathPattern, long hits) {}

  /** One row of the per-principal breakdown. */
  public record PrincipalCount(String principalSub, long hits) {}
}
