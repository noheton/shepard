package de.dlr.shepard.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.enterprise.inject.Instance;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * FS1a — exercises the {@link FileStorageRegistry} optional posture
 * (mirror of {@link de.dlr.shepard.publish.minter.MinterRegistry}
 * post-KIP1h). These tests pin the resolution outcomes that the
 * file-payload code path relies on (match / unset-or-none /
 * no-bean / disabled-bean) plus the duplicate-id and blank-id
 * corner cases.
 */
class FileStorageRegistryTest {

  @SuppressWarnings("unchecked")
  private static Instance<FileStorage> instanceOf(FileStorage... storages) {
    Instance<FileStorage> instance = mock(Instance.class);
    when(instance.iterator()).thenAnswer(inv -> List.of(storages).iterator());
    return instance;
  }

  /** Tiny fake adapter — name + isEnabled + no-op put/get/delete. */
  private static FileStorage fake(String id, boolean enabled) {
    return new FileStorage() {
      @Override
      public String id() {
        return id;
      }

      @Override
      public boolean isEnabled() {
        return enabled;
      }

      @Override
      public StorageLocator put(StoragePutRequest request) {
        return new StorageLocator(id, "fake-locator");
      }

      @Override
      public StorageGetResponse get(StorageLocator locator) {
        return new StorageGetResponse(id, "fake.bin", "application/octet-stream", 0L, null);
      }

      @Override
      public void delete(StorageLocator locator) {
        // no-op
      }
    };
  }

  // ---------- happy path ----------

  @Test
  void resolvesActiveStorageByConfiguredId() {
    FileStorage gridfs = fake("gridfs", true);
    FileStorage s3 = fake("s3", true);
    FileStorageRegistry r = new FileStorageRegistry("s3", instanceOf(gridfs, s3));

    Optional<FileStorage> active = r.activeStorage();
    assertTrue(active.isPresent());
    assertSame(s3, active.get());
    assertEquals("s3", r.activeStorageId());
  }

  @Test
  void resolvesGridFsByDefaultWhenItMatches() {
    FileStorage gridfs = fake("gridfs", true);
    FileStorageRegistry r = new FileStorageRegistry("gridfs", instanceOf(gridfs));

    assertTrue(r.activeStorage().isPresent());
    assertEquals("gridfs", r.activeStorageId());
  }

  @Test
  void listReturnsEveryDiscoveredAdapter() {
    FileStorage gridfs = fake("gridfs", true);
    FileStorage s3 = fake("s3", true);
    FileStorageRegistry r = new FileStorageRegistry("gridfs", instanceOf(gridfs, s3));
    assertEquals(2, r.list().size());
    assertEquals("gridfs", r.list().get(0).id());
    assertEquals("s3", r.list().get(1).id());
  }

  // ---------- optional posture (no fail-fast) ----------

  @Test
  void unsetConfigDegradesToNoActiveStorage() {
    FileStorage gridfs = fake("gridfs", true);
    FileStorageRegistry r = new FileStorageRegistry("", instanceOf(gridfs));
    assertFalse(r.activeStorage().isPresent());
    assertEquals("<unset>", r.activeStorageId());
  }

  @Test
  void nullConfigDegradesToNoActiveStorage() {
    FileStorage gridfs = fake("gridfs", true);
    FileStorageRegistry r = new FileStorageRegistry(null, instanceOf(gridfs));
    assertFalse(r.activeStorage().isPresent());
  }

  @Test
  void noneSentinelDegradesToNoActiveStorage() {
    // shepard.storage.provider=none is the operator-explicit
    // "disable file payloads" toggle. Lets an operator hand-shape
    // a read-only resolver posture (FS1c's signed-URL endpoints
    // would still work against existing rows) without removing
    // the adapter JAR.
    FileStorage gridfs = fake("gridfs", true);
    FileStorageRegistry r1 = new FileStorageRegistry("none", instanceOf(gridfs));
    FileStorageRegistry r2 = new FileStorageRegistry("NONE", instanceOf(gridfs));
    FileStorageRegistry r3 = new FileStorageRegistry("  NoNe  ", instanceOf(gridfs));
    assertFalse(r1.activeStorage().isPresent());
    assertFalse(r2.activeStorage().isPresent());
    assertFalse(r3.activeStorage().isPresent());
  }

  @Test
  void configuredIdWithNoMatchingBeanDegradesNotFailFast() {
    // The registry logs a WARN and continues with no active
    // storage; the file-payload endpoints emit 503 on demand.
    FileStorage gridfs = fake("gridfs", true);
    FileStorageRegistry r = new FileStorageRegistry("s3", instanceOf(gridfs));
    assertFalse(r.activeStorage().isPresent());
    assertEquals("<unset>", r.activeStorageId());
  }

  @Test
  void disabledActiveStorageDegradesNotFailFast() {
    FileStorage disabled = fake("s3", false);
    FileStorageRegistry r = new FileStorageRegistry("s3", instanceOf(disabled));
    assertFalse(r.activeStorage().isPresent());
  }

  @Test
  void emptyStorageListBoots() {
    // No adapters on the classpath at all (operator removed the
    // GridFS bean — illegal but recoverable). Registry boots,
    // file-payload endpoints emit 503 on demand.
    FileStorageRegistry r = new FileStorageRegistry("gridfs", instanceOf());
    assertFalse(r.activeStorage().isPresent());
    assertTrue(r.list().isEmpty());
  }

  // ---------- duplicate / blank id corner cases ----------

  @Test
  void duplicateIdsKeepsFirstAndWarns() {
    // Two adapters return id "gridfs". The registry keeps the
    // first one and logs a WARN (not a throw) — same shape as
    // MinterRegistry. The plugin-first posture means an operator
    // running with a stock + forked GridFS adapter should still
    // boot (with the warning); first-wins matches G1's
    // host-substring behaviour.
    FileStorage first = fake("gridfs", true);
    FileStorage second = fake("gridfs", true);
    FileStorageRegistry r = new FileStorageRegistry("gridfs", instanceOf(first, second));
    assertSame(first, r.activeStorage().orElseThrow());
    assertEquals(1, r.list().size());
  }

  @Test
  void blankIdAdapterIsSkipped() {
    FileStorage blank = fake("", true);
    FileStorage gridfs = fake("gridfs", true);
    FileStorageRegistry r = new FileStorageRegistry("gridfs", instanceOf(blank, gridfs));
    assertEquals("gridfs", r.activeStorageId());
  }

  @Test
  void multipleStoragesPickConfiguredOne() {
    // Realistic future scenario: gridfs + s3 + (future) seaweed
    // all wired, operator picks one via shepard.storage.provider.
    // The non-picked adapters stay discovered but inactive.
    FileStorage gridfs = fake("gridfs", true);
    FileStorage s3 = fake("s3", true);
    FileStorage seaweed = fake("seaweed", true);
    FileStorageRegistry r = new FileStorageRegistry("s3", instanceOf(gridfs, s3, seaweed));
    assertSame(s3, r.activeStorage().orElseThrow());
    assertEquals("s3", r.activeStorageId());
  }

  @Test
  void noneSentinelExposedAsConstant() {
    assertEquals("none", FileStorageRegistry.NONE);
  }

  // ---------- requireActive ----------

  @Test
  void requireActiveReturnsActiveWhenPresent() {
    FileStorage gridfs = fake("gridfs", true);
    FileStorageRegistry r = new FileStorageRegistry("gridfs", instanceOf(gridfs));
    assertSame(gridfs, r.requireActive());
  }

  @Test
  void requireActiveThrowsWhenNoActiveStorage() {
    FileStorage gridfs = fake("gridfs", true);
    FileStorageRegistry r = new FileStorageRegistry("s3", instanceOf(gridfs));
    StorageNotInstalledException ex = assertThrows(
      StorageNotInstalledException.class,
      r::requireActive
    );
    assertTrue(ex.getMessage().contains("shepard.storage.provider"));
    // Operator-actionable: lists the discovered adapters so they
    // know what to set the property to.
    assertTrue(ex.getMessage().contains("gridfs"));
  }

  @Test
  void requireActiveThrowsWithEmptyAdaptersList() {
    FileStorageRegistry r = new FileStorageRegistry("gridfs", instanceOf());
    StorageNotInstalledException ex = assertThrows(
      StorageNotInstalledException.class,
      r::requireActive
    );
    assertTrue(ex.getMessage().contains("<none>"));
  }
}
