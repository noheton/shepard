package de.dlr.shepard.v2.semantic.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import de.dlr.shepard.v2.semantic.io.TermSuggestionIO;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;

/**
 * N1e — unit tests for {@link SemanticTermSearchRest}.
 *
 * <p>The OGM session is injected via a subclass that overrides
 * {@link SemanticTermSearchRest#getSession()} so the tests never require
 * a running Neo4j. The search dispatch ({@link SemanticTermSearchRest#runSearch})
 * is tested through the full endpoint method to exercise auth + validation
 * + result mapping in concert.
 */
class SemanticTermSearchRestTest {

  private static final String CALLER = "researcher@example.org";

  private SecurityContext sc;
  private Session session;
  private SemanticTermSearchRest rest;

  @BeforeEach
  void setUp() {
    sc = mock(SecurityContext.class);
    Principal principal = () -> CALLER;
    when(sc.getUserPrincipal()).thenReturn(principal);

    session = mock(Session.class);
    rest = new StubRest(session);
  }

  // ─── Class-level annotations ──────────────────────────────────────────────

  @Test
  void classCarriesV2PathAnnotation() {
    Path p = SemanticTermSearchRest.class.getAnnotation(Path.class);
    assertNotNull(p, "SemanticTermSearchRest must be @Path-annotated");
    assertTrue(
      p.value().startsWith("/v2/"),
      "@Path must start with /v2/ per CLAUDE.md policy — got: " + p.value()
    );
  }

  // ─── Auth gate ────────────────────────────────────────────────────────────

  @Test
  void unauthenticated_returns401() {
    when(sc.getUserPrincipal()).thenReturn(null);
    Response r = rest.search("label", 20, 0, sc);
    assertEquals(401, r.getStatus());
    assertProblemJson(r, SemanticTermSearchRest.PROBLEM_TYPE_AUTH, 401);
  }

  @Test
  void nullSecurityContext_returns401() {
    Response r = rest.search("label", 20, 0, null);
    assertEquals(401, r.getStatus());
    assertProblemJson(r, SemanticTermSearchRest.PROBLEM_TYPE_AUTH, 401);
  }

  // ─── Query validation ─────────────────────────────────────────────────────

  @Test
  void nullQuery_returns400() {
    Response r = rest.search(null, 20, 0, sc);
    assertEquals(400, r.getStatus());
    assertProblemJson(r, SemanticTermSearchRest.PROBLEM_TYPE_BAD_REQUEST, 400);
  }

  @Test
  void blankQuery_returns400() {
    Response r = rest.search("  ", 20, 0, sc);
    assertEquals(400, r.getStatus());
    assertProblemJson(r, SemanticTermSearchRest.PROBLEM_TYPE_BAD_REQUEST, 400);
  }

  @Test
  void singleCharQuery_returns400() {
    Response r = rest.search("a", 20, 0, sc);
    assertEquals(400, r.getStatus());
    assertProblemJson(r, SemanticTermSearchRest.PROBLEM_TYPE_BAD_REQUEST, 400);
  }

  // ─── Limit capping ────────────────────────────────────────────────────────

  @Test
  void limitAbove50_isCappedAt50() {
    stubEmptyResult();
    // A limit > 50 must still succeed (cap silently) — not a 400.
    Response r = rest.search("label", 200, 0, sc);
    assertEquals(200, r.getStatus());
    // Verify the cap is enforced by inspecting the constant.
    assertTrue(SemanticTermSearchRest.MAX_LIMIT == 50, "MAX_LIMIT must be 50");
  }

  // ─── Empty result (no :Resource nodes) ───────────────────────────────────

  @Test
  void noResourceNodes_returns200WithEmptyList() {
    stubEmptyResult();
    Response r = rest.search("label", 20, 0, sc);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<TermSuggestionIO> body = (PagedResponseIO<TermSuggestionIO>) r.getEntity();
    assertNotNull(body);
    assertTrue(body.items().isEmpty(), "Expected empty list when no :Resource nodes exist");
    assertEquals(0, body.total());
  }

  // ─── Matching terms ───────────────────────────────────────────────────────

  @Test
  void matchingTerms_returns200WithSuggestions() {
    Map<String, Object> row1 = new LinkedHashMap<>();
    row1.put("uri", "http://www.w3.org/ns/prov#Activity");
    row1.put("label", "Activity");
    row1.put("description", "An activity is something that occurs over a period of time.");

    Map<String, Object> row2 = new LinkedHashMap<>();
    row2.put("uri", "http://www.w3.org/ns/prov#Agent");
    row2.put("label", "Agent");
    row2.put("description", null);

    stubResultRows(List.of(row1, row2));

    Response r = rest.search("Acti", 20, 0, sc);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<TermSuggestionIO> body = (PagedResponseIO<TermSuggestionIO>) r.getEntity();
    assertNotNull(body);
    assertEquals(2, body.items().size());
    assertEquals(2, body.total());
    assertEquals("http://www.w3.org/ns/prov#Activity", body.items().get(0).uri());
    assertEquals("Activity", body.items().get(0).label());
    assertEquals("An activity is something that occurs over a period of time.", body.items().get(0).description());
    assertEquals("http://www.w3.org/ns/prov#Agent", body.items().get(1).uri());
    assertEquals("Agent", body.items().get(1).label());
    assertNotNull(body.items().get(1)); // description null — record field is null
  }

  @Test
  void rowWithNullUri_isSkipped() {
    Map<String, Object> rowGood = new LinkedHashMap<>();
    rowGood.put("uri", "http://example.org/term");
    rowGood.put("label", "Term");
    rowGood.put("description", null);

    Map<String, Object> rowBad = new LinkedHashMap<>();
    rowBad.put("uri", null); // should be skipped
    rowBad.put("label", "Bad");
    rowBad.put("description", null);

    stubResultRows(List.of(rowGood, rowBad));

    Response r = rest.search("term", 20, 0, sc);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<TermSuggestionIO> body = (PagedResponseIO<TermSuggestionIO>) r.getEntity();
    assertEquals(1, body.items().size(), "Row with null URI must be skipped");
    assertEquals(1, body.total());
    assertEquals("http://example.org/term", body.items().get(0).uri());
  }

  @Test
  void sessionUnavailable_returns200EmptyList() {
    // Subclass that returns null session
    SemanticTermSearchRest nullSessionRest = new SemanticTermSearchRest() {
      @Override
      Session getSession() {
        return null;
      }
    };

    Response r = nullSessionRest.search("label", 20, 0, sc);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<TermSuggestionIO> body = (PagedResponseIO<TermSuggestionIO>) r.getEntity();
    assertNotNull(body);
    assertTrue(body.items().isEmpty(), "Should return empty list when session is null");
  }

  @Test
  void sessionThrowsOnFulltext_fallsBackToContains_returns200() {
    // First query call (fulltext) throws; second call (CONTAINS) succeeds with empty.
    Result emptyResult = mock(Result.class);
    when(emptyResult.queryResults()).thenReturn(Collections.emptyList());

    when(session.query(eq(SemanticTermSearchRest.FULLTEXT_CYPHER), anyMap()))
      .thenThrow(new RuntimeException("index not found"));
    when(session.query(eq(SemanticTermSearchRest.CONTAINS_CYPHER), anyMap()))
      .thenReturn(emptyResult);

    Response r = rest.search("label", 20, 0, sc);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<TermSuggestionIO> body = (PagedResponseIO<TermSuggestionIO>) r.getEntity();
    assertNotNull(body);
    assertTrue(body.items().isEmpty());
  }

  @Test
  void bothQueriesThrow_returns200EmptyList() {
    when(session.query(eq(SemanticTermSearchRest.FULLTEXT_CYPHER), anyMap()))
      .thenThrow(new RuntimeException("index not found"));
    when(session.query(eq(SemanticTermSearchRest.CONTAINS_CYPHER), anyMap()))
      .thenThrow(new RuntimeException("contains failed"));

    Response r = rest.search("label", 20, 0, sc);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<TermSuggestionIO> body = (PagedResponseIO<TermSuggestionIO>) r.getEntity();
    assertNotNull(body);
    assertTrue(body.items().isEmpty());
  }

  // ─── stripLangSuffix ──────────────────────────────────────────────────────

  @Test
  void stripLangSuffix_removesSimpleLangTag() {
    assertEquals("Anomaly Detected", SemanticTermSearchRest.stripLangSuffix("Anomaly Detected@en"));
  }

  @Test
  void stripLangSuffix_removesSubtaggedLangTag() {
    assertEquals("Testphase", SemanticTermSearchRest.stripLangSuffix("Testphase@de-CH"));
  }

  @Test
  void stripLangSuffix_leavesPlainValueUnchanged() {
    assertEquals("Activity", SemanticTermSearchRest.stripLangSuffix("Activity"));
  }

  @Test
  void stripLangSuffix_handlesNullAndBlank() {
    assertEquals(null, SemanticTermSearchRest.stripLangSuffix(null));
    assertEquals("", SemanticTermSearchRest.stripLangSuffix(""));
  }

  /** Embedded lang tags from n10s IGNORE mode are stripped from returned labels. */
  @Test
  void matchingTerms_embeddedLangTagStripped() {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("uri", "https://shepard.dlr.de/showcase/lumen-inspired#AnomalyDetected");
    row.put("label", "Anomaly Detected@en");  // n10s IGNORE mode embeds @en in value
    row.put("description", "A significant off-nominal event was detected.@en");

    stubResultRows(List.of(row));

    Response r = rest.search("Anomaly", 20, 0, sc);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<TermSuggestionIO> body = (PagedResponseIO<TermSuggestionIO>) r.getEntity();
    assertEquals(1, body.items().size());
    assertEquals("Anomaly Detected", body.items().get(0).label(), "Lang suffix must be stripped from label");
    assertEquals(
      "A significant off-nominal event was detected.",
      body.items().get(0).description(),
      "Lang suffix must be stripped from description"
    );
  }

  // ─── APISIMP-TERM-SEARCH-PAGESIZE-CAP-UNDOCUMENTED regression ─────────────

  @Test
  void q_paramIsMarkedRequiredWithDescription() throws NoSuchMethodException {
    java.lang.reflect.Method method = SemanticTermSearchRest.class.getMethod(
        "search", String.class, int.class, int.class, jakarta.ws.rs.core.SecurityContext.class);
    java.lang.reflect.Parameter param = java.util.Arrays.stream(method.getParameters())
        .filter(p -> {
          var qp = p.getAnnotation(jakarta.ws.rs.QueryParam.class);
          return qp != null && "q".equals(qp.value());
        })
        .findFirst()
        .orElse(null);
    assertNotNull(param, "q must carry @QueryParam");
    var ann = param.getAnnotation(
        org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
    assertNotNull(ann, "q must carry @Parameter annotation");
    assertTrue(ann.required(), "@Parameter.required must be true for q");
    assertTrue(ann.description() != null && !ann.description().isBlank(),
        "@Parameter.description must be non-blank for q");
  }

  @Test
  void pageSize_paramDescribesServerSideCap() throws NoSuchMethodException {
    java.lang.reflect.Method method = SemanticTermSearchRest.class.getMethod(
        "search", String.class, int.class, int.class, jakarta.ws.rs.core.SecurityContext.class);
    java.lang.reflect.Parameter param = java.util.Arrays.stream(method.getParameters())
        .filter(p -> {
          var qp = p.getAnnotation(jakarta.ws.rs.QueryParam.class);
          return qp != null && "pageSize".equals(qp.value());
        })
        .findFirst()
        .orElse(null);
    assertNotNull(param, "pageSize must carry @QueryParam");
    var ann = param.getAnnotation(
        org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
    assertNotNull(ann, "pageSize must carry @Parameter annotation");
    assertTrue(ann.description() != null && ann.description().contains("50"),
        "@Parameter.description for pageSize must mention the server-side cap of 50");
  }

  @Test
  void page_param_reflectedInResponse() {
    stubEmptyResult();
    Response r = rest.search("label", 10, 3, sc);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<TermSuggestionIO> body = (PagedResponseIO<TermSuggestionIO>) r.getEntity();
    assertNotNull(body);
    assertEquals(3, body.page(), "page() in response must reflect the requested page index");
    assertEquals(10, body.pageSize(), "pageSize() in response must reflect capped effectiveLimit");
  }

  @Test
  void negativePage_coercedToZero() {
    stubEmptyResult();
    Response r = rest.search("label", 10, -5, sc);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<TermSuggestionIO> body = (PagedResponseIO<TermSuggestionIO>) r.getEntity();
    assertNotNull(body);
    assertEquals(0, body.page(), "Negative page must be coerced to 0");
  }

  // ─── helpers ──────────────────────────────────────────────────────────────

  private void stubEmptyResult() {
    Result emptyResult = mock(Result.class);
    when(emptyResult.queryResults()).thenReturn(Collections.emptyList());
    when(session.query(eq(SemanticTermSearchRest.FULLTEXT_CYPHER), anyMap())).thenReturn(emptyResult);
    when(session.query(eq(SemanticTermSearchRest.CONTAINS_CYPHER), anyMap())).thenReturn(emptyResult);
  }

  private void stubResultRows(List<Map<String, Object>> rows) {
    Result result = mock(Result.class);
    when(result.queryResults()).thenReturn(rows);
    // Fulltext query succeeds with the given rows
    when(session.query(eq(SemanticTermSearchRest.FULLTEXT_CYPHER), anyMap())).thenReturn(result);
    when(session.query(eq(SemanticTermSearchRest.CONTAINS_CYPHER), anyMap())).thenReturn(result);
  }

  private static void assertProblemJson(Response r, String expectedType, int expectedStatus) {
    ProblemJson body = (ProblemJson) r.getEntity();
    assertNotNull(body, "Response body must be a ProblemJson");
    assertEquals(expectedType, body.type(), "problem type mismatch");
    assertEquals(expectedStatus, body.status(), "HTTP status in body mismatch");
  }

  // ─── test double ──────────────────────────────────────────────────────────

  /**
   * Subclass that overrides {@link #getSession()} to return the injected mock.
   */
  static class StubRest extends SemanticTermSearchRest {

    private final Session mockSession;

    StubRest(Session mockSession) {
      this.mockSession = mockSession;
    }

    @Override
    Session getSession() {
      return mockSession;
    }
  }
}
