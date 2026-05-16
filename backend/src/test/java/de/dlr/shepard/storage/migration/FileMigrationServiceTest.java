package de.dlr.shepard.storage.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.storage.FileStorage;
import de.dlr.shepard.storage.FileStorageRegistry;
import de.dlr.shepard.storage.StorageGetResponse;
import de.dlr.shepard.storage.StorageLocator;
import de.dlr.shepard.storage.StoragePutRequest;
import jakarta.enterprise.inject.Instance;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * FS1e1 — unit tests for {@link FileMigrationService}.
 *
 * <p>Uses a stubbed {@link FileStorageRegistry} and adapter mocks to
 * exercise the service layer without touching Neo4j or real storage
 * backends. The integration-level happy path (boot real Neo4j + two
 * adapters, trigger migration, verify providerId flips) is deferred
 * as a testcontainer fixture alongside the broader QuarkusTest
 * scaffolding.
 */
class FileMigrationServiceTest {

  private FileStorageRegistry registry;
  private FileMigrationService service;

  @SuppressWarnings("unchecked")
  private static FileStorageRegistry registryWith(FileStorage... adapters) {
    Instance<FileStorage> instance = mock(Instance.class);
    when(instance.iterator()).thenAnswer(inv -> List.of(adapters).iterator());
    FileStorageRegistry reg = new FileStorageRegistry("gridfs", instance);
    return reg;
  }

  private static FileStorage enabledAdapter(String id) {
    return new FileStorage() {
      @Override public String id() { return id; }
      @Override public boolean isEnabled() { return true; }
      @Override public StorageLocator put(StoragePutRequest r) throws Exception {
        return new StorageLocator(id, r.container() + "/" + r.fileName());
      }
      @Override public StorageGetResponse get(StorageLocator l) throws Exception {
        return new StorageGetResponse(id, "f.bin", null, 3L,
          new ByteArrayInputStream(new byte[]{1, 2, 3}));
      }
      @Override public void delete(StorageLocator l) throws Exception {}
    };
  }

  private static FileStorage disabledAdapter(String id) {
    return new FileStorage() {
      @Override public String id() { return id; }
      @Override public boolean isEnabled() { return false; }
      @Override public StorageLocator put(StoragePutRequest r) { return null; }
      @Override public StorageGetResponse get(StorageLocator l) { return null; }
      @Override public void delete(StorageLocator l) {}
    };
  }

  @BeforeEach
  void setUp() {
    registry = registryWith(enabledAdapter("gridfs"), enabledAdapter("s3"));
    service = new FileMigrationService();
    service.registry = registry;
  }

  // ─── initial state ─────────────────────────────────────────────────────────

  @Test
  void initialStateIsIdle() {
    FileMigrationState state = service.getState();
    assertEquals(FileMigrationStatus.IDLE, state.status());
    assertEquals(0, state.filesTotal());
    assertEquals(0, state.filesMigrated());
    assertEquals(0, state.filesFailed());
  }

  // ─── validation ────────────────────────────────────────────────────────────

  @Test
  void triggerRejectsBlankSource() {
    assertThrows(IllegalArgumentException.class,
      () -> service.triggerMigration("", "s3"));
  }

  @Test
  void triggerRejectsBlankTarget() {
    assertThrows(IllegalArgumentException.class,
      () -> service.triggerMigration("gridfs", ""));
  }

  @Test
  void triggerRejectsSameSourceAndTarget() {
    assertThrows(IllegalArgumentException.class,
      () -> service.triggerMigration("gridfs", "gridfs"));
  }

  @Test
  void triggerRejectsUnknownSource() {
    assertThrows(IllegalArgumentException.class,
      () -> service.triggerMigration("unknown", "s3"));
  }

  @Test
  void triggerRejectsUnknownTarget() {
    assertThrows(IllegalArgumentException.class,
      () -> service.triggerMigration("gridfs", "unknown"));
  }

  @Test
  void triggerRejectsDisabledAdapter() {
    FileStorageRegistry reg = registryWith(enabledAdapter("gridfs"), disabledAdapter("s3"));
    FileMigrationService svc = new FileMigrationService();
    svc.registry = reg;
    assertThrows(IllegalArgumentException.class,
      () -> svc.triggerMigration("gridfs", "s3"));
  }

  // ─── transition to RUNNING ─────────────────────────────────────────────────

  @Test
  void triggerTransitionsToRunning() {
    FileMigrationState state = service.triggerMigration("gridfs", "s3");
    assertEquals(FileMigrationStatus.RUNNING, state.status());
    assertEquals("gridfs", state.sourceProviderId());
    assertEquals("s3", state.targetProviderId());
    assertNotNull(state.startedAt());
  }

  @Test
  void secondTriggerAfterCompletionCanRetrigger() throws InterruptedException {
    service.triggerMigration("gridfs", "s3");
    // Let the background thread finish (Neo4j session is null → fast failure).
    Thread.sleep(200);
    FileMigrationState after = service.getState();
    // Should be DONE or FAILED (not RUNNING) after the background thread ran.
    assertTrue(after.status() != FileMigrationStatus.RUNNING,
      "Expected migration to complete quickly in unit test (no real Neo4j)");
    // A subsequent trigger must succeed (state is no longer RUNNING).
    FileMigrationState next = service.triggerMigration("gridfs", "s3");
    assertEquals(FileMigrationStatus.RUNNING, next.status());
  }

  // ─── state record ─────────────────────────────────────────────────────────

  @Test
  void idleStateFactoryHasNullTimestamps() {
    FileMigrationState idle = FileMigrationState.idle();
    assertEquals(FileMigrationStatus.IDLE, idle.status());
    assertEquals(0, idle.filesTotal());
    assertTrue(idle.startedAt() == null);
    assertTrue(idle.updatedAt() == null);
    assertTrue(idle.errorMessage() == null);
  }

  @Test
  void startingStateHasNonNullTimestamp() {
    FileMigrationState s = FileMigrationState.starting("gridfs", "s3");
    assertEquals(FileMigrationStatus.RUNNING, s.status());
    assertNotNull(s.startedAt());
    assertNotNull(s.updatedAt());
  }

  @Test
  void withTotalUpdatesTimestamp() throws InterruptedException {
    FileMigrationState s = FileMigrationState.starting("gridfs", "s3");
    Thread.sleep(1); // ensure clock advances
    FileMigrationState s2 = s.withTotal(100);
    assertEquals(100, s2.filesTotal());
    assertTrue(!s2.updatedAt().isBefore(s.updatedAt()));
  }

  @Test
  void withDoneFlipsStatus() {
    FileMigrationState s = FileMigrationState.starting("gridfs", "s3").withTotal(5).withProgress(5, 0);
    FileMigrationState done = s.withDone();
    assertEquals(FileMigrationStatus.DONE, done.status());
    assertEquals(5, done.filesMigrated());
  }

  @Test
  void withFailedFlipsStatusAndSetsError() {
    FileMigrationState s = FileMigrationState.starting("gridfs", "s3");
    FileMigrationState failed = s.withFailed("boom");
    assertEquals(FileMigrationStatus.FAILED, failed.status());
    assertEquals("boom", failed.errorMessage());
  }
}
