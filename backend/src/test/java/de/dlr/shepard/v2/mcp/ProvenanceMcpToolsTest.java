package de.dlr.shepard.v2.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.provenance.daos.ActivityDAO;
import de.dlr.shepard.provenance.entities.Activity;
import io.quarkiverse.mcp.server.McpException;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * MCP-COV-11-PROV — unit tests for {@link ProvenanceMcpTools}.
 *
 * <p>Pattern mirrors the other MCP test suites: hand-wired CDI,
 * Mockito mocks, real {@link McpToolSupport} + {@link ObjectMapper}.
 */
class ProvenanceMcpToolsTest {

  static final String ENTITY_APP_ID = "018f9c5a-7e26-7000-c000-000000000042";
  static final long ENTITY_OGM_ID = 99L;
  static final String ACTIVITY_APP_ID = "018f9c5a-7e26-7000-c000-000000000099";

  @Mock ActivityDAO activityDAO;
  @Mock EntityIdResolver entityIdResolver;
  @Mock McpContextBridge contextBridge;

  ProvenanceMcpTools tools;
  McpToolSupport support;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    support = new McpToolSupport();
    support.entityIdResolver = entityIdResolver;
    support.objectMapper = new ObjectMapper();

    tools = new ProvenanceMcpTools();
    tools.activityDAO = activityDAO;
    tools.contextBridge = contextBridge;
    tools.support = support;
    tools.entityIdResolver = entityIdResolver;

    // Default: entity exists (type doesn't matter for prov_query).
    when(entityIdResolver.resolveWithLabels(ENTITY_APP_ID))
      .thenReturn(new EntityIdResolver.LabeledResolution(ENTITY_OGM_ID, List.of("DataObject")));
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private Activity makeActivity(String appId, String actionKind, String targetKind, String targetAppId, String agent, Long startedAt) {
    Activity a = new Activity();
    a.setAppId(appId);
    a.setActionKind(actionKind);
    a.setTargetKind(targetKind);
    a.setTargetAppId(targetAppId);
    a.setAgentUsername(agent);
    a.setSummary("test summary");
    a.setStartedAtMillis(startedAt);
    return a;
  }

  // ── prov_query ────────────────────────────────────────────────────────────

  @Test
  void provQueryReturnsActivitiesForEntity() throws Exception {
    Activity a = makeActivity(ACTIVITY_APP_ID, "UPDATE", "DataObject", ENTITY_APP_ID, "flo", 1_000_000L);
    when(activityDAO.list(isNull(), isNull(), eq(ENTITY_APP_ID), isNull(), isNull(), eq(ProvenanceMcpTools.DEFAULT_LIMIT)))
      .thenReturn(List.of(a));

    String json = tools.provQuery(ENTITY_APP_ID, null);

    JsonNode root = new ObjectMapper().readTree(json);
    assertEquals(ENTITY_APP_ID, root.get("entityAppId").asText());
    assertEquals(ProvenanceMcpTools.DEFAULT_LIMIT, root.get("limit").asInt());
    assertEquals(1, root.get("count").asInt());
    JsonNode act = root.get("activities").get(0);
    assertEquals(ACTIVITY_APP_ID, act.get("appId").asText());
    assertEquals("UPDATE", act.get("actionKind").asText());
    assertEquals("DataObject", act.get("targetKind").asText());
    assertEquals("flo", act.get("agentUsername").asText());
    assertEquals("1970-01-01T00:16:40Z", act.get("startedAt").asText());
  }

  @Test
  void provQueryReturnsEmptyListWhenNoActivities() throws Exception {
    when(activityDAO.list(any(), any(), any(), any(), any(), eq(ProvenanceMcpTools.DEFAULT_LIMIT)))
      .thenReturn(List.of());

    String json = tools.provQuery(ENTITY_APP_ID, null);

    JsonNode root = new ObjectMapper().readTree(json);
    assertEquals(0, root.get("count").asInt());
    assertEquals(0, root.get("activities").size());
  }

  @Test
  void provQueryClampsLimitToMax() {
    when(activityDAO.list(any(), any(), any(), any(), any(), eq(ProvenanceMcpTools.MAX_LIMIT)))
      .thenReturn(List.of());

    tools.provQuery(ENTITY_APP_ID, 9999);

    verify(activityDAO).list(null, null, ENTITY_APP_ID, null, null, ProvenanceMcpTools.MAX_LIMIT);
  }

  @Test
  void provQueryClampsLimitToOne() {
    when(activityDAO.list(any(), any(), any(), any(), any(), eq(1)))
      .thenReturn(List.of());

    tools.provQuery(ENTITY_APP_ID, 0);

    verify(activityDAO).list(null, null, ENTITY_APP_ID, null, null, 1);
  }

  @Test
  void provQueryRejectsBlankEntityAppId() {
    McpException ex = assertThrows(McpException.class, () -> tools.provQuery(" ", null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    assertTrue(ex.getMessage().contains("entityAppId"));
  }

  @Test
  void provQueryRejectsNullEntityAppId() {
    McpException ex = assertThrows(McpException.class, () -> tools.provQuery(null, null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  @Test
  void provQueryRejectsNonExistentEntityAppId() {
    when(entityIdResolver.resolveWithLabels(ENTITY_APP_ID)).thenThrow(new NotFoundException());
    McpException ex = assertThrows(McpException.class, () -> tools.provQuery(ENTITY_APP_ID, null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    assertTrue(ex.getMessage().contains(ENTITY_APP_ID));
  }

  @Test
  void provQueryRedactsUuidShapedUsername() throws Exception {
    String uuidUsername = "12345678-abcd-0000-0000-000000000000";
    Activity a = makeActivity(ACTIVITY_APP_ID, "CREATE", "DataObject", ENTITY_APP_ID, uuidUsername, 500L);
    when(activityDAO.list(any(), any(), any(), any(), any(), eq(ProvenanceMcpTools.DEFAULT_LIMIT)))
      .thenReturn(List.of(a));

    String json = tools.provQuery(ENTITY_APP_ID, null);

    JsonNode root = new ObjectMapper().readTree(json);
    String displayed = root.get("activities").get(0).get("agentUsername").asText();
    // UUID-shaped names are truncated to 8-chars + ellipsis.
    assertTrue(displayed.startsWith("12345678"), "expected redacted prefix, got: " + displayed);
    assertTrue(displayed.contains("…"), "expected ellipsis, got: " + displayed);
  }

  // ── activity_list ─────────────────────────────────────────────────────────

  @Test
  void activityListPassesAllFiltersToDao() {
    when(activityDAO.list("flo", "Collection", ENTITY_APP_ID, 1000L, 2000L, 10))
      .thenReturn(List.of());

    tools.activityList("flo", "Collection", ENTITY_APP_ID, 1000L, 2000L, 10);

    verify(activityDAO).list("flo", "Collection", ENTITY_APP_ID, 1000L, 2000L, 10);
  }

  @Test
  void activityListUsesDefaultLimitWhenOmitted() {
    when(activityDAO.list(isNull(), isNull(), isNull(), isNull(), isNull(), eq(ProvenanceMcpTools.DEFAULT_LIMIT)))
      .thenReturn(List.of());

    tools.activityList(null, null, null, null, null, null);

    verify(activityDAO).list(null, null, null, null, null, ProvenanceMcpTools.DEFAULT_LIMIT);
  }

  @Test
  void activityListTreatsBlankStringsAsNullFilters() {
    when(activityDAO.list(isNull(), isNull(), isNull(), isNull(), isNull(), eq(ProvenanceMcpTools.DEFAULT_LIMIT)))
      .thenReturn(List.of());

    tools.activityList("  ", "  ", "  ", null, null, null);

    verify(activityDAO).list(null, null, null, null, null, ProvenanceMcpTools.DEFAULT_LIMIT);
  }

  @Test
  void activityListReturnsCorrectEnvelope() throws Exception {
    Activity a1 = makeActivity("act-1", "CREATE", "Collection", "coll-1", "anna", 2000L);
    Activity a2 = makeActivity("act-2", "DELETE", "DataObject", "do-1", "bob", 1000L);
    when(activityDAO.list(any(), any(), any(), any(), any(), eq(ProvenanceMcpTools.DEFAULT_LIMIT)))
      .thenReturn(List.of(a1, a2));

    String json = tools.activityList(null, null, null, null, null, null);

    JsonNode root = new ObjectMapper().readTree(json);
    assertEquals(ProvenanceMcpTools.DEFAULT_LIMIT, root.get("limit").asInt());
    assertEquals(2, root.get("count").asInt());
    JsonNode acts = root.get("activities");
    assertEquals("act-1", acts.get(0).get("appId").asText());
    assertEquals("CREATE", acts.get(0).get("actionKind").asText());
    assertEquals("act-2", acts.get(1).get("appId").asText());
    assertEquals("DELETE", acts.get(1).get("actionKind").asText());
  }

  @Test
  void activityListIncludesSourceModeWhenPresent() throws Exception {
    Activity a = makeActivity(ACTIVITY_APP_ID, "UPDATE", "DataObject", ENTITY_APP_ID, "agent-x", 3000L);
    a.setSourceMode("ai");
    a.setAgentId("claude-sonnet-4-6");
    when(activityDAO.list(any(), any(), any(), any(), any(), eq(ProvenanceMcpTools.DEFAULT_LIMIT)))
      .thenReturn(List.of(a));

    String json = tools.activityList(null, null, null, null, null, null);

    JsonNode root = new ObjectMapper().readTree(json);
    JsonNode act = root.get("activities").get(0);
    assertEquals("ai", act.get("sourceMode").asText());
    assertEquals("claude-sonnet-4-6", act.get("agentId").asText());
  }

  @Test
  void activityListOmitsSourceModeWhenNull() throws Exception {
    Activity a = makeActivity(ACTIVITY_APP_ID, "CREATE", "Collection", "coll-1", "flo", 1L);
    when(activityDAO.list(any(), any(), any(), any(), any(), eq(ProvenanceMcpTools.DEFAULT_LIMIT)))
      .thenReturn(List.of(a));

    String json = tools.activityList(null, null, null, null, null, null);

    JsonNode root = new ObjectMapper().readTree(json);
    JsonNode act = root.get("activities").get(0);
    assertTrue(act.get("sourceMode") == null || act.get("sourceMode").isNull(),
      "sourceMode should be absent or null for human activities");
    assertTrue(act.get("agentId") == null || act.get("agentId").isNull(),
      "agentId should be absent or null for human activities");
  }

  @Test
  void activityListIncludesAppliedFiltersInEnvelope() throws Exception {
    when(activityDAO.list(eq("flo"), any(), any(), any(), any(), anyInt()))
      .thenReturn(List.of());

    String json = tools.activityList("flo", null, null, 100L, 200L, 5);

    JsonNode root = new ObjectMapper().readTree(json);
    assertEquals("flo", root.get("agentUsername").asText());
    assertEquals(100L, root.get("sinceMillis").asLong());
    assertEquals(200L, root.get("untilMillis").asLong());
    assertEquals(5, root.get("limit").asInt());
  }
}
