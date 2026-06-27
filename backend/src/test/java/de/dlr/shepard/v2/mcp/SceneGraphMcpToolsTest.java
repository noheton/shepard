package de.dlr.shepard.v2.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.security.JWTPrincipal;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.v2.scenegraph.entities.DigitalTwinScene;
import de.dlr.shepard.v2.scenegraph.services.SceneGraphPermissionService;
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
  @Mock SceneGraphPermissionService scenePermissions;

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
    tools.scenePermissions = scenePermissions;
    // currentVertxRequest left null — readAiAgentHeader() falls back to null safely.

    when(authenticationContext.getCurrentUserName()).thenReturn(USERNAME);
    // Default: caller is not an instance-admin. Tests that exercise the
    // hand-built / admin-only path opt in via grantInstanceAdmin().
    when(authenticationContext.getPrincipal()).thenReturn(nonAdminPrincipal());
    // Default permission posture: ALLOW everything so the existing pre-PERMS-1-MCP
    // tests stay valid. Permission-denial tests opt-in by overriding the stub.
    when(scenePermissions.isAllowed(anyString(), any(), eq(USERNAME), anyBoolean()))
      .thenReturn(true);
    when(scenePermissions.canCreateFromSourceFile(anyString(), eq(USERNAME)))
      .thenReturn(true);
  }

  /** Build a principal carrying no {@code instance-admin} role. */
  static JWTPrincipal nonAdminPrincipal() {
    return new JWTPrincipal("aud", "issuedFor", USERNAME, "kid", List.of("user"));
  }

  /** Build a principal carrying the {@code instance-admin} role. */
  static JWTPrincipal adminPrincipal() {
    return new JWTPrincipal(
      "aud", "issuedFor", USERNAME, "kid",
      List.of("user", SceneGraphPermissionService.INSTANCE_ADMIN_ROLE)
    );
  }

  /** Swap the default non-admin principal in for an admin one. */
  void grantInstanceAdmin() {
    when(authenticationContext.getPrincipal()).thenReturn(adminPrincipal());
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

  // ── SCENEGRAPH-PERMS-1-MCP — per-tool permission gating ───────────────────

  static final String FRAME_APP_ID = "018f9c5a-7e26-7000-d000-0000000000a1";
  static final String JOINT_APP_ID = "018f9c5a-7e26-7000-d000-0000000000b1";

  /** Build a {@link DigitalTwinScene} stub with the given appId + sourceFileAppId. */
  static DigitalTwinScene sceneWithSource(String appId, String srcFileAppId) {
    DigitalTwinScene s = new DigitalTwinScene();
    s.setAppId(appId);
    s.setSourceFileAppId(srcFileAppId);
    return s;
  }

  // scene_graph_get — Read gate

  @Test
  void sceneGraphGetReturnsSceneWhenCallerHasRead() throws Exception {
    when(sceneGraphService.findScene(SCENE_APP_ID))
      .thenReturn(sceneWithSource(SCENE_APP_ID, FILE_REF_APP_ID));
    when(scenePermissions.isAllowed(eq(SCENE_APP_ID), eq(AccessType.Read), eq(USERNAME), eq(false)))
      .thenReturn(true);
    when(sceneGraphService.findFramesForScene(SCENE_APP_ID)).thenReturn(List.of());
    when(sceneGraphService.findJointsForScene(SCENE_APP_ID)).thenReturn(List.of());

    String json = tools.sceneGraphGet(SCENE_APP_ID);

    JsonNode root = mapper.readTree(json);
    assertEquals(SCENE_APP_ID, root.get("appId").asText());
  }

  @Test
  void sceneGraphGetMapsForbiddenToPermissionDenied() {
    when(sceneGraphService.findScene(SCENE_APP_ID))
      .thenReturn(sceneWithSource(SCENE_APP_ID, FILE_REF_APP_ID));
    when(scenePermissions.isAllowed(eq(SCENE_APP_ID), eq(AccessType.Read), eq(USERNAME), eq(false)))
      .thenReturn(false);

    McpException ex = assertThrows(McpException.class,
      () -> tools.sceneGraphGet(SCENE_APP_ID));
    assertEquals(-32002, ex.getJsonRpcErrorCode());
    verify(sceneGraphService, org.mockito.Mockito.never()).findFramesForScene(anyString());
  }

  // scene_graph_export_urdf — Read gate

  @Test
  void sceneGraphExportUrdfDeniesWithoutRead() {
    when(sceneGraphService.findScene(SCENE_APP_ID))
      .thenReturn(sceneWithSource(SCENE_APP_ID, FILE_REF_APP_ID));
    when(scenePermissions.isAllowed(eq(SCENE_APP_ID), eq(AccessType.Read), eq(USERNAME), eq(false)))
      .thenReturn(false);

    McpException ex = assertThrows(McpException.class,
      () -> tools.sceneGraphExportUrdf(SCENE_APP_ID));
    assertEquals(-32002, ex.getJsonRpcErrorCode());
  }

  // scene_graph_add_frame — Write gate

  @Test
  void sceneGraphAddFrameDeniesWithoutWrite() {
    when(scenePermissions.isAllowed(eq(SCENE_APP_ID), eq(AccessType.Write), eq(USERNAME), eq(false)))
      .thenReturn(false);

    McpException ex = assertThrows(McpException.class,
      () -> tools.sceneGraphAddFrame(
        SCENE_APP_ID, "tool0", null, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, null
      ));
    assertEquals(-32002, ex.getJsonRpcErrorCode());
    verify(sceneGraphService, org.mockito.Mockito.never())
      .addFrame(anyString(), any(), any());
  }

  // scene_graph_patch_frame — Write gate

  @Test
  void sceneGraphPatchFrameDeniesWithoutWrite() {
    when(scenePermissions.isAllowed(eq(SCENE_APP_ID), eq(AccessType.Write), eq(USERNAME), eq(false)))
      .thenReturn(false);

    McpException ex = assertThrows(McpException.class,
      () -> tools.sceneGraphPatchFrame(
        SCENE_APP_ID, FRAME_APP_ID, "renamed", null,
        null, null, null, null, null, null, null
      ));
    assertEquals(-32002, ex.getJsonRpcErrorCode());
    verify(sceneGraphService, org.mockito.Mockito.never())
      .patchFrame(anyString(), anyString(), any(), any());
  }

  // scene_graph_delete_frame — Write gate

  @Test
  void sceneGraphDeleteFrameDeniesWithoutWrite() {
    when(scenePermissions.isAllowed(eq(SCENE_APP_ID), eq(AccessType.Write), eq(USERNAME), eq(false)))
      .thenReturn(false);

    McpException ex = assertThrows(McpException.class,
      () -> tools.sceneGraphDeleteFrame(SCENE_APP_ID, FRAME_APP_ID));
    assertEquals(-32002, ex.getJsonRpcErrorCode());
    verify(sceneGraphService, org.mockito.Mockito.never())
      .deleteFrameSubtree(anyString(), anyString(), any());
  }

  // scene_graph_register_joint — Write gate

  @Test
  void sceneGraphRegisterJointDeniesWithoutWrite() {
    when(scenePermissions.isAllowed(eq(SCENE_APP_ID), eq(AccessType.Write), eq(USERNAME), eq(false)))
      .thenReturn(false);

    McpException ex = assertThrows(McpException.class,
      () -> tools.sceneGraphRegisterJoint(
        SCENE_APP_ID, FRAME_APP_ID, FRAME_APP_ID, "j1",
        0.0, 0.0, 1.0, -1.0, 1.0, "REVOLUTE", 0.0
      ));
    assertEquals(-32002, ex.getJsonRpcErrorCode());
    verify(sceneGraphService, org.mockito.Mockito.never())
      .addJoint(anyString(), any(), any());
  }

  // scene_graph_delete_joint — Write gate

  @Test
  void sceneGraphDeleteJointDeniesWithoutWrite() {
    when(scenePermissions.isAllowed(eq(SCENE_APP_ID), eq(AccessType.Write), eq(USERNAME), eq(false)))
      .thenReturn(false);

    McpException ex = assertThrows(McpException.class,
      () -> tools.sceneGraphDeleteJoint(SCENE_APP_ID, JOINT_APP_ID));
    assertEquals(-32002, ex.getJsonRpcErrorCode());
    verify(sceneGraphService, org.mockito.Mockito.never())
      .deleteJoint(anyString(), anyString(), any());
  }

  // scene_graph_create — hand-built + sourced gates

  @Test
  void sceneGraphCreateHandBuiltRequiresInstanceAdmin() {
    // Default principal is non-admin; sourceFileAppId blank → hand-built.
    McpException ex = assertThrows(McpException.class,
      () -> tools.sceneGraphCreate("hand-built", "no source", null));
    assertEquals(-32002, ex.getJsonRpcErrorCode());
    verify(sceneGraphService, org.mockito.Mockito.never())
      .createScene(any(), any());
  }

  @Test
  void sceneGraphCreateHandBuiltAllowedForInstanceAdmin() throws Exception {
    grantInstanceAdmin();
    DigitalTwinScene minted = new DigitalTwinScene();
    minted.setAppId(SCENE_APP_ID);
    minted.setName("hand-built");
    when(sceneGraphService.createScene(any(), any())).thenReturn(minted);

    String json = tools.sceneGraphCreate("hand-built", "admin-built", null);

    JsonNode root = mapper.readTree(json);
    assertEquals(SCENE_APP_ID, root.get("appId").asText());
  }

  @Test
  void sceneGraphCreateFromSourceFileDeniesWithoutWrite() {
    when(scenePermissions.canCreateFromSourceFile(eq(FILE_REF_APP_ID), eq(USERNAME)))
      .thenReturn(false);

    McpException ex = assertThrows(McpException.class,
      () -> tools.sceneGraphCreate("from-src", null, FILE_REF_APP_ID));
    assertEquals(-32002, ex.getJsonRpcErrorCode());
    verify(sceneGraphService, org.mockito.Mockito.never())
      .createScene(any(), any());
  }

  @Test
  void sceneGraphCreateFromSourceFileAllowedWithWrite() throws Exception {
    when(scenePermissions.canCreateFromSourceFile(eq(FILE_REF_APP_ID), eq(USERNAME)))
      .thenReturn(true);
    DigitalTwinScene minted = new DigitalTwinScene();
    minted.setAppId(SCENE_APP_ID);
    minted.setSourceFileAppId(FILE_REF_APP_ID);
    when(sceneGraphService.createScene(any(), any())).thenReturn(minted);

    String json = tools.sceneGraphCreate("from-src", null, FILE_REF_APP_ID);

    JsonNode root = mapper.readTree(json);
    assertEquals(SCENE_APP_ID, root.get("appId").asText());
  }

  // scene_list — post-filter

  @Test
  void sceneListPostFiltersUnreadableRows() throws Exception {
    SceneListRow row1 = new SceneListRow(
      SCENE_APP_ID, "visible", null, FILE_REF_APP_ID, null,
      0L, 0L, 0L, 0L
    );
    SceneListRow row2 = new SceneListRow(
      SCENE_APP_ID_2, "hidden", null, "other-fileref", null,
      0L, 0L, 0L, 0L
    );
    when(sceneGraphService.listScenes(0, 50))
      .thenReturn(new SceneListPage(List.of(row1, row2), 2L));
    // Override the blanket allow-all with selective denial of row2.
    when(scenePermissions.isAllowed(eq(SCENE_APP_ID), eq(AccessType.Read), eq(USERNAME), eq(false)))
      .thenReturn(true);
    when(scenePermissions.isAllowed(eq(SCENE_APP_ID_2), eq(AccessType.Read), eq(USERNAME), eq(false)))
      .thenReturn(false);

    String json = tools.sceneList(null, null);

    JsonNode root = mapper.readTree(json);
    // `total` envelope still reports the unfiltered total per the REST contract.
    assertEquals(2L, root.get("total").asLong());
    JsonNode items = root.get("items");
    assertEquals(1, items.size());
    assertEquals(SCENE_APP_ID, items.get(0).get("appId").asText());
  }

  @Test
  void sceneListRejectsUnauthenticatedCaller() {
    when(authenticationContext.getCurrentUserName()).thenReturn(null);

    McpException ex = assertThrows(McpException.class,
      () -> tools.sceneList(null, null));
    // NotAuthorizedException → AUTH_REQUIRED (-32001).
    assertEquals(-32001, ex.getJsonRpcErrorCode());
    verify(sceneGraphService, org.mockito.Mockito.never())
      .listScenes(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
  }
}
