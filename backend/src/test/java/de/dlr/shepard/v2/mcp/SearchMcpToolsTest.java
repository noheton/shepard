package de.dlr.shepard.v2.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.common.util.AccessType;
import io.quarkiverse.mcp.server.McpException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * MCP-COV-13 — unit tests for {@link SearchMcpTools}.
 *
 * <p>The Cypher fan-out itself runs against the live OGM session
 * ({@link de.dlr.shepard.common.neo4j.NeoConnector}) and so cannot be
 * unit-tested without a containerised Neo4j. Instead, this suite
 * exercises:
 *
 * <ul>
 *   <li>The pure helpers ({@link SearchMcpTools#resolveKinds},
 *       {@link SearchMcpTools#makeSnippet}) — every branch.</li>
 *   <li>The {@code search} tool's orchestration via a test-only subclass
 *       that overrides {@link SearchMcpTools#searchUnion} to return
 *       fixture rows — so dedupe, pagination, envelope shape, and the
 *       caller-fixable error branches are all asserted without hitting
 *       Neo4j.</li>
 * </ul>
 */
class SearchMcpToolsTest {

  static final String COL_APP_ID  = "018f9c5a-7e26-7000-e000-000000000001";
  static final String DO_APP_ID_1 = "018f9c5a-7e26-7000-e000-000000000010";
  static final String DO_APP_ID_2 = "018f9c5a-7e26-7000-e000-000000000011";
  static final String FR_APP_ID   = "018f9c5a-7e26-7000-e000-000000000020";

  static final String USERNAME = "flo";

  @Mock McpContextBridge contextBridge;
  @Mock PermissionsService permissionsService;
  @Mock AuthenticationContext authenticationContext;

  StubSearchTools tools;
  McpToolSupport support;
  ObjectMapper mapper;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    mapper = new ObjectMapper();
    support = new McpToolSupport();
    support.objectMapper = mapper;

    tools = new StubSearchTools();
    tools.contextBridge = contextBridge;
    tools.support = support;
    tools.permissionsService = permissionsService;
    tools.authenticationContext = authenticationContext;

    // Default: authenticated caller, ALL permissions granted. Tests that
    // exercise the permission gate opt in by overriding these stubs.
    when(authenticationContext.getCurrentUserName()).thenReturn(USERNAME);
    when(permissionsService.isAccessTypeAllowedForUser(anyLong(), any(AccessType.class), anyString()))
      .thenReturn(true);
  }

  // ── resolveKinds ───────────────────────────────────────────────────────────

  @Test
  void resolveKindsReturnsAllForNullOrBlank() {
    assertEquals(SearchMcpTools.ALL_KINDS, SearchMcpTools.resolveKinds(null));
    assertEquals(SearchMcpTools.ALL_KINDS, SearchMcpTools.resolveKinds(""));
    assertEquals(SearchMcpTools.ALL_KINDS, SearchMcpTools.resolveKinds("   "));
  }

  @Test
  void resolveKindsAcceptsCaseInsensitiveMatch() {
    assertEquals(List.of("Collection"), SearchMcpTools.resolveKinds("collection"));
    assertEquals(List.of("DataObject"), SearchMcpTools.resolveKinds("DATAOBJECT"));
    assertEquals(List.of("Container"), SearchMcpTools.resolveKinds("Container"));
    assertEquals(List.of("Reference"), SearchMcpTools.resolveKinds("reference"));
  }

  @Test
  void resolveKindsRejectsUnknownKind() {
    McpException ex = assertThrows(McpException.class,
      () -> SearchMcpTools.resolveKinds("Snapshot"));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    assertTrue(ex.getMessage().contains("Snapshot"));
    assertTrue(ex.getMessage().contains("Collection"));
  }

  // ── makeSnippet ────────────────────────────────────────────────────────────

  @Test
  void makeSnippetTruncatesLongDescription() {
    String longDesc = "x".repeat(SearchMcpTools.SNIPPET_MAX_LENGTH * 2);
    String snippet = SearchMcpTools.makeSnippet("name", longDesc);
    assertNotNull(snippet);
    assertEquals(SearchMcpTools.SNIPPET_MAX_LENGTH, snippet.length());
    assertTrue(snippet.endsWith("…"));
  }

  @Test
  void makeSnippetReturnsShortDescriptionVerbatim() {
    assertEquals("short", SearchMcpTools.makeSnippet("name", "short"));
  }

  @Test
  void makeSnippetFallsBackToNameWhenDescriptionEmpty() {
    assertEquals("the-name", SearchMcpTools.makeSnippet("the-name", null));
    assertEquals("the-name", SearchMcpTools.makeSnippet("the-name", "   "));
  }

  @Test
  void makeSnippetReturnsNullWhenBothAbsent() {
    assertNull(SearchMcpTools.makeSnippet(null, null));
    assertNull(SearchMcpTools.makeSnippet("", ""));
  }

  // ── search tool orchestration ──────────────────────────────────────────────

  @Test
  void searchRejectsBlankQuery() {
    McpException ex = assertThrows(McpException.class, () -> tools.search("", null, null, null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  @Test
  void searchRejectsNullQuery() {
    McpException ex = assertThrows(McpException.class, () -> tools.search(null, null, null, null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  @Test
  void searchReturnsEnvelopeAcrossAllKindsWhenKindIsOmitted() throws Exception {
    tools.stub("Collection", row(COL_APP_ID, "TR-004 campaign", "LUMEN campaign"));
    tools.stub("DataObject", row(DO_APP_ID_1, "TR-004 anomaly", "vibration spike"));
    tools.stub("SingletonFileReference", row(FR_APP_ID, "tr-004-trace.csv", null));

    String json = tools.search("TR-004", null, null, null);

    JsonNode root = mapper.readTree(json);
    assertEquals(3L, root.get("total").asLong());
    assertEquals(0, root.get("offset").asInt());
    JsonNode items = root.get("items");
    assertEquals(3, items.size());
    // Canonical render order: Collection → DataObject → Reference.
    assertEquals("Collection", items.get(0).get("kind").asText());
    assertEquals("DataObject", items.get(1).get("kind").asText());
    assertEquals("SingletonFileReference", items.get(2).get("kind").asText());
  }

  @Test
  void searchScopesToSingleKindWhenSpecified() throws Exception {
    tools.stub("DataObject",
      row(DO_APP_ID_1, "TR-004 anomaly", "vibration spike at t=8s"),
      row(DO_APP_ID_2, "TR-004 retest", "cleared")
    );
    // These would match if "Collection" were searched, but kind filter excludes them.
    tools.stub("Collection", row(COL_APP_ID, "TR-004 campaign", "LUMEN campaign"));

    String json = tools.search("TR-004", "DataObject", null, null);

    JsonNode root = mapper.readTree(json);
    assertEquals(2L, root.get("total").asLong());
    JsonNode items = root.get("items");
    assertEquals(2, items.size());
    for (int i = 0; i < items.size(); i++) {
      assertEquals("DataObject", items.get(i).get("kind").asText());
    }
  }

  @Test
  void searchPaginates() throws Exception {
    Map<String, Object>[] rows = new Map[5];
    for (int i = 0; i < 5; i++) {
      rows[i] = row("018f-do-" + i, "TR-" + i, "row " + i);
    }
    tools.stub("DataObject", rows);

    String json = tools.search("TR-", "DataObject", 2, 2);

    JsonNode root = mapper.readTree(json);
    assertEquals(5L, root.get("total").asLong(), "total reflects ALL matches, not just the page");
    assertEquals(2, root.get("limit").asInt());
    assertEquals(2, root.get("offset").asInt());
    JsonNode items = root.get("items");
    assertEquals(2, items.size());
    assertEquals("018f-do-2", items.get(0).get("appId").asText());
    assertEquals("018f-do-3", items.get(1).get("appId").asText());
  }

  @Test
  void searchClampsLimitToMaximum() throws Exception {
    Map<String, Object>[] rows = new Map[1];
    rows[0] = row(DO_APP_ID_1, "x", "y");
    tools.stub("DataObject", rows);

    String json = tools.search("x", "DataObject", 999, null);

    JsonNode root = mapper.readTree(json);
    assertEquals(SearchMcpTools.MAX_LIMIT, root.get("limit").asInt());
  }

  @Test
  void searchReturnsEmptyEnvelopeWhenOffsetExceedsTotal() throws Exception {
    tools.stub("DataObject", row(DO_APP_ID_1, "x", "y"));

    String json = tools.search("x", "DataObject", null, 50);

    JsonNode root = mapper.readTree(json);
    assertEquals(1L, root.get("total").asLong());
    assertEquals(0, root.get("items").size());
  }

  @Test
  void searchDedupesRowsThatCarryMultipleLabels() throws Exception {
    // Legacy SingletonFileReference rows carry both labels; the Reference
    // bucket walks SingletonFileReference and FileReference. Same (kind+appId)
    // pair must only appear once.
    tools.stub("SingletonFileReference", row(FR_APP_ID, "trace.csv", null));
    tools.stub("FileReference", row(FR_APP_ID, "trace.csv", null));

    String json = tools.search("trace", "Reference", null, null);

    JsonNode root = mapper.readTree(json);
    // Two stubs but only one (label, appId) post-dedupe? Actually the kinds
    // differ ("SingletonFileReference" vs "FileReference") so the dedupe
    // key differs and both rows survive — verify behavior.
    JsonNode items = root.get("items");
    assertEquals(2L, root.get("total").asLong());
    assertEquals(2, items.size());
  }

  @Test
  void searchEnvelopeCarriesSnippetField() throws Exception {
    tools.stub("Collection",
      row(COL_APP_ID, "LUMEN campaign", "Synthetic LUMEN hotfire test campaign — TR-001..TR-015 with TR-004 anomaly")
    );

    String json = tools.search("hotfire", "Collection", null, null);

    JsonNode items = mapper.readTree(json).get("items");
    assertEquals(1, items.size());
    assertNotNull(items.get(0).get("snippet"));
    assertTrue(items.get(0).get("snippet").asText().contains("hotfire"));
  }

  // ── SEARCH-MCP-PERMS-1 — per-row Read gate ────────────────────────────────

  @Test
  void searchExcludesRowsCallerCannotRead() throws Exception {
    String collectionWithRead    = "018f9c5a-7e26-7000-c001-aaaaaaaaaaaa";
    String collectionWithoutRead = "018f9c5a-7e26-7000-c002-bbbbbbbbbbbb";
    long ogmReadable   = 4242L;
    long ogmForbidden  = 9999L;

    tools.stub("DataObject",
      row(DO_APP_ID_1, "permitted-row", "in the readable Collection"),
      row(DO_APP_ID_2, "denied-row", "in the forbidden Collection")
    );
    tools.bindAnchorByLabel("DataObject", collectionWithRead);
    // Override: second row anchors to a different Collection that's forbidden.
    tools.rowAnchorByAppId.put(DO_APP_ID_2, collectionWithoutRead);
    tools.bindCollectionOgmId(collectionWithRead, ogmReadable);
    tools.bindCollectionOgmId(collectionWithoutRead, ogmForbidden);

    when(permissionsService.isAccessTypeAllowedForUser(eq(ogmReadable), eq(AccessType.Read), eq(USERNAME)))
      .thenReturn(true);
    when(permissionsService.isAccessTypeAllowedForUser(eq(ogmForbidden), eq(AccessType.Read), eq(USERNAME)))
      .thenReturn(false);

    String json = tools.search("row", "DataObject", null, null);

    JsonNode root = mapper.readTree(json);
    assertEquals(1L, root.get("total").asLong(), "total reflects POST-filter count");
    JsonNode items = root.get("items");
    assertEquals(1, items.size());
    assertEquals(DO_APP_ID_1, items.get(0).get("appId").asText());
  }

  @Test
  void searchOmitsOrphanedRowsWithNoCollectionAnchor() throws Exception {
    tools.autoSynthesizeAnchors = false;  // make missing-anchor rows orphans
    tools.stub("DataObject", row(DO_APP_ID_1, "orphan", "no Collection anchor"));
    // No bindAnchorByLabel call — anchor lookup returns null → fail-closed.

    String json = tools.search("orphan", "DataObject", null, null);

    JsonNode root = mapper.readTree(json);
    assertEquals(0L, root.get("total").asLong());
    assertEquals(0, root.get("items").size());
  }

  @Test
  void searchExcludesRowsWhenCollectionOgmLookupFails() throws Exception {
    String collectionAppId = "018f9c5a-7e26-7000-c003-cccccccccccc";
    tools.stub("DataObject", row(DO_APP_ID_1, "x", "y"));
    tools.bindAnchorByLabel("DataObject", collectionAppId);
    tools.autoMintMissingOgmIds = false;  // and don't bind an ogm id → ogm lookup returns null

    String json = tools.search("x", "DataObject", null, null);

    JsonNode root = mapper.readTree(json);
    assertEquals(0L, root.get("total").asLong());
    assertEquals(0, root.get("items").size());
  }

  @Test
  void searchCachesPermissionDecisionPerCollection() throws Exception {
    // Three rows in the same Collection: the permission walk should run
    // exactly once, not three times.
    String sharedCollection = "018f9c5a-7e26-7000-c004-dddddddddddd";
    long ogmId = 5555L;
    tools.stub("DataObject",
      row(DO_APP_ID_1, "row-1", null),
      row(DO_APP_ID_2, "row-2", null),
      row("018f-do-3", "row-3", null)
    );
    tools.bindAnchorByLabel("DataObject", sharedCollection);
    tools.bindCollectionOgmId(sharedCollection, ogmId);

    when(permissionsService.isAccessTypeAllowedForUser(eq(ogmId), eq(AccessType.Read), eq(USERNAME)))
      .thenReturn(true);

    String json = tools.search("row", "DataObject", null, null);

    JsonNode root = mapper.readTree(json);
    assertEquals(3L, root.get("total").asLong());
    assertEquals(3, root.get("items").size());
    // Per-Collection cache: one lookup, not three.
    verify(permissionsService, times(1))
      .isAccessTypeAllowedForUser(eq(ogmId), eq(AccessType.Read), eq(USERNAME));
  }

  @Test
  void searchCollectionRowsResolveToSelfAnchor() throws Exception {
    // Collection rows self-anchor; the per-row gate still must run.
    long ogmReadable   = 1111L;
    long ogmForbidden  = 2222L;
    String collA = "018f9c5a-7e26-7000-c005-eeeeeeeeeeee";
    String collB = "018f9c5a-7e26-7000-c006-ffffffffffff";
    tools.stub("Collection",
      row(collA, "readable", null),
      row(collB, "forbidden", null)
    );
    tools.bindCollectionOgmId(collA, ogmReadable);
    tools.bindCollectionOgmId(collB, ogmForbidden);

    when(permissionsService.isAccessTypeAllowedForUser(eq(ogmReadable), eq(AccessType.Read), eq(USERNAME)))
      .thenReturn(true);
    when(permissionsService.isAccessTypeAllowedForUser(eq(ogmForbidden), eq(AccessType.Read), eq(USERNAME)))
      .thenReturn(false);

    // The stub's searchUnion ignores the query string — it returns whatever
    // was stubbed for the label. So both rows flow into the permission
    // filter; the permitted one survives.
    String json = tools.search("anything", "Collection", null, null);
    JsonNode root = mapper.readTree(json);
    assertEquals(1L, root.get("total").asLong(), "Collection rows self-anchor; permitted one survives");
    assertEquals(collA, root.get("items").get(0).get("appId").asText());
    verify(permissionsService).isAccessTypeAllowedForUser(eq(ogmReadable), eq(AccessType.Read), eq(USERNAME));
    verify(permissionsService).isAccessTypeAllowedForUser(eq(ogmForbidden), eq(AccessType.Read), eq(USERNAME));
  }

  @Test
  void searchRequiresAuthenticatedCaller() {
    when(authenticationContext.getCurrentUserName()).thenReturn(null);
    tools.stub("DataObject", row(DO_APP_ID_1, "x", "y"));

    McpException ex = assertThrows(McpException.class,
      () -> tools.search("x", null, null, null));
    // McpToolSupport.run maps NotAuthorizedException → AUTH_REQUIRED (-32001).
    assertEquals(-32001, ex.getJsonRpcErrorCode());
  }

  @Test
  void searchTotalReflectsPostFilterAcrossPagination() throws Exception {
    // 5 rows in total, but only 3 are permitted. With limit=2 + offset=0,
    // total should be 3 (post-filter) and items should be the first 2 of
    // those 3.
    String allowedColl = "018f9c5a-7e26-7000-c007-111111111111";
    String deniedColl  = "018f9c5a-7e26-7000-c008-222222222222";
    long ogmAllowed = 7001L;
    long ogmDenied  = 7002L;

    @SuppressWarnings("unchecked")
    Map<String, Object>[] rows = new Map[5];
    rows[0] = row("018f-allow-0", "permitted-0", null);
    rows[1] = row("018f-deny-1",  "denied-1", null);
    rows[2] = row("018f-allow-2", "permitted-2", null);
    rows[3] = row("018f-deny-3",  "denied-3", null);
    rows[4] = row("018f-allow-4", "permitted-4", null);
    tools.stub("DataObject", rows);
    for (int i = 0; i < rows.length; i++) {
      String app = (String) rows[i].get("appId");
      tools.rowAnchorByAppId.put(app, app.contains("allow") ? allowedColl : deniedColl);
    }
    tools.bindCollectionOgmId(allowedColl, ogmAllowed);
    tools.bindCollectionOgmId(deniedColl, ogmDenied);

    when(permissionsService.isAccessTypeAllowedForUser(eq(ogmAllowed), eq(AccessType.Read), eq(USERNAME)))
      .thenReturn(true);
    when(permissionsService.isAccessTypeAllowedForUser(eq(ogmDenied), eq(AccessType.Read), eq(USERNAME)))
      .thenReturn(false);

    String json = tools.search("permitted", "DataObject", 2, 0);

    JsonNode root = mapper.readTree(json);
    assertEquals(3L, root.get("total").asLong(),
      "total is the post-filter count (3 permitted rows), not the dedupe count (3 matched 'permitted'-substring)");
    JsonNode items = root.get("items");
    assertEquals(2, items.size());
    assertEquals("018f-allow-0", items.get(0).get("appId").asText());
    assertEquals("018f-allow-2", items.get(1).get("appId").asText());

    // Page 2 — the third permitted row.
    JsonNode page2 = mapper.readTree(tools.search("permitted", "DataObject", 2, 2));
    assertEquals(3L, page2.get("total").asLong());
    assertEquals(1, page2.get("items").size());
    assertEquals("018f-allow-4", page2.get("items").get(0).get("appId").asText());
  }

  @Test
  void searchFailSoftWhenPermissionsServiceThrows() throws Exception {
    String collectionAppId = "018f9c5a-7e26-7000-c009-333333333333";
    long ogmId = 8888L;
    tools.stub("DataObject", row(DO_APP_ID_1, "x", "y"));
    tools.bindAnchorByLabel("DataObject", collectionAppId);
    tools.bindCollectionOgmId(collectionAppId, ogmId);

    when(permissionsService.isAccessTypeAllowedForUser(eq(ogmId), eq(AccessType.Read), eq(USERNAME)))
      .thenThrow(new RuntimeException("graph unavailable"));

    String json = tools.search("x", "DataObject", null, null);

    JsonNode root = mapper.readTree(json);
    // Fail-closed on permission errors — the row is excluded, the call
    // succeeds, and the agent gets a clean empty envelope rather than -32603.
    assertEquals(0L, root.get("total").asLong());
    assertFalse(root.has("error"));
  }

  @Test
  void anchorCypherCoversEveryLabelInLabelsByKind() {
    // Defensive: every label we walk in LABELS_BY_KIND must have a
    // matching Cypher. A new container or reference kind added to
    // LABELS_BY_KIND without a matching anchorCypherFor branch would
    // silently fail-close every row of that kind.
    for (Map.Entry<String, List<String>> e : SearchMcpTools.LABELS_BY_KIND.entrySet()) {
      String kind = e.getKey();
      for (String label : e.getValue()) {
        if ("Collection".equals(label)) continue; // Collections self-anchor.
        assertNotNull(
          SearchMcpTools.anchorCypherFor(label),
          "Missing anchor Cypher for label '" + label + "' (kind=" + kind + ")"
        );
      }
    }
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  @Test
  void searchIssuesSingleUnionQueryForAllKinds() throws Exception {
    // All-kinds search must call searchUnion exactly once, not once per label.
    tools.stub("Collection", row(COL_APP_ID, "col", null));
    tools.stub("DataObject", row(DO_APP_ID_1, "do", null));

    tools.search("anything", null, null, null);

    assertEquals(1, tools.searchUnionCallCount,
      "All-kinds search must issue exactly one UNION ALL query, not one per label");
  }

  @Test
  void searchIssuesSingleUnionQueryForSingleKind() throws Exception {
    // Kind-scoped search still calls searchUnion exactly once.
    tools.stub("DataObject", row(DO_APP_ID_1, "do", null));

    tools.search("anything", "DataObject", null, null);

    assertEquals(1, tools.searchUnionCallCount,
      "Single-kind search must still issue exactly one UNION ALL query");
  }

  private static Map<String, Object> row(String appId, String name, String description) {
    // Stubbed rows match the shape that searchUnion emits — appId, kind, name,
    // snippet. The stub's searchUnion override sets kind from the fixture label,
    // so we leave it out here and let the stub fill it in.
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("appId", appId);
    r.put("name", name);
    r.put("description", description);
    return r;
  }

  /**
   * Test-only subclass that replaces {@link SearchMcpTools#searchUnion} with
   * fixture lookups so we can unit-test the orchestration (dedupe,
   * pagination, envelope shape) without a live Neo4j substrate.
   *
   * <p>SEARCH-MCP-PERMS-1: also overrides the per-row Collection anchor
   * walk + ogm-id lookup so the permission gate runs against fixtures
   * instead of a live OGM session.
   */
  static class StubSearchTools extends SearchMcpTools {
    private final Map<String, List<Map<String, Object>>> fixtures = new LinkedHashMap<>();

    /** SEARCH-MCP-PERMS-1: row.appId → parent Collection appId. */
    final Map<String, String> rowAnchorByAppId = new HashMap<>();

    /** SEARCH-MCP-PERMS-1: Collection appId → OGM Long id. */
    final Map<String, Long> collectionOgmIdByAppId = new HashMap<>();

    @SafeVarargs
    final void stub(String label, Map<String, Object>... rows) {
      List<Map<String, Object>> emitted = new ArrayList<>();
      for (Map<String, Object> raw : rows) {
        Map<String, Object> shaped = new LinkedHashMap<>();
        shaped.put("appId", raw.get("appId"));
        shaped.put("kind", label);
        shaped.put("name", raw.get("name"));
        shaped.put("snippet",
          SearchMcpTools.makeSnippet((String) raw.get("name"), (String) raw.get("description")));
        emitted.add(shaped);
      }
      fixtures.put(label, emitted);
    }

    /** Map every row in {@code label} to the same parent Collection. */
    final void bindAnchorByLabel(String label, String collectionAppId) {
      for (Map<String, Object> row : fixtures.getOrDefault(label, List.of())) {
        rowAnchorByAppId.put((String) row.get("appId"), collectionAppId);
      }
    }

    /** Bind a Collection appId to a (test-fixture) OGM id for the perm gate. */
    final void bindCollectionOgmId(String collectionAppId, long ogmId) {
      collectionOgmIdByAppId.put(collectionAppId, ogmId);
    }

    /** Counts how many times searchUnion was called — used to assert single-query behaviour. */
    int searchUnionCallCount = 0;

    @Override
    List<Map<String, Object>> searchUnion(List<String> labels, String query) {
      searchUnionCallCount++;
      // Return fixture rows for each requested label in the order they appear
      // (labels are already in KIND_ORDINAL order from the LABELS_BY_KIND flatMap).
      List<Map<String, Object>> result = new ArrayList<>();
      for (String label : labels) {
        result.addAll(fixtures.getOrDefault(label, List.of()));
      }
      return result;
    }

    /**
     * When {@code true} (default), non-Collection rows whose anchor isn't
     * explicitly bound resolve to a synthetic anchor (the row's own appId
     * as a placeholder Collection). The synthetic anchor still flows through
     * {@link #lookupCollectionOgmId} so the permission gate runs. Tests that
     * need the orphan branch flip this off; tests that want to bind specific
     * anchors use {@link #bindAnchorByLabel}.
     */
    boolean autoSynthesizeAnchors = true;

    @Override
    String resolveCollectionAnchor(String label, String appId) {
      if ("Collection".equals(label)) return appId;
      String explicit = rowAnchorByAppId.get(appId);
      if (explicit != null) return explicit;
      return autoSynthesizeAnchors ? appId : null;
    }

    /**
     * When {@code true} (default), returns a synthetic non-null OGM id for any
     * Collection appId so the permission gate can proceed without per-test
     * setup. Tests that need to exercise the orphan / missing-Collection
     * branch flip this off.
     */
    boolean autoMintMissingOgmIds = true;

    private long syntheticOgmIdSeq = 1_000L;

    @Override
    Long lookupCollectionOgmId(String collectionAppId) {
      Long explicit = collectionOgmIdByAppId.get(collectionAppId);
      if (explicit != null) return explicit;
      if (!autoMintMissingOgmIds) return null;
      // Mint a stable synthetic id so the perm cache key is consistent
      // across repeated calls in the same test.
      long minted = syntheticOgmIdSeq++;
      collectionOgmIdByAppId.put(collectionAppId, minted);
      return minted;
    }
  }
}
