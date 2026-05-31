package de.dlr.shepard.v2.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.provenance.daos.ActivityDAO;
import de.dlr.shepard.provenance.entities.Activity;
import io.quarkiverse.mcp.server.McpException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * MCP-COV-11-PROV — unit tests for {@link ActivityMcpTools}.
 *
 * <p>Manual CDI wiring, Mockito mocks, real {@link McpToolSupport} with a
 * real {@link ObjectMapper} — same shape as {@link LineageMcpToolsTest}.
 */
class ActivityMcpToolsTest {

  static final String ENTITY_APP_ID   = "018f9c5a-7e26-7000-c000-000000000001";
  static final String ACTIVITY_APP_ID = "018f9c5a-7e26-7000-c000-000000000099";

  @Mock ActivityDAO activityDAO;
  @Mock McpContextBridge contextBridge;

  ActivityMcpTools tools;
  McpToolSupport support;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    support = new McpToolSupport();
    support.objectMapper = new ObjectMapper();

    tools = new ActivityMcpTools();
    tools.activityDAO = activityDAO;
    tools.contextBridge = contextBridge;
    tools.support = support;
  }

  private Activity makeActivity(String appId, String userId, String actionKind,
      String path, String method, Long startedAt, String sourceMode) {
    Activity a = new Activity();
    a.setAppId(appId);
    a.setAgentUsername(userId);
    a.setActionKind(actionKind);
    a.setPath(path);
    a.setMethod(method);
    a.setStartedAtMillis(startedAt);
    a.setSourceMode(sourceMode);
    return a;
  }

  // ── prov_query ────────────────────────────────────────────────────────────

  @Test
  void provQueryReturnsActivitiesWithRelation() throws Exception {
    Activity a = makeActivity(ACTIVITY_APP_ID, "alice", "CREATE",
        "/v2/dataobjects", "POST", 1_000_000L, "human");
    when(activityDAO.findByEntityAppId(eq(ENTITY_APP_ID), eq(ActivityMcpTools.PROV_DEFAULT_DEPTH)))
        .thenReturn(List.of(new ActivityDAO.ActivityEdgeRow(a, "GENERATED")));

    String json = tools.provQuery(ENTITY_APP_ID, null);

    JsonNode root = new ObjectMapper().readTree(json);
    assertEquals(ENTITY_APP_ID, root.get("entityAppId").asText());
    assertEquals(ActivityMcpTools.PROV_DEFAULT_DEPTH, root.get("depth").asInt());
    assertEquals(1, root.get("count").asInt());

    JsonNode first = root.get("activities").get(0);
    assertEquals(ACTIVITY_APP_ID, first.get("appId").asText());
    assertEquals("GENERATED", first.get("relation").asText());
    assertEquals("alice", first.get("userId").asText());
    assertEquals("CREATE", first.get("actionKind").asText());
    assertEquals("/v2/dataobjects", first.get("resourcePath").asText());
    assertEquals("POST", first.get("httpMethod").asText());
    assertEquals(1_000_000L, first.get("startedAtMillis").asLong());
    assertEquals("human", first.get("sourceMode").asText());
  }

  @Test
  void provQueryReturnsEmptyListWhenNoActivities() throws Exception {
    when(activityDAO.findByEntityAppId(eq(ENTITY_APP_ID), eq(ActivityMcpTools.PROV_DEFAULT_DEPTH)))
        .thenReturn(List.of());

    String json = tools.provQuery(ENTITY_APP_ID, null);

    JsonNode root = new ObjectMapper().readTree(json);
    assertEquals(0, root.get("count").asInt());
    assertEquals(0, root.get("activities").size());
  }

  @Test
  void provQueryClampsDepthToMax() {
    when(activityDAO.findByEntityAppId(eq(ENTITY_APP_ID), eq(ActivityMcpTools.PROV_MAX_DEPTH)))
        .thenReturn(List.of());
    tools.provQuery(ENTITY_APP_ID, 999);
    verify(activityDAO).findByEntityAppId(ENTITY_APP_ID, ActivityMcpTools.PROV_MAX_DEPTH);
  }

  @Test
  void provQueryClampsDepthToOne() {
    when(activityDAO.findByEntityAppId(eq(ENTITY_APP_ID), eq(1)))
        .thenReturn(List.of());
    tools.provQuery(ENTITY_APP_ID, 0);
    verify(activityDAO).findByEntityAppId(ENTITY_APP_ID, 1);
  }

  @Test
  void provQueryRejectsBlankEntityAppId() {
    McpException ex = assertThrows(McpException.class, () -> tools.provQuery("  ", null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  @Test
  void provQueryRejectsNullEntityAppId() {
    McpException ex = assertThrows(McpException.class, () -> tools.provQuery(null, null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  // ── activity_list ─────────────────────────────────────────────────────────

  @Test
  void activityListReturnsRecentActivitiesWithNoFilter() throws Exception {
    Activity a = makeActivity(ACTIVITY_APP_ID, "bob", "UPDATE",
        "/v2/collections/abc/data-objects/xyz", "PUT", 2_000_000L, "ai");
    when(activityDAO.listForMcp(isNull(), isNull(), isNull(),
        eq(ActivityMcpTools.LIST_DEFAULT_LIMIT)))
        .thenReturn(List.of(a));

    String json = tools.activityList(null, null, null, null);

    JsonNode root = new ObjectMapper().readTree(json);
    assertEquals(ActivityMcpTools.LIST_DEFAULT_LIMIT, root.get("limit").asInt());
    assertEquals(1, root.get("count").asInt());

    JsonNode first = root.get("activities").get(0);
    assertEquals(ACTIVITY_APP_ID, first.get("appId").asText());
    assertEquals("bob", first.get("userId").asText());
    assertEquals("UPDATE", first.get("actionKind").asText());
    assertEquals("ai", first.get("sourceMode").asText());

    // relation field must NOT be present in activity_list output
    assertTrue(first.get("relation") == null || first.get("relation").isNull());
  }

  @Test
  void activityListFiltersByUserId() {
    when(activityDAO.listForMcp(eq("alice"), isNull(), isNull(),
        eq(ActivityMcpTools.LIST_DEFAULT_LIMIT)))
        .thenReturn(List.of());
    tools.activityList("alice", null, null, null);
    verify(activityDAO).listForMcp("alice", null, null, ActivityMcpTools.LIST_DEFAULT_LIMIT);
  }

  @Test
  void activityListFiltersByResourcePath() {
    when(activityDAO.listForMcp(isNull(), eq("/v2/collections/"), isNull(),
        eq(ActivityMcpTools.LIST_DEFAULT_LIMIT)))
        .thenReturn(List.of());
    tools.activityList(null, "/v2/collections/", null, null);
    verify(activityDAO).listForMcp(null, "/v2/collections/", null, ActivityMcpTools.LIST_DEFAULT_LIMIT);
  }

  @Test
  void activityListClampsLimitToMax() {
    when(activityDAO.listForMcp(isNull(), isNull(), isNull(),
        eq(ActivityMcpTools.LIST_MAX_LIMIT)))
        .thenReturn(List.of());
    tools.activityList(null, null, null, 9999);
    verify(activityDAO).listForMcp(null, null, null, ActivityMcpTools.LIST_MAX_LIMIT);
  }

  @Test
  void activityListReturnsFilterEchoInResponse() throws Exception {
    when(activityDAO.listForMcp(eq("charlie"), eq("/v2/"), eq("POST"), eq(5)))
        .thenReturn(List.of());

    String json = tools.activityList("charlie", "/v2/", "POST", 5);

    JsonNode root = new ObjectMapper().readTree(json);
    assertEquals(5, root.get("limit").asInt());
    assertEquals(0, root.get("count").asInt());

    JsonNode filter = root.get("filter");
    assertEquals("charlie", filter.get("userId").asText());
    assertEquals("/v2/", filter.get("resourcePath").asText());
    assertEquals("POST", filter.get("httpMethod").asText());
  }
}
