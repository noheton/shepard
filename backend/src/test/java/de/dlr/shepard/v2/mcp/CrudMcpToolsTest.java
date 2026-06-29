package de.dlr.shepard.v2.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.context.collection.daos.CollectionDAO;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.context.collection.services.DataObjectService;
import io.quarkiverse.mcp.server.McpException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * MCP-COV-02-1 — unit tests for {@link CrudMcpTools}.
 *
 * <p>Pattern mirrors the other MCP test suites: hand-wired CDI,
 * Mockito mocks for all service / DAO / entity collaborators,
 * real {@link McpToolSupport} with a real {@link ObjectMapper}.
 */
class CrudMcpToolsTest {

  static final String COLL_APP_ID = "018f9c5a-7e26-7000-c000-000000000010";
  static final long   COLL_OGM_ID = 10L;
  static final String DO_APP_ID   = "018f9c5a-7e26-7000-c000-000000000020";
  static final long   DO_OGM_ID   = 20L;
  static final String PRED_APP_ID = "018f9c5a-7e26-7000-c000-000000000030";

  @Mock CollectionService  collectionService;
  @Mock CollectionDAO      collectionDAO;
  @Mock DataObjectService  dataObjectService;
  @Mock DataObjectDAO      dataObjectDAO;
  @Mock McpContextBridge   contextBridge;
  @Mock EntityIdResolver   entityIdResolver;

  CrudMcpTools   tools;
  McpToolSupport support;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    support = new McpToolSupport();
    support.entityIdResolver = entityIdResolver;
    support.objectMapper     = new ObjectMapper();

    tools = new CrudMcpTools();
    tools.collectionService = collectionService;
    tools.collectionDAO     = collectionDAO;
    tools.dataObjectService = dataObjectService;
    tools.dataObjectDAO     = dataObjectDAO;
    tools.contextBridge     = contextBridge;
    tools.support           = support;
  }

  // ── entity stubs ──────────────────────────────────────────────────────────

  private Collection collStub(String appId, String name, String status) {
    Collection c = mock(Collection.class);
    when(c.getAppId()).thenReturn(appId);
    when(c.getName()).thenReturn(name);
    when(c.getDescription()).thenReturn(null);
    when(c.getStatus()).thenReturn(status);
    when(c.getId()).thenReturn(COLL_OGM_ID);
    when(c.getAttributes()).thenReturn(null);
    when(c.getLicense()).thenReturn(null);
    when(c.getAccessRights()).thenReturn(null);
    return c;
  }

  private DataObject doStub(String appId, String name, String status, Collection coll) {
    DataObject d = mock(DataObject.class);
    when(d.getAppId()).thenReturn(appId);
    when(d.getName()).thenReturn(name);
    when(d.getDescription()).thenReturn(null);
    when(d.getStatus()).thenReturn(status);
    when(d.getId()).thenReturn(DO_OGM_ID);
    when(d.getCollection()).thenReturn(coll);
    when(d.getParent()).thenReturn(null);
    when(d.getPredecessors()).thenReturn(null);
    when(d.getAttributes()).thenReturn(null);
    when(d.getLicense()).thenReturn(null);
    when(d.getAccessRights()).thenReturn(null);
    return d;
  }

  // ── collection_create ────────────────────────────────────────────────────

  @Test
  void collectionCreateHappyPath() throws Exception {
    Collection created = collStub(COLL_APP_ID, "LUMEN 2024", "DRAFT");
    when(collectionService.createCollection(any(CollectionIO.class))).thenReturn(created);

    String json = tools.collectionCreate("LUMEN 2024", "A test campaign", null);

    JsonNode root = new ObjectMapper().readTree(json);
    assertEquals(COLL_APP_ID, root.get("appId").asText());
    assertEquals("LUMEN 2024", root.get("name").asText());
    assertEquals("DRAFT",      root.get("status").asText());
    verify(collectionService).createCollection(any(CollectionIO.class));
  }

  @Test
  void collectionCreateRejectsBlankName() {
    McpException ex = assertThrows(McpException.class,
        () -> tools.collectionCreate("  ", null, null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  @Test
  void collectionCreateRejectsNullName() {
    McpException ex = assertThrows(McpException.class,
        () -> tools.collectionCreate(null, null, null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  // ── collection_update ────────────────────────────────────────────────────

  @Test
  void collectionUpdateHappyPath() throws Exception {
    Collection existing = collStub(COLL_APP_ID, "Old Name", "DRAFT");
    Collection updated  = collStub(COLL_APP_ID, "New Name", "IN_REVIEW");
    when(collectionDAO.findByAppId(COLL_APP_ID)).thenReturn(existing);
    when(collectionService.updateCollectionByShepardId(eq(COLL_OGM_ID), any(CollectionIO.class)))
        .thenReturn(updated);

    String json = tools.collectionUpdate(COLL_APP_ID, "New Name", null, "IN_REVIEW");

    JsonNode root = new ObjectMapper().readTree(json);
    assertEquals(COLL_APP_ID, root.get("appId").asText());
    assertEquals("New Name",  root.get("name").asText());
    assertEquals("IN_REVIEW", root.get("status").asText());
  }

  @Test
  void collectionUpdatePreservesExistingFieldsWhenNull() throws Exception {
    Collection existing = collStub(COLL_APP_ID, "Preserved Name", "DRAFT");
    when(collectionDAO.findByAppId(COLL_APP_ID)).thenReturn(existing);
    when(collectionService.updateCollectionByShepardId(eq(COLL_OGM_ID), any(CollectionIO.class)))
        .thenReturn(existing);

    ArgumentCaptor<CollectionIO> captor = ArgumentCaptor.forClass(CollectionIO.class);
    tools.collectionUpdate(COLL_APP_ID, null, null, null);
    verify(collectionService).updateCollectionByShepardId(eq(COLL_OGM_ID), captor.capture());

    CollectionIO sentIo = captor.getValue();
    assertEquals("Preserved Name", sentIo.getName());
    assertEquals("DRAFT",          sentIo.getStatus());
  }

  @Test
  void collectionUpdateRejectsMissingAppId() {
    McpException ex = assertThrows(McpException.class,
        () -> tools.collectionUpdate("  ", "x", null, null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  @Test
  void collectionUpdateRejectsNotFound() {
    when(collectionDAO.findByAppId(COLL_APP_ID)).thenReturn(null);
    McpException ex = assertThrows(McpException.class,
        () -> tools.collectionUpdate(COLL_APP_ID, "x", null, null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  // ── data_object_create ───────────────────────────────────────────────────

  @Test
  void dataObjectCreateHappyPath() throws Exception {
    Collection coll    = collStub(COLL_APP_ID, "LUMEN 2024", "DRAFT");
    DataObject created = doStub(DO_APP_ID, "TR-004", "DRAFT", coll);
    when(collectionDAO.findByAppId(COLL_APP_ID)).thenReturn(coll);
    when(dataObjectService.createDataObject(eq(COLL_OGM_ID), any(DataObjectIO.class)))
        .thenReturn(created);

    String json = tools.dataObjectCreate(COLL_APP_ID, "TR-004", "anomaly run", null);

    JsonNode root = new ObjectMapper().readTree(json);
    assertEquals(DO_APP_ID,   root.get("appId").asText());
    assertEquals("TR-004",    root.get("name").asText());
    assertEquals("DRAFT",     root.get("status").asText());
    assertEquals(COLL_APP_ID, root.get("collectionAppId").asText());
  }

  @Test
  void dataObjectCreateRejectsMissingCollectionAppId() {
    McpException ex = assertThrows(McpException.class,
        () -> tools.dataObjectCreate(null, "TR-004", null, null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  @Test
  void dataObjectCreateRejectsBlankName() {
    McpException ex = assertThrows(McpException.class,
        () -> tools.dataObjectCreate(COLL_APP_ID, "", null, null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  @Test
  void dataObjectCreateRejectsCollectionNotFound() {
    when(collectionDAO.findByAppId(COLL_APP_ID)).thenReturn(null);
    McpException ex = assertThrows(McpException.class,
        () -> tools.dataObjectCreate(COLL_APP_ID, "TR-004", null, null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  // ── data_object_update ───────────────────────────────────────────────────

  @Test
  void dataObjectUpdateHappyPath() throws Exception {
    Collection coll     = collStub(COLL_APP_ID, "LUMEN 2024", "DRAFT");
    DataObject existing = doStub(DO_APP_ID, "TR-004", "DRAFT", coll);
    DataObject updated  = doStub(DO_APP_ID, "TR-004 revised", "IN_REVIEW", coll);
    when(collectionDAO.findByAppId(COLL_APP_ID)).thenReturn(coll);
    when(dataObjectDAO.findByAppId(DO_APP_ID)).thenReturn(existing);
    when(dataObjectService.updateDataObject(eq(COLL_OGM_ID), eq(DO_OGM_ID), any(DataObjectIO.class)))
        .thenReturn(updated);

    String json = tools.dataObjectUpdate(COLL_APP_ID, DO_APP_ID, "TR-004 revised", null, "IN_REVIEW");

    JsonNode root = new ObjectMapper().readTree(json);
    assertEquals(DO_APP_ID,        root.get("appId").asText());
    assertEquals("TR-004 revised", root.get("name").asText());
    assertEquals("IN_REVIEW",      root.get("status").asText());
    assertEquals(COLL_APP_ID,      root.get("collectionAppId").asText());
  }

  @Test
  void dataObjectUpdateCarriesThroughPredecessors() throws Exception {
    Collection coll = collStub(COLL_APP_ID, "LUMEN 2024", "DRAFT");

    DataObject pred = mock(DataObject.class);
    when(pred.getAppId()).thenReturn(PRED_APP_ID);

    DataObject existing = doStub(DO_APP_ID, "TR-005", "DRAFT", coll);
    when(existing.getPredecessors()).thenReturn(List.of(pred));

    DataObject updated = doStub(DO_APP_ID, "TR-005", "DRAFT", coll);
    when(collectionDAO.findByAppId(COLL_APP_ID)).thenReturn(coll);
    when(dataObjectDAO.findByAppId(DO_APP_ID)).thenReturn(existing);
    when(dataObjectService.updateDataObject(eq(COLL_OGM_ID), eq(DO_OGM_ID), any(DataObjectIO.class)))
        .thenReturn(updated);

    ArgumentCaptor<DataObjectIO> captor = ArgumentCaptor.forClass(DataObjectIO.class);
    tools.dataObjectUpdate(COLL_APP_ID, DO_APP_ID, null, null, null);
    verify(dataObjectService).updateDataObject(eq(COLL_OGM_ID), eq(DO_OGM_ID), captor.capture());

    String[] predIds = captor.getValue().getPredecessorAppIds();
    assertNotNull(predIds, "predecessorAppIds must be carried through from existing entity");
    assertEquals(1,           predIds.length);
    assertEquals(PRED_APP_ID, predIds[0]);
  }

  @Test
  void dataObjectUpdateRejectsMissingDataObjectAppId() {
    McpException ex = assertThrows(McpException.class,
        () -> tools.dataObjectUpdate(COLL_APP_ID, "  ", "x", null, null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  @Test
  void dataObjectUpdateRejectsDataObjectNotFound() {
    Collection coll = collStub(COLL_APP_ID, "LUMEN 2024", "DRAFT");
    when(collectionDAO.findByAppId(COLL_APP_ID)).thenReturn(coll);
    when(dataObjectDAO.findByAppId(DO_APP_ID)).thenReturn(null);
    McpException ex = assertThrows(McpException.class,
        () -> tools.dataObjectUpdate(COLL_APP_ID, DO_APP_ID, "x", null, null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }
}
