package de.dlr.shepard.v2.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.DataObject;
import io.quarkiverse.mcp.server.McpException;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * MCP-COV-11 — unit tests for {@link LineageMcpTools}.
 *
 * <p>Pattern mirrors the other MCP test suites: hand-wired CDI,
 * Mockito mocks, a real {@link McpToolSupport} with a real
 * {@link ObjectMapper}.
 */
class LineageMcpToolsTest {

  static final String DO_APP_ID = "018f9c5a-7e26-7000-c000-000000000001";
  static final long   DO_OGM_ID = 42L;

  @Mock DataObjectDAO dataObjectDAO;
  @Mock EntityIdResolver entityIdResolver;
  @Mock McpContextBridge contextBridge;

  LineageMcpTools tools;
  McpToolSupport support;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    support = new McpToolSupport();
    support.entityIdResolver = entityIdResolver;
    support.objectMapper = new ObjectMapper();

    tools = new LineageMcpTools();
    tools.dataObjectDAO = dataObjectDAO;
    tools.contextBridge = contextBridge;
    tools.support = support;

    // Default: resolver returns a DataObject-labeled node.
    when(entityIdResolver.resolveWithLabels(DO_APP_ID))
      .thenReturn(new EntityIdResolver.LabeledResolution(DO_OGM_ID, List.of("DataObject")));
  }

  private DataObject doRow(String appId, String name, String status) {
    DataObject d = new DataObject();
    d.setAppId(appId);
    d.setName(name);
    d.setStatus(status);
    return d;
  }

  // ── get_predecessor_chain ─────────────────────────────────────────────────

  @Test
  void predecessorChainReturnsOrderedRowsWithDefaultDepth() throws Exception {
    when(dataObjectDAO.findPredecessorChain(eq(DO_APP_ID), eq(LineageMcpTools.DEFAULT_DEPTH)))
      .thenReturn(List.of(
        doRow("018f-pred-1", "TR-005-hold", "BLOCKED"),
        doRow("018f-pred-2", "TR-004-anomaly", "FAILED")
      ));

    String json = tools.getPredecessorChain(DO_APP_ID, null);

    JsonNode root = new ObjectMapper().readTree(json);
    assertEquals(DO_APP_ID, root.get("dataObjectAppId").asText());
    assertEquals("predecessor", root.get("direction").asText());
    assertEquals(LineageMcpTools.DEFAULT_DEPTH, root.get("depth").asInt());
    assertEquals(2, root.get("count").asInt());
    var chain = root.get("chain");
    assertEquals("018f-pred-1", chain.get(0).get("appId").asText());
    assertEquals("TR-005-hold", chain.get(0).get("name").asText());
    assertEquals("BLOCKED", chain.get(0).get("status").asText());
  }

  @Test
  void predecessorChainClampsDepthToMax() {
    when(dataObjectDAO.findPredecessorChain(eq(DO_APP_ID), eq(LineageMcpTools.MAX_DEPTH)))
      .thenReturn(List.of());
    tools.getPredecessorChain(DO_APP_ID, 999);
    verify(dataObjectDAO).findPredecessorChain(DO_APP_ID, LineageMcpTools.MAX_DEPTH);
  }

  @Test
  void predecessorChainClampsDepthToOne() {
    when(dataObjectDAO.findPredecessorChain(eq(DO_APP_ID), eq(1)))
      .thenReturn(List.of());
    tools.getPredecessorChain(DO_APP_ID, 0);
    verify(dataObjectDAO).findPredecessorChain(DO_APP_ID, 1);
  }

  @Test
  void predecessorChainReturnsEmptyArrayForNoPredecessors() throws Exception {
    when(dataObjectDAO.findPredecessorChain(eq(DO_APP_ID), eq(LineageMcpTools.DEFAULT_DEPTH)))
      .thenReturn(List.of());
    String json = tools.getPredecessorChain(DO_APP_ID, null);
    JsonNode root = new ObjectMapper().readTree(json);
    assertEquals(0, root.get("count").asInt());
    assertEquals(0, root.get("chain").size());
  }

  @Test
  void predecessorChainRejectsWrongType() {
    when(entityIdResolver.resolveWithLabels(DO_APP_ID))
      .thenReturn(new EntityIdResolver.LabeledResolution(DO_OGM_ID, List.of("Collection")));
    McpException ex = assertThrows(McpException.class, () -> tools.getPredecessorChain(DO_APP_ID, null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    assertTrue(ex.getMessage().contains("DataObject"));
  }

  @Test
  void predecessorChainRejectsMissingAppId() {
    when(entityIdResolver.resolveWithLabels(DO_APP_ID)).thenThrow(new NotFoundException());
    McpException ex = assertThrows(McpException.class, () -> tools.getPredecessorChain(DO_APP_ID, null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  // ── get_successor_chain ───────────────────────────────────────────────────

  @Test
  void successorChainReturnsOrderedRowsWithCustomDepth() throws Exception {
    when(dataObjectDAO.findSuccessorChain(eq(DO_APP_ID), eq(5)))
      .thenReturn(List.of(
        doRow("018f-succ-1", "TR-005-hold", "BLOCKED"),
        doRow("018f-succ-2", "TR-006-retest", "READY")
      ));

    String json = tools.getSuccessorChain(DO_APP_ID, 5);

    JsonNode root = new ObjectMapper().readTree(json);
    assertEquals("successor", root.get("direction").asText());
    assertEquals(5, root.get("depth").asInt());
    assertEquals(2, root.get("count").asInt());
    assertEquals("018f-succ-2", root.get("chain").get(1).get("appId").asText());
    assertEquals("READY", root.get("chain").get(1).get("status").asText());
  }

  @Test
  void successorChainRejectsBlankAppId() {
    McpException ex = assertThrows(McpException.class, () -> tools.getSuccessorChain(" ", null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  @Test
  void successorChainAcceptsDefaultWhenDepthOmitted() {
    when(dataObjectDAO.findSuccessorChain(eq(DO_APP_ID), eq(LineageMcpTools.DEFAULT_DEPTH)))
      .thenReturn(List.of());
    tools.getSuccessorChain(DO_APP_ID, null);
    verify(dataObjectDAO).findSuccessorChain(DO_APP_ID, LineageMcpTools.DEFAULT_DEPTH);
  }
}
