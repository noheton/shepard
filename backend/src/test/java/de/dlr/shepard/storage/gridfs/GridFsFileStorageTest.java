package de.dlr.shepard.storage.gridfs;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.mongoDB.NamedInputStream;
import de.dlr.shepard.data.file.entities.ShepardFile;
import de.dlr.shepard.data.file.services.FileService;
import de.dlr.shepard.storage.StorageException;
import de.dlr.shepard.storage.StorageGetResponse;
import de.dlr.shepard.storage.StorageLocator;
import de.dlr.shepard.storage.StorageNotFoundException;
import de.dlr.shepard.storage.StoragePutRequest;
import jakarta.ws.rs.NotFoundException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * FS1a — exercises the {@link GridFsFileStorage} wrapper around
 * {@link FileService}. Behaviour-equivalence is the FS1a contract:
 * the adapter forwards put / get / delete to FileService and
 * translates the JAX-RS NotFoundException shape into the SPI's
 * {@link StorageNotFoundException}.
 *
 * <p>Plain JUnit (not {@code QuarkusComponentTest}) — the adapter
 * is a thin wrapper, so the dependency injection ceremony costs more
 * than it would buy. Construct directly and inject the mock
 * FileService via the package-private field.
 */
class GridFsFileStorageTest {

  private FileService fileService;
  private GridFsFileStorage storage;

  @BeforeEach
  void setUp() throws Exception {
    fileService = mock(FileService.class);
    storage = new GridFsFileStorage();
    var field = GridFsFileStorage.class.getDeclaredField("fileService");
    field.setAccessible(true);
    field.set(storage, fileService);
    storage.init();
  }

  @Test
  void idIsGridfs() {
    assertEquals("gridfs", storage.id());
    assertEquals("gridfs", GridFsFileStorage.ID);
  }

  @Test
  void postConstructEnablesWhenFileServicePresent() {
    assertTrue(storage.isEnabled());
  }

  // ---------- put ----------

  @Test
  void putDelegatesToFileServiceAndReturnsCompositeLocator() throws Exception {
    InputStream bytes = new ByteArrayInputStream(new byte[] { 1, 2, 3 });
    ShepardFile created = new ShepardFile("file-oid-123", new Date(), "f.bin", "md5");
    when(fileService.createFile("container-mongo-id", "f.bin", bytes)).thenReturn(created);

    StorageLocator locator = storage.put(new StoragePutRequest("container-mongo-id", "f.bin", bytes));

    assertEquals("gridfs", locator.providerId());
    assertEquals("container-mongo-id:file-oid-123", locator.locator());
    verify(fileService).createFile("container-mongo-id", "f.bin", bytes);
  }

  @Test
  void putWrapsNotFoundAsStorageNotFound() {
    InputStream bytes = new ByteArrayInputStream(new byte[] { 1 });
    when(fileService.createFile("bad", "f.bin", bytes)).thenThrow(new NotFoundException("Could not find container"));

    StorageNotFoundException ex = assertThrows(
      StorageNotFoundException.class,
      () -> storage.put(new StoragePutRequest("bad", "f.bin", bytes))
    );
    assertTrue(ex.getMessage().contains("bad"));
  }

  @Test
  void putWrapsRuntimeExceptionAsGenericStorageException() {
    InputStream bytes = new ByteArrayInputStream(new byte[] { 1 });
    when(fileService.createFile("c", "f.bin", bytes)).thenThrow(new RuntimeException("mongo down"));

    StorageException ex = assertThrows(
      StorageException.class,
      () -> storage.put(new StoragePutRequest("c", "f.bin", bytes))
    );
    // Subclasses also fail this — assert it's NOT a NotFound shape.
    assertTrue(ex.getMessage().contains("GridFS put failed"));
    assertTrue(ex.getMessage().contains("mongo down"));
  }

  // ---------- get ----------

  @Test
  void getDelegatesToFileServiceAndMapsToResponse() throws Exception {
    InputStream stream = new ByteArrayInputStream(new byte[] { 4, 5, 6 });
    NamedInputStream named = new NamedInputStream("oid", stream, "file.bin", 3L);
    when(fileService.getPayload("container-mongo-id", "file-oid-123")).thenReturn(named);

    StorageLocator locator = new StorageLocator("gridfs", "container-mongo-id:file-oid-123");
    StorageGetResponse resp = storage.get(locator);

    assertEquals("gridfs", resp.providerId());
    assertEquals("file.bin", resp.fileName());
    assertEquals(3L, resp.sizeBytes());
    assertNotNull(resp.stream());
  }

  @Test
  void getRejectsForeignProviderLocator() {
    StorageLocator s3Locator = new StorageLocator("s3", "bucket/key");
    StorageException ex = assertThrows(StorageException.class, () -> storage.get(s3Locator));
    assertTrue(ex.getMessage().contains("s3"));
  }

  @Test
  void getRejectsMalformedLocator() {
    // Missing separator entirely.
    StorageLocator badLocator = new StorageLocator("gridfs", "no-separator");
    StorageException ex = assertThrows(StorageException.class, () -> storage.get(badLocator));
    assertTrue(ex.getMessage().contains("Malformed GridFS locator"));
  }

  @Test
  void getWrapsNotFoundAsStorageNotFound() {
    when(fileService.getPayload("c", "f")).thenThrow(new NotFoundException("Could not find document with oid: f"));
    StorageLocator locator = new StorageLocator("gridfs", "c:f");
    assertThrows(StorageNotFoundException.class, () -> storage.get(locator));
  }

  // ---------- delete ----------

  @Test
  void deleteDelegatesToFileService() throws Exception {
    StorageLocator locator = new StorageLocator("gridfs", "c:f");
    storage.delete(locator);
    verify(fileService).deleteFile("c", "f");
  }

  @Test
  void deleteSwallowsFileMissingNotFound() {
    // The legacy FileService throws "Could not find and delete file
    // with oid: …" when the GridFS row is gone. The SPI contract
    // makes delete idempotent — swallow the not-found.
    doThrow(new NotFoundException("Could not find and delete file with oid: f"))
      .when(fileService)
      .deleteFile("c", "f");
    StorageLocator locator = new StorageLocator("gridfs", "c:f");
    assertDoesNotThrow(() -> storage.delete(locator));
  }

  @Test
  void deletePropagatesContainerMissingNotFound() {
    // Container-missing is a different shape ("Could not find
    // container with mongoId: …") — that's a real inconsistency
    // between the caller's bookkeeping and the storage tier, not
    // an idempotent-no-op. Propagate.
    doThrow(new NotFoundException("Could not find container with mongoId: c")).when(fileService).deleteFile("c", "f");
    StorageLocator locator = new StorageLocator("gridfs", "c:f");
    assertThrows(StorageNotFoundException.class, () -> storage.delete(locator));
  }
}
