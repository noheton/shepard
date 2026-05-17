package de.dlr.shepard.storage.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import de.dlr.shepard.storage.FileStorage;
import de.dlr.shepard.storage.FileStorageRegistry;
import de.dlr.shepard.storage.StorageGetResponse;
import de.dlr.shepard.storage.StorageLocator;
import de.dlr.shepard.storage.StoragePutRequest;
import jakarta.enterprise.inject.Instance;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * FS1e2 — unit tests for the {@link FileMigrationService#autoSweep()} method.
 *
 * <p>Follows the same direct-construction + field-assignment pattern as
 * {@link FileMigrationServiceTest}. Config properties ({@code autoSweepEnabled},
 * {@code autoSweepSource}, {@code autoSweepTarget}) are package-visible and
 * assigned directly. {@code stateRef} is private-final and requires a single
 * reflective write only in the "already-running" guard test.
 */
class FileMigrationAutoSweepTest {

  private FileMigrationService service;

  @SuppressWarnings("unchecked")
  private static FileStorageRegistry registryWith(FileStorage... adapters) {
    Instance<FileStorage> instance = Mockito.mock(Instance.class);
    Mockito.when(instance.iterator()).thenAnswer(inv -> List.of(adapters).iterator());
    return new FileStorageRegistry("gridfs", instance);
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

  @BeforeEach
  void setUp() {
    service = new FileMigrationService();
    service.registry = registryWith(enabledAdapter("gridfs"), enabledAdapter("s3"));
    // Reset config fields to safe defaults before each test
    service.autoSweepEnabled = false;
    service.autoSweepSource = "";
    service.autoSweepTarget = "";
  }

  // ─── disabled by default ──────────────────────────────────────────────────

  @Test
  void autoSweep_disabledByDefault_noMigrationTriggered() {
    service.autoSweepEnabled = false;
    service.autoSweep();
    assertEquals(FileMigrationStatus.IDLE, service.getState().status());
  }

  // ─── missing configuration guards ─────────────────────────────────────────

  @Test
  void autoSweep_missingSource_logsWarningAndSkips() {
    service.autoSweepEnabled = true;
    service.autoSweepSource = "";
    service.autoSweepTarget = "s3";
    service.autoSweep();
    assertEquals(FileMigrationStatus.IDLE, service.getState().status());
  }

  @Test
  void autoSweep_missingTarget_logsWarningAndSkips() {
    service.autoSweepEnabled = true;
    service.autoSweepSource = "gridfs";
    service.autoSweepTarget = "";
    service.autoSweep();
    assertEquals(FileMigrationStatus.IDLE, service.getState().status());
  }

  // ─── already-running guard ────────────────────────────────────────────────

  @Test
  void autoSweep_alreadyRunning_skipsWithoutSecondTrigger() throws Exception {
    // Force stateRef to RUNNING via reflection (it is private final)
    Field stateRefField = FileMigrationService.class.getDeclaredField("stateRef");
    stateRefField.setAccessible(true);
    @SuppressWarnings("unchecked")
    AtomicReference<FileMigrationState> ref =
      (AtomicReference<FileMigrationState>) stateRefField.get(service);
    ref.set(FileMigrationState.starting("gridfs", "s3"));

    // Pre-check: state is RUNNING
    assertEquals(FileMigrationStatus.RUNNING, service.getState().status());
    String startedAt = service.getState().startedAt().toString();

    service.autoSweepEnabled = true;
    service.autoSweepSource = "gridfs";
    service.autoSweepTarget = "s3";
    service.autoSweep();

    // State should still be the same RUNNING record — no new trigger
    assertEquals(FileMigrationStatus.RUNNING, service.getState().status());
    assertEquals(startedAt, service.getState().startedAt().toString());
  }

  // ─── happy path ───────────────────────────────────────────────────────────

  @Test
  void autoSweep_enabledAndValid_transitionsToRunning() {
    service.autoSweepEnabled = true;
    service.autoSweepSource = "gridfs";
    service.autoSweepTarget = "s3";
    service.autoSweep();
    // triggerMigration() sets state to RUNNING synchronously before dispatching
    // to the executor. The background thread will fail (no Neo4j session), but
    // the synchronous transition has already happened — matches the pattern in
    // FileMigrationServiceTest#triggerTransitionsToRunning.
    FileMigrationState state = service.getState();
    assertEquals(FileMigrationStatus.RUNNING, state.status());
    assertEquals("gridfs", state.sourceProviderId());
    assertEquals("s3", state.targetProviderId());
    assertNotNull(state.startedAt());
  }
}
