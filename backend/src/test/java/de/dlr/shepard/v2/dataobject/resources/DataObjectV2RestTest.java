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

import com.fasterxml.jackson.core.type.TypeReference;
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
import de.dlr.shepard.v2.dataobject.io.CreateDataObjectV2IO;
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

  /** Test ObjectMapper used to deserialise the list-endpoint JSON body
   *  (DB-OPT5 changed the entity from List<IO> to a serialised JSON string). */
  static final ObjectMapper TEST_MAPPER = new ObjectMapper();

  /** Parse the v2 list-endpoint body (now a JSON String — DB-OPT5) back
   *  into typed IO objects so the existing assertion shape works unchanged. */
  static List<DataObjectListItemV2IO> parseListBody(Response r) {
    Object entity = r.getEntity();
    if (entity instanceof String s) {
      try {
        return TEST_MAPPER.readValue(s, new TypeReference<List<DataObjectListItemV2IO>>() {});
      } catch (Exception e) {
        throw new RuntimeException("Failed to parse list body JSON", e);
      }
    }
    // Fall back to the legacy shape so any non-list endpoint tests keep working.
    @SuppressWarnings("unchecked")
    List<DataObjectListItemV2IO> cast = (List<DataObjectListItemV2IO>) entity;
    return cast;
  }

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
    // Default: container-by-appId Cypher query returns empty-row map so
    // buildContainersFromCypher() does not NPE on a null row.
    when(dataObjectDAO.findContainersByDataObjectAppId(any())).thenReturn(Collections.emptyMap());
    // Default: total-count query returns 0 (long primitive — Mockito would also default to 0,
    // but explicit stub avoids accidental any()-vs-specific-arg conflicts in count tests).
    when(dataObjectDAO.countByCollectionByShepardIds(anyLong(), any())).thenReturn(0L);
  }

  // ── list ──────────────────────────────────────────────────────────────────

  @Test
  void listReturns404WhenCollectionUnknown() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenThrow(new NotFoundException());
    Response r = resource.list(COLL_APP_ID, null, null, 0, 50, null, null, securityContext);
    assertEquals(404, r.getStatus());
    verify(dataObjectService, never()).getAllDataObjectsByShepardIds(anyLong(), any(), any());
  }

  @Test
  void listReturns403WhenNoReadOnCollection() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(false);
    Response r = resource.list(COLL_APP_ID, null, null, 0, 50, null, null, securityContext);
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

    Response r = resource.list(COLL_APP_ID, null, null, 0, 50, null, null, securityContext);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    List<DataObjectListItemV2IO> body = parseListBody(r);
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

    Response r = resource.list(COLL_APP_ID, null, null, 0, 50, null, null, securityContext);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    List<DataObjectListItemV2IO> body = parseListBody(r);
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

    Response r = resource.list(COLL_APP_ID, null, null, 0, 50, "time-bounds", null, securityContext);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    List<DataObjectListItemV2IO> body = parseListBody(r);
    assertEquals(1, body.size());
    DataObjectListItemV2IO item = body.get(0);
    assertEquals(1_000_000L, item.getTimeBoundsStart());
    assertEquals(9_000_000L, item.getTimeBoundsEnd());
  }

  @Test
  void listReturnsContentRangeHeader() {
    // UX-DOPANEL-TOTAL-COUNT: list() must emit Content-Range and X-Total-Count headers.
    DataObject d = makeDataObject(DO_OGM_ID, DO_APP_ID, "sensor-track-1");
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(true);
    when(dataObjectService.getAllDataObjectsByShepardIds(eq(COLL_OGM_ID), any(), eq(null)))
      .thenReturn(List.of(d));
    when(dataObjectDAO.countByCollectionByShepardIds(eq(COLL_OGM_ID), any())).thenReturn(8514L);

    // page=3, size=25 → firstIndex=75, lastIndex=75 (1 item)
    Response r = resource.list(COLL_APP_ID, null, null, 3, 25, null, null, securityContext);

    assertEquals(200, r.getStatus());
    // Content-Range must be present with format "dataobjects firstIndex-lastIndex/total"
    String contentRange = (String) r.getHeaders().getFirst("Content-Range");
    assertNotNull(contentRange);
    assertEquals("dataobjects 75-75/8514", contentRange);
    // X-Total-Count convenience header
    Object xTotalCount = r.getHeaders().getFirst("X-Total-Count");
    assertNotNull(xTotalCount);
    assertEquals(8514L, ((Number) xTotalCount).longValue());
  }

  @Test
  void listContentRangeEmptyPageWhenNoResults() {
    // UX-DOPANEL-TOTAL-COUNT: when no items are returned lastIndex must be -1.
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(true);
    when(dataObjectService.getAllDataObjectsByShepardIds(eq(COLL_OGM_ID), any(), eq(null)))
      .thenReturn(Collections.emptyList());
    when(dataObjectDAO.countByCollectionByShepardIds(eq(COLL_OGM_ID), any())).thenReturn(0L);

    Response r = resource.list(COLL_APP_ID, null, null, 0, 25, null, null, securityContext);

    assertEquals(200, r.getStatus());
    String contentRange = (String) r.getHeaders().getFirst("Content-Range");
    assertNotNull(contentRange);
    assertEquals("dataobjects 0--1/0", contentRange);
    Object xTotalCount = r.getHeaders().getFirst("X-Total-Count");
    assertNotNull(xTotalCount);
    assertEquals(0L, ((Number) xTotalCount).longValue());
  }

  @Test
  void listTimeBoundsAbsentWhenNotRequested() {
    DataObject d = makeDataObject(DO_OGM_ID, DO_APP_ID, "sensor-track-1");
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(true);
    when(dataObjectService.getAllDataObjectsByShepardIds(eq(COLL_OGM_ID), any(), eq(null)))
      .thenReturn(List.of(d));

    Response r = resource.list(COLL_APP_ID, null, null, 0, 50, null, null, securityContext);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    List<DataObjectListItemV2IO> body = parseListBody(r);
    assertEquals(1, body.size());
    assertNull(body.get(0).getTimeBoundsStart());
    assertNull(body.get(0).getTimeBoundsEnd());
  }

  // ── get ───────────────────────────────────────────────────────────────────

  @Test
  void getReturns404WhenDataObjectUnknown() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenThrow(new NotFoundException());
    Response r = resource.get(COLL_APP_ID, DO_APP_ID, null, securityContext);
    assertEquals(404, r.getStatus());
    verify(permissionsService, never()).isAccessAllowedForDataObjectAppId(any(), any(), any());
  }

  @Test
  void getReturns403WhenNoRead() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenReturn(DO_OGM_ID);
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(DO_APP_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(false);
    Response r = resource.get(COLL_APP_ID, DO_APP_ID, null, securityContext);
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

    Response r = resource.get(COLL_APP_ID, DO_APP_ID, null, securityContext);

    assertEquals(200, r.getStatus());
    DataObjectIO io = (DataObjectIO) r.getEntity();
    assertNotNull(io);
    assertEquals(DO_APP_ID, io.getAppId());
  }

  // ── create ────────────────────────────────────────────────────────────────

  @Test
  void createReturns404WhenCollectionUnknown() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenThrow(new NotFoundException());
    CreateDataObjectV2IO body = new CreateDataObjectV2IO();
    body.setName("new do");
    Response r = resource.create(COLL_APP_ID, body, null, securityContext);
    assertEquals(404, r.getStatus());
  }

  @Test
  void createReturns403WhenNoWriteOnCollection() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Write), eq(CALLER)))
      .thenReturn(false);
    CreateDataObjectV2IO body = new CreateDataObjectV2IO();
    body.setName("new do");
    Response r = resource.create(COLL_APP_ID, body, null, securityContext);
    assertEquals(403, r.getStatus());
    verify(dataObjectService, never()).createDataObject(anyLong(), any());
  }

  @Test
  void createReturns201WithMintedAppId() {
    DataObject created = makeDataObject(99L, "018f9c5a-9999-7000-a000-000000000099", "new do");
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Write), eq(CALLER)))
      .thenReturn(true);
    CreateDataObjectV2IO body = new CreateDataObjectV2IO();
    body.setName("new do");
    when(dataObjectService.createDataObject(eq(COLL_OGM_ID), eq(body))).thenReturn(created);

    Response r = resource.create(COLL_APP_ID, body, null, securityContext);

    assertEquals(201, r.getStatus());
    DataObjectDetailV2IO io = (DataObjectDetailV2IO) r.getEntity();
    assertEquals("018f9c5a-9999-7000-a000-000000000099", io.getAppId());
  }

  // ── PROV1j: provenanceMode auto-detection via X-AI-Agent header ──────────

  @Test
  void createSetsProvenanceModeAiWhenXAiAgentHeaderPresent() {
    // PROV1j: when caller sends X-AI-Agent header and body has no provenanceMode,
    // the resource sets body.provenanceMode = "ai" before calling the service.
    DataObject created = makeDataObject(99L, "018f9c5a-9999-7000-a000-000000000099", "ai-do");
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Write), eq(CALLER)))
      .thenReturn(true);
    CreateDataObjectV2IO body = new CreateDataObjectV2IO();
    body.setName("ai-do");
    when(dataObjectService.createDataObject(eq(COLL_OGM_ID), any())).thenReturn(created);

    Response r = resource.create(COLL_APP_ID, body, "claude-mcp-client/1.0", securityContext);

    assertEquals(201, r.getStatus());
    // Body must have been mutated to "ai" before service call.
    assertEquals("ai", body.getProvenanceMode());
  }

  @Test
  void createDoesNotOverrideExplicitProvenanceModeWhenHeaderPresent() {
    // PROV1j: when caller explicitly sets provenanceMode = "collaborative",
    // the X-AI-Agent header must not override it.
    DataObject created = makeDataObject(99L, "018f9c5a-9999-7000-a000-000000000099", "collab-do");
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Write), eq(CALLER)))
      .thenReturn(true);
    CreateDataObjectV2IO body = new CreateDataObjectV2IO();
    body.setName("collab-do");
    body.setProvenanceMode("collaborative");
    when(dataObjectService.createDataObject(eq(COLL_OGM_ID), any())).thenReturn(created);

    Response r = resource.create(COLL_APP_ID, body, "some-ai-agent/2.0", securityContext);

    assertEquals(201, r.getStatus());
    // Explicit value must be preserved.
    assertEquals("collaborative", body.getProvenanceMode());
  }

  @Test
  void createLeavesProvenanceModeNullWhenNoHeaderAndNoneInBody() {
    // PROV1j: when neither X-AI-Agent header nor an explicit provenanceMode is set,
    // body.provenanceMode stays null (semantically equivalent to human-authored).
    DataObject created = makeDataObject(99L, "018f9c5a-9999-7000-a000-000000000099", "human-do");
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Write), eq(CALLER)))
      .thenReturn(true);
    CreateDataObjectV2IO body = new CreateDataObjectV2IO();
    body.setName("human-do");
    when(dataObjectService.createDataObject(eq(COLL_OGM_ID), any())).thenReturn(created);

    Response r = resource.create(COLL_APP_ID, body, null, securityContext);

    assertEquals(201, r.getStatus());
    assertNull(body.getProvenanceMode());
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

    Response r = resource.get(COLL_APP_ID, DO_APP_ID, null, securityContext);

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
    // API1: no containers → typed refAppId arrays are null (omitted from JSON).
    assertNull(detail.getTimeseriesReferenceAppIds());
    assertNull(detail.getFileReferenceAppIds());
    assertNull(detail.getStructuredDataReferenceAppIds());
  }

  /**
   * API1 — typed reference appId arrays are populated when the Cypher query
   * returns container rows carrying {@code refAppId} values.
   */
  @Test
  void getDataObject_returnsTimeseriesReferenceAppIds() {
    DataObject d = makeDataObject(DO_OGM_ID, DO_APP_ID, "tr-004");
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenReturn(DO_OGM_ID);
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(DO_APP_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(true);
    when(dataObjectService.getDataObject(COLL_OGM_ID, DO_OGM_ID)).thenReturn(d);

    // Simulate Cypher returning one TS reference with a known refAppId.
    String tsRefAppId = "01900000-0000-7000-aaaa-000000000001";
    String tsContainerAppId = "01900000-0000-7000-bbbb-000000000002";
    Map<String, Object> tsRef = Map.of(
      "refShepardId", 55L,
      "refAppId", tsRefAppId,
      "containerAppId", tsContainerAppId,
      "containerName", "vibration-ts",
      "containerId", 66L
    );
    when(dataObjectDAO.findContainersByDataObjectAppId(DO_APP_ID))
      .thenReturn(Map.of("tsRefs", List.of(tsRef), "fileRefs", List.of(), "sdRefs", List.of()));

    Response r = resource.get(COLL_APP_ID, DO_APP_ID, null, securityContext);

    assertEquals(200, r.getStatus());
    DataObjectDetailV2IO detail = (DataObjectDetailV2IO) r.getEntity();
    assertNotNull(detail.getTimeseriesReferenceAppIds());
    assertEquals(1, detail.getTimeseriesReferenceAppIds().size());
    assertEquals(tsRefAppId, detail.getTimeseriesReferenceAppIds().get(0));
    // Typed container list also correctly populated.
    assertEquals(1, detail.getContainers().getTimeseries().size());
    assertEquals(tsContainerAppId, detail.getContainers().getTimeseries().get(0).getContainerAppId());
    // File and SD arrays null (no refs).
    assertNull(detail.getFileReferenceAppIds());
    assertNull(detail.getStructuredDataReferenceAppIds());
  }

  /**
   * API1 — the legacy {@code referenceIds} long array from the frozen v1 parent
   * class must still be present in the response (backward compat guard).
   */
  @Test
  void getDataObject_legacyReferenceIdsArrayStillPresent() {
    DataObject d = makeDataObject(DO_OGM_ID, DO_APP_ID, "sensor-track-1");
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenReturn(DO_OGM_ID);
    when(permissionsService.isAccessAllowedForDataObjectAppId(eq(DO_APP_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(true);
    when(dataObjectService.getDataObject(COLL_OGM_ID, DO_OGM_ID)).thenReturn(d);

    Response r = resource.get(COLL_APP_ID, DO_APP_ID, null, securityContext);

    assertEquals(200, r.getStatus());
    DataObjectDetailV2IO detail = (DataObjectDetailV2IO) r.getEntity();
    // The legacy field from DataObjectIO must be present (non-null, empty array for
    // a stub DO with no references) — confirms v1 wire shape is intact.
    assertNotNull(detail.getReferenceIds());
  }

  // ── API2: deprecated int count fields backward-compat guard ──────────────

  /**
   * API2 backward-compatibility guard: the three deprecated {@code int} count
   * fields on {@link de.dlr.shepard.context.collection.io.DataObjectIO}
   * ({@code timeseriesReferenceCount}, {@code fileBundleCount},
   * {@code structuredDataReferenceCount}) must still be present and return
   * zero for a stub DataObject with no references.  Adding {@code @Deprecated}
   * must not change their wire-serialisation (fields are still emitted as JSON).
   */
  @Test
  void deprecatedIntCountFieldsStillPresentOnDataObjectIO() {
    // Build a minimal DataObject with no references — counts should all be 0.
    DataObject d = makeDataObject(DO_OGM_ID, DO_APP_ID, "sensor-track-1");

    de.dlr.shepard.context.collection.io.DataObjectIO io =
      new de.dlr.shepard.context.collection.io.DataObjectIO(d);

    // Deprecated but still present: must return the zero-reference default.
    assertEquals(0, io.getTimeseriesReferenceCount(),
      "timeseriesReferenceCount must still be present and return 0 (deprecated, not removed)");
    assertEquals(0, io.getFileBundleCount(),
      "fileBundleCount must still be present and return 0 (deprecated, not removed)");
    assertEquals(0, io.getStructuredDataReferenceCount(),
      "structuredDataReferenceCount must still be present and return 0 (deprecated, not removed)");
    // videoStreamReferenceCount is NOT deprecated yet (no v2 long counterpart).
    assertEquals(0, io.getVideoStreamReferenceCount(),
      "videoStreamReferenceCount must still be present and return 0");
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

  // ── DB-OPT5: ?fields= and default-trim payload diet ───────────────────────

  /** Stub the standard 200 path for the DB-OPT5 list tests (auth + single DO). */
  private DataObject stubListSingleDataObject() {
    DataObject d = makeDataObject(DO_OGM_ID, DO_APP_ID, "sensor-track-1");
    d.setDescription("Long markdown description that should be trimmed by default");
    d.setAttributes(Map.of("bench", "P8", "propellant", "LOX/LH2", "test_engineer", "alice"));
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(true);
    when(dataObjectService.getAllDataObjectsByShepardIds(eq(COLL_OGM_ID), any(), eq(null)))
      .thenReturn(List.of(d));
    return d;
  }

  @Test
  void listDefaultTrimDropsHeavyFields() {
    stubListSingleDataObject();
    Response r = resource.list(COLL_APP_ID, null, null, 0, 50, null, null, securityContext);
    assertEquals(200, r.getStatus());
    String body = (String) r.getEntity();
    // Heavy fields gone by default
    org.junit.jupiter.api.Assertions.assertFalse(body.contains("\"description\""), "description should be trimmed by default");
    org.junit.jupiter.api.Assertions.assertFalse(body.contains("\"attributes\""), "attributes should be trimmed by default");
    // Deprecated int counts gone by default
    org.junit.jupiter.api.Assertions.assertFalse(body.contains("\"timeseriesReferenceCount\""), "deprecated timeseriesReferenceCount should be trimmed");
    org.junit.jupiter.api.Assertions.assertFalse(body.contains("\"fileBundleCount\""), "deprecated fileBundleCount should be trimmed");
    org.junit.jupiter.api.Assertions.assertFalse(body.contains("\"structuredDataReferenceCount\""), "deprecated structuredDataReferenceCount should be trimmed");
    // Identity + v2 counts still present
    org.junit.jupiter.api.Assertions.assertTrue(body.contains("\"appId\""));
    org.junit.jupiter.api.Assertions.assertTrue(body.contains("\"name\""));
    org.junit.jupiter.api.Assertions.assertTrue(body.contains("\"timeseriesCount\""));
    org.junit.jupiter.api.Assertions.assertTrue(body.contains("\"fileCount\""));
    org.junit.jupiter.api.Assertions.assertTrue(body.contains("\"structuredDataCount\""));
    // Diet header surfaces the mode
    assertEquals("default-trim", r.getHeaders().getFirst("X-Shepard-Payload-Diet"));
  }

  @Test
  void listIncludeFullReturnsFullShape() {
    stubListSingleDataObject();
    Response r = resource.list(COLL_APP_ID, null, null, 0, 50, "full", null, securityContext);
    assertEquals(200, r.getStatus());
    String body = (String) r.getEntity();
    // All fields back including the heavy ones
    org.junit.jupiter.api.Assertions.assertTrue(body.contains("\"description\""));
    org.junit.jupiter.api.Assertions.assertTrue(body.contains("\"attributes\""));
    org.junit.jupiter.api.Assertions.assertTrue(body.contains("\"timeseriesReferenceCount\""));
    assertEquals("full", r.getHeaders().getFirst("X-Shepard-Payload-Diet"));
  }

  @Test
  void listFieldsParamLimitsToRequestedFields() {
    stubListSingleDataObject();
    Response r = resource.list(COLL_APP_ID, null, null, 0, 50, null, "appId,name,createdAt", securityContext);
    assertEquals(200, r.getStatus());
    String body = (String) r.getEntity();
    org.junit.jupiter.api.Assertions.assertTrue(body.contains("\"appId\""));
    org.junit.jupiter.api.Assertions.assertTrue(body.contains("\"name\""));
    org.junit.jupiter.api.Assertions.assertTrue(body.contains("\"createdAt\""));
    // Not asked for → not emitted
    org.junit.jupiter.api.Assertions.assertFalse(body.contains("\"timeseriesCount\""));
    org.junit.jupiter.api.Assertions.assertFalse(body.contains("\"description\""));
    org.junit.jupiter.api.Assertions.assertFalse(body.contains("\"attributes\""));
    org.junit.jupiter.api.Assertions.assertFalse(body.contains("\"referenceIds\""));
    assertEquals("fields", r.getHeaders().getFirst("X-Shepard-Payload-Diet"));
  }

  @Test
  void listFieldsParamAlwaysIncludesIdentity() {
    stubListSingleDataObject();
    // Ask only for createdAt; id, appId, name should still come back as identity guarantees.
    Response r = resource.list(COLL_APP_ID, null, null, 0, 50, null, "createdAt", securityContext);
    assertEquals(200, r.getStatus());
    String body = (String) r.getEntity();
    org.junit.jupiter.api.Assertions.assertTrue(body.contains("\"appId\""));
    org.junit.jupiter.api.Assertions.assertTrue(body.contains("\"name\""));
    // "id" key check would false-match other fields like fileId, so use the parsed body
    List<DataObjectListItemV2IO> items = parseListBody(r);
    assertEquals(1, items.size());
    assertEquals(DO_APP_ID, items.get(0).getAppId());
    assertEquals("sensor-track-1", items.get(0).getName());
  }

  @Test
  void listFieldsParamEmptyStringTreatedAsAbsent() {
    stubListSingleDataObject();
    Response r = resource.list(COLL_APP_ID, null, null, 0, 50, null, "", securityContext);
    assertEquals(200, r.getStatus());
    // Empty fields → default-trim mode (not 400, not "fields" mode)
    assertEquals("default-trim", r.getHeaders().getFirst("X-Shepard-Payload-Diet"));
  }

  @Test
  void listFieldsParamUnknownFieldReturns400() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(true);
    Response r = resource.list(COLL_APP_ID, null, null, 0, 50, null, "appId,bogusField,name", securityContext);
    assertEquals(400, r.getStatus());
    // 400 returns ProblemJson entity with the offending field name in 'detail'
    de.dlr.shepard.common.exceptions.ProblemJson body = (de.dlr.shepard.common.exceptions.ProblemJson) r.getEntity();
    String detail = body.detail();
    org.junit.jupiter.api.Assertions.assertNotNull(detail);
    org.junit.jupiter.api.Assertions.assertTrue(detail.contains("bogusField"), "400 body should cite the offending field name; got: " + detail);
    // 400 must short-circuit before any DB hit
    verify(dataObjectService, never()).getAllDataObjectsByShepardIds(anyLong(), any(), any());
  }

  @Test
  void listFieldsParamWhitespaceTolerated() {
    stubListSingleDataObject();
    // Whitespace around commas should not produce 400.
    Response r = resource.list(COLL_APP_ID, null, null, 0, 50, null, "appId, name , createdAt", securityContext);
    assertEquals(200, r.getStatus());
    assertEquals("fields", r.getHeaders().getFirst("X-Shepard-Payload-Diet"));
  }

  @Test
  void listDefaultTrimYieldsSmallerPayloadThanIncludeFull() {
    // The whole point of DB-OPT5: default response is meaningfully smaller
    // than ?include=full on a DataObject with realistic-size description+attributes.
    DataObject d = makeDataObject(DO_OGM_ID, DO_APP_ID, "sensor-track-1");
    d.setDescription("X".repeat(800));
    java.util.Map<String, String> attrs = new java.util.HashMap<>();
    for (int i = 0; i < 12; i++) attrs.put("key_" + i, "value_" + i + "_with_some_text");
    d.setAttributes(attrs);
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(true);
    when(dataObjectService.getAllDataObjectsByShepardIds(eq(COLL_OGM_ID), any(), eq(null)))
      .thenReturn(List.of(d, d, d, d, d));

    String trimmed = (String) resource.list(COLL_APP_ID, null, null, 0, 50, null, null, securityContext).getEntity();
    String full = (String) resource.list(COLL_APP_ID, null, null, 0, 50, "full", null, securityContext).getEntity();
    System.out.printf(
      "DB-OPT5 end-to-end measurement (5 DOs, 800-char description + 12-attr map per DO): full=%d B, default-trim=%d B (%.1f%% smaller)%n",
      full.length(), trimmed.length(), 100.0 * (full.length() - trimmed.length()) / full.length()
    );
    org.junit.jupiter.api.Assertions.assertTrue(
      trimmed.length() < full.length() / 2,
      "DB-OPT5 default-trim should be < 50% of ?include=full payload (got " + trimmed.length() + " vs " + full.length() + ")"
    );
  }
}
