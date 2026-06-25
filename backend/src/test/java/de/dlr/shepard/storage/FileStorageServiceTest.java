package de.dlr.shepard.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.mongoDB.NamedInputStream;
import de.dlr.shepard.data.file.entities.ShepardFile;
import de.dlr.shepard.data.file.services.FileService;
import de.dlr.shepard.storage.gridfs.GridFsFileStorage;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ServiceUnavailableException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * STORAGE-SPI-UNIFY-1 — proves {@link FileStorageService} routes every
 * single-file byte operation through {@link FileStorageRegistry}, never
 * a hardcoded substrate, and resolves reads per-row so existing GridFS
 * content survives a provider flip to S3.
 *
 * <p>Plain JUnit + Mockito (no Quarkus container) — the service is a
 * thin routing layer over the registry + the GridFS implementation
 * detail ({@link FileService}).
 */
class FileStorageServiceTest {

  private static final String CONTAINER = "_shepard_files";

  private FileStorageRegistry registry;
  private FileService fileService;
  private FileStorage gridfs;
  private FileStorage s3;
  private FileStorageService service;

  @BeforeEach
  void setUp() {
    registry = mock(FileStorageRegistry.class);
    fileService = mock(FileService.class);
    gridfs = mock(FileStorage.class);
    s3 = mock(FileStorage.class);
    when(gridfs.id()).thenReturn(GridFsFileStorage.ID);
    when(gridfs.isEnabled()).thenReturn(true);
    when(s3.id()).thenReturn("s3");
    when(s3.isEnabled()).thenReturn(true);
    service = new FileStorageService(registry, fileService);
  }

  // ── (b) store under gridfs → routes to the GridFS implementation ───────────

  @Test
  void storeFile_gridfsActive_routesThroughFileServiceAndStampsProvider() {
    when(registry.requireActive()).thenReturn(gridfs);
    ShepardFile created = new ShepardFile("oid-1", new Date(), "doc.pdf", "md5");
    InputStream bytes = new ByteArrayInputStream(new byte[] { 1, 2, 3 });
    when(fileService.createFile(CONTAINER, "doc.pdf", bytes, 3L)).thenReturn(created);

    ShepardFile result = service.storeFile(CONTAINER, "doc.pdf", bytes, 3L);

    assertNotNull(result);
    assertEquals("oid-1", result.getOid());
    // providerId stamped so a later read routes back to gridfs.
    assertEquals(GridFsFileStorage.ID, result.getProviderId());
    verify(fileService).createFile(CONTAINER, "doc.pdf", bytes, 3L);
  }

  // ── (a) store with provider=s3 → server-side put() through the SPI ─────────

  @Test
  void storeFile_s3Active_routesThroughSpiPutAndStampsProvider() throws Exception {
    // STORAGE-SPI-UNIFY-PUT: the non-GridFS path now streams server-side
    // through the adapter's put(), returning a ShepardFile keyed on the
    // locator's oid — never touching the GridFS magic route.
    when(registry.requireActive()).thenReturn(s3);
    byte[] payload = new byte[] { 1, 2, 3, 4, 5 };
    InputStream bytes = new ByteArrayInputStream(payload);
    // S3 put() returns "<container>/<uuid>"; the service must persist
    // the uuid as the oid so getPayload (buildLocator = container/oid)
    // reconstructs the same key.
    when(s3.put(any(StoragePutRequest.class)))
      .thenReturn(new StorageLocator("s3", CONTAINER + "/abc-123"));

    ShepardFile result = service.storeFile(CONTAINER, "clip.bin", bytes, 5L);

    assertNotNull(result);
    // oid is the locator key segment — round-trips with buildLocator.
    assertEquals("abc-123", result.getOid());
    assertEquals("s3", result.getProviderId());
    assertEquals("clip.bin", result.getFilename());
    assertEquals(5L, result.getFileSize());
    // md5 computed while streaming (FB1a bookkeeping for object stores).
    assertNotNull(result.getMd5());

    // The captured put request carries the container, filename + declared size.
    ArgumentCaptor<StoragePutRequest> putCaptor = ArgumentCaptor.forClass(StoragePutRequest.class);
    verify(s3).put(putCaptor.capture());
    assertEquals(CONTAINER, putCaptor.getValue().container());
    assertEquals("clip.bin", putCaptor.getValue().fileName());
    assertEquals(5L, putCaptor.getValue().sizeBytes());

    // GridFS magic route is NEVER consulted on the s3 path.
    verify(fileService, never()).createFile(any(), any(), any(), org.mockito.ArgumentMatchers.anyLong());
    verify(fileService, never()).createFile(any(), any(), any());
  }

  @Test
  void storeFile_s3Active_roundTripsLocatorThroughGetPayload() throws Exception {
    // Make-or-break correctness: the oid persisted by storeFile must,
    // when fed back through getPayload, reconstruct the EXACT locator the
    // adapter expects. We prove it end-to-end with the same mock adapter.
    when(registry.requireActive()).thenReturn(s3);
    when(registry.list()).thenReturn(List.of(gridfs, s3));
    when(s3.put(any(StoragePutRequest.class)))
      .thenReturn(new StorageLocator("s3", CONTAINER + "/key-xyz"));

    ShepardFile stored = service.storeFile(
      CONTAINER, "f.bin", new ByteArrayInputStream(new byte[] { 7 }), 1L);
    stored.setProviderId("s3"); // storeFile already stamps this; explicit for clarity.

    InputStream payload = new ByteArrayInputStream(new byte[] { 7 });
    StorageGetResponse resp = new StorageGetResponse("s3", "f.bin", null, 1L, payload);
    // getPayload rebuilds container/oid → must equal the put locator.
    when(s3.get(new StorageLocator("s3", CONTAINER + "/key-xyz"))).thenReturn(resp);

    NamedInputStream named = service.getPayload(CONTAINER, stored);
    assertEquals("f.bin", named.getName());
    verify(s3).get(new StorageLocator("s3", CONTAINER + "/key-xyz"));
  }

  @Test
  void storeFile_s3PutFails_mapsToServiceUnavailable() throws Exception {
    when(registry.requireActive()).thenReturn(s3);
    when(s3.put(any(StoragePutRequest.class))).thenThrow(new StorageException("garage down"));
    assertThrows(
      ServiceUnavailableException.class,
      () -> service.storeFile(CONTAINER, "f.bin", new ByteArrayInputStream(new byte[] { 1 }), 1L)
    );
  }

  @Test
  void oidFromLocator_extractsKeySegmentAfterContainerPrefix() {
    assertEquals("uuid-1", FileStorageService.oidFromLocator("c", "c/uuid-1"));
    // Defensive fallback when the adapter used a different prefix.
    assertEquals("tail", FileStorageService.oidFromLocator("c", "other/tail"));
    assertEquals("whole", FileStorageService.oidFromLocator("c", "whole"));
  }

  @Test
  void storeFile_noActiveProvider_throwsNotInstalled() {
    when(registry.requireActive()).thenThrow(new StorageNotInstalledException("none"));
    InputStream bytes = new ByteArrayInputStream(new byte[] { 1 });
    assertThrows(
      StorageNotInstalledException.class,
      () -> service.storeFile(CONTAINER, "f.bin", bytes, 1L)
    );
  }

  // ── (c) read of gridfs-stored content resolves while active=s3 ─────────────

  @Test
  void getPayload_gridfsRow_resolvesViaGridfsAdapterEvenWhenActiveIsS3() throws Exception {
    // Active provider is s3, but the file was stored under gridfs.
    when(registry.list()).thenReturn(List.of(gridfs, s3));
    ShepardFile file = new ShepardFile("oid-7", new Date(), "old.bin", "md5");
    file.setProviderId(GridFsFileStorage.ID);

    InputStream payload = new ByteArrayInputStream(new byte[] { 4, 5, 6 });
    StorageGetResponse resp = new StorageGetResponse(GridFsFileStorage.ID, "old.bin", null, 3L, payload);
    // The gridfs adapter receives the gridfs-shaped locator (container:oid).
    when(gridfs.get(new StorageLocator(GridFsFileStorage.ID, CONTAINER + ":oid-7"))).thenReturn(resp);

    NamedInputStream named = service.getPayload(CONTAINER, file);

    assertEquals("old.bin", named.getName());
    assertEquals(3L, named.getSize());
    // The s3 adapter must NOT be asked for a gridfs row.
    verify(s3, never()).get(any());
    verify(gridfs).get(new StorageLocator(GridFsFileStorage.ID, CONTAINER + ":oid-7"));
  }

  @Test
  void getPayload_nullProviderId_defaultsToGridfs() throws Exception {
    when(registry.list()).thenReturn(List.of(gridfs, s3));
    ShepardFile file = new ShepardFile("oid-8", new Date(), "legacy.bin", "md5");
    // providerId null — V79 backfill default applies → gridfs.
    InputStream payload = new ByteArrayInputStream(new byte[] { 1 });
    StorageGetResponse resp = new StorageGetResponse(GridFsFileStorage.ID, "legacy.bin", null, 1L, payload);
    when(gridfs.get(new StorageLocator(GridFsFileStorage.ID, CONTAINER + ":oid-8"))).thenReturn(resp);

    NamedInputStream named = service.getPayload(CONTAINER, file);
    assertEquals("legacy.bin", named.getName());
    verify(gridfs).get(any());
  }

  @Test
  void getPayload_storageNotFound_mapsToJaxRsNotFound() throws Exception {
    when(registry.list()).thenReturn(List.of(gridfs));
    ShepardFile file = new ShepardFile("oid-9", new Date(), "x.bin", "md5");
    file.setProviderId(GridFsFileStorage.ID);
    when(gridfs.get(any())).thenThrow(new StorageNotFoundException("gone"));
    assertThrows(NotFoundException.class, () -> service.getPayload(CONTAINER, file));
  }

  @Test
  void getPayload_storageFailure_mapsToServiceUnavailable() throws Exception {
    when(registry.list()).thenReturn(List.of(gridfs));
    when(registry.requireActive()).thenReturn(gridfs);
    ShepardFile file = new ShepardFile("oid-10", new Date(), "x.bin", "md5");
    file.setProviderId(GridFsFileStorage.ID);
    when(gridfs.get(any())).thenThrow(new StorageException("mongo down"));
    assertThrows(ServiceUnavailableException.class, () -> service.getPayload(CONTAINER, file));
  }

  // ── delete routes per-row + is idempotent ──────────────────────────────────

  @Test
  void deleteFile_gridfsRow_routesToGridfsAdapter() throws Exception {
    when(registry.list()).thenReturn(List.of(gridfs, s3));
    ShepardFile file = new ShepardFile("oid-11", new Date(), "x.bin", "md5");
    file.setProviderId(GridFsFileStorage.ID);

    service.deleteFile(CONTAINER, file);

    verify(gridfs).delete(new StorageLocator(GridFsFileStorage.ID, CONTAINER + ":oid-11"));
    verify(s3, never()).delete(any());
  }

  @Test
  void deleteFile_s3Row_usesSlashLocator() throws Exception {
    when(registry.list()).thenReturn(List.of(gridfs, s3));
    ShepardFile file = new ShepardFile("oid-12", new Date(), "x.bin", "md5");
    file.setProviderId("s3");

    service.deleteFile(CONTAINER, file);

    verify(s3).delete(new StorageLocator("s3", CONTAINER + "/oid-12"));
  }

  @Test
  void deleteFile_nullFileOrOid_isNoOp() throws Exception {
    service.deleteFile(CONTAINER, null);
    ShepardFile noOid = new ShepardFile(new Date(), "x.bin", "md5");
    service.deleteFile(CONTAINER, noOid);
    verify(gridfs, never()).delete(any());
    verify(s3, never()).delete(any());
  }

  @Test
  void deleteFile_storageNotFound_isSwallowed() throws Exception {
    when(registry.list()).thenReturn(List.of(gridfs));
    ShepardFile file = new ShepardFile("oid-13", new Date(), "x.bin", "md5");
    file.setProviderId(GridFsFileStorage.ID);
    org.mockito.Mockito.doThrow(new StorageNotFoundException("missing")).when(gridfs).delete(any());
    // idempotent — must not throw
    service.deleteFile(CONTAINER, file);
    verify(gridfs).delete(any());
  }

  @Test
  void deleteFile_disabledRowProvider_fallsBackToActive() throws Exception {
    // Row says gridfs but the gridfs adapter is disabled → fall back to active.
    when(gridfs.isEnabled()).thenReturn(false);
    when(registry.list()).thenReturn(List.of(gridfs));
    when(registry.requireActive()).thenReturn(s3);
    ShepardFile file = new ShepardFile("oid-14", new Date(), "x.bin", "md5");
    file.setProviderId(GridFsFileStorage.ID);

    service.deleteFile(CONTAINER, file);
    // Falls back to the active adapter (s3) — still routed through the SPI.
    verify(s3).delete(any());
  }

  // ── locator-shape helper ────────────────────────────────────────────────────

  @Test
  void buildLocator_usesColonForGridfsSlashForOthers() {
    StorageLocator g = FileStorageService.buildLocator(GridFsFileStorage.ID, "c", "oid");
    assertEquals("c:oid", g.locator());
    StorageLocator s = FileStorageService.buildLocator("s3", "c", "oid");
    assertEquals("c/oid", s.locator());
  }

  @Test
  void effectiveProviderId_defaultsToGridfsForNullOrBlank() {
    assertEquals(GridFsFileStorage.ID, FileStorageService.effectiveProviderId(null));
    ShepardFile blank = new ShepardFile("o", new Date(), "f", "m");
    assertEquals(GridFsFileStorage.ID, FileStorageService.effectiveProviderId(blank));
    ShepardFile s3row = new ShepardFile("o", new Date(), "f", "m");
    s3row.setProviderId("s3");
    assertEquals("s3", FileStorageService.effectiveProviderId(s3row));
  }

  @Test
  void storeFile_gridfs_nullResultIsTolerated() {
    // Defensive: a null from the adapter must not NPE the stamp guard.
    when(registry.requireActive()).thenReturn(gridfs);
    InputStream bytes = new ByteArrayInputStream(new byte[] { 1 });
    when(fileService.createFile(eq(CONTAINER), eq("f"), any(), eq(0L))).thenReturn(null);
    assertEquals(null, service.storeFile(CONTAINER, "f", bytes, 0L));
  }

  @Test
  void getPayload_nullFile_throwsNotFound() {
    assertThrows(NotFoundException.class, () -> service.getPayload(CONTAINER, null));
  }

  @Test
  void distinctAdaptersAreNotSame() {
    // Sanity: the two mocks are distinct identities the router must keep apart.
    org.junit.jupiter.api.Assertions.assertNotEquals(gridfs, s3);
    assertSame(gridfs, gridfs);
  }
}
