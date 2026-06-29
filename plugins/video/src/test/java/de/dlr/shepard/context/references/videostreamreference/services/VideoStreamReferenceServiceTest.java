package de.dlr.shepard.context.references.videostreamreference.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.videostreamreference.daos.VideoStreamReferenceDAO;
import de.dlr.shepard.context.references.videostreamreference.model.VideoStreamReference;
import de.dlr.shepard.storage.FileStorage;
import de.dlr.shepard.storage.FileStorageRegistry;
import de.dlr.shepard.storage.StorageException;
import de.dlr.shepard.storage.StorageGetResponse;
import de.dlr.shepard.storage.StorageLocator;
import de.dlr.shepard.storage.StorageNotInstalledException;
import de.dlr.shepard.storage.StoragePutRequest;
import jakarta.ws.rs.NotFoundException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * VID1a — unit tests for {@link VideoStreamReferenceService}.
 *
 * <p>All collaborators are Mockito mocks. No Quarkus container, no database.
 * Coverage target: ≥ 70% line / branch on the service class.
 */
class VideoStreamReferenceServiceTest {

  static final String DO_APPID = "do-test-appid";
  static final long DO_OGM_ID = 42L;
  static final String REF_APPID = "vsr-test-appid";
  static final String PROVIDER_ID = "gridfs";
  static final String LOCATOR_KEY = "locator-abc";
  static final String STORAGE_LOCATOR = PROVIDER_ID + ":" + LOCATOR_KEY;

  VideoStreamReferenceService service;

  VideoStreamReferenceDAO videoStreamReferenceDAO;
  DataObjectDAO dataObjectDAO;
  FileStorageRegistry fileStorageRegistry;
  FileStorage fileStorage;
  VideoProbeService videoProbeService;
  de.dlr.shepard.plugins.video.transcode.VideoTranscodeOrchestrator transcodeOrchestrator;
  UserService userService;
  DateHelper dateHelper;
  EntityIdResolver entityIdResolver;

  DataObject parentDataObject;
  User currentUser;

  @BeforeEach
  void setUp() {
    videoStreamReferenceDAO = mock(VideoStreamReferenceDAO.class);
    dataObjectDAO = mock(DataObjectDAO.class);
    fileStorageRegistry = mock(FileStorageRegistry.class);
    fileStorage = mock(FileStorage.class);
    videoProbeService = mock(VideoProbeService.class);
    transcodeOrchestrator = mock(de.dlr.shepard.plugins.video.transcode.VideoTranscodeOrchestrator.class);
    userService = mock(UserService.class);
    dateHelper = mock(DateHelper.class);
    entityIdResolver = mock(EntityIdResolver.class);

    service = new VideoStreamReferenceService();
    service.videoStreamReferenceDAO = videoStreamReferenceDAO;
    service.dataObjectDAO = dataObjectDAO;
    service.fileStorageRegistry = fileStorageRegistry;
    service.videoProbeService = videoProbeService;
    service.transcodeOrchestrator = transcodeOrchestrator;
    service.userService = userService;
    service.dateHelper = dateHelper;
    service.entityIdResolver = entityIdResolver;

    parentDataObject = new DataObject();
    parentDataObject.setId(DO_OGM_ID);
    parentDataObject.setAppId(DO_APPID);

    currentUser = new User("alice");

    when(userService.getCurrentUser()).thenReturn(currentUser);
    when(dateHelper.getDate()).thenReturn(new Date(0L));
    when(entityIdResolver.resolveLong(DO_APPID)).thenReturn(DO_OGM_ID);
    when(dataObjectDAO.findByNeo4jId(DO_OGM_ID)).thenReturn(parentDataObject);
    when(fileStorageRegistry.activeStorage()).thenReturn(Optional.of(fileStorage));
    // Orchestrator is mocked; default behaviour returns the entity unchanged so
    // existing service-level assertions on the returned entity still hold.
    when(transcodeOrchestrator.submit(any())).thenAnswer(inv -> inv.getArgument(0));
  }

  // ─── listByDataObject ─────────────────────────────────────────────────────

  @Test
  void listByDataObject_returnsRefs() {
    VideoStreamReference ref = new VideoStreamReference(1L);
    when(videoStreamReferenceDAO.findByDataObjectNeo4jId(DO_OGM_ID)).thenReturn(List.of(ref));

    List<VideoStreamReference> result = service.listByDataObject(DO_APPID);

    assertThat(result).containsExactly(ref);
  }

  @Test
  void listByDataObject_missingDataObject_throws() {
    when(entityIdResolver.resolveLong(DO_APPID)).thenThrow(new NotFoundException("not found"));

    assertThatThrownBy(() -> service.listByDataObject(DO_APPID)).isInstanceOf(NotFoundException.class);
  }

  // ─── findByAppId ──────────────────────────────────────────────────────────

  @Test
  void findByAppId_delegatesToDAO() {
    VideoStreamReference ref = new VideoStreamReference(2L);
    when(videoStreamReferenceDAO.findByAppId(REF_APPID)).thenReturn(ref);

    assertThat(service.findByAppId(REF_APPID)).isSameAs(ref);
  }

  // ─── getDataObjectOgmId ───────────────────────────────────────────────────

  @Test
  void getDataObjectOgmId_returnsId() {
    assertThat(service.getDataObjectOgmId(DO_APPID)).isEqualTo(DO_OGM_ID);
  }

  @Test
  void getDataObjectOgmId_missingDataObject_returnsNull() {
    when(entityIdResolver.resolveLong(DO_APPID)).thenThrow(new NotFoundException());
    assertThat(service.getDataObjectOgmId(DO_APPID)).isNull();
  }

  // ─── create ────────────────────────────────────────────────────────────────

  @Test
  void create_happyPath_persistsAndReturnsRef() throws Exception {
    StorageLocator locator = new StorageLocator(PROVIDER_ID, LOCATOR_KEY);
    when(fileStorage.put(any(StoragePutRequest.class))).thenReturn(locator);

    StorageGetResponse getResp = mock(StorageGetResponse.class);
    when(getResp.stream()).thenReturn(InputStream.nullInputStream());
    when(fileStorage.get(locator)).thenReturn(getResp);

    VideoProbeResult probe = new VideoProbeResult(120.0, 1024L, 1920, 1080, 29.97, "h264", "aac", null);
    when(videoProbeService.probe(any(InputStream.class), any())).thenReturn(probe);

    VideoStreamReference persisted = new VideoStreamReference(10L);
    persisted.setAppId(REF_APPID);
    when(videoStreamReferenceDAO.createOrUpdate(any())).thenReturn(persisted);

    VideoStreamReference result = service.create(
      DO_APPID, "My Video", "video.mp4", "video/mp4", 1024L, InputStream.nullInputStream()
    );

    assertThat(result).isNotNull();
    assertThat(result.getAppId()).isEqualTo(REF_APPID);
    verify(videoStreamReferenceDAO, org.mockito.Mockito.times(2)).createOrUpdate(any());
  }

  @Test
  void create_nullName_usesFilename() throws Exception {
    StorageLocator locator = new StorageLocator(PROVIDER_ID, LOCATOR_KEY);
    when(fileStorage.put(any(StoragePutRequest.class))).thenReturn(locator);

    StorageGetResponse getResp = mock(StorageGetResponse.class);
    when(getResp.stream()).thenReturn(InputStream.nullInputStream());
    when(fileStorage.get(locator)).thenReturn(getResp);

    when(videoProbeService.probe(any(InputStream.class), any())).thenReturn(VideoProbeResult.empty());

    VideoStreamReference persisted = new VideoStreamReference(11L);
    persisted.setAppId("vsr-2");
    when(videoStreamReferenceDAO.createOrUpdate(any())).thenReturn(persisted);

    VideoStreamReference result = service.create(
      DO_APPID, null, "clip.mkv", "video/x-matroska", null, InputStream.nullInputStream()
    );

    assertThat(result).isNotNull();
    verify(fileStorage).put(argThat(req -> "clip.mkv".equals(req.fileName())));
  }

  @Test
  void create_recordsAuthoritativeStoredSize_notDeclaredContentLength() throws Exception {
    // IMPORT-VIDEO-MP4-SHORTUPLOAD regression: when ffprobe yields no size and
    // the declared Content-Length is unreliable (null/mismatched for large
    // chunked PUTs), the persisted fileSize must be the storage adapter's
    // authoritative stored byte count — otherwise the importer's stored!=size
    // guard retry-loops forever on large MP4s.
    long storedSize = 1_331_387_223L; // the real on-disk MP4 size
    StorageLocator locator = new StorageLocator(PROVIDER_ID, LOCATOR_KEY);
    when(fileStorage.put(any(StoragePutRequest.class))).thenReturn(locator);

    StorageGetResponse getResp = mock(StorageGetResponse.class);
    when(getResp.stream()).thenReturn(InputStream.nullInputStream());
    when(getResp.sizeBytes()).thenReturn(storedSize);
    when(fileStorage.get(locator)).thenReturn(getResp);

    // ffprobe fails → empty probe (no fileSizeBytes).
    when(videoProbeService.probe(any(InputStream.class), any())).thenReturn(VideoProbeResult.empty());

    VideoStreamReference persisted = new VideoStreamReference(12L);
    persisted.setAppId("vsr-stored-size");
    when(videoStreamReferenceDAO.createOrUpdate(any())).thenReturn(persisted);

    org.mockito.ArgumentCaptor<VideoStreamReference> captor =
      org.mockito.ArgumentCaptor.forClass(VideoStreamReference.class);

    // Declared content-length is deliberately wrong (null) — the large-upload
    // failure shape. The stored size must still be recorded.
    service.create(
      DO_APPID, "Big", "P02_1.Bahn.MP4", "video/mp4", null, InputStream.nullInputStream()
    );

    verify(videoStreamReferenceDAO, org.mockito.Mockito.atLeastOnce()).createOrUpdate(captor.capture());
    assertThat(captor.getAllValues())
      .anyMatch(r -> Long.valueOf(storedSize).equals(r.getFileSizeBytes()));
  }

  @Test
  void create_missingDataObject_throws() throws Exception {
    when(entityIdResolver.resolveLong(DO_APPID)).thenThrow(new NotFoundException());

    assertThatThrownBy(() ->
      service.create(DO_APPID, "v", "v.mp4", "video/mp4", null, InputStream.nullInputStream())
    ).isInstanceOf(NotFoundException.class);
    verify(fileStorage, never()).put(any());
  }

  @Test
  void create_noStorageAdapter_throws() throws Exception {
    when(fileStorageRegistry.activeStorage()).thenReturn(Optional.empty());

    assertThatThrownBy(() ->
      service.create(DO_APPID, "v", "v.mp4", "video/mp4", null, InputStream.nullInputStream())
    ).isInstanceOf(StorageNotInstalledException.class);
  }

  @Test
  void create_probeFails_stillPersists() throws Exception {
    StorageLocator locator = new StorageLocator(PROVIDER_ID, LOCATOR_KEY);
    when(fileStorage.put(any(StoragePutRequest.class))).thenReturn(locator);
    when(fileStorage.get(locator)).thenThrow(new StorageException("probe-fetch failed"));

    VideoStreamReference persisted = new VideoStreamReference(12L);
    persisted.setAppId("vsr-probe-fail");
    when(videoStreamReferenceDAO.createOrUpdate(any())).thenReturn(persisted);

    VideoStreamReference result = service.create(
      DO_APPID, "v", "v.mp4", "video/mp4", 512L, InputStream.nullInputStream()
    );

    assertThat(result).isNotNull();
    verify(videoStreamReferenceDAO, org.mockito.Mockito.times(2)).createOrUpdate(any());
  }

  // ─── getPayload ────────────────────────────────────────────────────────────

  @Test
  void getPayload_happyPath_returnsStream() throws Exception {
    VideoStreamReference ref = new VideoStreamReference(30L);
    ref.setAppId(REF_APPID);
    ref.setStorageLocator(STORAGE_LOCATOR);

    StorageGetResponse getResp = mock(StorageGetResponse.class);
    when(fileStorage.get(new StorageLocator(PROVIDER_ID, LOCATOR_KEY))).thenReturn(getResp);

    StorageGetResponse result = service.getPayload(ref);
    assertThat(result).isSameAs(getResp);
  }

  @Test
  void getPayload_nullLocator_throws() {
    VideoStreamReference ref = new VideoStreamReference(31L);
    ref.setStorageLocator(null);

    assertThatThrownBy(() -> service.getPayload(ref)).isInstanceOf(NotFoundException.class);
  }

  @Test
  void getPayload_malformedLocator_throws() {
    VideoStreamReference ref = new VideoStreamReference(32L);
    ref.setStorageLocator("no-colon-here");

    assertThatThrownBy(() -> service.getPayload(ref)).isInstanceOf(NotFoundException.class);
  }

  @Test
  void getPayload_noStorageAdapter_throws() {
    VideoStreamReference ref = new VideoStreamReference(33L);
    ref.setStorageLocator(STORAGE_LOCATOR);
    when(fileStorageRegistry.activeStorage()).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getPayload(ref)).isInstanceOf(StorageNotInstalledException.class);
  }

  // ─── delete ────────────────────────────────────────────────────────────────

  @Test
  void delete_withLocator_deletesFromStorageAndSoftDeletes() throws Exception {
    VideoStreamReference ref = new VideoStreamReference(20L);
    ref.setAppId(REF_APPID);
    ref.setStorageLocator(STORAGE_LOCATOR);
    when(videoStreamReferenceDAO.createOrUpdate(any())).thenReturn(ref);

    service.delete(ref);

    assertThat(ref.isDeleted()).isTrue();
    verify(fileStorage).delete(new StorageLocator(PROVIDER_ID, LOCATOR_KEY));
  }

  @Test
  void delete_nullLocator_skipsStorageDelete() throws Exception { // FileStorage.delete() throws StorageException
    VideoStreamReference ref = new VideoStreamReference(21L);
    ref.setAppId(REF_APPID);
    ref.setStorageLocator(null);
    when(videoStreamReferenceDAO.createOrUpdate(any())).thenReturn(ref);

    service.delete(ref);

    verify(fileStorage, never()).delete(any());
    assertThat(ref.isDeleted()).isTrue();
  }

  @Test
  void delete_storageDeleteFails_stillSoftDeletes() throws Exception {
    VideoStreamReference ref = new VideoStreamReference(22L);
    ref.setAppId(REF_APPID);
    ref.setStorageLocator(STORAGE_LOCATOR);
    when(videoStreamReferenceDAO.createOrUpdate(any())).thenReturn(ref);
    org.mockito.Mockito.doThrow(new StorageException("boom")).when(fileStorage).delete(any());

    service.delete(ref);

    assertThat(ref.isDeleted()).isTrue();
    verify(videoStreamReferenceDAO).createOrUpdate(ref);
  }

  // ─── storeWithDedup → SPI-only store (STORAGE-SPI-UNIFY-1) ────────────────

  /**
   * STORAGE-SPI-UNIFY-1: bytes are stored exclusively through the active
   * {@link FileStorage} adapter — no direct MongoDB / GridFS access. The
   * returned locator is whatever the adapter produced.
   */
  @Test
  void storeWithDedup_routesThroughActiveAdapter() throws Exception {
    StorageLocator expected = new StorageLocator(PROVIDER_ID, LOCATOR_KEY);
    when(fileStorage.put(any(StoragePutRequest.class))).thenReturn(expected);

    byte[] payload = new byte[] { 0x0A, 0x0B, 0x0C };
    StorageLocator locator = service.storeWithDedup(
      fileStorage, "new.mp4", "video/mp4", (long) payload.length, new ByteArrayInputStream(payload)
    );

    // The adapter's put() is the only write path.
    verify(fileStorage).put(any(StoragePutRequest.class));
    assertThat(locator).isEqualTo(expected);
  }

  /**
   * STORAGE-SPI-UNIFY-1: when the active provider is S3 (not GridFS), the
   * upload still routes through the SPI {@code put} and returns the S3
   * adapter's locator — proving no GridFS "magic route" remains. Before
   * the fix the dedup short-circuit could synthesise a {@code gridfs:}
   * locator even under an S3 backend.
   */
  @Test
  void storeWithDedup_underS3Provider_returnsS3Locator() throws Exception {
    StorageLocator s3Locator = new StorageLocator("s3", VideoStreamReferenceService.VIDEO_CONTAINER + "/uuid-key");
    when(fileStorage.put(any(StoragePutRequest.class))).thenReturn(s3Locator);

    byte[] payload = new byte[] { 0x01, 0x02 };
    StorageLocator locator = service.storeWithDedup(
      fileStorage, "clip.mp4", "video/mp4", (long) payload.length, new ByteArrayInputStream(payload)
    );

    verify(fileStorage).put(any(StoragePutRequest.class));
    assertThat(locator.providerId()).isEqualTo("s3");
    assertThat(locator).isEqualTo(s3Locator);
  }
}
