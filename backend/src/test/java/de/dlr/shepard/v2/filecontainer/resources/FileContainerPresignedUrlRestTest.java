package de.dlr.shepard.v2.filecontainer.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.data.file.entities.ShepardFile;
import de.dlr.shepard.data.file.services.FileContainerService;
import de.dlr.shepard.storage.FileStorage;
import de.dlr.shepard.storage.StorageException;
import de.dlr.shepard.v2.filecontainer.io.PresignedDownloadUrlIO;
import de.dlr.shepard.v2.filecontainer.io.PresignedUploadRequestIO;
import de.dlr.shepard.v2.filecontainer.io.PresignedUploadUrlIO;
import de.dlr.shepard.v2.filecontainer.io.UploadCommitIO;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.InternalServerErrorException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Plain-Mockito unit tests for {@link FileContainerPresignedUrlRest} (FS1c).
 * Wires the resource by hand and exercises every endpoint and its error branches.
 */
class FileContainerPresignedUrlRestTest {

  private static final String CONTAINER_APP_ID = "container-app-1";
  private static final long CONTAINER_OGM_ID = 42L;

  @Mock
  FileContainerService service;

  FileContainerPresignedUrlRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new FileContainerPresignedUrlRest();
    resource.fileContainerService = service;
  }

  private FileContainer container() {
    return new FileContainer(CONTAINER_OGM_ID);
  }

  // ─── POST /upload-url ─────────────────────────────────────────────────────

  @Test
  void getUploadUrl_returns400WhenRequestIsNull() {
    assertThrows(BadRequestException.class, () -> resource.getUploadUrl(CONTAINER_APP_ID, null));
  }

  @Test
  void getUploadUrl_returns400WhenFileNameIsNull() {
    var req = new PresignedUploadRequestIO();
    assertThrows(BadRequestException.class, () -> resource.getUploadUrl(CONTAINER_APP_ID, req));
  }

  @Test
  void getUploadUrl_returns400WhenFileNameIsBlank() {
    var req = new PresignedUploadRequestIO();
    req.setFileName("   ");
    assertThrows(BadRequestException.class, () -> resource.getUploadUrl(CONTAINER_APP_ID, req));
  }

  @Test
  void getUploadUrl_returns200OnHappyPath() throws Exception {
    when(service.getContainerByAppId(CONTAINER_APP_ID)).thenReturn(container());

    URI uploadUrl = URI.create("https://storage.example.com/bucket/key?X-Amz=sig");
    Instant expiry = Instant.now().plus(Duration.ofMinutes(15));
    FileStorage.PresignedPut presignedPut = new FileStorage.PresignedPut(uploadUrl, "new-oid", expiry);
    when(service.presignedUploadUrl(eq(CONTAINER_OGM_ID), eq("sensor.csv"), any())).thenReturn(presignedPut);

    var req = new PresignedUploadRequestIO();
    req.setFileName("sensor.csv");

    var r = resource.getUploadUrl(CONTAINER_APP_ID, req);

    assertEquals(200, r.getStatus());
    var io = (PresignedUploadUrlIO) r.getEntity();
    assertEquals(uploadUrl.toString(), io.getUploadUrl());
    assertEquals("new-oid", io.getOid());
    assertEquals(expiry, io.getExpiresAt());
  }

  @Test
  void getUploadUrl_throws500OnStorageException() throws Exception {
    when(service.getContainerByAppId(CONTAINER_APP_ID)).thenReturn(container());
    when(service.presignedUploadUrl(anyLong(), anyString(), any()))
      .thenThrow(new StorageException("S3 unreachable"));

    var req = new PresignedUploadRequestIO();
    req.setFileName("sensor.csv");

    assertThrows(InternalServerErrorException.class, () -> resource.getUploadUrl(CONTAINER_APP_ID, req));
  }

  // ─── POST /upload-url/commit ──────────────────────────────────────────────

  @Test
  void commitUpload_returns400WhenCommitBodyIsNull() {
    assertThrows(BadRequestException.class, () -> resource.commitUpload(CONTAINER_APP_ID, null));
  }

  @Test
  void commitUpload_returns400WhenOidIsNull() {
    var commit = new UploadCommitIO();
    commit.setFileName("sensor.csv");
    assertThrows(BadRequestException.class, () -> resource.commitUpload(CONTAINER_APP_ID, commit));
  }

  @Test
  void commitUpload_returns400WhenOidIsBlank() {
    var commit = new UploadCommitIO();
    commit.setOid("  ");
    commit.setFileName("sensor.csv");
    assertThrows(BadRequestException.class, () -> resource.commitUpload(CONTAINER_APP_ID, commit));
  }

  @Test
  void commitUpload_returns400WhenFileNameIsNull() {
    var commit = new UploadCommitIO();
    commit.setOid("some-oid");
    assertThrows(BadRequestException.class, () -> resource.commitUpload(CONTAINER_APP_ID, commit));
  }

  @Test
  void commitUpload_returns201OnHappyPath() throws Exception {
    when(service.getContainerByAppId(CONTAINER_APP_ID)).thenReturn(container());

    ShepardFile file = new ShepardFile("some-oid", new Date(), "sensor.csv", null);
    file.setFileSize(4096L);
    when(service.commitUpload(CONTAINER_OGM_ID, "some-oid", "sensor.csv", 4096L)).thenReturn(file);

    var commit = new UploadCommitIO();
    commit.setOid("some-oid");
    commit.setFileName("sensor.csv");
    commit.setFileSize(4096L);

    var r = resource.commitUpload(CONTAINER_APP_ID, commit);

    assertEquals(201, r.getStatus());
    assertEquals(file, r.getEntity());
  }

  @Test
  void commitUpload_throws500OnStorageException() throws Exception {
    when(service.getContainerByAppId(CONTAINER_APP_ID)).thenReturn(container());
    when(service.commitUpload(anyLong(), anyString(), anyString(), any()))
      .thenThrow(new StorageException("Neo4j write failed"));

    var commit = new UploadCommitIO();
    commit.setOid("some-oid");
    commit.setFileName("sensor.csv");

    assertThrows(InternalServerErrorException.class, () -> resource.commitUpload(CONTAINER_APP_ID, commit));
  }

  // ─── GET /files/{oid}/download-url ────────────────────────────────────────

  @Test
  void getDownloadUrl_returns200OnHappyPath() throws Exception {
    when(service.getContainerByAppId(CONTAINER_APP_ID)).thenReturn(container());

    URI downloadUrl = URI.create("https://storage.example.com/bucket/key?X-Amz=sig");
    when(service.presignedDownloadUrl(eq(CONTAINER_OGM_ID), eq("dl-oid"), any())).thenReturn(downloadUrl);

    var r = resource.getDownloadUrl(CONTAINER_APP_ID, "dl-oid");

    assertEquals(200, r.getStatus());
    var io = (PresignedDownloadUrlIO) r.getEntity();
    assertEquals(downloadUrl.toString(), io.getDownloadUrl());
  }

  @Test
  void getDownloadUrl_throws500OnStorageException() throws Exception {
    when(service.getContainerByAppId(CONTAINER_APP_ID)).thenReturn(container());
    when(service.presignedDownloadUrl(anyLong(), anyString(), any()))
      .thenThrow(new StorageException("S3 unreachable"));

    assertThrows(InternalServerErrorException.class, () -> resource.getDownloadUrl(CONTAINER_APP_ID, "dl-oid"));
  }
}
