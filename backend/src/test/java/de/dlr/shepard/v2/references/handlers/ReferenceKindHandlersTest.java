package de.dlr.shepard.v2.references.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.context.collection.daos.CollectionDAO;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.dataobject.entities.CollectionReference;
import de.dlr.shepard.context.references.dataobject.entities.DataObjectReference;
import de.dlr.shepard.context.references.dataobject.services.CollectionReferenceService;
import de.dlr.shepard.context.references.dataobject.services.DataObjectReferenceService;
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
  CollectionDAO collectionDAO;

  @Mock
  TimeseriesReferenceService timeseriesReferenceService;

  @Mock
  TimeseriesReferenceDAO timeseriesReferenceDAO;

  @Mock
  CollectionReferenceService collectionReferenceService;

  @Mock
  DataObjectReferenceService dataObjectReferenceService;

  UriReferenceKindHandler uriHandler;
  FileReferenceKindHandler fileHandler;
  TimeseriesReferenceKindHandler tsHandler;
  CollectionReferenceKindHandler collectionHandler;
  DataObjectReferenceKindHandler dataObjectRefHandler;

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
    collectionHandler = new CollectionReferenceKindHandler();
    collectionHandler.collectionReferenceService = collectionReferenceService;
    collectionHandler.dataObjectDAO = dataObjectDAO;
    collectionHandler.collectionDAO = collectionDAO;
    dataObjectRefHandler = new DataObjectReferenceKindHandler();
    dataObjectRefHandler.dataObjectReferenceService = dataObjectReferenceService;
    dataObjectRefHandler.dataObjectDAO = dataObjectDAO;

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

  // ─── FILEREF-CONTENT-DOWNLOAD-MISSING-2026-06-30 — downloadContent ────────

  @Test
  void file_downloadContent_fullBody_returns200WithHeaders() {
    byte[] bytes = "robot { joint1 }".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    var stream = new java.io.ByteArrayInputStream(bytes);
    var nis = new de.dlr.shepard.common.mongoDB.NamedInputStream(
      "oid-x", stream, "robot.urdf", (long) bytes.length);
    when(singletonService.getPayload("file-1")).thenReturn(nis);
    when(singletonService.getByAppId("file-1")).thenReturn(fileRef());

    var resp = fileHandler.downloadContent("file-1", null);
    assertEquals(200, resp.getStatus());
    assertEquals("attachment; filename=\"robot.urdf\"", resp.getHeaderString("Content-Disposition"));
    assertEquals("bytes", resp.getHeaderString("Accept-Ranges"));
    assertEquals(String.valueOf(bytes.length), resp.getHeaderString("Content-Length"));
  }

  @Test
  void file_downloadContent_missingOid_returns404() {
    when(singletonService.getPayload("missing-1"))
      .thenThrow(new NotFoundException("Singleton FileReference appId=missing-1 has no attached file"));
    var resp = fileHandler.downloadContent("missing-1", null);
    assertEquals(404, resp.getStatus());
  }

  @Test
  void file_downloadContent_rangeRequest_returns206WithContentRange() {
    byte[] bytes = new byte[1024];
    for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) (i & 0xff);
    var stream = new java.io.ByteArrayInputStream(bytes);
    var nis = new de.dlr.shepard.common.mongoDB.NamedInputStream(
      "oid-y", stream, "big.bin", (long) bytes.length);
    when(singletonService.getPayload("file-1")).thenReturn(nis);
    when(singletonService.getByAppId("file-1")).thenReturn(fileRef());

    var resp = fileHandler.downloadContent("file-1", "bytes=0-99");
    assertEquals(206, resp.getStatus());
    assertEquals("bytes 0-99/1024", resp.getHeaderString("Content-Range"));
    assertEquals("100", resp.getHeaderString("Content-Length"));
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

  // ─── collection handler ────────────────────────────────────────────────────

  private CollectionReference collRef() {
    var ref = new CollectionReference(20L);
    ref.setAppId("coll-ref-1");
    ref.setName("linked collection");
    ref.setRelationship("relatedTo");
    ref.setDataObject(parent);
    ref.setShepardId(20L);
    Collection referencedColl = new Collection(99L);
    referencedColl.setShepardId(99L);
    referencedColl.setAppId("coll-app-99");
    ref.setReferencedCollection(referencedColl);
    return ref;
  }

  @Test
  void collection_kindAndOwns() {
    assertEquals("collection", collectionHandler.kind());
    assertTrue(collectionHandler.owns(collRef()));
    assertFalse(collectionHandler.owns(uriRef()));
  }

  @Test
  void collection_toIO_flattensPayload() {
    var io = collectionHandler.toIO(collRef());
    assertEquals("collection", io.getKind());
    assertEquals("coll-app-99", io.getPayload().get("referencedCollectionAppId"));
    assertEquals("relatedTo", io.getPayload().get("relationship"));
  }

  @Test
  void collection_toIO_nullReferencedCollection_yieldsNullAppId() {
    var ref = collRef();
    ref.setReferencedCollection(null);
    var io = collectionHandler.toIO(ref);
    assertNull(io.getPayload().get("referencedCollectionAppId"));
  }

  @Test
  void collection_findByAppId_delegates() {
    when(collectionReferenceService.findByAppId("coll-ref-1")).thenReturn(collRef());
    var found = collectionHandler.findByAppId("coll-ref-1");
    assertTrue(found instanceof CollectionReference);
  }

  @Test
  void collection_patch_delegates() {
    when(collectionReferenceService.patchReferenceByAppId(eq("coll-ref-1"), any())).thenReturn(collRef());
    var io = collectionHandler.patch("coll-ref-1", Map.of("relationship", "seeAlso"));
    assertEquals("collection", io.getKind());
  }

  @Test
  void collection_delete_delegates() {
    when(collectionReferenceService.findByAppId("coll-ref-1")).thenReturn(collRef());
    collectionHandler.delete("coll-ref-1");
    verify(collectionReferenceService).deleteReference(eq(1L), eq(101L), eq(20L));
  }

  @Test
  void collection_delete_missing_throwsNotFound() {
    when(collectionReferenceService.findByAppId("coll-ref-1")).thenReturn(null);
    assertThrows(NotFoundException.class, () -> collectionHandler.delete("coll-ref-1"));
  }

  @Test
  void collection_list_delegates() {
    when(dataObjectDAO.findByAppId(DO_APP_ID)).thenReturn(parent);
    when(collectionReferenceService.getAllReferencesByDataObjectId(eq(1L), eq(101L), any()))
      .thenReturn(List.of(collRef()));
    var out = collectionHandler.listByDataObject(DO_APP_ID, null);
    assertEquals(1, out.size());
    assertEquals("collection", out.get(0).getKind());
  }

  @Test
  void collection_create_usesAppId() {
    Collection referencedColl = new Collection(99L);
    referencedColl.setShepardId(99L);
    referencedColl.setAppId("coll-app-99");
    when(dataObjectDAO.findByAppId(DO_APP_ID)).thenReturn(parent);
    when(collectionDAO.findByAppId("coll-app-99")).thenReturn(referencedColl);
    when(collectionReferenceService.createReference(eq(1L), eq(101L), any())).thenReturn(collRef());
    var io = collectionHandler.create(DO_APP_ID, Map.of("name", "link", "referencedCollectionAppId", "coll-app-99"));
    assertEquals("collection", io.getKind());
    verify(collectionReferenceService).createReference(eq(1L), eq(101L), any());
  }

  @Test
  void collection_create_missingAppId_throws400() {
    when(dataObjectDAO.findByAppId(DO_APP_ID)).thenReturn(parent);
    assertThrows(BadRequestException.class, () -> collectionHandler.create(DO_APP_ID, Map.of("name", "link")));
  }

  @Test
  void collection_create_unknownCollection_throws404() {
    when(dataObjectDAO.findByAppId(DO_APP_ID)).thenReturn(parent);
    when(collectionDAO.findByAppId("no-such")).thenReturn(null);
    assertThrows(NotFoundException.class, () -> collectionHandler.create(DO_APP_ID, Map.of("name", "x", "referencedCollectionAppId", "no-such")));
  }

  // ─── dataobject handler ────────────────────────────────────────────────────

  private DataObjectReference doRef() {
    var ref = new DataObjectReference(30L);
    ref.setAppId("do-ref-1");
    ref.setName("linked dataobject");
    ref.setRelationship("uses");
    ref.setDataObject(parent);
    ref.setShepardId(30L);
    DataObject referencedDO = new DataObject(200L);
    referencedDO.setShepardId(200L);
    referencedDO.setAppId("do-app-200");
    ref.setReferencedDataObject(referencedDO);
    return ref;
  }

  @Test
  void dataobject_kindAndOwns() {
    assertEquals("dataobject", dataObjectRefHandler.kind());
    assertTrue(dataObjectRefHandler.owns(doRef()));
    assertFalse(dataObjectRefHandler.owns(uriRef()));
  }

  @Test
  void dataobject_toIO_flattensPayload() {
    var io = dataObjectRefHandler.toIO(doRef());
    assertEquals("dataobject", io.getKind());
    assertEquals("do-app-200", io.getPayload().get("referencedDataObjectAppId"));
    assertEquals("uses", io.getPayload().get("relationship"));
  }

  @Test
  void dataobject_toIO_nullReferencedDataObject_yieldsNullAppId() {
    var ref = doRef();
    ref.setReferencedDataObject(null);
    var io = dataObjectRefHandler.toIO(ref);
    assertNull(io.getPayload().get("referencedDataObjectAppId"));
  }

  @Test
  void dataobject_findByAppId_delegates() {
    when(dataObjectReferenceService.findByAppId("do-ref-1")).thenReturn(doRef());
    var found = dataObjectRefHandler.findByAppId("do-ref-1");
    assertTrue(found instanceof DataObjectReference);
  }

  @Test
  void dataobject_patch_delegates() {
    when(dataObjectReferenceService.patchReferenceByAppId(eq("do-ref-1"), any())).thenReturn(doRef());
    var io = dataObjectRefHandler.patch("do-ref-1", Map.of("relationship", "linkedTo"));
    assertEquals("dataobject", io.getKind());
  }

  @Test
  void dataobject_delete_delegates() {
    when(dataObjectReferenceService.findByAppId("do-ref-1")).thenReturn(doRef());
    dataObjectRefHandler.delete("do-ref-1");
    verify(dataObjectReferenceService).deleteReference(eq(1L), eq(101L), eq(30L));
  }

  @Test
  void dataobject_delete_missing_throwsNotFound() {
    when(dataObjectReferenceService.findByAppId("do-ref-1")).thenReturn(null);
    assertThrows(NotFoundException.class, () -> dataObjectRefHandler.delete("do-ref-1"));
  }

  @Test
  void dataobject_list_delegates() {
    when(dataObjectDAO.findByAppId(DO_APP_ID)).thenReturn(parent);
    when(dataObjectReferenceService.getAllReferencesByDataObjectId(eq(1L), eq(101L), any()))
      .thenReturn(List.of(doRef()));
    var out = dataObjectRefHandler.listByDataObject(DO_APP_ID, null);
    assertEquals(1, out.size());
    assertEquals("dataobject", out.get(0).getKind());
  }

  @Test
  void dataobject_create_usesAppId() {
    DataObject referencedDO = new DataObject(200L);
    referencedDO.setShepardId(200L);
    referencedDO.setAppId("do-app-200");
    when(dataObjectDAO.findByAppId(DO_APP_ID)).thenReturn(parent);
    when(dataObjectDAO.findByAppId("do-app-200")).thenReturn(referencedDO);
    when(dataObjectReferenceService.createReference(eq(1L), eq(101L), any())).thenReturn(doRef());
    var io = dataObjectRefHandler.create(DO_APP_ID, Map.of("name", "link", "referencedDataObjectAppId", "do-app-200"));
    assertEquals("dataobject", io.getKind());
    verify(dataObjectReferenceService).createReference(eq(1L), eq(101L), any());
  }

  @Test
  void dataobject_create_missingAppId_throws400() {
    when(dataObjectDAO.findByAppId(DO_APP_ID)).thenReturn(parent);
    assertThrows(BadRequestException.class, () -> dataObjectRefHandler.create(DO_APP_ID, Map.of("name", "link")));
  }

  @Test
  void dataobject_create_unknownDataObject_throws404() {
    when(dataObjectDAO.findByAppId(DO_APP_ID)).thenReturn(parent);
    when(dataObjectDAO.findByAppId("no-such")).thenReturn(null);
    assertThrows(NotFoundException.class, () -> dataObjectRefHandler.create(DO_APP_ID, Map.of("name", "x", "referencedDataObjectAppId", "no-such")));
  }

  // MISSING-V2-APPID-IN-REFLISTS slice 3 — enriched payload ──────────────────

  @Test
  void dataobject_toIO_enrichesNameAndCollection_whenRefDoFound() {
    Collection refColl = new Collection(500L);
    refColl.setAppId("coll-app-500");
    refColl.setName("Reference Collection");
    DataObject fullRefDo = new DataObject(200L);
    fullRefDo.setAppId("do-app-200");
    fullRefDo.setName("Referenced DO");
    fullRefDo.setCollection(refColl);
    when(dataObjectDAO.findByAppId("do-app-200")).thenReturn(fullRefDo);

    var io = dataObjectRefHandler.toIO(doRef());

    assertEquals("Referenced DO", io.getPayload().get("referencedDataObjectName"));
    assertEquals("coll-app-500", io.getPayload().get("referencedCollectionAppId"));
    assertEquals("Reference Collection", io.getPayload().get("referencedCollectionName"));
  }

  @Test
  void dataobject_toIO_enrichmentFieldsNull_whenRefDoNotFound() {
    when(dataObjectDAO.findByAppId("do-app-200")).thenReturn(null);

    var io = dataObjectRefHandler.toIO(doRef());

    assertNull(io.getPayload().get("referencedDataObjectName"));
    assertNull(io.getPayload().get("referencedCollectionAppId"));
    assertNull(io.getPayload().get("referencedCollectionName"));
    // Core fields still present
    assertEquals("do-app-200", io.getPayload().get("referencedDataObjectAppId"));
  }

  @Test
  void dataobject_toIO_enrichmentSkipped_whenReferencedDoHasNoAppId() {
    var ref = doRef();
    DataObject stubNoAppId = new DataObject(200L);
    // appId intentionally not set
    ref.setReferencedDataObject(stubNoAppId);

    var io = dataObjectRefHandler.toIO(ref);

    assertNull(io.getPayload().get("referencedDataObjectAppId"));
    assertNull(io.getPayload().get("referencedDataObjectName"));
  }
}
