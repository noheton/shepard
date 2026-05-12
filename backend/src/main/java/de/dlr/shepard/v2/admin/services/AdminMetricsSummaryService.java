package de.dlr.shepard.v2.admin.services;

import de.dlr.shepard.v2.admin.io.AdminMetricsSummaryIO;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.lang.management.ManagementFactory;
import java.util.concurrent.TimeUnit;

/**
 * Snapshot of in-process metrics for the admin-page strip
 * (A3b1, per {@code aidocs/51 §9.5}).
 *
 * <p>Reads from Java's {@link Runtime} + {@link ManagementFactory} and
 * the Micrometer {@link MeterRegistry} — no Prometheus dependency.
 * Cheap enough to call on every admin page-load.
 */
@ApplicationScoped
public class AdminMetricsSummaryService {

  static final String CACHE_GETS_METER = "cache.gets";
  static final String CACHE_TAG = "cache";
  static final String RESULT_TAG = "result";
  static final String PERMISSIONS_CACHE = "permissions-service-cache";
  static final String HTTP_SERVER_TIMER = "http.server.requests";

  @Inject
  MeterRegistry registry;

  public AdminMetricsSummaryIO snapshot() {
    Runtime rt = Runtime.getRuntime();
    long heapUsed = rt.totalMemory() - rt.freeMemory();
    long heapMax = rt.maxMemory();
    long uptime = ManagementFactory.getRuntimeMXBean().getUptime();

    // HTTP requests — sum across all per-tag Timers.
    long httpTotal = 0L;
    double httpTotalMillis = 0.0;
    for (Timer t : registry.find(HTTP_SERVER_TIMER).timers()) {
      httpTotal += t.count();
      httpTotalMillis += t.totalTime(TimeUnit.MILLISECONDS);
    }
    Double httpMeanMillis = httpTotal == 0 ? null : httpTotalMillis / httpTotal;

    // Permissions cache hits / misses.
    long hits = (long) sum(registry.find(CACHE_GETS_METER).tag(CACHE_TAG, PERMISSIONS_CACHE).tag(RESULT_TAG, "hit").counters().stream());
    long misses = (long) sum(registry.find(CACHE_GETS_METER).tag(CACHE_TAG, PERMISSIONS_CACHE).tag(RESULT_TAG, "miss").counters().stream());
    long totalGets = hits + misses;
    Double hitRatio = totalGets == 0 ? null : (double) hits / (double) totalGets;

    return new AdminMetricsSummaryIO(heapUsed, heapMax, uptime, httpTotal, httpMeanMillis, hits, misses, hitRatio);
  }

  private static double sum(java.util.stream.Stream<io.micrometer.core.instrument.Counter> counters) {
    return counters.mapToDouble(io.micrometer.core.instrument.Counter::count).sum();
  }
}
