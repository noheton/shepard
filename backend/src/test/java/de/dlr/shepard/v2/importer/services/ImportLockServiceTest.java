package de.dlr.shepard.v2.importer.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.v2.importer.daos.ImportLockDAO;
import de.dlr.shepard.v2.importer.entities.ImportLock;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * IMP-LOCK — unit tests for {@link ImportLockService}.
 *
 * <p>All Neo4j I/O is mocked.  Tests exercise acquire idempotency,
 * stale-heartbeat eviction, heartbeat, release, abandon, and cancel.
 * A real {@link ImportDiagnosticsLog} (no external deps) is injected so that
 * IMP-DIAG event emission is also verified.
 */
class ImportLockServiceTest {

  static final String COLLECTION_APP_ID = "019e3c96-5678-7000-a000-000000000030";
  static final String ALICE = "alice";
  static final String LOCK_ID = "019f0000-0000-7000-0000-000000000001";

  @Mock
  ImportLockDAO importLockDAO;

  ImportDiagnosticsLog diagnosticsLog;

  ImportLockService service;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    // Use a real (lightweight) diagnostics log — it has no external dependencies.
    diagnosticsLog = new ImportDiagnosticsLog();
    service = new ImportLockService();
    service.importLockDAO = importLockDAO;
    service.diagnosticsLog = diagnosticsLog;

    // Default: save returns argument with an appId
    when(importLockDAO.createOrUpdate(any(ImportLock.class)))
      .thenAnswer(inv -> {
        ImportLock l = inv.getArgument(0);
        if (l.getAppId() == null) l.setAppId("generated-app-id");
        return l;
      });
  }

  // ─── acquire: no existing lock ────────────────────────────────────────────

  @Test
  void acquire_whenNoExistingLock_returnsNewRunningLock() {
    when(importLockDAO.findRunning()).thenReturn(List.of());

    ImportLock result = service.acquire(COLLECTION_APP_ID, ALICE);

    assertNotNull(result);
    assertEquals("RUNNING", result.getStatus());
    assertEquals(COLLECTION_APP_ID, result.getTargetCollectionAppId());
    assertEquals(ALICE, result.getStartedBy());
    assertNotNull(result.getLockId());
    assertNotNull(result.getStartedAt());
    assertNotNull(result.getLastHeartbeatAt());
    verify(importLockDAO).createOrUpdate(result);

    // IMP-DIAG: a WARMUP event must have been emitted for the new lock.
    var events = diagnosticsLog.query(result.getLockId(), null, null);
    assertFalse(events.isEmpty(), "acquire() must emit a WARMUP diagnostic event");
    assertEquals("WARMUP", events.get(0).phase());
    assertEquals("INFO",   events.get(0).level());
  }

  // ─── acquire: fresh lock exists → conflict ────────────────────────────────

  @Test
  void acquire_whenFreshLockExists_returnsNull() {
    ImportLock existing = runningLock(LOCK_ID, System.currentTimeMillis());
    when(importLockDAO.findRunning()).thenReturn(List.of(existing));

    ImportLock result = service.acquire(COLLECTION_APP_ID, ALICE);

    assertNull(result, "Expected null when a fresh RUNNING lock exists");
    // Must NOT persist a new lock
    verify(importLockDAO, never()).createOrUpdate(any());
  }

  // ─── acquire: stale lock exists → abandon old, create new ─────────────────

  @Test
  void acquire_whenStaleLockExists_abandonsOldAndCreatesNew() {
    long staleHeartbeat = System.currentTimeMillis() - (ImportLockService.STALE_HEARTBEAT_MS + 10_000L);
    ImportLock stale = runningLock(LOCK_ID, staleHeartbeat);
    when(importLockDAO.findRunning()).thenReturn(List.of(stale));

    ImportLock result = service.acquire(COLLECTION_APP_ID, ALICE);

    assertNotNull(result, "Expected a new lock despite stale existing lock");
    assertEquals("RUNNING", result.getStatus());

    // The stale lock must have been transitioned to ABANDONED
    ArgumentCaptor<ImportLock> captor = ArgumentCaptor.forClass(ImportLock.class);
    verify(importLockDAO, org.mockito.Mockito.atLeast(2)).createOrUpdate(captor.capture());
    boolean abandonFound = captor.getAllValues().stream()
      .anyMatch(l -> "ABANDONED".equals(l.getStatus()) && LOCK_ID.equals(l.getLockId()));
    assertTrue(abandonFound, "Expected stale lock to be saved with status=ABANDONED");
  }

  // ─── acquire: stale lock with null heartbeat (only startedAt) ─────────────

  @Test
  void acquire_whenStaleLockWithNullHeartbeat_abandonsOldAndCreatesNew() {
    ImportLock stale = new ImportLock(1L);
    stale.setLockId(LOCK_ID);
    stale.setStatus("RUNNING");
    stale.setStartedAt(System.currentTimeMillis() - (ImportLockService.STALE_HEARTBEAT_MS + 5_000L));
    stale.setLastHeartbeatAt(null); // null heartbeat falls back to startedAt
    stale.setTargetCollectionAppId(COLLECTION_APP_ID);
    stale.setStartedBy(ALICE);
    when(importLockDAO.findRunning()).thenReturn(List.of(stale));

    ImportLock result = service.acquire(COLLECTION_APP_ID, ALICE);

    assertNotNull(result);
    assertEquals("RUNNING", result.getStatus());
  }

  // ─── heartbeat ────────────────────────────────────────────────────────────

  @Test
  void heartbeat_whenRunning_updatesLastHeartbeatAt() {
    long originalHb = System.currentTimeMillis() - 30_000L;
    ImportLock lock = runningLock(LOCK_ID, originalHb);
    when(importLockDAO.findByLockId(LOCK_ID)).thenReturn(lock);

    ImportLock result = service.heartbeat(LOCK_ID);

    assertNotNull(result);
    assertTrue(result.getLastHeartbeatAt() > originalHb,
      "lastHeartbeatAt should have been updated");
    assertEquals("RUNNING", result.getStatus());
    verify(importLockDAO).createOrUpdate(result);
  }

  @Test
  void heartbeat_whenNotFound_returnsNull() {
    when(importLockDAO.findByLockId(LOCK_ID)).thenReturn(null);
    assertNull(service.heartbeat(LOCK_ID));
    verify(importLockDAO, never()).createOrUpdate(any());
  }

  @Test
  void heartbeat_whenNotRunning_returnsNull() {
    ImportLock completed = runningLock(LOCK_ID, System.currentTimeMillis());
    completed.setStatus("COMPLETED");
    when(importLockDAO.findByLockId(LOCK_ID)).thenReturn(completed);

    assertNull(service.heartbeat(LOCK_ID));
    verify(importLockDAO, never()).createOrUpdate(any());
  }

  // ─── release ──────────────────────────────────────────────────────────────

  @Test
  void release_whenRunning_transitionsToCompleted() {
    ImportLock lock = runningLock(LOCK_ID, System.currentTimeMillis());
    when(importLockDAO.findByLockId(LOCK_ID)).thenReturn(lock);

    ImportLock result = service.release(LOCK_ID);

    assertNotNull(result);
    assertEquals("COMPLETED", result.getStatus());
    verify(importLockDAO).createOrUpdate(result);

    // IMP-DIAG: a COMPLETE/INFO event must have been emitted.
    var events = diagnosticsLog.query(LOCK_ID, "INFO", "COMPLETE");
    assertFalse(events.isEmpty(), "release() must emit a COMPLETE INFO diagnostic event");
  }

  @Test
  void release_whenNotFound_returnsNull() {
    when(importLockDAO.findByLockId(LOCK_ID)).thenReturn(null);
    assertNull(service.release(LOCK_ID));
    verify(importLockDAO, never()).createOrUpdate(any());
  }

  @Test
  void release_whenNotRunning_returnsNull() {
    ImportLock failed = runningLock(LOCK_ID, System.currentTimeMillis());
    failed.setStatus("FAILED");
    when(importLockDAO.findByLockId(LOCK_ID)).thenReturn(failed);

    assertNull(service.release(LOCK_ID));
    verify(importLockDAO, never()).createOrUpdate(any());
  }

  // ─── abandon ──────────────────────────────────────────────────────────────

  @Test
  void abandon_whenRunning_transitionsToFailed() {
    ImportLock lock = runningLock(LOCK_ID, System.currentTimeMillis());
    when(importLockDAO.findByLockId(LOCK_ID)).thenReturn(lock);

    ImportLock result = service.abandon(LOCK_ID, "out of memory");

    assertNotNull(result);
    assertEquals("FAILED", result.getStatus());
    assertEquals("out of memory", result.getErrorMessage());
    verify(importLockDAO).createOrUpdate(result);

    // IMP-DIAG: a COMPLETE/ERROR event must have been emitted.
    var events = diagnosticsLog.query(LOCK_ID, "ERROR", "COMPLETE");
    assertFalse(events.isEmpty(), "abandon() must emit a COMPLETE ERROR diagnostic event");
    assertTrue(events.get(0).message().contains("out of memory"));
  }

  @Test
  void abandon_whenNotFound_returnsNull() {
    when(importLockDAO.findByLockId(LOCK_ID)).thenReturn(null);
    assertNull(service.abandon(LOCK_ID, "err"));
    verify(importLockDAO, never()).createOrUpdate(any());
  }

  // ─── cancel ───────────────────────────────────────────────────────────────

  @Test
  void cancel_whenRunning_transitionsToCancelled() {
    ImportLock lock = runningLock(LOCK_ID, System.currentTimeMillis());
    when(importLockDAO.findByLockId(LOCK_ID)).thenReturn(lock);

    ImportLock result = service.cancel(LOCK_ID);

    assertNotNull(result);
    assertEquals("CANCELLED", result.getStatus());
    verify(importLockDAO).createOrUpdate(result);
  }

  @Test
  void cancel_whenAlreadyCompleted_returnsAsIsWithoutSave() {
    ImportLock completed = runningLock(LOCK_ID, System.currentTimeMillis());
    completed.setStatus("COMPLETED");
    when(importLockDAO.findByLockId(LOCK_ID)).thenReturn(completed);

    ImportLock result = service.cancel(LOCK_ID);

    assertNotNull(result);
    assertEquals("COMPLETED", result.getStatus());
    verify(importLockDAO, never()).createOrUpdate(any());
  }

  @Test
  void cancel_whenNotFound_returnsNull() {
    when(importLockDAO.findByLockId(LOCK_ID)).thenReturn(null);
    assertNull(service.cancel(LOCK_ID));
  }

  // ─── findCurrent ──────────────────────────────────────────────────────────

  @Test
  void findCurrent_delegatesToDAO() {
    ImportLock lock = runningLock(LOCK_ID, System.currentTimeMillis());
    when(importLockDAO.findLatest()).thenReturn(lock);
    assertEquals(lock, service.findCurrent());
  }

  @Test
  void findCurrent_whenNone_returnsNull() {
    when(importLockDAO.findLatest()).thenReturn(null);
    assertNull(service.findCurrent());
  }

  // ─── Factories ────────────────────────────────────────────────────────────

  private static ImportLock runningLock(String lockId, long heartbeatEpochMs) {
    ImportLock l = new ImportLock(1L);
    l.setLockId(lockId);
    l.setStatus("RUNNING");
    l.setStartedAt(heartbeatEpochMs - 1000L);
    l.setLastHeartbeatAt(heartbeatEpochMs);
    l.setTargetCollectionAppId(COLLECTION_APP_ID);
    l.setStartedBy(ALICE);
    l.setAppId("test-app-id");
    return l;
  }
}
