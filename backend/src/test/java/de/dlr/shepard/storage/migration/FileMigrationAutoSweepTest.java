package de.dlr.shepard.storage.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import de.dlr.shepard.storage.FileStorage;
import de.dlr.shepard.storage.FileStorageRegistry;
import de.dlr.shepard.storage.StorageException;
import de.dlr.shepard.storage.StorageGetResponse;
import de.dlr.shepard.storage.StorageLocator;
import de.dlr.shepard.storage.StoragePutRequest;
import de.dlr.shepard.v2.admin.storage.services.AutosweepConfigService;
import jakarta.enterprise.inject.Instance;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * FS1e2 — unit tests for the {@link FileMigrationService#autoSweep()} method.
 *
 * <p>FTOGGLE-AUTOSWEEP-1: config is now read from a mocked
 * {@link AutosweepConfigService} rather than bare {@code @ConfigProperty} fields.
 */
class FileMigrationAutoSweepTest {

  private FileMigrationService service;
  private AutosweepConfigService autosweepConfigService;

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
      @Override public StorageLocator put(StoragePutRequest r) throws StorageException {
        return new StorageLocator(id, r.container() + "/" + r.fileName());
      }
      @Override public StorageGetResponse get(StorageLocator l) throws StorageException {
        return new StorageGetResponse(id, "f.bin", null, 3L,
          new ByteArrayInputStream(new byte[]{1, 2, 3}));
      }
      @Override public void delete(StorageLocator l) throws StorageException {}
    };
  }

  @BeforeEach
  void setUp() {
    autosweepConfigService = Mockito.mock(AutosweepConfigService.class);
    Mockito.when(autosweepConfigService.effectiveEnabled()).thenReturn(false);
    Mockito.when(autosweepConfigService.effectiveSource()).thenReturn("");
    Mockito.when(autosweepConfigService.effectiveTarget()).thenReturn("");
    service = new FileMigrationService();
    service.registry = registryWith(enabledAdapter("gridfs"), enabledAdapter("s3"));
    service.autosweepConfigService = autosweepConfigService;
  }

  // ─── disabled by default ──────────────────────────────────────────────────

  @Test
  void autoSweep_disabledByDefault_noMigrationTriggered() {
    Mockito.when(autosweepConfigService.effectiveEnabled()).thenReturn(false);
    service.autoSweep();
    assertEquals(FileMigrationStatus.IDLE, service.getState().status());
  }

  // ─── missing configuration guards ─────────────────────────────────────────

  @Test
  void autoSweep_missingSource_logsWarningAndSkips() {
    Mockito.when(autosweepConfigService.effectiveEnabled()).thenReturn(true);
    Mockito.when(autosweepConfigService.effectiveSource()).thenReturn("");
    Mockito.when(autosweepConfigService.effectiveTarget()).thenReturn("s3");
    service.autoSweep();
    assertEquals(FileMigrationStatus.IDLE, service.getState().status());
  }

  @Test
  void autoSweep_missingTarget_logsWarningAndSkips() {
    Mockito.when(autosweepConfigService.effectiveEnabled()).thenReturn(true);
    Mockito.when(autosweepConfigService.effectiveSource()).thenReturn("gridfs");
    Mockito.when(autosweepConfigService.effectiveTarget()).thenReturn("");
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

    Mockito.when(autosweepConfigService.effectiveEnabled()).thenReturn(true);
    Mockito.when(autosweepConfigService.effectiveSource()).thenReturn("gridfs");
    Mockito.when(autosweepConfigService.effectiveTarget()).thenReturn("s3");
    service.autoSweep();

    // State should still be the same RUNNING record — no new trigger
    assertEquals(FileMigrationStatus.RUNNING, service.getState().status());
    assertEquals(startedAt, service.getState().startedAt().toString());
  }

  // ─── happy path ───────────────────────────────────────────────────────────

  @Disabled("CI-BASELINE-4: race condition — executor.submit() returns to the test thread " +
    "before the background thread checks the Neo4j session; NeoConnector.getInstance() " +
    "returns null immediately in unit tests, so runMigration() transitions state to FAILED " +
    "before the assertion checks RUNNING. " +
    "Fix: inject a testable executor or add a wait/latch seam on the RUNNING→FAILED path.")
  @Test
  void autoSweep_enabledAndValid_transitionsToRunning() throws Exception {
    Mockito.when(autosweepConfigService.effectiveEnabled()).thenReturn(true);
    Mockito.when(autosweepConfigService.effectiveSource()).thenReturn("gridfs");
    Mockito.when(autosweepConfigService.effectiveTarget()).thenReturn("s3");
    // Replace the background executor with a no-op so the synchronous RUNNING
    // transition is observed deterministically.
    installNoOpExecutor(service);
    service.autoSweep();
    FileMigrationState state = service.getState();
    assertEquals(FileMigrationStatus.RUNNING, state.status());
    assertEquals("gridfs", state.sourceProviderId());
    assertEquals("s3", state.targetProviderId());
    assertNotNull(state.startedAt());
  }

  /**
   * Reflectively swap the service's private-final single-thread executor for a
   * no-op so submitted background migrations never run.
   */
  private static void installNoOpExecutor(FileMigrationService svc) throws Exception {
    java.util.concurrent.ExecutorService noop =
        new java.util.concurrent.AbstractExecutorService() {
          @Override public void execute(Runnable command) { /* swallow — never run */ }
          @Override public void shutdown() {}
          @Override public List<Runnable> shutdownNow() { return List.of(); }
          @Override public boolean isShutdown() { return false; }
          @Override public boolean isTerminated() { return false; }
          @Override public boolean awaitTermination(long timeout, java.util.concurrent.TimeUnit unit) {
            return true;
          }
        };
    Field f = FileMigrationService.class.getDeclaredField("executor");
    f.setAccessible(true);
    f.set(svc, noop);
  }
}
