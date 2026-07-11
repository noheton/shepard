package de.dlr.shepard.v2.containers.handlers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import de.dlr.shepard.data.file.daos.FileContainerDAO;
import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.data.file.entities.ShepardFile;
import de.dlr.shepard.data.file.services.FileContainerService;
import de.dlr.shepard.data.file.thumbnail.ThumbnailService;
import de.dlr.shepard.storage.FileStorage;
import de.dlr.shepard.storage.PresignTtlValidator;
import de.dlr.shepard.storage.StorageException;
import java.util.ArrayList;
import java.util.List;
import de.dlr.shepard.v2.filecontainer.io.PresignedDownloadUrlIO;
import de.dlr.shepard.v2.filecontainer.io.PresignedUploadRequestIO;
import de.dlr.shepard.v2.filecontainer.io.PresignedUploadUrlIO;
import de.dlr.shepard.v2.filecontainer.io.UploadCommitIO;
import de.dlr.shepard.common.exceptions.ProblemJson;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for the presigned-URL and thumbnail methods of
 * {@link FileContainerKindHandler} (APISIMP-CONT-NS-COLLAPSE-5).
 */
class FileContainerKindHandlerPresignedUrlTest {

  private static final String APP_ID = "file-container-app-1";
  private static final long CONTAINER_ID = 42L;

  @Mock FileContainerService service;
  @Mock FileContainerDAO dao;
  @Mock ThumbnailService thumbnailService;
  @Mock PresignTtlValidator ttlValidator;

  FileContainerKindHandler handler;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    handler = new FileContainerKindHandler();
    handler.service = service;
    handler.dao = dao;
    handler.thumbnailService = thumbnailService;
    handler.ttlValidator = ttlValidator;
    when(ttlValidator.effectiveUploadTtl()).thenReturn(Duration.ofMinutes(15));
    when(ttlValidator.effectiveDownloadTtl()).thenReturn(Duration.ofMinutes(5));
  }

  private FileContainer container() {
    return new FileContainer(CONTAINER_ID);
  }

  private void stubDaoFind() {
    FileContainer c = container();
    when(dao.findByAppId(APP_ID)).thenReturn(Optional.of(c));
  }

  // ─── getThumbnail ────────────────────────────────────────────────────────

  @Test
  void getThumbnail_returnsOkWithPng() {
    byte[] png = {(byte)0x89, 0x50, 0x4E, 0x47};
    when(thumbnailService.getThumbnail(APP_ID, "file-oid", 200)).thenReturn(png);

    Optional<Response> result = handler.getThumbnail(APP_ID, "file-oid", 200);

    assertTrue(result.isPresent());
    assertEquals(200, result.get().getStatus());
  }

  @Test
  void getThumbnail_returns404WhenThumbnailNull() {
    when(thumbnailService.getThumbnail(APP_ID, "file-oid", 400)).thenReturn(null);

    assertThrows(NotFoundException.class, () -> handler.getThumbnail(APP_ID, "file-oid", null));
  }

  @Test
  void getThumbnail_normalisesInvalidSize() {
    byte[] png = {1, 2, 3};
    when(thumbnailService.getThumbnail(APP_ID, "file-oid", 400)).thenReturn(png);

    Optional<Response> result = handler.getThumbnail(APP_ID, "file-oid", 999);

    assertTrue(result.isPresent());
    verify(thumbnailService).getThumbnail(APP_ID, "file-oid", 400);
  }

  @Test
  void getThumbnail_returns503ProblemJsonWhenServiceUnavailable() {
    when(thumbnailService.getThumbnail(APP_ID, "file-oid", 400))
      .thenThrow(new ServiceUnavailableException("cache miss"));

    Optional<Response> result = handler.getThumbnail(APP_ID, "file-oid", null);

    assertTrue(result.isPresent());
    Response r = result.get();
    assertEquals(503, r.getStatus());
    assertEquals("5", r.getHeaderString("Retry-After"));
    assertEquals("application/problem+json", r.getMediaType().toString());
    var body = (ProblemJson) r.getEntity();
    assertEquals("/problems/files.thumbnail-unavailable", body.type());
    assertEquals(503, body.status());
  }

  // ─── getUploadUrl ────────────────────────────────────────────────────────

  @Test
  void getUploadUrl_returns400WhenRequestNull() {
    assertThrows(BadRequestException.class, () -> handler.getUploadUrl(APP_ID, null));
  }

  @Test
  void getUploadUrl_returns400WhenFileNameBlank() {
    var req = new PresignedUploadRequestIO();
    req.setFileName("   ");
    assertThrows(BadRequestException.class, () -> handler.getUploadUrl(APP_ID, req));
  }

  @Test
  void getUploadUrl_returnsPresignedUrl() throws Exception {
    stubDaoFind();
    URI url = URI.create("https://s3.example.com/bucket/key?sig=abc");
    Instant expiry = Instant.now().plusSeconds(900);
    FileStorage.PresignedPut put = new FileStorage.PresignedPut(url, "new-oid", expiry);
    when(service.presignedUploadUrl(eq(CONTAINER_ID), eq("data.csv"), any())).thenReturn(put);

    var req = new PresignedUploadRequestIO();
    req.setFileName("data.csv");
    Optional<Response> result = handler.getUploadUrl(APP_ID, req);

    assertTrue(result.isPresent());
    assertEquals(200, result.get().getStatus());
    var io = (PresignedUploadUrlIO) result.get().getEntity();
    assertEquals(url.toString(), io.getUploadUrl());
    assertEquals("new-oid", io.getFileId());
  }

  @Test
  void getUploadUrl_throws500OnStorageException() throws Exception {
    stubDaoFind();
    when(service.presignedUploadUrl(anyLong(), anyString(), any()))
      .thenThrow(new StorageException("S3 down"));

    var req = new PresignedUploadRequestIO();
    req.setFileName("data.csv");
    assertThrows(InternalServerErrorException.class, () -> handler.getUploadUrl(APP_ID, req));
  }

  // ─── commitUpload ────────────────────────────────────────────────────────

  @Test
  void commitUpload_returns400WhenCommitNull() {
    assertThrows(BadRequestException.class, () -> handler.commitUpload(APP_ID, null));
  }

  @Test
  void commitUpload_returns400WhenFileIdBlank() {
    var commit = new UploadCommitIO();
    commit.setFileId("  ");
    commit.setFileName("data.csv");
    assertThrows(BadRequestException.class, () -> handler.commitUpload(APP_ID, commit));
  }

  @Test
  void commitUpload_returns201OnSuccess() throws Exception {
    stubDaoFind();
    ShepardFile file = new ShepardFile("oid-1", new Date(), "data.csv", null);
    when(service.commitUpload(CONTAINER_ID, "oid-1", "data.csv", 1024L)).thenReturn(file);

    var commit = new UploadCommitIO();
    commit.setFileId("oid-1");
    commit.setFileName("data.csv");
    commit.setFileSize(1024L);
    Optional<Response> result = handler.commitUpload(APP_ID, commit);

    assertTrue(result.isPresent());
    assertEquals(201, result.get().getStatus());
    assertEquals(file, result.get().getEntity());
  }

  // ─── getDownloadUrl ──────────────────────────────────────────────────────

  @Test
  void getDownloadUrl_returnsPresignedUrl() throws Exception {
    stubDaoFind();
    URI dl = URI.create("https://s3.example.com/bucket/key?sig=xyz");
    when(service.presignedDownloadUrl(eq(CONTAINER_ID), eq("dl-oid"), any())).thenReturn(dl);

    Optional<Response> result = handler.getDownloadUrl(APP_ID, "dl-oid");

    assertTrue(result.isPresent());
    assertEquals(200, result.get().getStatus());
    var io = (PresignedDownloadUrlIO) result.get().getEntity();
    assertEquals(dl.toString(), io.getDownloadUrl());
  }

  @Test
  void getDownloadUrl_throws500OnStorageException() throws Exception {
    stubDaoFind();
    when(service.presignedDownloadUrl(anyLong(), anyString(), any()))
      .thenThrow(new StorageException("S3 down"));

    assertThrows(InternalServerErrorException.class, () -> handler.getDownloadUrl(APP_ID, "dl-oid"));
  }

  @Test
  void getDownloadUrl_throws404WhenContainerNotFound() {
    when(dao.findByAppId(APP_ID)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> handler.getDownloadUrl(APP_ID, "dl-oid"));
  }

  // ─── getThumbnailByFileAppId (APISIMP-OID-PATHPARAM-REPLACE slice 2) ────

  @Test
  void getThumbnailByFileAppId_resolvesOidAndReturnsPng() {
    byte[] png = {(byte)0x89, 0x50, 0x4E, 0x47};
    ShepardFile file = new ShepardFile("resolved-oid", new Date(), "image.png", null);
    file.setAppId("01930b92-1111-7000-8000-000000000001");
    FileContainer c = container();
    c.setFiles(new ArrayList<>(List.of(file)));
    when(dao.findByAppId(APP_ID)).thenReturn(Optional.of(c));
    when(thumbnailService.getThumbnail(APP_ID, "resolved-oid", 200)).thenReturn(png);

    Optional<Response> result = handler.getThumbnailByFileAppId(
      APP_ID, "01930b92-1111-7000-8000-000000000001", 200);

    assertTrue(result.isPresent());
    assertEquals(200, result.get().getStatus());
    verify(thumbnailService).getThumbnail(APP_ID, "resolved-oid", 200);
  }

  @Test
  void getThumbnailByFileAppId_throws404WhenFileAppIdNotFound() {
    FileContainer c = container();
    c.setFiles(new ArrayList<>());
    when(dao.findByAppId(APP_ID)).thenReturn(Optional.of(c));

    assertThrows(NotFoundException.class,
      () -> handler.getThumbnailByFileAppId(APP_ID, "01930b92-0000-7000-8000-000000000099", 200));
  }

  @Test
  void getThumbnailByFileAppId_throws404WhenContainerNotFound() {
    when(dao.findByAppId(APP_ID)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class,
      () -> handler.getThumbnailByFileAppId(APP_ID, "01930b92-1111-7000-8000-000000000001", 200));
  }

  // ─── getDownloadUrlByFileAppId (APISIMP-OID-PATHPARAM-REPLACE slice 2) ──

  @Test
  void getDownloadUrlByFileAppId_resolvesOidAndReturnsUrl() throws Exception {
    URI dl = URI.create("https://s3.example.com/bucket/key?sig=xyz");
    ShepardFile file = new ShepardFile("resolved-oid", new Date(), "data.csv", null);
    file.setAppId("01930b92-2222-7000-8000-000000000002");
    FileContainer c = container();
    c.setFiles(new ArrayList<>(List.of(file)));
    when(dao.findByAppId(APP_ID)).thenReturn(Optional.of(c));
    when(service.presignedDownloadUrl(eq(CONTAINER_ID), eq("resolved-oid"), any())).thenReturn(dl);

    Optional<Response> result = handler.getDownloadUrlByFileAppId(
      APP_ID, "01930b92-2222-7000-8000-000000000002");

    assertTrue(result.isPresent());
    assertEquals(200, result.get().getStatus());
    var io = (PresignedDownloadUrlIO) result.get().getEntity();
    assertEquals(dl.toString(), io.getDownloadUrl());
  }

  @Test
  void getDownloadUrlByFileAppId_throws404WhenFileAppIdNotFound() {
    FileContainer c = container();
    c.setFiles(new ArrayList<>());
    when(dao.findByAppId(APP_ID)).thenReturn(Optional.of(c));

    assertThrows(NotFoundException.class,
      () -> handler.getDownloadUrlByFileAppId(APP_ID, "01930b92-0000-7000-8000-000000000099"));
  }

  @Test
  void getDownloadUrlByFileAppId_throws404WhenContainerNotFound() {
    when(dao.findByAppId(APP_ID)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class,
      () -> handler.getDownloadUrlByFileAppId(APP_ID, "01930b92-2222-7000-8000-000000000002"));
  }
}
