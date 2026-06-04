package de.dlr.shepard.v2.containers.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.data.file.daos.FileContainerDAO;
import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.data.file.services.FileContainerService;
import de.dlr.shepard.data.timeseries.daos.TimeseriesContainerDAO;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * V2CONV-A3 — direct unit tests for the in-tree {@code ContainerKindHandler}
 * implementations and the shared {@link ContainerPatchSupport}. Mirrors
 * {@code ReferenceKindHandlersTest}. Per-kind services + DAOs are mocked.
 */
class ContainerKindHandlersTest {

  @Mock
  FileContainerService fileService;

  @Mock
  FileContainerDAO fileDao;

  @Mock
  TimeseriesContainerService tsService;

  @Mock
  TimeseriesContainerDAO tsDao;

  @Mock
  UserService userService;

  @Mock
  DateHelper dateHelper;

  FileContainerKindHandler fileHandler;
  TimeseriesContainerKindHandler tsHandler;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    fileHandler = new FileContainerKindHandler();
    fileHandler.service = fileService;
    fileHandler.dao = fileDao;
    fileHandler.userService = userService;
    fileHandler.dateHelper = dateHelper;

    tsHandler = new TimeseriesContainerKindHandler();
    tsHandler.service = tsService;
    tsHandler.dao = tsDao;
    tsHandler.userService = userService;
    tsHandler.dateHelper = dateHelper;

    when(userService.getCurrentUser()).thenReturn(new User());
    when(dateHelper.getDate()).thenReturn(new Date());
  }

  private FileContainer fileContainer(String appId) {
    var c = new FileContainer(5L);
    c.setAppId(appId);
    c.setName("scans");
    c.setMongoId("mongo-abc");
    return c;
  }

  private TimeseriesContainer tsContainer(String appId) {
    var c = new TimeseriesContainer();
    c.setAppId(appId);
    c.setName("telemetry");
    return c;
  }

  // ─── file handler ────────────────────────────────────────────────────────

  @Test
  void file_kindAndOwns() {
    assertEquals("file", fileHandler.kind());
    assertTrue(fileHandler.owns(fileContainer("a")));
    assertFalse(fileHandler.owns(tsContainer("b")));
  }

  @Test
  void file_toIO_projectsPayload() {
    var io = fileHandler.toIO(fileContainer("file-1"));
    assertEquals("file", io.getKind());
    assertEquals("mongo-abc", io.getPayload().get("oid"));
  }

  @Test
  void file_create_delegatesAndReturnsIO() {
    when(fileService.createContainer(any())).thenReturn(fileContainer("file-new"));
    var io = fileHandler.create(Map.of("name", "scans"));
    assertEquals("file", io.getKind());
    assertEquals("file-new", io.getAppId());
    verify(fileService).createContainer(any());
  }

  @Test
  void file_create_blankName_throws() {
    assertThrows(BadRequestException.class, () -> fileHandler.create(Map.of("name", "  ")));
    assertThrows(BadRequestException.class, () -> fileHandler.create(Map.of()));
  }

  @Test
  void file_patch_rename_persists() {
    var existing = fileContainer("file-1");
    when(fileDao.findByAppId("file-1")).thenReturn(Optional.of(existing));
    when(fileDao.createOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));
    var io = fileHandler.patch("file-1", Map.of("name", "scans-v2"));
    assertEquals("scans-v2", io.getName());
    verify(fileDao).createOrUpdate(any());
  }

  @Test
  void file_patch_noChange_skipsPersist() {
    var existing = fileContainer("file-1");
    when(fileDao.findByAppId("file-1")).thenReturn(Optional.of(existing));
    var io = fileHandler.patch("file-1", Map.of("name", "scans")); // same name
    assertEquals("scans", io.getName());
    verify(fileDao, org.mockito.Mockito.never()).createOrUpdate(any());
  }

  @Test
  void file_patch_unknown_throwsNotFound() {
    when(fileDao.findByAppId("missing")).thenReturn(Optional.empty());
    assertThrows(NotFoundException.class, () -> fileHandler.patch("missing", Map.of("name", "x")));
  }

  @Test
  void file_delete_delegatesToService() {
    var existing = fileContainer("file-1");
    when(fileDao.findByAppId("file-1")).thenReturn(Optional.of(existing));
    fileHandler.delete("file-1");
    verify(fileService).deleteContainer(5L);
  }

  @Test
  void file_delete_unknown_throwsNotFound() {
    when(fileDao.findByAppId("missing")).thenReturn(Optional.empty());
    assertThrows(NotFoundException.class, () -> fileHandler.delete("missing"));
  }

  @Test
  void file_list_filtersByName() {
    when(fileService.getAllContainers(any())).thenReturn(List.of(fileContainer("file-1")));
    var out = fileHandler.list("sca");
    assertEquals(1, out.size());
    assertEquals("file", out.get(0).getKind());
  }

  // ─── timeseries handler ────────────────────────────────────────────────────

  @Test
  void ts_kindAndOwns() {
    assertEquals("timeseries", tsHandler.kind());
    assertTrue(tsHandler.owns(tsContainer("a")));
    assertFalse(tsHandler.owns(fileContainer("b")));
  }

  @Test
  void ts_toIO_noExtraPayload() {
    var io = tsHandler.toIO(tsContainer("ts-1"));
    assertEquals("timeseries", io.getKind());
    assertTrue(io.getPayload().isEmpty());
  }

  @Test
  void ts_create_delegates() {
    when(tsService.createContainer(any())).thenReturn(tsContainer("ts-new"));
    var io = tsHandler.create(Map.of("name", "telemetry"));
    assertEquals("timeseries", io.getKind());
    verify(tsService).createContainer(any());
  }

  // ─── patch support validation ──────────────────────────────────────────────

  @Test
  void patchSupport_invalidStatus_throws() {
    var existing = fileContainer("file-1");
    when(fileDao.findByAppId("file-1")).thenReturn(Optional.of(existing));
    assertThrows(
      BadRequestException.class,
      () -> fileHandler.patch("file-1", Map.of("status", "BOGUS"))
    );
  }

  @Test
  void patchSupport_validStatus_persists() {
    var existing = fileContainer("file-1");
    when(fileDao.findByAppId("file-1")).thenReturn(Optional.of(existing));
    when(fileDao.createOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));
    var io = fileHandler.patch("file-1", Map.of("status", "ARCHIVED"));
    assertEquals("ARCHIVED", io.getStatus());
  }
}
