package de.dlr.shepard.v2.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.v2.collectionwatchers.daos.CollectionWatcherDAO;
import de.dlr.shepard.v2.collectionwatchers.entities.CollectionWatcher;
import de.dlr.shepard.v2.collectionwatchers.io.CollectionWatcherIO;
import de.dlr.shepard.v2.collectionwatchers.services.CollectionWatcherService;
import io.quarkiverse.mcp.server.McpException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * MCP-COV-06 — unit tests for {@link WatchMcpTools}.
 */
class WatchMcpToolsTest {

  static final String CALLER       = "alice";
  static final String COLL_APP_ID  = "018f9c5a-7e26-7000-d200-000000000001";
  static final String WATCH_APP_ID = "018f9c5a-7e26-7000-d200-000000000010";

  @Mock CollectionWatcherService collectionWatcherService;
  @Mock CollectionWatcherDAO collectionWatcherDAO;
  @Mock AuthenticationContext authenticationContext;
  @Mock McpContextBridge contextBridge;

  WatchMcpTools tools;
  McpToolSupport support;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    support = new McpToolSupport();
    support.objectMapper = new ObjectMapper();

    tools = new WatchMcpTools();
    tools.collectionWatcherService = collectionWatcherService;
    tools.collectionWatcherDAO = collectionWatcherDAO;
    tools.authenticationContext = authenticationContext;
    tools.contextBridge = contextBridge;
    tools.support = support;

    when(authenticationContext.getCurrentUserName()).thenReturn(CALLER);
  }

  private CollectionWatcher mkWatch(String appId, String collAppId, long since) {
    CollectionWatcher w = new CollectionWatcher();
    w.setAppId(appId);
    w.setCollectionAppId(collAppId);
    w.setUsername(CALLER);
    w.setSince(since);
    return w;
  }

  // ── watch_list ───────────────────────────────────────────────────────────

  @Test
  void listReturnsCurrentUserWatches() throws Exception {
    when(collectionWatcherDAO.findByUsername(CALLER))
      .thenReturn(List.of(
        mkWatch(WATCH_APP_ID, COLL_APP_ID, 1700000000_000L),
        mkWatch("018f-w-2", "018f-c-2", 1700000001_000L)
      ));

    String json = tools.watchList();
    JsonNode arr = new ObjectMapper().readTree(json);
    assertTrue(arr.isArray());
    assertEquals(2, arr.size());
    assertEquals(WATCH_APP_ID, arr.get(0).get("watcherAppId").asText());
    assertEquals(COLL_APP_ID, arr.get(0).get("collectionAppId").asText());
    assertEquals(CALLER, arr.get(0).get("username").asText());
  }

  @Test
  void listReturnsEmptyArrayWhenNoWatches() throws Exception {
    when(collectionWatcherDAO.findByUsername(CALLER)).thenReturn(List.of());
    String json = tools.watchList();
    JsonNode arr = new ObjectMapper().readTree(json);
    assertTrue(arr.isArray());
    assertEquals(0, arr.size());
  }

  @Test
  void listRejectsUnauthenticated() {
    when(authenticationContext.getCurrentUserName()).thenReturn(null);
    McpException ex = assertThrows(McpException.class, () -> tools.watchList());
    assertEquals(-32001, ex.getJsonRpcErrorCode());
  }

  // ── watch_add ────────────────────────────────────────────────────────────

  @Test
  void addReturnsCreatedRow() throws Exception {
    CollectionWatcherIO io = new CollectionWatcherIO(WATCH_APP_ID, CALLER, COLL_APP_ID, "2023-11-14T22:13:20Z");
    when(collectionWatcherService.watch(COLL_APP_ID, CALLER)).thenReturn(io);

    String json = tools.watchAdd(COLL_APP_ID);
    JsonNode row = new ObjectMapper().readTree(json);
    assertEquals(WATCH_APP_ID, row.get("watcherAppId").asText());
    assertEquals(COLL_APP_ID, row.get("collectionAppId").asText());
  }

  @Test
  void addRejectsBlankCollectionAppId() {
    McpException ex = assertThrows(McpException.class, () -> tools.watchAdd(""));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  @Test
  void addPropagatesForbidden() {
    when(collectionWatcherService.watch(COLL_APP_ID, CALLER))
      .thenThrow(new jakarta.ws.rs.ForbiddenException("no read"));
    McpException ex = assertThrows(McpException.class, () -> tools.watchAdd(COLL_APP_ID));
    assertEquals(-32002, ex.getJsonRpcErrorCode());
  }

  // ── watch_remove ─────────────────────────────────────────────────────────

  @Test
  void removeReturnsConfirmation() throws Exception {
    String json = tools.watchRemove(COLL_APP_ID);
    JsonNode row = new ObjectMapper().readTree(json);
    assertTrue(row.get("removed").asBoolean());
    assertEquals(COLL_APP_ID, row.get("collectionAppId").asText());
    verify(collectionWatcherService).unwatch(COLL_APP_ID, CALLER);
  }

  @Test
  void removeRejectsBlankCollectionAppId() {
    McpException ex = assertThrows(McpException.class, () -> tools.watchRemove(""));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }
}
