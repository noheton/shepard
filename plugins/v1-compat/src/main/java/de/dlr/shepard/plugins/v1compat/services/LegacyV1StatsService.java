package de.dlr.shepard.plugins.v1compat.services;

import de.dlr.shepard.plugins.v1compat.io.LegacyV1StatsIO;
import de.dlr.shepard.plugins.v1compat.io.LegacyV1StatsIO.EndpointCount;
import de.dlr.shepard.plugins.v1compat.io.LegacyV1StatsIO.PrincipalCount;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * V1COMPAT.0 — in-process counter store for v1 surface hits (per
 * the design's clarification 3 lean D: hybrid in-memory counters +
 * write-only audit + once-per-(path, sub) WARN).
 *
 * <p>State lives in three {@link ConcurrentHashMap}s + a few
 * {@link AtomicLong}s — trusted JDK concurrent primitives, no
 * external cache library. Caffeine was considered (per the design)
 * but TTL-eviction isn't needed for Phase 1 — the per-process
 * lifetime is sufficient (a process restart resets stats; that's
 * the documented behaviour).
 *
 * <p>Memory ceiling: bounded by (distinct endpoint paths) × (distinct
 * principals). The path pattern is the v1 resource family (the
 * second path segment after {@code /shepard/api/} — e.g.
 * {@code /shepard/api/collections}), so endpoint cardinality is
 * O(20) for upstream's surface. Principal cardinality is bounded by
 * the installed user count. Total worst-case is ~20 × users, with
 * each entry holding 64 bytes — a thousand-user install costs
 * ~1 MB. The design doc accepts this; a future RING-buffer / LRU
 * variant lands if anyone hits the ceiling.
 *
 * <p>The first-hit-per-(path, sub)-per-process set ({@link #firstHitSeen})
 * is the deduplication seam for the once-per-pair WARN — the
 * {@code LegacyV1DeprecationFilter} consults this set to decide
 * whether to log.
 */
@ApplicationScoped
public class LegacyV1StatsService {

  /** Default top-N cap on the stats endpoint's two breakdown lists. */
  public static final int DEFAULT_TOP_N = 50;

  /** Total hits since process start. */
  private final AtomicLong totalHits = new AtomicLong(0);

  /** Per-endpoint hit counts (key = path pattern, e.g. "/shepard/api/collections"). */
  private final ConcurrentHashMap<String, AtomicLong> endpointHits = new ConcurrentHashMap<>();

  /** Per-principal hit counts (key = principal sub, or "anonymous" / "ip:x.x.x.x" fallback). */
  private final ConcurrentHashMap<String, AtomicLong> principalHits = new ConcurrentHashMap<>();

  /** First-hit set: "(path, principal)" pairs seen at least once this process lifetime. */
  private final Set<String> firstHitSeen = ConcurrentHashMap.newKeySet();

  /** Wall-clock time of the first observed v1 hit (millis since epoch); 0 = none. */
  private final AtomicLong firstHitMillis = new AtomicLong(0);

  /** Wall-clock time of the most-recent observed v1 hit; 0 = none. */
  private final AtomicReference<Long> mostRecentHitMillis = new AtomicReference<>(0L);

  /** Test-seam clock; production uses {@link System#currentTimeMillis()}. */
  private final ClockSource clock;

  public LegacyV1StatsService() {
    this.clock = System::currentTimeMillis;
  }

  /** Test-seam ctor — pass a fake clock. */
  public LegacyV1StatsService(ClockSource clock) {
    this.clock = clock;
  }

  /**
   * Record a hit. Idempotent in the sense that two concurrent
   * threads racing on the same (path, principal) pair both see the
   * counters increment correctly, but only one wins the
   * "first-hit" probe in {@link #checkAndMarkFirstHit(String, String)}.
   *
   * @param pathPattern  the abstracted path pattern (e.g.
   *                      {@code "/shepard/api/collections"})
   * @param principal    the principal sub (or fallback identifier)
   */
  public void recordHit(String pathPattern, String principal) {
    totalHits.incrementAndGet();
    counter(endpointHits, pathPattern).incrementAndGet();
    counter(principalHits, nullToAnonymous(principal)).incrementAndGet();
    long now = clock.nowMillis();
    firstHitMillis.compareAndSet(0L, now);
    mostRecentHitMillis.set(now);
  }

  /**
   * Probe + mark a (path, principal) pair as "WARN already logged
   * for this combination in this process". Returns {@code true} when
   * this call is the first observer (so the caller logs); subsequent
   * calls for the same pair return {@code false}.
   *
   * <p>Concurrency: backed by {@link Set#add(Object)} on the
   * concurrent set, which is atomic.
   */
  public boolean checkAndMarkFirstHit(String pathPattern, String principal) {
    return firstHitSeen.add(pathPattern + "\0" + nullToAnonymous(principal));
  }

  /** Snapshot the counters into an immutable IO record for the stats REST. */
  public LegacyV1StatsIO snapshot() {
    return snapshot(DEFAULT_TOP_N);
  }

  /** Snapshot with explicit top-N — for tests + future per-caller overrides. */
  public LegacyV1StatsIO snapshot(int topN) {
    int cap = Math.max(1, topN);
    long first = firstHitMillis.get();
    long recent = mostRecentHitMillis.get();
    return new LegacyV1StatsIO(
      totalHits.get(),
      topByCount(endpointHits, cap, (k, v) -> new EndpointCount(k, v)),
      topByCount(principalHits, cap, (k, v) -> new PrincipalCount(k, v)),
      first == 0L ? null : new Date(first),
      recent == 0L ? null : new Date(recent)
    );
  }

  /**
   * Reset the in-memory store. Not exposed via REST in Phase 1
   * (a process restart is the operator's reset gesture). Useful for
   * tests + Phase 2 admin-driven "reset stats" feature if needed.
   */
  public synchronized void reset() {
    totalHits.set(0);
    endpointHits.clear();
    principalHits.clear();
    firstHitSeen.clear();
    firstHitMillis.set(0);
    mostRecentHitMillis.set(0L);
  }

  /** Test-seam — total hit count without snapshotting the full IO. */
  public long getTotalHits() {
    return totalHits.get();
  }

  // ──────────────────────────────────────────────────────────────────────
  //  Helpers
  // ──────────────────────────────────────────────────────────────────────

  private static AtomicLong counter(ConcurrentHashMap<String, AtomicLong> map, String key) {
    return map.computeIfAbsent(key, k -> new AtomicLong(0));
  }

  private static String nullToAnonymous(String s) {
    return s == null || s.isBlank() ? "anonymous" : s;
  }

  private static <T> List<T> topByCount(
    ConcurrentHashMap<String, AtomicLong> source,
    int cap,
    java.util.function.BiFunction<String, Long, T> ctor
  ) {
    List<Map.Entry<String, Long>> all = new ArrayList<>();
    source.forEach((k, v) -> all.add(Map.entry(k, v.get())));
    all.sort(
      Comparator
        .<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue)
        .reversed()
        .thenComparing(Map.Entry::getKey)
    );
    List<Map.Entry<String, Long>> capped = all.size() > cap ? all.subList(0, cap) : all;
    List<T> out = new ArrayList<>(capped.size());
    for (Map.Entry<String, Long> e : capped) {
      out.add(ctor.apply(e.getKey(), e.getValue()));
    }
    return out;
  }

  /** Trivial clock-source seam so tests can pin timestamps. */
  @FunctionalInterface
  public interface ClockSource {
    long nowMillis();
  }
}
