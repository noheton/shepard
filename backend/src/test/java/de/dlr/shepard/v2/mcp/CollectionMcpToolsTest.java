package de.dlr.shepard.v2.mcp;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.context.collection.services.DataObjectService;
import jakarta.ws.rs.NotFoundException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class CollectionMcpToolsTest {

  static final String COLL_APP_ID = "018f9c5a-7e26-7000-a000-000000000010";
  static final String DO_APP_ID   = "018f9c5a-7e26-7000-a000-000000000020";
  static final long   COLL_OGM_ID = 42L;
  static final long   DO_OGM_ID   = 84L;

  static Collection makeCollection(long ogmId, String appId, String name) {
    Collection c = new Collection();
    c.setShepardId(ogmId);
    c.setAppId(appId);
    c.setName(name);
    return c;
  }

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

  @Mock CollectionService collectionService;
  @Mock DataObjectService dataObjectService;
  @Mock DataObjectDAO dataObjectDAO;
  @Mock EntityIdResolver entityIdResolver;
  @Mock McpContextBridge contextBridge;

  CollectionMcpTools tools;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    tools = new CollectionMcpTools();
    tools.collectionService = collectionService;
    tools.dataObjectService = dataObjectService;
    tools.dataObjectDAO = dataObjectDAO;
    tools.entityIdResolver = entityIdResolver;
    tools.objectMapper = new ObjectMapper();
    tools.contextBridge = contextBridge;
    when(dataObjectDAO.findRefCountsByAppIds(any())).thenReturn(Collections.emptyMap());
  }

  // ── listCollections ───────────────────────────────────────────────────────

  @Test
  void listCollectionsReturnsJsonArray() throws Exception {
    Collection c = makeCollection(COLL_OGM_ID, COLL_APP_ID, "LUMEN Campaign 2024");
    when(collectionService.getAllCollections(any())).thenReturn(List.of(c));

    String json = tools.listCollections(null, null, null);

    assertNotNull(json);
    var nodes = new ObjectMapper().readTree(json);
    assertTrue(nodes.isArray());
    assertTrue(nodes.size() == 1);
    assertTrue(nodes.get(0).get("appId").asText().equals(COLL_APP_ID));
    assertTrue(nodes.get(0).get("name").asText().equals("LUMEN Campaign 2024"));
  }

  @Test
  void listCollectionsPageSizeDefaults() throws Exception {
    when(collectionService.getAllCollections(any())).thenReturn(Collections.emptyList());

    String json = tools.listCollections(null, null, null);

    assertNotNull(json);
    assertTrue(new ObjectMapper().readTree(json).isArray());
  }

  @Test
  void listCollectionsCapsSize() {
    when(collectionService.getAllCollections(any())).thenReturn(Collections.emptyList());
    tools.listCollections(0, 9999, null);
    // Verify the QueryParamHelper doesn't crash with oversized size.
    verify(collectionService).getAllCollections(any());
  }

  // ── listDataObjects ───────────────────────────────────────────────────────

  @Test
  void listDataObjectsThrowsNotFoundWhenCollectionUnknown() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenThrow(new NotFoundException());

    org.junit.jupiter.api.Assertions.assertThrows(
      NotFoundException.class,
      () -> tools.listDataObjects(COLL_APP_ID, null, null, null)
    );
    verify(dataObjectService, never()).getAllDataObjectsByShepardIds(anyLong(), any(), any());
  }

  @Test
  void listDataObjectsReturnsJsonArrayWithCounts() throws Exception {
    DataObject d = makeDataObject(DO_OGM_ID, DO_APP_ID, "TR-001");
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(dataObjectService.getAllDataObjectsByShepardIds(eq(COLL_OGM_ID), any(), eq(null)))
      .thenReturn(List.of(d));
    when(dataObjectDAO.findRefCountsByAppIds(any()))
      .thenReturn(Map.of(DO_APP_ID, new long[] { 1, 2, 3 }));

    String json = tools.listDataObjects(COLL_APP_ID, null, null, null);

    var root = new ObjectMapper().readTree(json);
    assertTrue(root.isArray());
    var row = root.get(0);
    assertTrue(row.get("appId").asText().equals(DO_APP_ID));
    assertTrue(row.get("name").asText().equals("TR-001"));
    assertTrue(row.get("timeseriesCount").asLong() == 1L);
    assertTrue(row.get("fileCount").asLong() == 2L);
    assertTrue(row.get("structuredDataCount").asLong() == 3L);
  }

  // ── getDataObject ─────────────────────────────────────────────────────────

  @Test
  void getDataObjectThrowsNotFoundWhenCollectionUnknown() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenThrow(new NotFoundException());

    org.junit.jupiter.api.Assertions.assertThrows(
      NotFoundException.class,
      () -> tools.getDataObject(COLL_APP_ID, DO_APP_ID)
    );
  }

  @Test
  void getDataObjectThrowsNotFoundWhenDataObjectUnknown() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenThrow(new NotFoundException());

    org.junit.jupiter.api.Assertions.assertThrows(
      NotFoundException.class,
      () -> tools.getDataObject(COLL_APP_ID, DO_APP_ID)
    );
  }

  @Test
  void getDataObjectReturnsJsonWithContainers() throws Exception {
    DataObject d = makeDataObject(DO_OGM_ID, DO_APP_ID, "TR-004");
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenReturn(DO_OGM_ID);
    when(dataObjectService.getDataObject(COLL_OGM_ID, DO_OGM_ID)).thenReturn(d);

    String json = tools.getDataObject(COLL_APP_ID, DO_APP_ID);

    var root = new ObjectMapper().readTree(json);
    assertTrue(root.get("appId").asText().equals(DO_APP_ID));
    assertTrue(root.get("name").asText().equals("TR-004"));
    assertTrue(root.has("containers"));
    assertTrue(root.get("containers").has("timeseries"));
    assertTrue(root.get("containers").has("files"));
    assertTrue(root.get("containers").has("structuredData"));
  }
}
