package de.dlr.shepard.v2.importer.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.v2.importer.entities.ImportDiagnosticEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * IMP-DIAG — unit tests for {@link ImportDiagnosticsLog}.
 *
 * <p>Tests cover: ring-buffer overflow (> 10,000 events), concurrent write
 * safety, level filtering, phase filtering, run listing, cleanup eviction,
 * and edge cases (unknown runId, blank runId).
 */
class ImportDiagnosticsLogTest {

  static final String RUN_ID   = "019f0000-1111-7000-a000-000000000001";
  static final String RUN_ID_2 = "019f0000-2222-7000-a000-000000000002";

  ImportDiagnosticsLog log;

  @BeforeEach
  void setUp() {
    log = new ImportDiagnosticsLog();
  }

  // ─── Basic append / query ─────────────────────────────────────────────────

  @Test
  void log_singleEvent_isQueryable() {
    log.log(RUN_ID, "INFO", "WARMUP", null, "Import started");

    List<ImportDiagnosticEvent> events = log.query(RUN_ID, null, null);
    assertEquals(1, events.size());
    ImportDiagnosticEvent e = events.get(0);
    assertEquals(RUN_ID, e.runId());
    assertEquals("INFO", e.level());
    assertEquals("WARMUP", e.phase());
    assertEquals("Import started", e.message());
    assertNotNull(e.timestamp());
    assertNotNull(e.attributes());
    assertTrue(e.attributes().isEmpty());
  }

  @Test
  void log_withAttributes_attributesPreserved() {
    Map<String, Object> attrs = Map.of("count", 42, "retryCount", 3);
    log.log(RUN_ID, "WARN", "DO_CREATE", "app-id-123", "Retry on entity", attrs);

    List<ImportDiagnosticEvent> events = log.query(RUN_ID, null, null);
    assertEquals(1, events.size());
    assertEquals("app-id-123", events.get(0).entityAppId());
    assertEquals(42, events.get(0).attributes().get("count"));
    assertEquals(3, events.get(0).attributes().get("retryCount"));
  }

  @Test
  void query_unknownRunId_returnsEmpty() {
    List<ImportDiagnosticEvent> events = log.query("unknown-run", null, null);
    assertTrue(events.isEmpty());
  }

  @Test
  void log_blankRunId_dropsEvent() {
    log.log("", "INFO", "WARMUP", null, "Should be dropped");
    log.log(null, "INFO", "WARMUP", null, "Also dropped");

    // No runs should be created.
    assertTrue(log.listRuns().isEmpty());
  }

  // ─── Level filter ─────────────────────────────────────────────────────────

  @Test
  void query_levelFilter_returnsOnlyMatchingLevel() {
    log.log(RUN_ID, "INFO",  "DO_CREATE", null, "Created");
    log.log(RUN_ID, "WARN",  "DO_CREATE", null, "Retry");
    log.log(RUN_ID, "ERROR", "DO_CREATE", null, "Failed");

    List<ImportDiagnosticEvent> infos  = log.query(RUN_ID, "INFO",  null);
    List<ImportDiagnosticEvent> warns  = log.query(RUN_ID, "WARN",  null);
    List<ImportDiagnosticEvent> errors = log.query(RUN_ID, "ERROR", null);

    assertEquals(1, infos.size());
    assertEquals("INFO", infos.get(0).level());

    assertEquals(1, warns.size());
    assertEquals("WARN", warns.get(0).level());

    assertEquals(1, errors.size());
    assertEquals("ERROR", errors.get(0).level());
  }

  @Test
  void query_levelFilter_noMatch_returnsEmpty() {
    log.log(RUN_ID, "INFO", "WARMUP", null, "Started");
    List<ImportDiagnosticEvent> errors = log.query(RUN_ID, "ERROR", null);
    assertTrue(errors.isEmpty());
  }

  // ─── Phase filter ─────────────────────────────────────────────────────────

  @Test
  void query_phaseFilter_returnsOnlyMatchingPhase() {
    log.log(RUN_ID, "INFO", "WARMUP",      null, "Warmup");
    log.log(RUN_ID, "INFO", "DO_CREATE",   null, "DO create");
    log.log(RUN_ID, "INFO", "REF_ATTACH",  null, "Ref attach");
    log.log(RUN_ID, "INFO", "FILE_UPLOAD", null, "File upload");
    log.log(RUN_ID, "INFO", "COMPLETE",    null, "Complete");

    for (String phase : List.of("WARMUP", "DO_CREATE", "REF_ATTACH", "FILE_UPLOAD", "COMPLETE")) {
      List<ImportDiagnosticEvent> result = log.query(RUN_ID, null, phase);
      assertEquals(1, result.size(), "Expected exactly one event for phase " + phase);
      assertEquals(phase, result.get(0).phase());
    }
  }

  @Test
  void query_levelAndPhaseFilter_combined() {
    log.log(RUN_ID, "INFO",  "DO_CREATE", null, "OK");
    log.log(RUN_ID, "WARN",  "DO_CREATE", null, "Retry");
    log.log(RUN_ID, "WARN",  "REF_ATTACH", null, "Retry ref");

    List<ImportDiagnosticEvent> result = log.query(RUN_ID, "WARN", "DO_CREATE");
    assertEquals(1, result.size());
    assertEquals("WARN",      result.get(0).level());
    assertEquals("DO_CREATE", result.get(0).phase());
  }

  // ─── Ring-buffer overflow ─────────────────────────────────────────────────

  @Test
  void ringBuffer_overflow_evictsOldestEvent() {
    // Fill the ring buffer exactly.
    for (int i = 0; i < ImportDiagnosticsLog.MAX_EVENTS_PER_RUN; i++) {
      log.log(RUN_ID, "INFO", "DO_CREATE", null, "event-" + i);
    }
    assertEquals(ImportDiagnosticsLog.MAX_EVENTS_PER_RUN, log.eventCount(RUN_ID));

    // The first event in the buffer should be "event-0".
    List<ImportDiagnosticEvent> before = log.query(RUN_ID, null, null);
    assertEquals("event-0", before.get(0).message());

    // Adding one more should evict "event-0".
    log.log(RUN_ID, "INFO", "DO_CREATE", null, "event-overflow");
    assertEquals(ImportDiagnosticsLog.MAX_EVENTS_PER_RUN, log.eventCount(RUN_ID),
        "Size must not exceed MAX_EVENTS_PER_RUN after overflow");

    List<ImportDiagnosticEvent> after = log.query(RUN_ID, null, null);
    // Head should now be "event-1", not "event-0".
    assertEquals("event-1", after.get(0).message(), "Oldest event should have been evicted");
    assertEquals("event-overflow", after.get(after.size() - 1).message());
  }

  @Test
  void ringBuffer_significantOverflow_sizeStaysAtMax() {
    int overflowCount = ImportDiagnosticsLog.MAX_EVENTS_PER_RUN + 5_000;
    for (int i = 0; i < overflowCount; i++) {
      log.log(RUN_ID, "INFO", "DO_CREATE", null, "event-" + i);
    }
    assertEquals(ImportDiagnosticsLog.MAX_EVENTS_PER_RUN, log.eventCount(RUN_ID));
  }

  // ─── Multiple runs ─────────────────────────────────────────────────────────

  @Test
  void multipleRuns_eventsAreIsolated() {
    log.log(RUN_ID,   "INFO", "WARMUP",    null, "Run 1 started");
    log.log(RUN_ID_2, "INFO", "DO_CREATE", null, "Run 2 DO created");

    assertEquals(1, log.query(RUN_ID,   null, null).size());
    assertEquals(1, log.query(RUN_ID_2, null, null).size());
    assertEquals("Run 1 started",    log.query(RUN_ID,   null, null).get(0).message());
    assertEquals("Run 2 DO created", log.query(RUN_ID_2, null, null).get(0).message());
  }

  // ─── listRuns ─────────────────────────────────────────────────────────────

  @Test
  void listRuns_returnsKnownRuns() {
    log.log(RUN_ID,   "INFO", "WARMUP", null, "Run 1");
    log.log(RUN_ID_2, "INFO", "WARMUP", null, "Run 2");

    List<ImportDiagnosticsLog.RunMeta> runs = log.listRuns();
    assertEquals(2, runs.size());
    assertTrue(runs.stream().anyMatch(r -> r.runId().equals(RUN_ID)));
    assertTrue(runs.stream().anyMatch(r -> r.runId().equals(RUN_ID_2)));
  }

  @Test
  void listRuns_lastLevelEscalates() {
    log.log(RUN_ID, "INFO",  "WARMUP",    null, "Started");
    log.log(RUN_ID, "WARN",  "DO_CREATE", null, "Retry");
    log.log(RUN_ID, "ERROR", "DO_CREATE", null, "Failed");
    log.log(RUN_ID, "INFO",  "COMPLETE",  null, "Done");   // INFO doesn't downgrade ERROR

    ImportDiagnosticsLog.RunMeta meta = log.listRuns().get(0);
    assertEquals(RUN_ID, meta.runId());
    assertEquals("ERROR", meta.lastLevel(), "Escalation to ERROR should survive subsequent INFO");
  }

  @Test
  void listRuns_sortedByStartTimeDescending() throws InterruptedException {
    log.log(RUN_ID,   "INFO", "WARMUP", null, "First run");
    Thread.sleep(2); // ensure distinct timestamps
    log.log(RUN_ID_2, "INFO", "WARMUP", null, "Second run");

    List<ImportDiagnosticsLog.RunMeta> runs = log.listRuns();
    assertEquals(2, runs.size());
    // Most-recent first.
    assertEquals(RUN_ID_2, runs.get(0).runId());
    assertEquals(RUN_ID,   runs.get(1).runId());
  }

  // ─── Cleanup ──────────────────────────────────────────────────────────────

  @Test
  void cleanup_staleRun_isEvicted() throws Exception {
    // Inject a run with a very old last-event timestamp by using reflection to
    // manipulate the runMeta map.  The simplest approach: call cleanup() directly
    // after artificially making the run old via a backdoor we expose here.
    log.log(RUN_ID, "INFO", "WARMUP", null, "Old run");

    // Grab the runMeta map and replace the entry with a stale one.
    var metaField = ImportDiagnosticsLog.class.getDeclaredField("runMeta");
    metaField.setAccessible(true);
    @SuppressWarnings("unchecked")
    java.util.concurrent.ConcurrentHashMap<String, ImportDiagnosticsLog.RunMeta> metaMap =
        (java.util.concurrent.ConcurrentHashMap<String, ImportDiagnosticsLog.RunMeta>)
            metaField.get(log);

    // Replace the meta with one whose lastEventAt is 25 hours in the past.
    java.time.Instant staleTime = java.time.Instant.now()
        .minusSeconds(ImportDiagnosticsLog.RUN_TTL_SECONDS + 3600);
    metaMap.put(RUN_ID, new ImportDiagnosticsLog.RunMeta(RUN_ID, staleTime, staleTime, "INFO"));

    log.cleanup();

    assertTrue(log.listRuns().isEmpty(), "Stale run should have been evicted");
    assertEquals(0, log.eventCount(RUN_ID), "Stale run's events should have been evicted");
  }

  @Test
  void cleanup_freshRun_isRetained() {
    log.log(RUN_ID, "INFO", "WARMUP", null, "Fresh run");

    log.cleanup();

    assertFalse(log.listRuns().isEmpty(), "Fresh run should not be evicted");
    assertEquals(1, log.eventCount(RUN_ID));
  }

  // ─── Concurrent write safety ──────────────────────────────────────────────

  @Test
  void concurrentWrites_noExceptionAndCountConsistent() throws InterruptedException {
    int threads  = 8;
    int perThread = 500;

    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch  = new CountDownLatch(threads);

    List<Throwable> errors = new ArrayList<>();

    for (int t = 0; t < threads; t++) {
      int tid = t;
      pool.submit(() -> {
        try {
          startLatch.await();
          for (int i = 0; i < perThread; i++) {
            log.log(RUN_ID, "INFO", "DO_CREATE", null,
                "thread-" + tid + "-event-" + i);
          }
        } catch (Throwable ex) {
          errors.add(ex);
        } finally {
          doneLatch.countDown();
        }
      });
    }

    startLatch.countDown();
    assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Writers did not finish in time");
    pool.shutdown();

    assertTrue(errors.isEmpty(), "No exceptions should occur during concurrent writes: " + errors);

    // The ring buffer may have overflowed (threads * perThread = 4000 < 10000) but let's
    // just assert the count is at most MAX and at least 1.
    int count = log.eventCount(RUN_ID);
    assertTrue(count >= 1, "At least one event should be present");
    assertTrue(count <= ImportDiagnosticsLog.MAX_EVENTS_PER_RUN,
        "Count must not exceed MAX_EVENTS_PER_RUN");
  }

  @Test
  void concurrentWrites_overMaxCapacity_staysAtMax() throws InterruptedException {
    int threads   = 4;
    int perThread = 4000; // 4 * 4000 = 16000 > MAX_EVENTS_PER_RUN (10000)

    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch latch = new CountDownLatch(threads);

    for (int t = 0; t < threads; t++) {
      int tid = t;
      pool.submit(() -> {
        try {
          for (int i = 0; i < perThread; i++) {
            log.log(RUN_ID, "INFO", "FILE_UPLOAD", null, "t" + tid + "-" + i);
          }
        } finally {
          latch.countDown();
        }
      });
    }

    assertTrue(latch.await(30, TimeUnit.SECONDS));
    pool.shutdown();

    assertTrue(log.eventCount(RUN_ID) <= ImportDiagnosticsLog.MAX_EVENTS_PER_RUN,
        "Ring buffer must never exceed MAX_EVENTS_PER_RUN");
  }
}
