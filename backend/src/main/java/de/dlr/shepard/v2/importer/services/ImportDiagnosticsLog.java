package de.dlr.shepard.v2.importer.services;

import de.dlr.shepard.v2.importer.entities.ImportDiagnosticEvent;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IMP-DIAG — in-memory ring-buffer store for structured import diagnostic events.
 *
 * <h2>Design</h2>
 * <p>Events are stored per run in a {@link ConcurrentHashMap} of bounded
 * {@link Deque}s.  When a run exceeds {@value #MAX_EVENTS_PER_RUN} events the
 * oldest event is evicted (FIFO ring behaviour), keeping the tail of the run
 * fully visible at low memory cost.
 *
 * <p>Run metadata is recorded in a parallel {@code ConcurrentHashMap<String, RunMeta>}
 * so that {@code GET /v2/import/runs} can return the run list without scanning all
 * event deques.
 *
 * <h2>Cleanup</h2>
 * <p>A {@code @Scheduled} job runs every hour and removes runs whose last event is
 * older than {@value #RUN_TTL_SECONDS} seconds (24 hours).  This prevents unbounded
 * memory growth across many import cycles.
 *
 * <h2>Thread safety</h2>
 * <p>Both maps are {@link ConcurrentHashMap}s.  The event deques themselves are
 * {@link LinkedList}s synchronised on the deque monitor.  All reads and writes
 * take the deque lock, keeping the synchronisation scope narrow.
 */
@ApplicationScoped
public class ImportDiagnosticsLog {

  /** Maximum events retained per run (ring-buffer overflow evicts oldest). */
  static final int MAX_EVENTS_PER_RUN = 10_000;

  /** Age in seconds after which a run's events are eligible for eviction (24 h). */
  static final long RUN_TTL_SECONDS = 86_400L;

  // ─── State ────────────────────────────────────────────────────────────────

  /** runId → ring buffer of events. */
  private final ConcurrentHashMap<String, Deque<ImportDiagnosticEvent>> eventsByRun =
      new ConcurrentHashMap<>();

  /** runId → lightweight run metadata for the /runs listing endpoint. */
  private final ConcurrentHashMap<String, RunMeta> runMeta = new ConcurrentHashMap<>();

  // ─── Public API ───────────────────────────────────────────────────────────

  /**
   * Append a new diagnostic event for the given run.
   *
   * <p>If the run's buffer is full ({@value #MAX_EVENTS_PER_RUN} entries),
   * the oldest event is silently discarded so the tail of the run stays current.
   *
   * @param runId       the import lock's {@code lockId}; must not be blank
   * @param level       one of {@code INFO / WARN / ERROR}
   * @param phase       one of {@code WARMUP / DO_CREATE / REF_ATTACH / FILE_UPLOAD / COMPLETE}
   * @param entityAppId appId of the entity this event relates to; may be {@code null}
   * @param message     human-readable description; must not be blank
   * @param attributes  optional structured context; may be {@code null}
   */
  public void log(
      String runId,
      String level,
      String phase,
      String entityAppId,
      String message,
      Map<String, Object> attributes) {

    if (runId == null || runId.isBlank()) {
      Log.warnf("IMP-DIAG: log() called with blank runId — event dropped (level=%s msg=%s)",
          level, message);
      return;
    }

    ImportDiagnosticEvent event = new ImportDiagnosticEvent(
        runId,
        Instant.now(),
        level,
        phase,
        entityAppId,
        message,
        attributes == null ? Map.of() : attributes
    );

    // Ensure run deque exists.
    Deque<ImportDiagnosticEvent> deque = eventsByRun.computeIfAbsent(
        runId, k -> new LinkedList<>());

    synchronized (deque) {
      if (deque.size() >= MAX_EVENTS_PER_RUN) {
        deque.pollFirst();   // evict oldest
      }
      deque.addLast(event);
    }

    // Update or create run metadata.
    runMeta.compute(runId, (k, meta) -> {
      Instant now = event.timestamp();
      if (meta == null) {
        return new RunMeta(runId, now, now, level);
      }
      // Escalate status if new event is more severe.
      String newStatus = escalate(meta.lastLevel(), level);
      return new RunMeta(runId, meta.startedAt(), now, newStatus);
    });
  }

  /**
   * Convenience overload without attributes.
   */
  public void log(String runId, String level, String phase, String entityAppId, String message) {
    log(runId, level, phase, entityAppId, message, Map.of());
  }

  /**
   * Return all events for the given run, optionally filtered by {@code level} and/or
   * {@code phase}.
   *
   * @param runId      the run to query
   * @param levelFilter if non-null, only events with this level are returned
   * @param phaseFilter if non-null, only events in this phase are returned
   * @return ordered list of matching events (oldest first); empty if run unknown
   */
  public List<ImportDiagnosticEvent> query(
      String runId,
      String levelFilter,
      String phaseFilter) {

    Deque<ImportDiagnosticEvent> deque = eventsByRun.get(runId);
    if (deque == null) return List.of();

    List<ImportDiagnosticEvent> snapshot;
    synchronized (deque) {
      snapshot = new ArrayList<>(deque);
    }

    if (levelFilter == null && phaseFilter == null) return snapshot;

    return snapshot.stream()
        .filter(e -> levelFilter == null || levelFilter.equals(e.level()))
        .filter(e -> phaseFilter == null || phaseFilter.equals(e.phase()))
        .toList();
  }

  /**
   * Return metadata for all known runs, sorted by start time descending
   * (most recent first).
   *
   * @return snapshot of run metadata records
   */
  public List<RunMeta> listRuns() {
    Collection<RunMeta> values = runMeta.values();
    List<RunMeta> sorted = new ArrayList<>(values);
    sorted.sort((a, b) -> b.startedAt().compareTo(a.startedAt()));
    return Collections.unmodifiableList(sorted);
  }

  /**
   * Return the set of known run IDs.  Exposed for testing.
   */
  Set<String> runIds() {
    return Collections.unmodifiableSet(eventsByRun.keySet());
  }

  /**
   * Number of events stored for a run.  Exposed for testing.
   */
  int eventCount(String runId) {
    Deque<ImportDiagnosticEvent> deque = eventsByRun.get(runId);
    if (deque == null) return 0;
    synchronized (deque) {
      return deque.size();
    }
  }

  // ─── Scheduled cleanup ────────────────────────────────────────────────────

  /**
   * Evict runs whose last event is older than {@value #RUN_TTL_SECONDS} seconds.
   * Runs every hour.  Does not require a request context.
   */
  @Scheduled(every = "1h", identity = "import-diagnostics-cleanup")
  void cleanup() {
    Instant cutoff = Instant.now().minusSeconds(RUN_TTL_SECONDS);
    int removed = 0;

    for (Map.Entry<String, RunMeta> entry : runMeta.entrySet()) {
      if (entry.getValue().lastEventAt().isBefore(cutoff)) {
        String runId = entry.getKey();
        runMeta.remove(runId);
        eventsByRun.remove(runId);
        removed++;
      }
    }

    if (removed > 0) {
      Log.infof("IMP-DIAG: cleanup evicted %d stale run(s)", removed);
    }
  }

  // ─── Inner types ──────────────────────────────────────────────────────────

  /**
   * Lightweight metadata record for a run, stored separately from the event deque
   * to allow fast {@code /runs} listings without iterating all events.
   *
   * @param runId        the lock's {@code lockId}
   * @param startedAt    timestamp of the first event for this run
   * @param lastEventAt  timestamp of the most recent event
   * @param lastLevel    most-severe level seen so far ({@code INFO}, {@code WARN},
   *                     or {@code ERROR})
   */
  public record RunMeta(
      String runId,
      Instant startedAt,
      Instant lastEventAt,
      String lastLevel
  ) {}

  // ─── Private helpers ──────────────────────────────────────────────────────

  /** Return the more severe of two level strings (ERROR > WARN > INFO). */
  private static String escalate(String current, String incoming) {
    Map<String, Integer> rank = new TreeMap<>(Map.of(
        ImportDiagnosticEvent.LEVEL_INFO,  0,
        ImportDiagnosticEvent.LEVEL_WARN,  1,
        ImportDiagnosticEvent.LEVEL_ERROR, 2
    ));
    int curRank = rank.getOrDefault(current, 0);
    int inRank  = rank.getOrDefault(incoming, 0);
    return inRank > curRank ? incoming : current;
  }
}
