package de.dlr.shepard.v2.references.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.file.entities.FileReference;
import de.dlr.shepard.context.references.file.services.SingletonFileReferenceService;
import de.dlr.shepard.context.references.timeseriesreference.daos.TimeseriesReferenceDAO;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.context.references.timeseriesreference.services.TimeseriesReferenceService;
import de.dlr.shepard.context.references.uri.entities.URIReference;
import de.dlr.shepard.context.references.uri.services.URIReferenceService;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * V2CONV-A2 — direct unit tests for the in-tree {@link
 * de.dlr.shepard.v2.references.spi.ReferenceKindHandler} implementations,
 * exercising the IO projection + dispatch with mocked per-kind services.
 */
class ReferenceKindHandlersTest {

  private static final String DO_APP_ID = "do-app-1";

  @Mock
  URIReferenceService uriReferenceService;

  @Mock
  SingletonFileReferenceService singletonService;

  @Mock
  DataObjectDAO dataObjectDAO;

  @Mock
  TimeseriesReferenceService timeseriesReferenceService;

  @Mock
  TimeseriesReferenceDAO timeseriesReferenceDAO;

  UriReferenceKindHandler uriHandler;
  FileReferenceKindHandler fileHandler;
  TimeseriesReferenceKindHandler tsHandler;

  DataObject parent;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    uriHandler = new UriReferenceKindHandler();
    uriHandler.uriReferenceService = uriReferenceService;
    uriHandler.dataObjectDAO = dataObjectDAO;
    fileHandler = new FileReferenceKindHandler();
    fileHandler.singletonService = singletonService;
    tsHandler = new TimeseriesReferenceKindHandler();
    tsHandler.timeseriesReferenceService = timeseriesReferenceService;
    tsHandler.timeseriesReferenceDAO = timeseriesReferenceDAO;
    tsHandler.dataObjectDAO = dataObjectDAO;
    tsHandler.objectMapper = new ObjectMapper();

    var coll = new Collection(1L);
    coll.setShepardId(1L);
    parent = new DataObject(42L);
    parent.setAppId(DO_APP_ID);
    parent.setShepardId(101L);
    parent.setCollection(coll);
  }

  private URIReference uriRef() {
    var ref = new URIReference(7L);
    ref.setAppId("uri-1");
    ref.setName("DLR");
    ref.setUri("https://www.dlr.de");
    ref.setRelationship("seeAlso");
    ref.setDataObject(parent);
    ref.setShepardId(7L);
    return ref;
  }

  private FileReference fileRef() {
    var ref = new FileReference(9L);
    ref.setAppId("file-1");
    ref.setName("robot.urdf");
    ref.setFileKind("urdf");
    ref.setDataObject(parent);
    ref.setShepardId(9L);
    return ref;
  }

  // ─── uri handler ───────────────────────────────────────────────────────────

  @Test
  void uri_kindAndOwns() {
    assertEquals("uri", uriHandler.kind());
    assertTrue(uriHandler.owns(uriRef()));
    assertFalse(uriHandler.owns(fileRef()));
  }

  @Test
  void uri_toIO_flattensPayload() {
    var io = uriHandler.toIO(uriRef());
    assertEquals("uri", io.getKind());
    assertEquals("https://www.dlr.de", io.getPayload().get("uri"));
    assertEquals("seeAlso", io.getPayload().get("relationship"));
  }

  @Test
  void uri_create_delegates() {
    when(dataObjectDAO.findByAppId(DO_APP_ID)).thenReturn(parent);
    when(uriReferenceService.createReference(eq(1L), eq(101L), any())).thenReturn(uriRef());
    var io = uriHandler.create(DO_APP_ID, Map.of("name", "DLR", "uri", "https://www.dlr.de"));
    assertEquals("uri", io.getKind());
    verify(uriReferenceService).createReference(eq(1L), eq(101L), any());
  }

  @Test
  void uri_create_unknownDataObject_throwsNotFound() {
    when(dataObjectDAO.findByAppId(DO_APP_ID)).thenReturn(null);
    assertThrows(NotFoundException.class, () -> uriHandler.create(DO_APP_ID, Map.of("uri", "x")));
  }

  @Test
  void uri_patch_delegates() {
    when(uriReferenceService.patchReferenceByAppId(eq("uri-1"), any())).thenReturn(uriRef());
    var io = uriHandler.patch("uri-1", Map.of("name", "new"));
    assertEquals("uri", io.getKind());
  }

  @Test
  void uri_delete_delegates() {
    when(uriReferenceService.findByAppId("uri-1")).thenReturn(uriRef());
    uriHandler.delete("uri-1");
    verify(uriReferenceService).deleteReference(eq(1L), eq(101L), eq(7L));
  }

  @Test
  void uri_delete_missing_throwsNotFound() {
    when(uriReferenceService.findByAppId("uri-1")).thenReturn(null);
    assertThrows(NotFoundException.class, () -> uriHandler.delete("uri-1"));
  }

  @Test
  void uri_list_delegates() {
    when(dataObjectDAO.findByAppId(DO_APP_ID)).thenReturn(parent);
    when(uriReferenceService.getAllReferencesByDataObjectId(eq(1L), eq(101L), any()))
      .thenReturn(List.of(uriRef()));
    var out = uriHandler.listByDataObject(DO_APP_ID, null);
    assertEquals(1, out.size());
    assertEquals("uri", out.get(0).getKind());
  }

  // ─── file handler ──────────────────────────────────────────────────────────

  @Test
  void file_kindAndShape() {
    assertEquals("file", fileHandler.kind());
    assertTrue(fileHandler.owns(fileRef()));
    var io = fileHandler.toIO(fileRef());
    assertEquals("singleton", io.getReferenceShape());
    assertEquals("urdf", io.getFileKind());
  }

  @Test
  void file_create_rejectsBinary() {
    assertThrows(BadRequestException.class, () -> fileHandler.create(DO_APP_ID, Map.of()));
  }

  @Test
  void file_patch_delegates() {
    when(singletonService.patchSingleton(eq("file-1"), any())).thenReturn(fileRef());
    var io = fileHandler.patch("file-1", Map.of("name", "renamed"));
    assertEquals("file", io.getKind());
  }

  @Test
  void file_delete_delegates() {
    when(singletonService.getByAppId("file-1")).thenReturn(fileRef());
    fileHandler.delete("file-1");
    verify(singletonService).deleteSingleton("file-1");
  }

  @Test
  void file_list_filtersByFileKind() {
    var urdf = fileRef();
    var pdf = fileRef();
    pdf.setAppId("file-2");
    pdf.setFileKind("pdf");
    when(singletonService.listByDataObject(DO_APP_ID)).thenReturn(List.of(urdf, pdf));

    var all = fileHandler.listByDataObject(DO_APP_ID, null);
    assertEquals(2, all.size());

    var onlyUrdf = fileHandler.listByDataObject(DO_APP_ID, "urdf");
    assertEquals(1, onlyUrdf.size());
    assertEquals("urdf", onlyUrdf.get(0).getFileKind());
  }

  // ─── timeseries handler ────────────────────────────────────────────────────

  private TimeseriesReference tsRef() {
    var ref = new TimeseriesReference(11L);
    ref.setAppId("ts-1");
    ref.setName("DAQ window");
    ref.setStart(1000L);
    ref.setEnd(2000L);
    ref.setTimeReference("WALL_CLOCK");
    ref.setDataObject(parent);
    ref.setShepardId(11L);
    return ref;
  }

  @Test
  void ts_kindAndOwns() {
    assertEquals("timeseries", tsHandler.kind());
    assertTrue(tsHandler.owns(tsRef()));
    assertFalse(tsHandler.owns(uriRef()));
  }

  @Test
  void ts_toIO_flattensPayload() {
    var io = tsHandler.toIO(tsRef());
    assertEquals("timeseries", io.getKind());
    assertEquals(1000L, io.getPayload().get("start"));
    assertEquals(2000L, io.getPayload().get("end"));
    assertEquals("WALL_CLOCK", io.getPayload().get("timeReference"));
  }

  @Test
  void ts_patch_experimentRelativeWithoutOffset_throws400() {
    when(timeseriesReferenceDAO.findByAppId("ts-1")).thenReturn(tsRef());
    assertThrows(
      BadRequestException.class,
      () -> tsHandler.patch("ts-1", Map.of("timeReference", "EXPERIMENT_RELATIVE"))
    );
  }

  @Test
  void ts_patch_validOffset_delegates() {
    var ref = tsRef();
    when(timeseriesReferenceDAO.findByAppId("ts-1")).thenReturn(ref);
    when(timeseriesReferenceService.updateTimeReference(eq(ref), any())).thenReturn(ref);
    var io = tsHandler.patch("ts-1", Map.of("timeReference", "EXPERIMENT_RELATIVE", "wallClockOffset", 5L));
    assertEquals("timeseries", io.getKind());
  }

  @Test
  void ts_patch_missing_throwsNotFound() {
    when(timeseriesReferenceDAO.findByAppId("ts-1")).thenReturn(null);
    assertThrows(NotFoundException.class, () -> tsHandler.patch("ts-1", Map.of()));
  }

  @Test
  void ts_delete_delegates() {
    when(timeseriesReferenceDAO.findByAppId("ts-1")).thenReturn(tsRef());
    tsHandler.delete("ts-1");
    verify(timeseriesReferenceService).deleteReference(eq(1L), eq(101L), eq(11L));
  }

  @Test
  void ts_list_delegates() {
    when(dataObjectDAO.findByAppId(DO_APP_ID)).thenReturn(parent);
    when(timeseriesReferenceService.getAllReferencesByDataObjectId(eq(1L), eq(101L), any()))
      .thenReturn(List.of(tsRef()));
    var out = tsHandler.listByDataObject(DO_APP_ID, null);
    assertEquals(1, out.size());
    assertEquals("timeseries", out.get(0).getKind());
  }
}
