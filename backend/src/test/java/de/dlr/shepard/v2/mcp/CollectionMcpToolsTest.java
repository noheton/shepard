package de.dlr.shepard.v2.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import io.quarkiverse.mcp.server.McpException;
import jakarta.ws.rs.NotFoundException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for {@link CollectionMcpTools}. After task #80
 * (MCP type-checking + clean errors, MCP-2 entry in aidocs/34) the
 * appId resolver and JSON mapper moved into the shared
 * {@link McpToolSupport} bean — so this test now constructs a real
 * {@code support} with mocks injected, mirroring {@link TimeseriesMcpToolsTest}.
 * Tools dispatch every appId through {@code support.resolveOfType(...)} which
 * delegates to {@link EntityIdResolver#resolveWithLabels(String)} and rewraps
 * mismatches / not-founds as {@link McpException} (-32602).
 */
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
  McpToolSupport support;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    support = new McpToolSupport();
    support.entityIdResolver = entityIdResolver;
    support.objectMapper = new ObjectMapper();

    tools = new CollectionMcpTools();
    tools.collectionService = collectionService;
    tools.dataObjectService = dataObjectService;
    tools.dataObjectDAO = dataObjectDAO;
    tools.contextBridge = contextBridge;
    tools.support = support;

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
    assertEquals(1, nodes.size());
    assertEquals(COLL_APP_ID, nodes.get(0).get("appId").asText());
    assertEquals("LUMEN Campaign 2024", nodes.get(0).get("name").asText());
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
  void listDataObjectsThrowsInvalidParamsWhenCollectionUnknown() {
    when(entityIdResolver.resolveWithLabels(COLL_APP_ID)).thenThrow(new NotFoundException());

    McpException ex = assertThrows(
      McpException.class,
      () -> tools.listDataObjects(COLL_APP_ID, null, null, null)
    );
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    verify(dataObjectService, never()).getAllDataObjectsByShepardIds(anyLong(), any(), any());
  }

  @Test
  void listDataObjectsRejectsWrongCollectionType() {
    // Caller pasted a DataObject appId where a Collection appId was expected.
    when(entityIdResolver.resolveWithLabels(COLL_APP_ID))
      .thenReturn(new EntityIdResolver.LabeledResolution(COLL_OGM_ID, List.of("DataObject")));

    McpException ex = assertThrows(
      McpException.class,
      () -> tools.listDataObjects(COLL_APP_ID, null, null, null)
    );
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    assertTrue(ex.getMessage().contains("Collection"));
  }

  @Test
  void listDataObjectsReturnsJsonArrayWithCounts() throws Exception {
    DataObject d = makeDataObject(DO_OGM_ID, DO_APP_ID, "TR-001");
    when(entityIdResolver.resolveWithLabels(COLL_APP_ID))
      .thenReturn(new EntityIdResolver.LabeledResolution(COLL_OGM_ID, List.of("Collection")));
    when(dataObjectService.getAllDataObjectsByShepardIds(eq(COLL_OGM_ID), any(), eq(null)))
      .thenReturn(List.of(d));
    when(dataObjectDAO.findRefCountsByAppIds(any()))
      .thenReturn(Map.of(DO_APP_ID, new long[] { 1, 2, 3 }));

    String json = tools.listDataObjects(COLL_APP_ID, null, null, null);

    var root = new ObjectMapper().readTree(json);
    assertTrue(root.isArray());
    var row = root.get(0);
    assertEquals(DO_APP_ID, row.get("appId").asText());
    assertEquals("TR-001", row.get("name").asText());
    assertEquals(1L, row.get("timeseriesCount").asLong());
    assertEquals(2L, row.get("fileCount").asLong());
    assertEquals(3L, row.get("structuredDataCount").asLong());
  }

  // ── getDataObject ─────────────────────────────────────────────────────────

  @Test
  void getDataObjectThrowsInvalidParamsWhenCollectionUnknown() {
    when(entityIdResolver.resolveWithLabels(COLL_APP_ID)).thenThrow(new NotFoundException());

    McpException ex = assertThrows(
      McpException.class,
      () -> tools.getDataObject(COLL_APP_ID, DO_APP_ID)
    );
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  @Test
  void getDataObjectThrowsInvalidParamsWhenDataObjectUnknown() {
    when(entityIdResolver.resolveWithLabels(COLL_APP_ID))
      .thenReturn(new EntityIdResolver.LabeledResolution(COLL_OGM_ID, List.of("Collection")));
    when(entityIdResolver.resolveWithLabels(DO_APP_ID)).thenThrow(new NotFoundException());

    McpException ex = assertThrows(
      McpException.class,
      () -> tools.getDataObject(COLL_APP_ID, DO_APP_ID)
    );
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  @Test
  void getDataObjectReturnsJsonWithContainers() throws Exception {
    DataObject d = makeDataObject(DO_OGM_ID, DO_APP_ID, "TR-004");
    when(entityIdResolver.resolveWithLabels(COLL_APP_ID))
      .thenReturn(new EntityIdResolver.LabeledResolution(COLL_OGM_ID, List.of("Collection")));
    when(entityIdResolver.resolveWithLabels(DO_APP_ID))
      .thenReturn(new EntityIdResolver.LabeledResolution(DO_OGM_ID, List.of("DataObject")));
    // Tool calls both DAO.findByShepardIdAtDepth (for deeper references) AND
    // the service for auth + lineage. Stub both.
    when(dataObjectDAO.findByShepardIdAtDepth(DO_OGM_ID, 2)).thenReturn(d);
    when(dataObjectService.getDataObject(COLL_OGM_ID, DO_OGM_ID)).thenReturn(d);

    String json = tools.getDataObject(COLL_APP_ID, DO_APP_ID);

    var root = new ObjectMapper().readTree(json);
    assertEquals(DO_APP_ID, root.get("appId").asText());
    assertEquals("TR-004", root.get("name").asText());
    assertTrue(root.has("containers"));
    assertTrue(root.get("containers").has("timeseries"));
    assertTrue(root.get("containers").has("files"));
    assertTrue(root.get("containers").has("structuredData"));
  }
}
