package de.dlr.shepard.v2.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.snapshot.entities.Snapshot;
import de.dlr.shepard.context.snapshot.services.SnapshotService;
import io.quarkiverse.mcp.server.McpException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * MCP-COV-06-SNAPSHOTS — unit tests for {@link SnapshotMcpTools}.
 *
 * <p>Pattern: manual CDI wiring with Mockito mocks, a real {@link McpToolSupport}
 * with an {@link ObjectMapper}, no container startup. Mirrors the shape of
 * {@link CollectionMcpToolsTest} and {@link AnnotationMcpToolsTest}.
 *
 * <p>Coverage:
 * <ul>
 *   <li>Happy-path for each of the five tools.</li>
 *   <li>Empty-result test for {@code snapshot_list}.</li>
 *   <li>Not-found tests for {@code snapshot_get}, {@code snapshot_delete},
 *       {@code snapshot_diff}.</li>
 *   <li>Self-diff rejection for {@code snapshot_diff}.</li>
 *   <li>Missing-arg guards (blank appId, blank name).</li>
 * </ul>
 */
class SnapshotMcpToolsTest {

  static final String COLL_APP_ID  = "018f9c5a-7e26-7000-c000-000000000010";
  static final String SNAP_A_ID    = "018f9c5a-7e26-7000-c000-000000000020";
  static final String SNAP_B_ID    = "018f9c5a-7e26-7000-c000-000000000030";
  static final long   SNAP_A_OGM   = 101L;
  static final long   SNAP_B_OGM   = 102L;
  static final long   CAP_AT_MS_A  = 1_700_000_000_000L;
  static final long   CAP_AT_MS_B  = 1_700_000_001_000L;

  @Mock SnapshotService snapshotService;
  @Mock McpContextBridge contextBridge;
  @Mock AuthenticationContext authContext;

  SnapshotMcpTools tools;
  McpToolSupport support;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    support = new McpToolSupport();
    support.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    // entityIdResolver is not used by SnapshotMcpTools — no resolveOfType call.

    tools = new SnapshotMcpTools();
    tools.snapshotService = snapshotService;
    tools.authenticationContext = authContext;
    tools.contextBridge = contextBridge;
    tools.support = support;
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  static Snapshot makeSnapshot(long ogmId, String appId, String name, long capturedAtMs) {
    Collection coll = new Collection();
    coll.setAppId(COLL_APP_ID);
    coll.setName("Test Collection");

    Snapshot s = new Snapshot();
    // AbstractEntity exposes the OGM id via setId (Lombok @Setter on protected Long id).
    // This is package-visible in tests via the test-only AbstractEntity(long) constructor
    // only if same package — instead we use the public Lombok setter.
    s.setId(ogmId);
    s.setAppId(appId);
    s.setName(name);
    s.setDescription("Test description for " + name);
    s.setSnapshotCapturedAtMs(capturedAtMs);
    s.setSnapshotCreatedByUsername("alice");
    s.setCollection(coll);
    s.setEntryCount(3);
    return s;
  }

  // ── snapshot_list ──────────────────────────────────────────────────────────

  @Test
  void snapshotListReturnsItemsArray() throws Exception {
    Snapshot s = makeSnapshot(SNAP_A_OGM, SNAP_A_ID, "v1.0", CAP_AT_MS_A);
    when(snapshotService.listByCollection(eq(COLL_APP_ID), anyInt(), anyInt()))
      .thenReturn(List.of(s));

    String json = tools.snapshotList(COLL_APP_ID, null, null);

    JsonNode root = new ObjectMapper().readTree(json);
    assertTrue(root.isArray());
    assertEquals(1, root.size());
    JsonNode row = root.get(0);
    assertEquals(SNAP_A_ID, row.get("appId").asText());
    assertEquals("v1.0", row.get("name").asText());
    assertEquals(COLL_APP_ID, row.get("collectionAppId").asText());
    assertEquals("Test Collection", row.get("collectionName").asText());
    assertNotNull(row.get("createdAt"));
  }

  @Test
  void snapshotListReturnsEmptyArrayWhenNone() throws Exception {
    when(snapshotService.listByCollection(anyString(), anyInt(), anyInt()))
      .thenReturn(Collections.emptyList());

    String json = tools.snapshotList(COLL_APP_ID, null, null);

    JsonNode root = new ObjectMapper().readTree(json);
    assertTrue(root.isArray());
    assertEquals(0, root.size());
  }

  @Test
  void snapshotListCapsSizeAt100() {
    when(snapshotService.listByCollection(anyString(), anyInt(), anyInt()))
      .thenReturn(Collections.emptyList());

    // Should not throw even with oversized request
    tools.snapshotList(COLL_APP_ID, 0, 9999);
    verify(snapshotService).listByCollection(eq(COLL_APP_ID), eq(0), eq(100));
  }

  @Test
  void snapshotListThrowsOnBlankCollectionAppId() {
    McpException ex = assertThrows(McpException.class,
      () -> tools.snapshotList("", null, null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    verify(snapshotService, never()).listByCollection(anyString(), anyInt(), anyInt());
  }

  // ── snapshot_get ──────────────────────────────────────────────────────────

  @Test
  void snapshotGetReturnsFullMetadata() throws Exception {
    Snapshot s = makeSnapshot(SNAP_A_OGM, SNAP_A_ID, "v1.0", CAP_AT_MS_A);
    when(snapshotService.findByAppId(SNAP_A_ID)).thenReturn(s);

    String json = tools.snapshotGet(SNAP_A_ID);

    JsonNode root = new ObjectMapper().readTree(json);
    assertEquals(SNAP_A_ID, root.get("appId").asText());
    assertEquals("v1.0", root.get("name").asText());
    assertEquals("Test description for v1.0", root.get("description").asText());
    assertEquals("alice", root.get("snapshotCreatedByUsername").asText());
    assertEquals(COLL_APP_ID, root.get("collectionAppId").asText());
    assertEquals(3, root.get("entryCount").asInt());
    assertNotNull(root.get("snapshotCapturedAt"));
  }

  @Test
  void snapshotGetThrowsInvalidParamsWhenNotFound() {
    when(snapshotService.findByAppId(SNAP_A_ID)).thenReturn(null);

    McpException ex = assertThrows(McpException.class,
      () -> tools.snapshotGet(SNAP_A_ID));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    assertTrue(ex.getMessage().contains(SNAP_A_ID));
  }

  @Test
  void snapshotGetThrowsOnBlankId() {
    McpException ex = assertThrows(McpException.class,
      () -> tools.snapshotGet(""));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    verify(snapshotService, never()).findByAppId(anyString());
  }

  // ── snapshot_create ───────────────────────────────────────────────────────

  @Test
  void snapshotCreateReturnsCreatedSnapshot() throws Exception {
    when(authContext.getCurrentUserName()).thenReturn("alice");
    Snapshot created = makeSnapshot(SNAP_A_OGM, SNAP_A_ID, "v1.0", CAP_AT_MS_A);
    when(snapshotService.createSnapshot(COLL_APP_ID, "v1.0", "A description", "alice"))
      .thenReturn(created);

    String json = tools.snapshotCreate(COLL_APP_ID, "v1.0", "A description");

    JsonNode root = new ObjectMapper().readTree(json);
    assertEquals(SNAP_A_ID, root.get("appId").asText());
    assertEquals("v1.0", root.get("name").asText());
    assertEquals(3, root.get("entryCount").asInt());
  }

  @Test
  void snapshotCreateWorksWithNullDescription() throws Exception {
    when(authContext.getCurrentUserName()).thenReturn("alice");
    Snapshot created = makeSnapshot(SNAP_A_OGM, SNAP_A_ID, "minimal", CAP_AT_MS_A);
    when(snapshotService.createSnapshot(COLL_APP_ID, "minimal", null, "alice"))
      .thenReturn(created);

    String json = tools.snapshotCreate(COLL_APP_ID, "minimal", null);
    assertNotNull(json);
    JsonNode root = new ObjectMapper().readTree(json);
    assertEquals(SNAP_A_ID, root.get("appId").asText());
  }

  @Test
  void snapshotCreateThrowsAuthRequiredWhenNoUser() {
    when(authContext.getCurrentUserName()).thenReturn(null);

    McpException ex = assertThrows(McpException.class,
      () -> tools.snapshotCreate(COLL_APP_ID, "v1.0", null));
    assertEquals(-32001, ex.getJsonRpcErrorCode());
    verify(snapshotService, never()).createSnapshot(anyString(), anyString(), any(), anyString());
  }

  @Test
  void snapshotCreateThrowsWhenNameIsBlank() {
    when(authContext.getCurrentUserName()).thenReturn("alice");

    McpException ex = assertThrows(McpException.class,
      () -> tools.snapshotCreate(COLL_APP_ID, "", null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    verify(snapshotService, never()).createSnapshot(anyString(), anyString(), any(), anyString());
  }

  @Test
  void snapshotCreateThrowsWhenCollectionAppIdIsBlank() {
    when(authContext.getCurrentUserName()).thenReturn("alice");

    McpException ex = assertThrows(McpException.class,
      () -> tools.snapshotCreate("", "v1.0", null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    verify(snapshotService, never()).createSnapshot(anyString(), anyString(), any(), anyString());
  }

  // ── snapshot_delete ───────────────────────────────────────────────────────

  @Test
  void snapshotDeleteReturnsConfirmationString() {
    when(snapshotService.deleteSnapshot(SNAP_A_ID)).thenReturn(true);

    String result = tools.snapshotDelete(SNAP_A_ID);

    assertNotNull(result);
    assertTrue(result.contains(SNAP_A_ID));
    assertTrue(result.toLowerCase().contains("deleted"));
    verify(snapshotService).deleteSnapshot(SNAP_A_ID);
  }

  @Test
  void snapshotDeleteThrowsInvalidParamsWhenNotFound() {
    when(snapshotService.deleteSnapshot(SNAP_A_ID)).thenReturn(false);

    McpException ex = assertThrows(McpException.class,
      () -> tools.snapshotDelete(SNAP_A_ID));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    assertTrue(ex.getMessage().contains(SNAP_A_ID));
  }

  @Test
  void snapshotDeleteThrowsOnBlankId() {
    McpException ex = assertThrows(McpException.class,
      () -> tools.snapshotDelete("   "));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    verify(snapshotService, never()).deleteSnapshot(anyString());
  }

  // ── snapshot_diff ─────────────────────────────────────────────────────────

  @Test
  void snapshotDiffReturnsStructuredDiff() throws Exception {
    Snapshot a = makeSnapshot(SNAP_A_OGM, SNAP_A_ID, "base", CAP_AT_MS_A);
    Snapshot b = makeSnapshot(SNAP_B_OGM, SNAP_B_ID, "head", CAP_AT_MS_B);
    when(snapshotService.findByAppId(SNAP_A_ID)).thenReturn(a);
    when(snapshotService.findByAppId(SNAP_B_ID)).thenReturn(b);

    // A has entity-1 (rev 1) and entity-2 (rev 1)
    // B has entity-1 (rev 2, changed) and entity-3 (new)
    when(snapshotService.getEntryRevisionMap(SNAP_A_OGM))
      .thenReturn(Map.of("entity-1", 1L, "entity-2", 1L));
    when(snapshotService.getEntryRevisionMap(SNAP_B_OGM))
      .thenReturn(Map.of("entity-1", 2L, "entity-3", 1L));

    String json = tools.snapshotDiff(SNAP_A_ID, SNAP_B_ID);

    JsonNode root = new ObjectMapper().readTree(json);
    assertEquals(SNAP_A_ID, root.get("snapshotAAppId").asText());
    assertEquals(SNAP_B_ID, root.get("snapshotBAppId").asText());
    assertEquals(CAP_AT_MS_A, root.get("snapshotACapturedAtMs").asLong());
    assertEquals(CAP_AT_MS_B, root.get("snapshotBCapturedAtMs").asLong());

    // entity-3 was added
    JsonNode added = root.get("added");
    assertTrue(added.isArray());
    assertEquals(1, added.size());
    assertEquals("entity-3", added.get(0).asText());

    // entity-2 was removed
    JsonNode removed = root.get("removed");
    assertTrue(removed.isArray());
    assertEquals(1, removed.size());
    assertEquals("entity-2", removed.get(0).asText());

    // entity-1 changed from rev 1 to rev 2
    JsonNode changed = root.get("changed");
    assertTrue(changed.isArray());
    assertEquals(1, changed.size());
    JsonNode diffEntry = changed.get(0);
    assertEquals("entity-1", diffEntry.get("entityAppId").asText());
    assertEquals(1L, diffEntry.get("revisionA").asLong());
    assertEquals(2L, diffEntry.get("revisionB").asLong());

    // No unchanged entities
    assertEquals(0, root.get("unchangedCount").asInt());
  }

  @Test
  void snapshotDiffCountsUnchangedEntities() throws Exception {
    Snapshot a = makeSnapshot(SNAP_A_OGM, SNAP_A_ID, "base", CAP_AT_MS_A);
    Snapshot b = makeSnapshot(SNAP_B_OGM, SNAP_B_ID, "head", CAP_AT_MS_B);
    when(snapshotService.findByAppId(SNAP_A_ID)).thenReturn(a);
    when(snapshotService.findByAppId(SNAP_B_ID)).thenReturn(b);
    when(snapshotService.getEntryRevisionMap(SNAP_A_OGM))
      .thenReturn(Map.of("entity-1", 1L, "entity-2", 1L));
    when(snapshotService.getEntryRevisionMap(SNAP_B_OGM))
      .thenReturn(Map.of("entity-1", 1L, "entity-2", 1L)); // identical

    String json = tools.snapshotDiff(SNAP_A_ID, SNAP_B_ID);
    JsonNode root = new ObjectMapper().readTree(json);

    assertEquals(2, root.get("unchangedCount").asInt());
    assertEquals(0, root.get("added").size());
    assertEquals(0, root.get("removed").size());
    assertEquals(0, root.get("changed").size());
  }

  @Test
  void snapshotDiffThrowsOnSelfDiff() {
    McpException ex = assertThrows(McpException.class,
      () -> tools.snapshotDiff(SNAP_A_ID, SNAP_A_ID));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    assertTrue(ex.getMessage().contains("self-diff"));
    verify(snapshotService, never()).findByAppId(anyString());
  }

  @Test
  void snapshotDiffThrowsWhenSnapshotANotFound() {
    when(snapshotService.findByAppId(SNAP_A_ID)).thenReturn(null);

    McpException ex = assertThrows(McpException.class,
      () -> tools.snapshotDiff(SNAP_A_ID, SNAP_B_ID));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    assertTrue(ex.getMessage().contains(SNAP_A_ID));
  }

  @Test
  void snapshotDiffThrowsWhenSnapshotBNotFound() {
    Snapshot a = makeSnapshot(SNAP_A_OGM, SNAP_A_ID, "base", CAP_AT_MS_A);
    when(snapshotService.findByAppId(SNAP_A_ID)).thenReturn(a);
    when(snapshotService.findByAppId(SNAP_B_ID)).thenReturn(null);

    McpException ex = assertThrows(McpException.class,
      () -> tools.snapshotDiff(SNAP_A_ID, SNAP_B_ID));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    assertTrue(ex.getMessage().contains(SNAP_B_ID));
  }

  @Test
  void snapshotDiffThrowsOnBlankSnapshotAAppId() {
    McpException ex = assertThrows(McpException.class,
      () -> tools.snapshotDiff("", SNAP_B_ID));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    verify(snapshotService, never()).findByAppId(anyString());
  }

  @Test
  void snapshotDiffThrowsOnBlankSnapshotBAppId() {
    McpException ex = assertThrows(McpException.class,
      () -> tools.snapshotDiff(SNAP_A_ID, ""));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    verify(snapshotService, never()).findByAppId(anyString());
  }
}
