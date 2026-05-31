package de.dlr.shepard.v2.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.v2.scenegraph.entities.DigitalTwinScene;
import de.dlr.shepard.v2.scenegraph.services.SceneGraphService;
import de.dlr.shepard.v2.scenegraph.services.SceneGraphService.SceneListPage;
import de.dlr.shepard.v2.scenegraph.services.SceneGraphService.SceneListRow;
import de.dlr.shepard.v2.scenegraph.services.ScenegraphFromUrdfService;
import de.dlr.shepard.v2.scenegraph.services.ScenegraphFromUrdfService.ExistingSceneException;
import io.quarkiverse.mcp.server.McpException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * MCP-COV-08 — unit tests for the two new tools on {@link SceneGraphMcpTools}:
 * {@code scene_list} and {@code scene_create_from_urdf}.
 *
 * <p>The other five {@code scene_graph_*} tools shipped earlier in the
 * SCENEGRAPH-REST-1 wave and are exercised by the REST + service tests.
 * This file focuses on the two newly-added MCP wrappers — the audit
 * coverage gap that MCP-COV-08 closes.
 *
 * <p>Pattern mirrors {@link AnnotationMcpToolsTest}: hand-wired CDI,
 * Mockito mocks, a real {@link McpToolSupport} with a real
 * {@link ObjectMapper} so the JSON envelope shape is asserted as-emitted.
 */
class SceneGraphMcpToolsTest {

  static final String SCENE_APP_ID    = "018f9c5a-7e26-7000-d000-000000000001";
  static final String SCENE_APP_ID_2  = "018f9c5a-7e26-7000-d000-000000000002";
  static final String FILE_REF_APP_ID = "018f9c5a-7e26-7000-d000-000000000010";
  static final String USERNAME        = "flo";

  @Mock SceneGraphService sceneGraphService;
  @Mock ScenegraphFromUrdfService scenegraphFromUrdfService;
  @Mock AuthenticationContext authenticationContext;
  @Mock McpContextBridge contextBridge;

  SceneGraphMcpTools tools;
  McpToolSupport support;
  ObjectMapper mapper;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    mapper = new ObjectMapper();
    support = new McpToolSupport();
    support.objectMapper = mapper;

    tools = new SceneGraphMcpTools();
    tools.sceneGraphService = sceneGraphService;
    tools.scenegraphFromUrdfService = scenegraphFromUrdfService;
    tools.authenticationContext = authenticationContext;
    tools.contextBridge = contextBridge;
    tools.support = support;
    tools.objectMapper = mapper;
    // currentVertxRequest left null — readAiAgentHeader() falls back to null safely.

    when(authenticationContext.getCurrentUserName()).thenReturn(USERNAME);
  }

  // ── scene_list ──────────────────────────────────────────────────────────────

  @Test
  void sceneListReturnsEnvelopeWithItemsAndTotal() throws Exception {
    SceneListRow row1 = new SceneListRow(
      SCENE_APP_ID, "kr210-r2700", "KUKA KR210 model", "fileref-1", "frame-root",
      1000L, 2000L, 18L, 17L
    );
    SceneListRow row2 = new SceneListRow(
      SCENE_APP_ID_2, "iiwa-7", null, null, null,
      500L, 1500L, 8L, 7L
    );
    when(sceneGraphService.listScenes(0, 50))
      .thenReturn(new SceneListPage(List.of(row1, row2), 2L));

    String json = tools.sceneList(null, null);

    JsonNode root = mapper.readTree(json);
    assertEquals(2L, root.get("total").asLong());
    assertEquals(0, root.get("page").asInt());
    assertEquals(50, root.get("size").asInt());
    JsonNode items = root.get("items");
    assertEquals(2, items.size());
    assertEquals(SCENE_APP_ID, items.get(0).get("appId").asText());
    assertEquals("kr210-r2700", items.get(0).get("name").asText());
    assertEquals(18L, items.get(0).get("frameCount").asLong());
    assertEquals(17L, items.get(0).get("jointCount").asLong());
    // Second row carries nullable fields — verify they survive the round-trip
    // as either null nodes or are simply absent (JsonInclude.NON_NULL).
    JsonNode item2Description = items.get(1).get("description");
    assertTrue(item2Description == null || item2Description.isNull());
  }

  @Test
  void sceneListClampsSizeToMaximum() throws Exception {
    when(sceneGraphService.listScenes(eq(2), eq(SceneGraphMcpTools.SCENE_LIST_MAX_SIZE)))
      .thenReturn(new SceneListPage(List.of(), 0L));

    String json = tools.sceneList(2, 9999);

    JsonNode root = mapper.readTree(json);
    assertEquals(2, root.get("page").asInt());
    assertEquals(SceneGraphMcpTools.SCENE_LIST_MAX_SIZE, root.get("size").asInt());
    verify(sceneGraphService).listScenes(2, SceneGraphMcpTools.SCENE_LIST_MAX_SIZE);
  }

  // ── scene_create_from_urdf ──────────────────────────────────────────────────

  @Test
  void sceneCreateFromUrdfReturnsCreatedShape() throws Exception {
    DigitalTwinScene scene = new DigitalTwinScene();
    scene.setAppId(SCENE_APP_ID);
    scene.setName("kr210");
    when(scenegraphFromUrdfService.createFromUrdf(
      eq(FILE_REF_APP_ID), eq("kr210"), any(), any(), eq(USERNAME)
    )).thenReturn(scene);

    String json = tools.sceneCreateFromUrdf(FILE_REF_APP_ID, "kr210", null);

    JsonNode root = mapper.readTree(json);
    assertEquals("created", root.get("status").asText());
    assertEquals(SCENE_APP_ID, root.get("sceneAppId").asText());
    assertEquals(SCENE_APP_ID, root.get("scene").get("appId").asText());
  }

  @Test
  void sceneCreateFromUrdfReturnsExistsShapeOnConflict() throws Exception {
    when(scenegraphFromUrdfService.createFromUrdf(
      eq(FILE_REF_APP_ID), any(), any(), any(), eq(USERNAME)
    )).thenThrow(new ExistingSceneException(SCENE_APP_ID));

    String json = tools.sceneCreateFromUrdf(FILE_REF_APP_ID, null, null);

    JsonNode root = mapper.readTree(json);
    assertEquals("exists", root.get("status").asText());
    assertEquals(SCENE_APP_ID, root.get("existingSceneAppId").asText());
    assertEquals(FILE_REF_APP_ID, root.get("fileReferenceAppId").asText());
  }

  @Test
  void sceneCreateFromUrdfMapsForbiddenToPermissionDenied() {
    when(scenegraphFromUrdfService.createFromUrdf(
      eq(FILE_REF_APP_ID), any(), any(), any(), eq(USERNAME)
    )).thenThrow(new ForbiddenException("no write on parent collection"));

    McpException ex = assertThrows(McpException.class,
      () -> tools.sceneCreateFromUrdf(FILE_REF_APP_ID, null, null));
    // FORBIDDEN custom code per McpToolSupport.
    assertEquals(-32002, ex.getJsonRpcErrorCode());
  }

  @Test
  void sceneCreateFromUrdfMapsNotFoundToInvalidParams() {
    when(scenegraphFromUrdfService.createFromUrdf(
      eq(FILE_REF_APP_ID), any(), any(), any(), eq(USERNAME)
    )).thenThrow(new NotFoundException("No FileReference with appId " + FILE_REF_APP_ID));

    McpException ex = assertThrows(McpException.class,
      () -> tools.sceneCreateFromUrdf(FILE_REF_APP_ID, null, null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    assertTrue(ex.getMessage().contains(FILE_REF_APP_ID));
  }

  @Test
  void sceneCreateFromUrdfMapsBadRequestToInvalidParams() {
    when(scenegraphFromUrdfService.createFromUrdf(
      eq(FILE_REF_APP_ID), any(), any(), any(), eq(USERNAME)
    )).thenThrow(new BadRequestException("URDF body lacks <robot> root"));

    McpException ex = assertThrows(McpException.class,
      () -> tools.sceneCreateFromUrdf(FILE_REF_APP_ID, null, null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    assertTrue(ex.getMessage().contains("URDF"));
  }

  @Test
  void sceneCreateFromUrdfRequiresAuthenticatedUser() {
    when(authenticationContext.getCurrentUserName()).thenReturn(null);

    McpException ex = assertThrows(McpException.class,
      () -> tools.sceneCreateFromUrdf(FILE_REF_APP_ID, null, null));
    // McpToolSupport.run maps NotAuthorizedException to AUTH_REQUIRED.
    assertEquals(-32001, ex.getJsonRpcErrorCode());
  }
}
