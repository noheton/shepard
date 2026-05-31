package de.dlr.shepard.v2.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.context.version.entities.Version;
import de.dlr.shepard.context.version.services.VersionService;
import io.quarkiverse.mcp.server.McpException;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * MCP-COV-06 — unit tests for {@link VersionMcpTools}.
 */
class VersionMcpToolsTest {

  static final String COLL_APP_ID = "018f9c5a-7e26-7000-d100-000000000001";
  static final long   COLL_OGM_ID = 7L;
  static final UUID   V_UID       = UUID.fromString("11111111-1111-1111-1111-111111111111");
  static final UUID   V_PRED_UID  = UUID.fromString("22222222-2222-2222-2222-222222222222");

  @Mock VersionService versionService;
  @Mock EntityIdResolver entityIdResolver;
  @Mock McpContextBridge contextBridge;

  VersionMcpTools tools;
  McpToolSupport support;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    support = new McpToolSupport();
    support.entityIdResolver = entityIdResolver;
    support.objectMapper = new ObjectMapper();

    tools = new VersionMcpTools();
    tools.versionService = versionService;
    tools.entityIdResolver = entityIdResolver;
    tools.contextBridge = contextBridge;
    tools.support = support;
  }

  private Version mkVersion(UUID uid, String name, boolean head, Version predecessor, String creator) {
    Version v = new Version(uid);
    v.setName(name);
    v.setDescription("desc-" + name);
    v.setAppId("app-" + uid);
    v.setHEADVersion(head);
    v.setCreatedAt(new Date(1700000000_000L));
    v.setPredecessor(predecessor);
    if (creator != null) {
      User u = new User();
      u.setUsername(creator);
      v.setCreatedBy(u);
    }
    return v;
  }

  // ── version_list ─────────────────────────────────────────────────────────

  @Test
  void listReturnsRowsForCollection() throws Exception {
    when(entityIdResolver.resolveWithLabels(COLL_APP_ID))
      .thenReturn(new EntityIdResolver.LabeledResolution(COLL_OGM_ID, List.of("Collection")));
    when(versionService.getAllVersions(COLL_OGM_ID))
      .thenReturn(List.of(
        mkVersion(V_UID, "v1", true, null, "alice"),
        mkVersion(V_PRED_UID, "v0", false, null, "alice")
      ));

    String json = tools.versionList(COLL_APP_ID);
    JsonNode arr = new ObjectMapper().readTree(json);
    assertTrue(arr.isArray());
    assertEquals(2, arr.size());
    assertEquals(V_UID.toString(), arr.get(0).get("uid").asText());
    assertEquals("v1", arr.get(0).get("name").asText());
    assertTrue(arr.get(0).get("isHEADVersion").asBoolean());
    assertEquals("alice", arr.get(0).get("createdBy").asText());
  }

  @Test
  void listRejectsBlankEntityAppId() {
    McpException ex = assertThrows(McpException.class, () -> tools.versionList(""));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  @Test
  void listRejectsNonCollectionAppId() {
    when(entityIdResolver.resolveWithLabels(COLL_APP_ID))
      .thenReturn(new EntityIdResolver.LabeledResolution(COLL_OGM_ID, List.of("DataObject")));
    McpException ex = assertThrows(McpException.class, () -> tools.versionList(COLL_APP_ID));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    assertTrue(ex.getMessage().contains("Collection"));
  }

  // ── version_get ──────────────────────────────────────────────────────────

  @Test
  void getReturnsRowWithPredecessor() throws Exception {
    Version predecessor = mkVersion(V_PRED_UID, "v0", false, null, "alice");
    Version current = mkVersion(V_UID, "v1", true, predecessor, "alice");
    when(versionService.getVersion(V_UID)).thenReturn(current);

    String json = tools.versionGet(V_UID.toString());
    JsonNode row = new ObjectMapper().readTree(json);
    assertEquals(V_UID.toString(), row.get("uid").asText());
    assertEquals("v1", row.get("name").asText());
    assertEquals(V_PRED_UID.toString(), row.get("predecessorUid").asText());
  }

  @Test
  void getReturnsNullPredecessorWhenAbsent() throws Exception {
    Version current = mkVersion(V_UID, "v1", true, null, "alice");
    when(versionService.getVersion(V_UID)).thenReturn(current);

    String json = tools.versionGet(V_UID.toString());
    JsonNode row = new ObjectMapper().readTree(json);
    assertTrue(row.get("predecessorUid").isNull());
  }

  @Test
  void getRejectsMalformedUuid() {
    McpException ex = assertThrows(McpException.class, () -> tools.versionGet("not-a-uuid"));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  @Test
  void getMapsMissingVersionToInvalidParams() {
    when(versionService.getVersion(V_UID)).thenReturn(null);
    McpException ex = assertThrows(McpException.class, () -> tools.versionGet(V_UID.toString()));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }
}
