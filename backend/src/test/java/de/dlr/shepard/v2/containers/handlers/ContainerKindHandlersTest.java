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
import de.dlr.shepard.data.structureddata.daos.StructuredDataContainerDAO;
import de.dlr.shepard.data.structureddata.entities.StructuredDataContainer;
import de.dlr.shepard.data.structureddata.services.StructuredDataContainerService;
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
  StructuredDataContainerService sdService;

  @Mock
  StructuredDataContainerDAO sdDao;

  @Mock
  UserService userService;

  @Mock
  DateHelper dateHelper;

  FileContainerKindHandler fileHandler;
  TimeseriesContainerKindHandler tsHandler;
  StructuredDataContainerKindHandler sdHandler;

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

    sdHandler = new StructuredDataContainerKindHandler();
    sdHandler.service = sdService;
    sdHandler.dao = sdDao;
    sdHandler.userService = userService;
    sdHandler.dateHelper = dateHelper;

    when(userService.getCurrentUser()).thenReturn(new User());
    when(dateHelper.getDate()).thenReturn(new Date());
  }

  private StructuredDataContainer sdContainer(String appId) {
    var c = new StructuredDataContainer(8L);
    c.setAppId(appId);
    c.setName("records");
    c.setMongoId("mongo-sd");
    return c;
  }

  private FileContainer fileContainer(String appId) {
    var c = new FileContainer(5L);
    c.setAppId(appId);
    c.setName("scans");
    c.setMongoId("mongo-abc");
    return c;
  }

  private TimeseriesContainer tsContainer(String appId) {
    var c = new TimeseriesContainer(7L);
    c.setAppId(appId);
    c.setName("telemetry");
    return c;
  }

  private de.dlr.shepard.context.collection.entities.DataObject linkedDataObject(String appId) {
    var col = new de.dlr.shepard.context.collection.entities.Collection();
    col.setShepardId(1L);
    var d = new de.dlr.shepard.context.collection.entities.DataObject();
    d.setAppId(appId);
    d.setCollection(col);
    return d;
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

  @Test
  void file_listLinkedDataObjects_delegatesToService() {
    var d = linkedDataObject("do-1");
    when(fileService.findLinkedDataObjectsByAppId("file-1")).thenReturn(List.of(d));
    var out = fileHandler.listLinkedDataObjects("file-1");
    assertTrue(out.isPresent());
    assertEquals(1, out.get().size());
    verify(fileService).findLinkedDataObjectsByAppId("file-1");
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

  @Test
  void ts_patch_rename_persists() {
    var existing = tsContainer("ts-1");
    when(tsDao.findByAppId("ts-1")).thenReturn(Optional.of(existing));
    when(tsDao.createOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));
    var io = tsHandler.patch("ts-1", Map.of("name", "telemetry-v2"));
    assertEquals("telemetry-v2", io.getName());
    verify(tsDao).createOrUpdate(any());
  }

  @Test
  void ts_delete_delegates() {
    var existing = tsContainer("ts-1");
    when(tsDao.findByAppId("ts-1")).thenReturn(Optional.of(existing));
    tsHandler.delete("ts-1");
    verify(tsService).deleteContainer(existing.getId());
  }

  @Test
  void ts_delete_unknown_throwsNotFound() {
    when(tsDao.findByAppId("missing")).thenReturn(Optional.empty());
    assertThrows(NotFoundException.class, () -> tsHandler.delete("missing"));
  }

  @Test
  void ts_list_returnsEnvelopes() {
    when(tsService.getAllContainers(any())).thenReturn(List.of(tsContainer("ts-1")));
    var out = tsHandler.list(null);
    assertEquals(1, out.size());
    assertEquals("timeseries", out.get(0).getKind());
  }

  @Test
  void ts_findByAppId_skipsDeleted() {
    var deleted = tsContainer("ts-del");
    deleted.setDeleted(true);
    when(tsDao.findByAppId("ts-del")).thenReturn(Optional.of(deleted));
    org.junit.jupiter.api.Assertions.assertNull(tsHandler.findByAppId("ts-del"));
  }

  @Test
  void ts_listLinkedDataObjects_delegatesToService() {
    var d = linkedDataObject("do-1");
    when(tsService.findLinkedDataObjectsByAppId("ts-1")).thenReturn(List.of(d));
    var out = tsHandler.listLinkedDataObjects("ts-1");
    assertTrue(out.isPresent());
    assertEquals(1, out.get().size());
    verify(tsService).findLinkedDataObjectsByAppId("ts-1");
  }

  // ─── structured-data handler ────────────────────────────────────────────────

  @Test
  void sd_kindAndOwns() {
    assertEquals("structured-data", sdHandler.kind());
    assertTrue(sdHandler.owns(sdContainer("a")));
    assertFalse(sdHandler.owns(fileContainer("b")));
  }

  @Test
  void sd_toIO_projectsOid() {
    var io = sdHandler.toIO(sdContainer("sd-1"));
    assertEquals("structured-data", io.getKind());
    assertEquals("mongo-sd", io.getPayload().get("oid"));
  }

  @Test
  void sd_create_delegates() {
    when(sdService.createContainer(any())).thenReturn(sdContainer("sd-new"));
    var io = sdHandler.create(Map.of("name", "records"));
    assertEquals("structured-data", io.getKind());
    verify(sdService).createContainer(any());
  }

  @Test
  void sd_patch_rename_persists() {
    var existing = sdContainer("sd-1");
    when(sdDao.findByAppId("sd-1")).thenReturn(Optional.of(existing));
    when(sdDao.createOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));
    var io = sdHandler.patch("sd-1", Map.of("name", "records-v2"));
    assertEquals("records-v2", io.getName());
  }

  @Test
  void sd_patch_unknown_throwsNotFound() {
    when(sdDao.findByAppId("missing")).thenReturn(Optional.empty());
    assertThrows(NotFoundException.class, () -> sdHandler.patch("missing", Map.of("name", "x")));
  }

  @Test
  void sd_delete_delegates() {
    var existing = sdContainer("sd-1");
    when(sdDao.findByAppId("sd-1")).thenReturn(Optional.of(existing));
    sdHandler.delete("sd-1");
    verify(sdService).deleteContainer(existing.getId());
  }

  @Test
  void sd_delete_unknown_throwsNotFound() {
    when(sdDao.findByAppId("missing")).thenReturn(Optional.empty());
    assertThrows(NotFoundException.class, () -> sdHandler.delete("missing"));
  }

  @Test
  void sd_list_filtersByName() {
    when(sdService.getAllContainers(any())).thenReturn(List.of(sdContainer("sd-1")));
    var out = sdHandler.list("rec");
    assertEquals(1, out.size());
    assertEquals("structured-data", out.get(0).getKind());
  }

  @Test
  void sd_findByAppId_resolves() {
    var existing = sdContainer("sd-1");
    when(sdDao.findByAppId("sd-1")).thenReturn(Optional.of(existing));
    org.junit.jupiter.api.Assertions.assertEquals(existing, sdHandler.findByAppId("sd-1"));
  }

  @Test
  void sd_listLinkedDataObjects_delegatesToService() {
    var d = linkedDataObject("do-1");
    when(sdService.findLinkedDataObjectsByAppId("sd-1")).thenReturn(List.of(d));
    var out = sdHandler.listLinkedDataObjects("sd-1");
    assertTrue(out.isPresent());
    assertEquals(1, out.get().size());
    verify(sdService).findLinkedDataObjectsByAppId("sd-1");
  }

  // ─── findLinkedDataObjectAppIds ────────────────────────────────────────────

  @Test
  void file_findLinkedDataObjectAppIds_returnsAppIds() {
    var container = fileContainer("file-1");
    when(fileDao.findByAppId("file-1")).thenReturn(Optional.of(container));
    var linkedDo = org.mockito.Mockito.mock(de.dlr.shepard.context.collection.entities.DataObject.class);
    when(linkedDo.getAppId()).thenReturn("do-app-1");
    when(fileService.findLinkedDataObjectsById(5L)).thenReturn(List.of(linkedDo));
    var result = fileHandler.findLinkedDataObjectAppIds("file-1");
    assertTrue(result.isPresent());
    assertEquals(List.of("do-app-1"), result.get());
  }

  @Test
  void file_findLinkedDataObjectAppIds_unknownContainer_returnsEmpty() {
    when(fileDao.findByAppId("missing")).thenReturn(Optional.empty());
    assertFalse(fileHandler.findLinkedDataObjectAppIds("missing").isPresent());
  }

  @Test
  void ts_findLinkedDataObjectAppIds_returnsAppIds() {
    var container = tsContainer("ts-1");
    when(tsDao.findByAppId("ts-1")).thenReturn(Optional.of(container));
    var linkedDo = org.mockito.Mockito.mock(de.dlr.shepard.context.collection.entities.DataObject.class);
    when(linkedDo.getAppId()).thenReturn("do-app-2");
    when(tsService.findLinkedDataObjectsById(7L)).thenReturn(List.of(linkedDo));
    var result = tsHandler.findLinkedDataObjectAppIds("ts-1");
    assertTrue(result.isPresent());
    assertEquals(List.of("do-app-2"), result.get());
  }

  @Test
  void sd_findLinkedDataObjectAppIds_returnsAppIds() {
    var container = sdContainer("sd-1");
    when(sdDao.findByAppId("sd-1")).thenReturn(Optional.of(container));
    var linkedDo = org.mockito.Mockito.mock(de.dlr.shepard.context.collection.entities.DataObject.class);
    when(linkedDo.getAppId()).thenReturn("do-app-3");
    when(sdService.findLinkedDataObjectsById(8L)).thenReturn(List.of(linkedDo));
    var result = sdHandler.findLinkedDataObjectAppIds("sd-1");
    assertTrue(result.isPresent());
    assertEquals(List.of("do-app-3"), result.get());
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

  @Test
  void patchSupport_blankNameInPatch_throws() {
    var existing = fileContainer("file-1");
    when(fileDao.findByAppId("file-1")).thenReturn(Optional.of(existing));
    var patch = new java.util.HashMap<String, Object>();
    patch.put("name", "   ");
    assertThrows(BadRequestException.class, () -> fileHandler.patch("file-1", patch));
  }

  @Test
  void patchSupport_explicitNullName_throws() {
    var existing = fileContainer("file-1");
    when(fileDao.findByAppId("file-1")).thenReturn(Optional.of(existing));
    var patch = new java.util.HashMap<String, Object>();
    patch.put("name", null);
    assertThrows(BadRequestException.class, () -> fileHandler.patch("file-1", patch));
  }

  @Test
  void patchSupport_clearStatusWithNull_persists() {
    var existing = fileContainer("file-1");
    existing.setStatus("READY");
    when(fileDao.findByAppId("file-1")).thenReturn(Optional.of(existing));
    when(fileDao.createOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));
    var patch = new java.util.HashMap<String, Object>();
    patch.put("status", null);
    var io = fileHandler.patch("file-1", patch);
    org.junit.jupiter.api.Assertions.assertNull(io.getStatus());
  }

  @Test
  void patchSupport_emptyPatch_noPersist() {
    var existing = fileContainer("file-1");
    when(fileDao.findByAppId("file-1")).thenReturn(Optional.of(existing));
    var io = fileHandler.patch("file-1", Map.of());
    assertEquals("scans", io.getName());
    verify(fileDao, org.mockito.Mockito.never()).createOrUpdate(any());
  }

  @Test
  void file_findByAppId_resolvesAndSkipsDeleted() {
    var existing = fileContainer("file-1");
    when(fileDao.findByAppId("file-1")).thenReturn(Optional.of(existing));
    assertEquals(existing, fileHandler.findByAppId("file-1"));

    var deleted = fileContainer("file-del");
    deleted.setDeleted(true);
    when(fileDao.findByAppId("file-del")).thenReturn(Optional.of(deleted));
    org.junit.jupiter.api.Assertions.assertNull(fileHandler.findByAppId("file-del"));
  }
}
