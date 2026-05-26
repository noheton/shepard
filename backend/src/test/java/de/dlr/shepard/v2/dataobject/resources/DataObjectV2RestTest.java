package de.dlr.shepard.v2.dataobject.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.data.timeseries.repositories.TimeseriesDataPointRepository;
import de.dlr.shepard.v2.dataobject.io.DataObjectDetailV2IO;
import de.dlr.shepard.v2.dataobject.io.DataObjectListItemV2IO;
import de.dlr.shepard.v2.dataobject.io.DataObjectSummaryIO;
import jakarta.validation.Validator;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * L2d Phase A.2 — unit tests for the {@code /v2/collections/{appId}/data-objects}
 * resource. Same six-shape grid as {@code CollectionV2RestTest}: 401, 403,
 * 404, plus 200 / 201 / 204 happy paths.
 */
class DataObjectV2RestTest {

  static final String COLL_APP_ID = "018f9c5a-7e26-7000-a000-000000000010";
  static final String DO_APP_ID = "018f9c5a-7e26-7000-a000-000000000020";
  static final long COLL_OGM_ID = 42L;
  static final long DO_OGM_ID = 84L;
  static final String CALLER = "alice";

  /** Build a minimal DataObject with a stub Collection so DataObjectIO(d) does not NPE. */
  static DataObject makeDataObject(long ogmId, String appId, String name) {
    Collection coll = new Collection();
    coll.setShepardId(COLL_OGM_ID);
    DataObject d = new DataObject();
    d.setShepardId(ogmId);
    d.setAppId(appId);
    d.setName(name);
    d.setCollection(coll);
    return d;
  }

  @Mock
  DataObjectService dataObjectService;

  @Mock
  DataObjectDAO dataObjectDAO;

  @Mock
  TimeseriesDataPointRepository timeseriesDataPointRepository;

  @Mock
  PermissionsService permissionsService;

  @Mock
  EntityIdResolver entityIdResolver;

  @Mock
  Validator validator;

  @Mock
  SecurityContext securityContext;

  @Mock
  Principal principal;

  DataObjectV2Rest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new DataObjectV2Rest();
    resource.dataObjectService = dataObjectService;
    resource.dataObjectDAO = dataObjectDAO;
    resource.permissionsService = permissionsService;
    resource.entityIdResolver = entityIdResolver;
    resource.validator = validator;
    resource.objectMapper = new ObjectMapper();
    resource.timeseriesDataPointRepository = timeseriesDataPointRepository;
    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
    when(validator.validate(any())).thenReturn(Collections.emptySet());
    // Default: batch count query returns empty map (counts default to 0).
    when(dataObjectDAO.findRefCountsByAppIds(any())).thenReturn(Collections.emptyMap());
    // Default: time-bounds DAO returns empty map (no TS containers).
    when(dataObjectDAO.findTsContainerIdsByDataObjectAppIds(any())).thenReturn(Collections.emptyMap());
  }

  // ── list ──────────────────────────────────────────────────────────────────

  @Test
  void listReturns404WhenCollectionUnknown() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenThrow(new NotFoundException());
    Response r = resource.list(COLL_APP_ID, null, 0, 50, null, securityContext);
    assertEquals(404, r.getStatus());
    verify(dataObjectService, never()).getAllDataObjectsByShepardIds(anyLong(), any(), any());
  }

  @Test
  void listReturns403WhenNoReadOnCollection() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(false);
    Response r = resource.list(COLL_APP_ID, null, 0, 50, null, securityContext);
    assertEquals(403, r.getStatus());
  }

  @Test
  void listReturns200WithRows() {
    DataObject d = makeDataObject(DO_OGM_ID, DO_APP_ID, "sensor-track-1");
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(true);
    when(dataObjectService.getAllDataObjectsByShepardIds(eq(COLL_OGM_ID), any(), eq(null)))
      .thenReturn(List.of(d));

    Response r = resource.list(COLL_APP_ID, null, 0, 50, null, securityContext);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    List<DataObjectListItemV2IO> body = (List<DataObjectListItemV2IO>) r.getEntity();
    assertEquals(1, body.size());
    assertEquals(DO_APP_ID, body.get(0).getAppId());
  }

  @Test
  void listIncludesRefCounts() {
    DataObject d = makeDataObject(DO_OGM_ID, DO_APP_ID, "sensor-track-1");
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(true);
    when(dataObjectService.getAllDataObjectsByShepardIds(eq(COLL_OGM_ID), any(), eq(null)))
      .thenReturn(List.of(d));
    // Simulate the batch count query returning specific counts for this DO.
    when(dataObjectDAO.findRefCountsByAppIds(List.of(DO_APP_ID)))
      .thenReturn(Map.of(DO_APP_ID, new long[] { 3L, 5L, 2L }));

    Response r = resource.list(COLL_APP_ID, null, 0, 50, null, securityContext);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    List<DataObjectListItemV2IO> body = (List<DataObjectListItemV2IO>) r.getEntity();
    assertEquals(1, body.size());
    DataObjectListItemV2IO item = body.get(0);
    assertEquals(DO_APP_ID, item.getAppId());
    assertEquals(3L, item.getTimeseriesCount());
    assertEquals(5L, item.getFileCount());
    assertEquals(2L, item.getStructuredDataCount());
  }

  @Test
  void listIncludesTimeBoundsWhenRequested() {
    DataObject d = makeDataObject(DO_OGM_ID, DO_APP_ID, "sensor-track-1");
    long containerNeo4jId = 77L;
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(true);
    when(dataObjectService.getAllDataObjectsByShepardIds(eq(COLL_OGM_ID), any(), eq(null)))
      .thenReturn(List.of(d));
    when(dataObjectDAO.findTsContainerIdsByDataObjectAppIds(List.of(DO_APP_ID)))
      .thenReturn(Map.of(DO_APP_ID, List.of(containerNeo4jId)));
    when(timeseriesDataPointRepository.findTimeBoundsByContainerIds(List.of(containerNeo4jId)))
      .thenReturn(Map.of(containerNeo4jId, new long[] { 1_000_000L, 9_000_000L }));

    Response r = resource.list(COLL_APP_ID, null, 0, 50, "time-bounds", securityContext);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    List<DataObjectListItemV2IO> body = (List<DataObjectListItemV2IO>) r.getEntity();
    assertEquals(1, body.size());
    DataObjectListItemV2IO item = body.get(0);
    assertEquals(1_000_000L, item.getTimeBoundsStart());
    assertEquals(9_000_000L, item.getTimeBoundsEnd());
  }

  @Test
  void listTimeBoundsAbsentWhenNotRequested() {
    DataObject d = makeDataObject(DO_OGM_ID, DO_APP_ID, "sensor-track-1");
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(true);
    when(dataObjectService.getAllDataObjectsByShepardIds(eq(COLL_OGM_ID), any(), eq(null)))
      .thenReturn(List.of(d));

    Response r = resource.list(COLL_APP_ID, null, 0, 50, null, securityContext);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    List<DataObjectListItemV2IO> body = (List<DataObjectListItemV2IO>) r.getEntity();
    assertEquals(1, body.size());
    assertNull(body.get(0).getTimeBoundsStart());
    assertNull(body.get(0).getTimeBoundsEnd());
  }

  // ── get ───────────────────────────────────────────────────────────────────

  @Test
  void getReturns404WhenDataObjectUnknown() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenThrow(new NotFoundException());
    Response r = resource.get(COLL_APP_ID, DO_APP_ID, securityContext);
    assertEquals(404, r.getStatus());
    verify(permissionsService, never()).isAccessAllowedForDataObjectAppId(any(), any(), any());
  }

  @Test
  void getReturns403WhenNoRead() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenReturn(DO_OGM_ID);
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(DO_APP_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(false);
    Response r = resource.get(COLL_APP_ID, DO_APP_ID, securityContext);
    assertEquals(403, r.getStatus());
  }

  @Test
  void getReturns200WithDataObject() {
    DataObject d = makeDataObject(DO_OGM_ID, DO_APP_ID, "sensor-track-1");
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenReturn(DO_OGM_ID);
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(DO_APP_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(true);
    when(dataObjectService.getDataObject(COLL_OGM_ID, DO_OGM_ID)).thenReturn(d);

    Response r = resource.get(COLL_APP_ID, DO_APP_ID, securityContext);

    assertEquals(200, r.getStatus());
    DataObjectIO io = (DataObjectIO) r.getEntity();
    assertNotNull(io);
    assertEquals(DO_APP_ID, io.getAppId());
  }

  // ── create ────────────────────────────────────────────────────────────────

  @Test
  void createReturns404WhenCollectionUnknown() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenThrow(new NotFoundException());
    DataObjectIO body = new DataObjectIO();
    body.setName("new do");
    Response r = resource.create(COLL_APP_ID, body, securityContext);
    assertEquals(404, r.getStatus());
  }

  @Test
  void createReturns403WhenNoWriteOnCollection() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Write), eq(CALLER)))
      .thenReturn(false);
    DataObjectIO body = new DataObjectIO();
    body.setName("new do");
    Response r = resource.create(COLL_APP_ID, body, securityContext);
    assertEquals(403, r.getStatus());
    verify(dataObjectService, never()).createDataObject(anyLong(), any());
  }

  @Test
  void createReturns201WithMintedAppId() {
    DataObject created = makeDataObject(99L, "018f9c5a-9999-7000-a000-000000000099", "new do");
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Write), eq(CALLER)))
      .thenReturn(true);
    DataObjectIO body = new DataObjectIO();
    body.setName("new do");
    when(dataObjectService.createDataObject(eq(COLL_OGM_ID), eq(body))).thenReturn(created);

    Response r = resource.create(COLL_APP_ID, body, securityContext);

    assertEquals(201, r.getStatus());
    DataObjectIO io = (DataObjectIO) r.getEntity();
    assertEquals("018f9c5a-9999-7000-a000-000000000099", io.getAppId());
  }

  // ── patch ─────────────────────────────────────────────────────────────────

  @Test
  void patchReturns400WhenBodyIsNotAnObject() {
    assertThrows(InvalidBodyException.class, () ->
      resource.patch(COLL_APP_ID, DO_APP_ID, JsonNodeFactory.instance.arrayNode(), securityContext)
    );
  }

  @Test
  void patchReturns404WhenDataObjectUnknown() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenThrow(new NotFoundException());
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.put("description", "x");
    Response r = resource.patch(COLL_APP_ID, DO_APP_ID, body, securityContext);
    assertEquals(404, r.getStatus());
  }

  @Test
  void patchReturns403WhenNoWrite() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenReturn(DO_OGM_ID);
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(DO_APP_ID), eq(AccessType.Write), eq(CALLER)))
      .thenReturn(false);
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.put("description", "x");
    Response r = resource.patch(COLL_APP_ID, DO_APP_ID, body, securityContext);
    assertEquals(403, r.getStatus());
    verify(dataObjectService, never()).updateDataObject(anyLong(), anyLong(), any());
  }

  @Test
  void patchReturns200WithMergedBody() {
    DataObject existing = makeDataObject(DO_OGM_ID, DO_APP_ID, "sensor-track-1");
    existing.setDescription("old");

    DataObject updated = makeDataObject(DO_OGM_ID, DO_APP_ID, "sensor-track-1");
    updated.setDescription("new");

    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.put("description", "new");

    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenReturn(DO_OGM_ID);
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(DO_APP_ID), eq(AccessType.Write), eq(CALLER)))
      .thenReturn(true);
    when(dataObjectService.getDataObject(COLL_OGM_ID, DO_OGM_ID)).thenReturn(existing);
    when(dataObjectService.updateDataObject(eq(COLL_OGM_ID), eq(DO_OGM_ID), any())).thenReturn(updated);

    Response r = resource.patch(COLL_APP_ID, DO_APP_ID, body, securityContext);

    assertEquals(200, r.getStatus());
    DataObjectIO io = (DataObjectIO) r.getEntity();
    assertEquals("new", io.getDescription());
  }

  // ── delete ────────────────────────────────────────────────────────────────

  @Test
  void deleteReturns404WhenDataObjectUnknown() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenThrow(new NotFoundException());
    Response r = resource.delete(COLL_APP_ID, DO_APP_ID, securityContext);
    assertEquals(404, r.getStatus());
    verify(dataObjectService, never()).deleteDataObject(anyLong(), anyLong());
  }

  @Test
  void deleteReturns403WhenNoWrite() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenReturn(DO_OGM_ID);
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(DO_APP_ID), eq(AccessType.Write), eq(CALLER)))
      .thenReturn(false);
    Response r = resource.delete(COLL_APP_ID, DO_APP_ID, securityContext);
    assertEquals(403, r.getStatus());
    verify(dataObjectService, never()).deleteDataObject(anyLong(), anyLong());
  }

  @Test
  void deleteReturns204OnSuccess() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenReturn(DO_OGM_ID);
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(DO_APP_ID), eq(AccessType.Write), eq(CALLER)))
      .thenReturn(true);
    Response r = resource.delete(COLL_APP_ID, DO_APP_ID, securityContext);
    assertEquals(204, r.getStatus());
    verify(dataObjectService).deleteDataObject(COLL_OGM_ID, DO_OGM_ID);
  }

  // ── REF-1: get() returns DataObjectDetailV2IO ─────────────────────────────

  @Test
  void getReturnsDataObjectDetailV2IO() {
    DataObject d = makeDataObject(DO_OGM_ID, DO_APP_ID, "sensor-track-1");
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenReturn(DO_OGM_ID);
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(DO_APP_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(true);
    when(dataObjectService.getDataObject(COLL_OGM_ID, DO_OGM_ID)).thenReturn(d);

    Response r = resource.get(COLL_APP_ID, DO_APP_ID, securityContext);

    assertEquals(200, r.getStatus());
    // Response must be a DataObjectDetailV2IO (subtype of DataObjectIO).
    assertNotNull(r.getEntity());
    DataObjectDetailV2IO detail = (DataObjectDetailV2IO) r.getEntity();
    assertEquals(DO_APP_ID, detail.getAppId());
    // No references on the stub DataObject → containers all empty.
    assertNotNull(detail.getContainers());
    assertNotNull(detail.getContainers().getTimeseries());
    assertNotNull(detail.getContainers().getFiles());
    assertNotNull(detail.getContainers().getStructuredData());
    assertEquals(0, detail.getContainers().getTimeseries().size());
    assertEquals(0, detail.getContainers().getFiles().size());
    assertEquals(0, detail.getContainers().getStructuredData().size());
    // No predecessor/successor/parent/children on stub.
    assertEquals(0, detail.getPredecessorSummaries().size());
    assertEquals(0, detail.getSuccessorSummaries().size());
    assertEquals(0, detail.getChildSummaries().size());
    assertNull(detail.getParentSummary());
  }

  // ── ANC-1: predecessors / successors / children ───────────────────────────

  @Test
  void predecessorsReturns404WhenDataObjectUnknown() {
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenThrow(new NotFoundException());
    Response r = resource.predecessors(COLL_APP_ID, DO_APP_ID, securityContext);
    assertEquals(404, r.getStatus());
  }

  @Test
  void predecessorsReturns403WhenNoRead() {
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenReturn(DO_OGM_ID);
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(DO_APP_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(false);
    Response r = resource.predecessors(COLL_APP_ID, DO_APP_ID, securityContext);
    assertEquals(403, r.getStatus());
  }

  @Test
  void predecessorsReturns200WithSummaries() {
    DataObject pred = makeDataObject(11L, "018f-pred-0011", "pred-run");
    DataObject d = makeDataObject(DO_OGM_ID, DO_APP_ID, "sensor-track-1");
    d.getPredecessors().add(pred);

    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenReturn(DO_OGM_ID);
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(DO_APP_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(true);
    when(dataObjectService.getDataObject(COLL_OGM_ID, DO_OGM_ID)).thenReturn(d);

    Response r = resource.predecessors(COLL_APP_ID, DO_APP_ID, securityContext);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    List<DataObjectSummaryIO> body = (List<DataObjectSummaryIO>) r.getEntity();
    assertEquals(1, body.size());
    assertEquals("018f-pred-0011", body.get(0).getAppId());
  }

  @Test
  void successorsReturns200WithSummaries() {
    DataObject succ = makeDataObject(22L, "018f-succ-0022", "next-run");
    DataObject d = makeDataObject(DO_OGM_ID, DO_APP_ID, "sensor-track-1");
    d.getSuccessors().add(succ);

    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenReturn(DO_OGM_ID);
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(DO_APP_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(true);
    when(dataObjectService.getDataObject(COLL_OGM_ID, DO_OGM_ID)).thenReturn(d);

    Response r = resource.successors(COLL_APP_ID, DO_APP_ID, securityContext);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    List<DataObjectSummaryIO> body = (List<DataObjectSummaryIO>) r.getEntity();
    assertEquals(1, body.size());
    assertEquals("018f-succ-0022", body.get(0).getAppId());
  }

  @Test
  void childrenReturns200WithSummaries() {
    DataObject child = makeDataObject(33L, "018f-child-0033", "sub-run");
    DataObject d = makeDataObject(DO_OGM_ID, DO_APP_ID, "sensor-track-1");
    d.getChildren().add(child);

    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenReturn(DO_OGM_ID);
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(DO_APP_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(true);
    when(dataObjectService.getDataObject(COLL_OGM_ID, DO_OGM_ID)).thenReturn(d);

    Response r = resource.children(COLL_APP_ID, DO_APP_ID, securityContext);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    List<DataObjectSummaryIO> body = (List<DataObjectSummaryIO>) r.getEntity();
    assertEquals(1, body.size());
    assertEquals("018f-child-0033", body.get(0).getAppId());
  }

  // ── ANC-1: predecessor-chain / successor-chain ────────────────────────────

  @Test
  void predecessorChainReturns404WhenDataObjectUnknown() {
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenThrow(new NotFoundException());
    Response r = resource.predecessorChain(COLL_APP_ID, DO_APP_ID, 5, securityContext);
    assertEquals(404, r.getStatus());
    verify(dataObjectDAO, never()).findPredecessorChain(any(), anyInt());
  }

  @Test
  void predecessorChainReturns403WhenNoRead() {
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenReturn(DO_OGM_ID);
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(DO_APP_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(false);
    Response r = resource.predecessorChain(COLL_APP_ID, DO_APP_ID, 5, securityContext);
    assertEquals(403, r.getStatus());
    verify(dataObjectDAO, never()).findPredecessorChain(any(), anyInt());
  }

  @Test
  void predecessorChainReturns200WithRows() {
    DataObject p1 = makeDataObject(10L, "018f-pred-0010", "run-A");
    DataObject p2 = makeDataObject(9L, "018f-pred-0009", "run-B");

    when(entityIdResolver.resolveLong(DO_APP_ID)).thenReturn(DO_OGM_ID);
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(DO_APP_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(true);
    when(dataObjectDAO.findPredecessorChain(eq(DO_APP_ID), eq(5)))
      .thenReturn(List.of(p1, p2));

    Response r = resource.predecessorChain(COLL_APP_ID, DO_APP_ID, 5, securityContext);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    List<DataObjectSummaryIO> body = (List<DataObjectSummaryIO>) r.getEntity();
    assertEquals(2, body.size());
    assertEquals("018f-pred-0010", body.get(0).getAppId());
    assertEquals("018f-pred-0009", body.get(1).getAppId());
  }

  @Test
  void successorChainReturns200WithRows() {
    DataObject s1 = makeDataObject(100L, "018f-succ-0100", "run-X");

    when(entityIdResolver.resolveLong(DO_APP_ID)).thenReturn(DO_OGM_ID);
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(DO_APP_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(true);
    when(dataObjectDAO.findSuccessorChain(eq(DO_APP_ID), eq(10)))
      .thenReturn(List.of(s1));

    Response r = resource.successorChain(COLL_APP_ID, DO_APP_ID, 10, securityContext);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    List<DataObjectSummaryIO> body = (List<DataObjectSummaryIO>) r.getEntity();
    assertEquals(1, body.size());
    assertEquals("018f-succ-0100", body.get(0).getAppId());
  }

  @Test
  void successorChainReturns404WhenDataObjectUnknown() {
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenThrow(new NotFoundException());
    Response r = resource.successorChain(COLL_APP_ID, DO_APP_ID, 10, securityContext);
    assertEquals(404, r.getStatus());
    verify(dataObjectDAO, never()).findSuccessorChain(any(), anyInt());
  }
}
