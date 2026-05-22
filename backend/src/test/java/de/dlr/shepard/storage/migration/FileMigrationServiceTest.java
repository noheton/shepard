package de.dlr.shepard.storage.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.storage.FileStorage;
import de.dlr.shepard.storage.FileStorageRegistry;
import de.dlr.shepard.storage.StorageException;
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

  // ─── FS1e3 — stamp + swap Cypher shape ────────────────────────────────────

  /**
   * FS1e3 — the CYPHER_UPDATE statement MUST stamp previousProviderId
   * + previousLocator + migratedAt + migrationHmac in the same SET as
   * the providerId swap. Cypher evaluates SET clauses left-to-right,
   * so previousProviderId captures the pre-swap providerId before the
   * subsequent clause overwrites it. This test fixes that semantic
   * structure so a future refactor can't accidentally split the
   * "stamp before swap" guarantee into separate statements.
   */
  @Test
  void cypherUpdateStampsAllFourFs1e3FieldsBeforeSwap() {
    String stmt = FileMigrationService.CYPHER_UPDATE;
    // Single MATCH + single SET — stamp + swap atomic per transaction
    assertTrue(stmt.contains("MATCH (f:ShepardFile {oid: $oid})"),
      "must match by oid: " + stmt);
    // The four FS1e3 fields are stamped
    assertTrue(stmt.contains("f.previousProviderId = f.providerId"),
      "previousProviderId must capture pre-swap providerId: " + stmt);
    assertTrue(stmt.contains("f.previousLocator"),
      "previousLocator must be stamped: " + stmt);
    assertTrue(stmt.contains("f.migratedAt"),
      "migratedAt must be stamped: " + stmt);
    assertTrue(stmt.contains("f.migrationHmac"),
      "migrationHmac must be stamped (null today, FS1e6 populates): " + stmt);
    // The providerId swap happens AFTER stamping (left-to-right SET)
    int idxStamp = stmt.indexOf("f.previousProviderId = f.providerId");
    int idxSwap  = stmt.indexOf("f.providerId         = $target");
    assertTrue(idxStamp >= 0 && idxSwap > idxStamp,
      "providerId swap must come AFTER previousProviderId stamp: " + stmt);
  }

  /**
   * FS1e3 — the CYPHER_ROLLBACK statement MUST refuse rows that have
   * nothing to revert (previousProviderId IS NULL) and clear all four
   * FS1e3 bookkeeping fields when a rollback proceeds.
   */
  @Test
  void cypherRollbackRefusesNullPreviousAndClearsAllFields() {
    String stmt = FileMigrationService.CYPHER_ROLLBACK;
    assertTrue(stmt.contains("WHERE f.previousProviderId IS NOT NULL"),
      "rollback must refuse rows without previousProviderId: " + stmt);
    assertTrue(stmt.contains("f.providerId = f.previousProviderId"),
      "rollback must restore providerId: " + stmt);
    assertTrue(stmt.contains("REMOVE f.previousProviderId"),
      "rollback must clear previousProviderId: " + stmt);
    assertTrue(stmt.contains("f.previousLocator"),
      "rollback must clear previousLocator: " + stmt);
    assertTrue(stmt.contains("f.migratedAt"),
      "rollback must clear migratedAt: " + stmt);
    assertTrue(stmt.contains("f.migrationHmac"),
      "rollback must clear migrationHmac: " + stmt);
    // Matches by appId (v2-consistent), not oid
    assertTrue(stmt.contains("MATCH (f:ShepardFile {appId: $appId})"),
      "rollback must key by appId, not oid (v2 convention): " + stmt);
  }

  // ─── FS1e3 — migrateOne round-trip + rollback ─────────────────────────────

  /**
   * FS1e3 — round-trip integration of {@link FileMigrationService#migrateOne}
   * + {@link FileMigrationService#rollbackOne} against in-memory storage
   * stubs. Asserts: (a) the source bytes are read once + written to
   * target; (b) the target's locator is recorded for verification; (c)
   * the source delete fires (today's fused-Phase-4 behaviour stays per
   * FS1e3 task spec); (d) the rollback re-puts to source using the
   * original assignedObjectKey.
   *
   * <p>Neo4j is mocked at the call-sites; this is a service-layer
   * test, not a graph integration test. The Cypher contracts are
   * verified separately in
   * {@link #cypherUpdateStampsAllFourFs1e3FieldsBeforeSwap} +
   * {@link #cypherRollbackRefusesNullPreviousAndClearsAllFields}.
   */
  @Test
  void migrateOneRoundTripPreservesOidAndWritesToTarget() throws Exception {
    var src = new TrackingAdapter("src", new byte[]{10, 20, 30});
    var tgt = new TrackingAdapter("tgt", null);
    FileStorageRegistry reg = registryWith(src, tgt);
    FileMigrationService svc = new FileMigrationService();
    svc.registry = reg;

    // Mock Neo4j session — record the params passed to the stamp Cypher.
    var capturedParams = new java.util.concurrent.atomic.AtomicReference<java.util.Map<String, Object>>();
    org.neo4j.ogm.session.Session session = mock(org.neo4j.ogm.session.Session.class);
    when(session.query(anyString(), anyMap())).thenAnswer(inv -> {
      capturedParams.set(inv.getArgument(1));
      return null;
    });

    // Call the package-private migrateOne with a fixed locator shape
    // matching the gridfs-style format.
    svc.migrateOne(session, "src", "tgt", "OID-abc", "CONTAINER-1", "report.pdf", 3L);

    // Target received a put keyed at the preserved oid
    assertEquals(1, tgt.puts.size(), "target should receive exactly one put");
    assertEquals("OID-abc", tgt.puts.get(0).assignedObjectKey(),
      "assignedObjectKey must preserve source oid (FS1e1 OID preservation)");
    assertEquals("CONTAINER-1", tgt.puts.get(0).container());
    assertEquals("report.pdf", tgt.puts.get(0).fileName());

    // Source was read AND deleted (today's fused Phase 4)
    assertEquals(1, src.gets.size(), "source should be read once");
    assertEquals(1, src.deletes.size(), "source should be deleted (FS1e3 keeps fused Phase 4)");

    // Neo4j stamp captured the four FS1e3 params
    assertNotNull(capturedParams.get());
    var p = capturedParams.get();
    assertEquals("OID-abc", p.get("oid"));
    assertEquals("tgt", p.get("target"));
    assertTrue(p.containsKey("sourceLocator"),
      "stamp must include the source locator for previousLocator");
    assertTrue(p.containsKey("hmac"), "stamp must include hmac slot (null today)");
    assertEquals(null, p.get("hmac"),
      "hmac is null today — FS1e6 verify+report populates it");
  }

  /**
   * FS1e3 — {@link FileMigrationService#rollbackOne} writes bytes back
   * from the current adapter to the previous adapter using the
   * preserved {@code previousLocator}, then restores the providerId
   * via the rollback Cypher. This test verifies the byte round-trip;
   * the Cypher shape is verified separately above.
   */
  @Test
  void rollbackOneWritesBytesBackToPreviousProviderUsingPreviousLocator() throws Exception {
    // src holds the post-migration bytes (the current adapter);
    // tgt is the previous adapter we want to restore to.
    var src = new TrackingAdapter("s3", new byte[]{42, 42, 42});
    var tgt = new TrackingAdapter("gridfs", null);
    FileStorageRegistry reg = registryWith(src, tgt);
    FileMigrationService svc = new FileMigrationService();
    svc.registry = reg;

    // Stub a Neo4j session that returns a rollback context row.
    org.neo4j.ogm.session.Session session = mock(org.neo4j.ogm.session.Session.class);

    org.neo4j.ogm.model.Result fetchResult = mock(org.neo4j.ogm.model.Result.class);
    java.util.Map<String, Object> row = new java.util.HashMap<>();
    row.put("oid", "OID-xyz");
    row.put("containerMongoId", "CONTAINER-2");
    row.put("filename", "report.pdf");
    row.put("fileSize", 3L);
    row.put("currentProviderId", "s3");
    row.put("previousProviderId", "gridfs");
    row.put("previousLocator", "CONTAINER-2:OID-xyz");
    when(fetchResult.iterator()).thenReturn(java.util.List.of(row).iterator());
    when(session.query(eq(FileMigrationService.CYPHER_FETCH_ROLLBACK_CTX), anyMap()))
      .thenReturn(fetchResult);
    when(session.query(eq(FileMigrationService.CYPHER_ROLLBACK), anyMap()))
      .thenReturn(null);

    svc.rollbackOne("APPID-deadbeef", session);

    // tgt (previous adapter) received a put keyed at the original oid
    assertEquals(1, tgt.puts.size(),
      "previous adapter should receive exactly one put on rollback");
    assertEquals("OID-xyz", tgt.puts.get(0).assignedObjectKey(),
      "rollback put must use the original oid as assignedObjectKey");
    assertEquals("CONTAINER-2", tgt.puts.get(0).container());

    // src (current adapter) was read once for the rollback bytes
    assertEquals(1, src.gets.size(),
      "current adapter should be read to source rollback bytes");
    // src is NOT deleted on rollback — that's a separate sweep-orphans step
    assertEquals(0, src.deletes.size(),
      "rollback must NOT delete from the current adapter (orphan; sweep-orphans handles it)");
  }

  @Test
  void rollbackOneRefusesWhenNothingToRevert() throws Exception {
    var src = new TrackingAdapter("s3", new byte[]{1});
    var tgt = new TrackingAdapter("gridfs", null);
    FileStorageRegistry reg = registryWith(src, tgt);
    FileMigrationService svc = new FileMigrationService();
    svc.registry = reg;

    org.neo4j.ogm.session.Session session = mock(org.neo4j.ogm.session.Session.class);
    org.neo4j.ogm.model.Result fetchResult = mock(org.neo4j.ogm.model.Result.class);
    java.util.Map<String, Object> row = new java.util.HashMap<>();
    row.put("oid", "OID-q");
    row.put("containerMongoId", "C-1");
    row.put("filename", "a.txt");
    row.put("fileSize", 1L);
    row.put("currentProviderId", "s3");
    row.put("previousProviderId", null);
    row.put("previousLocator", null);
    when(fetchResult.iterator()).thenReturn(java.util.List.of(row).iterator());
    when(session.query(eq(FileMigrationService.CYPHER_FETCH_ROLLBACK_CTX), anyMap()))
      .thenReturn(fetchResult);

    assertThrows(IllegalStateException.class,
      () -> svc.rollbackOne("APPID-never-migrated", session));

    // No bytes moved, no Cypher rollback called
    assertEquals(0, tgt.puts.size());
    assertEquals(0, src.gets.size());
  }

  @Test
  void rollbackOneRefusesWhenAppIdNotFound() throws Exception {
    var src = new TrackingAdapter("s3", null);
    var tgt = new TrackingAdapter("gridfs", null);
    FileStorageRegistry reg = registryWith(src, tgt);
    FileMigrationService svc = new FileMigrationService();
    svc.registry = reg;

    org.neo4j.ogm.session.Session session = mock(org.neo4j.ogm.session.Session.class);
    org.neo4j.ogm.model.Result fetchResult = mock(org.neo4j.ogm.model.Result.class);
    when(fetchResult.iterator()).thenReturn(java.util.Collections.emptyIterator());
    when(session.query(eq(FileMigrationService.CYPHER_FETCH_ROLLBACK_CTX), anyMap()))
      .thenReturn(fetchResult);

    assertThrows(IllegalArgumentException.class,
      () -> svc.rollbackOne("APPID-missing", session));
  }

  // ─── helpers ──────────────────────────────────────────────────────────────

  /**
   * A FileStorage that records every interaction so tests can
   * assert on the put/get/delete sequence. Optionally returns a
   * fixed byte payload on get().
   */
  private static final class TrackingAdapter implements FileStorage {
    final String id;
    final byte[] payload;
    final java.util.List<StoragePutRequest> puts = new java.util.ArrayList<>();
    final java.util.List<StorageLocator> gets = new java.util.ArrayList<>();
    final java.util.List<StorageLocator> deletes = new java.util.ArrayList<>();

    TrackingAdapter(String id, byte[] payload) {
      this.id = id;
      this.payload = payload;
    }

    @Override public String id() { return id; }
    @Override public boolean isEnabled() { return true; }

    @Override public StorageLocator put(StoragePutRequest r) {
      puts.add(r);
      // Honor assignedObjectKey when present — matches the S3 adapter
      // behaviour the FS1e1 OID-preservation contract relies on.
      String key = r.assignedObjectKey() != null && !r.assignedObjectKey().isBlank()
        ? r.assignedObjectKey()
        : "minted-" + java.util.UUID.randomUUID();
      return new StorageLocator(id, r.container() + "/" + key);
    }

    @Override public StorageGetResponse get(StorageLocator l) {
      gets.add(l);
      byte[] bytes = payload != null ? payload : new byte[0];
      return new StorageGetResponse(id, "f.bin", null, (long) bytes.length,
        new ByteArrayInputStream(bytes));
    }

    @Override public void delete(StorageLocator l) {
      deletes.add(l);
    }
  }
}
