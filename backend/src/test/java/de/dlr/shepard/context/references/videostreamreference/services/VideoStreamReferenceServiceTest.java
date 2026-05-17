package de.dlr.shepard.context.references.videostreamreference.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.mongoDB.NamedInputStream;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.videostreamreference.daos.VideoStreamReferenceDAO;
import de.dlr.shepard.context.references.videostreamreference.model.VideoStreamReference;
import de.dlr.shepard.data.file.entities.ShepardFile;
import de.dlr.shepard.data.file.services.FileService;
import jakarta.ws.rs.NotFoundException;
import java.io.File;
import java.io.InputStream;
import java.util.Date;
import java.util.List;
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
  static final String CONTAINER_ID = "FileContainerABC";
  static final String FILE_OID = "fileoid123";

  VideoStreamReferenceService service;

  VideoStreamReferenceDAO videoStreamReferenceDAO;
  DataObjectDAO dataObjectDAO;
  FileService fileService;
  VideoProbeService videoProbeService;
  UserService userService;
  DateHelper dateHelper;
  EntityIdResolver entityIdResolver;

  DataObject parentDataObject;
  User currentUser;

  @BeforeEach
  void setUp() {
    videoStreamReferenceDAO = mock(VideoStreamReferenceDAO.class);
    dataObjectDAO = mock(DataObjectDAO.class);
    fileService = mock(FileService.class);
    videoProbeService = mock(VideoProbeService.class);
    userService = mock(UserService.class);
    dateHelper = mock(DateHelper.class);
    entityIdResolver = mock(EntityIdResolver.class);

    service = new VideoStreamReferenceService();
    service.videoStreamReferenceDAO = videoStreamReferenceDAO;
    service.dataObjectDAO = dataObjectDAO;
    service.fileService = fileService;
    service.videoProbeService = videoProbeService;
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
  }

  // ─── listByDataObjectAppId ─────────────────────────────────────────────────

  @Test
  void listByDataObjectAppId_returnsRefs() {
    VideoStreamReference ref = new VideoStreamReference(1L);
    when(videoStreamReferenceDAO.findByDataObjectOgmId(DO_OGM_ID)).thenReturn(List.of(ref));

    List<VideoStreamReference> result = service.listByDataObjectAppId(DO_APPID);

    assertThat(result).containsExactly(ref);
  }

  @Test
  void listByDataObjectAppId_missingDataObject_throws() {
    when(entityIdResolver.resolveLong(DO_APPID)).thenThrow(new NotFoundException("not found"));

    assertThatThrownBy(() -> service.listByDataObjectAppId(DO_APPID)).isInstanceOf(NotFoundException.class);
  }

  // ─── getByAppId ────────────────────────────────────────────────────────────

  @Test
  void getByAppId_delegatesToDAO() {
    VideoStreamReference ref = new VideoStreamReference(2L);
    when(videoStreamReferenceDAO.findByAppId(REF_APPID)).thenReturn(ref);

    assertThat(service.getByAppId(REF_APPID)).isSameAs(ref);
  }

  // ─── getDataObjectOgmId ────────────────────────────────────────────────────

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
  void create_happyPath_persistsAndReturnRef() throws Exception {
    VideoProbeResult probe = new VideoProbeResult(120.0, 1920, 1080, 29.97, "h264", "aac", 1L);
    when(videoProbeService.probe(any(InputStream.class))).thenReturn(probe);
    when(fileService.createFileContainer()).thenReturn(CONTAINER_ID);

    ShepardFile savedFile = new ShepardFile(FILE_OID, new Date(), "video.mp4", "abc");
    savedFile.setFileSize(1024L);
    when(fileService.createFile(eq(CONTAINER_ID), eq("video.mp4"), any(InputStream.class))).thenReturn(savedFile);

    VideoStreamReference persisted = new VideoStreamReference(10L);
    persisted.setAppId(REF_APPID);
    persisted.setMongoContainerId(CONTAINER_ID);
    persisted.setFileOid(FILE_OID);
    when(videoStreamReferenceDAO.createOrUpdate(any())).thenReturn(persisted);

    ShepardFile backfillFile = new ShepardFile(FILE_OID, new Date(), "video.mp4", "abc");
    backfillFile.setFileSize(2048L);
    when(fileService.getFile(CONTAINER_ID, FILE_OID)).thenReturn(backfillFile);

    File tempFile = File.createTempFile("vsr-test", ".mp4");
    tempFile.deleteOnExit();

    VideoStreamReference result = service.create(DO_APPID, "My Video", "video.mp4", "video/mp4", tempFile);

    assertThat(result).isNotNull();
    assertThat(result.getAppId()).isEqualTo(REF_APPID);
    // Three createOrUpdate calls: initial persist, setShepardId, fileSizeBytes backfill
    verify(videoStreamReferenceDAO, org.mockito.Mockito.times(3)).createOrUpdate(any());
  }

  @Test
  void create_nullName_usesFilename() throws Exception {
    VideoProbeResult probe = new VideoProbeResult(null, null, null, null, null, null, null);
    when(videoProbeService.probe(any(InputStream.class))).thenReturn(probe);
    when(fileService.createFileContainer()).thenReturn(CONTAINER_ID);

    ShepardFile savedFile = new ShepardFile(FILE_OID, new Date(), "clip.mkv", "def");
    when(fileService.createFile(eq(CONTAINER_ID), eq("clip.mkv"), any(InputStream.class))).thenReturn(savedFile);

    VideoStreamReference persisted = new VideoStreamReference(11L);
    persisted.setAppId("vsr-2");
    when(videoStreamReferenceDAO.createOrUpdate(any())).thenReturn(persisted);
    when(fileService.getFile(anyString(), anyString())).thenThrow(new NotFoundException("no file"));

    File tempFile = File.createTempFile("vsr-test2", ".mkv");
    tempFile.deleteOnExit();

    // name=null → falls back to filename "clip.mkv" (handled in service)
    VideoStreamReference result = service.create(DO_APPID, null, "clip.mkv", "video/x-matroska", tempFile);
    assertThat(result).isNotNull();
  }

  @Test
  void create_missingDataObject_throws() throws Exception {
    when(entityIdResolver.resolveLong(DO_APPID)).thenThrow(new NotFoundException());

    File tempFile = File.createTempFile("vsr-test3", ".mp4");
    tempFile.deleteOnExit();

    assertThatThrownBy(() -> service.create(DO_APPID, "v", "v.mp4", "video/mp4", tempFile)).isInstanceOf(
      NotFoundException.class
    );
    verify(fileService, never()).createFileContainer();
  }

  // ─── delete ────────────────────────────────────────────────────────────────

  @Test
  void delete_softDeletesNodeAndDropsContainer() {
    VideoStreamReference ref = new VideoStreamReference(20L);
    ref.setAppId(REF_APPID);
    ref.setMongoContainerId(CONTAINER_ID);
    when(videoStreamReferenceDAO.findByAppId(REF_APPID)).thenReturn(ref);
    when(videoStreamReferenceDAO.createOrUpdate(any())).thenReturn(ref);

    service.delete(REF_APPID);

    assertThat(ref.isDeleted()).isTrue();
    verify(fileService).deleteFileContainer(CONTAINER_ID);
  }

  @Test
  void delete_nullContainerId_skipsContainerDelete() {
    VideoStreamReference ref = new VideoStreamReference(21L);
    ref.setAppId(REF_APPID);
    ref.setMongoContainerId(null);
    when(videoStreamReferenceDAO.findByAppId(REF_APPID)).thenReturn(ref);
    when(videoStreamReferenceDAO.createOrUpdate(any())).thenReturn(ref);

    service.delete(REF_APPID);

    verify(fileService, never()).deleteFileContainer(anyString());
  }

  @Test
  void delete_missingRef_throws() {
    when(videoStreamReferenceDAO.findByAppId(REF_APPID)).thenReturn(null);
    assertThatThrownBy(() -> service.delete(REF_APPID)).isInstanceOf(NotFoundException.class);
  }

  // ─── getPayload ────────────────────────────────────────────────────────────

  @Test
  void getPayload_happyPath_returnsStream() {
    VideoStreamReference ref = new VideoStreamReference(30L);
    ref.setAppId(REF_APPID);
    ref.setMongoContainerId(CONTAINER_ID);
    ref.setFileOid(FILE_OID);
    when(videoStreamReferenceDAO.findByAppId(REF_APPID)).thenReturn(ref);

    NamedInputStream nis = new NamedInputStream(FILE_OID, InputStream.nullInputStream(), "video.mp4", 1024L);
    when(fileService.getPayload(CONTAINER_ID, FILE_OID)).thenReturn(nis);

    NamedInputStream result = service.getPayload(REF_APPID);
    assertThat(result).isSameAs(nis);
  }

  @Test
  void getPayload_missingRef_throws() {
    when(videoStreamReferenceDAO.findByAppId(REF_APPID)).thenReturn(null);
    assertThatThrownBy(() -> service.getPayload(REF_APPID)).isInstanceOf(NotFoundException.class);
  }

  @Test
  void getPayload_nullMongoContainerId_throws() {
    VideoStreamReference ref = new VideoStreamReference(31L);
    ref.setAppId(REF_APPID);
    ref.setMongoContainerId(null);
    ref.setFileOid(FILE_OID);
    when(videoStreamReferenceDAO.findByAppId(REF_APPID)).thenReturn(ref);

    assertThatThrownBy(() -> service.getPayload(REF_APPID)).isInstanceOf(NotFoundException.class);
  }
}
