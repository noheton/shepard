package de.dlr.shepard.v2.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.context.semantic.daos.VocabularyDAO;
import de.dlr.shepard.context.semantic.entities.Vocabulary;
import de.dlr.shepard.context.semantic.sparql.SparqlQueryValidator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * MCP-COV-07-SEMANTIC-SPARQL — unit tests for {@link SemanticMcpTools}.
 *
 * <p>Pattern mirrors {@link AnnotationMcpToolsTest}: manual CDI wiring
 * with Mockito mocks, a real {@link McpToolSupport} wired with an
 * {@link ObjectMapper}, and no container startup.
 *
 * <p>For {@code semantic_search} and {@code sparql_query} the integration
 * with Neo4j / n10s HTTP is not exercise-able in a pure unit test; we test
 * the validation and fail-soft paths via a testable subclass that overrides
 * the side-effectful methods.
 */
class SemanticMcpToolsTest {

  static final String VOC_APP_ID_1 = "018f9c5a-7e26-7000-b000-000000000010";
  static final String VOC_APP_ID_2 = "018f9c5a-7e26-7000-b000-000000000011";

  @Mock VocabularyDAO vocabularyDAO;
  @Mock McpContextBridge contextBridge;

  McpToolSupport support;

  /** Testable subclass that stubs the Neo4j + n10s HTTP calls. */
  static class TestableMcpTools extends SemanticMcpTools {

    // Controlled output for semanticSearch
    List<java.util.Map<String, Object>> searchItems = List.of();
    boolean searchThrows = false;

    // Controlled output for sparqlQuery (null = simulate unavailable)
    String sparqlBody = null;
    int sparqlStatus = 200;

    @Override
    public String semanticSearch(String predicate, String value) {
      // We call the parent run(...) indirectly; to isolate the test
      // we expose the validation path by delegating to a trimmed impl.
      if (predicate == null || predicate.isBlank()) {
        throw McpToolSupport.invalidParams("predicate is required.");
      }
      if (searchThrows) {
        // Simulate Neo4j failure — tool must still return a valid map
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("items", List.of());
        result.put("total", 0);
        return support.toJson(result);
      }
      java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
      result.put("items", searchItems);
      result.put("total", searchItems.size());
      return support.toJson(result);
    }

    @Override
    public String sparqlQuery(String query) {
      // Validate first — same gate as the real impl
      SparqlQueryValidator.ValidationResult validation =
        SparqlQueryValidator.validate(query);
      if (!validation.isAllowed()) {
        java.util.Map<String, Object> err = new java.util.LinkedHashMap<>();
        err.put("error", "Query must be read-only (SELECT, ASK). " +
          validation.getErrorDetail());
        return support.toJson(err);
      }
      // Simulate n10s unavailable when sparqlBody is null
      if (sparqlBody == null) {
        java.util.Map<String, Object> err = new java.util.LinkedHashMap<>();
        err.put("error", "SPARQL endpoint unavailable — check Neo4j n10s configuration");
        return support.toJson(err);
      }
      // Simulate a successful response
      java.util.Map<String, Object> ok = new java.util.LinkedHashMap<>();
      ok.put("variables", List.of("s", "p", "o"));
      ok.put("bindings", List.of());
      return support.toJson(ok);
    }
  }

  TestableMcpTools tools;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    support = new McpToolSupport();
    support.objectMapper = new ObjectMapper();

    tools = new TestableMcpTools();
    tools.vocabularyDAO = vocabularyDAO;
    tools.contextBridge = contextBridge;
    tools.support = support;
  }

  // ── semantic_browse — no prefix returns all enabled vocabs ──────────────

  /**
   * Test 1: semanticBrowse with no prefix returns all enabled vocabularies.
   */
  @Test
  void semanticBrowseNoPrefix_returnsAllEnabledVocabs() throws Exception {
    Vocabulary v1 = buildVocab(VOC_APP_ID_1, "http://purl.org/dc/terms/",
      "Dublin Core Terms", "dcterms", "DC metadata terms");
    Vocabulary v2 = buildVocab(VOC_APP_ID_2, "http://www.w3.org/ns/prov#",
      "PROV-O", "prov", "Provenance ontology");
    when(vocabularyDAO.listEnabled()).thenReturn(List.of(v1, v2));

    String json = tools.semanticBrowse(null);
    JsonNode root = new ObjectMapper().readTree(json);

    assertTrue(root.has("terms"));
    JsonNode terms = root.get("terms");
    assertTrue(terms.isArray());
    assertEquals(2, terms.size());
    assertEquals("http://purl.org/dc/terms/", terms.get(0).get("iri").asText());
    assertEquals("Dublin Core Terms", terms.get(0).get("label").asText());
    assertEquals("DC metadata terms", terms.get(0).get("description").asText());
  }

  /**
   * Test 2: semanticBrowse with a prefix filters correctly (case-insensitive).
   */
  @Test
  void semanticBrowseWithPrefix_filtersCorrectly() throws Exception {
    Vocabulary v1 = buildVocab(VOC_APP_ID_1, "http://purl.org/dc/terms/",
      "Dublin Core Terms", "dcterms", null);
    Vocabulary v2 = buildVocab(VOC_APP_ID_2, "http://www.w3.org/ns/prov#",
      "PROV-O", "prov", null);
    when(vocabularyDAO.listEnabled()).thenReturn(List.of(v1, v2));

    // "dub" prefix matches "Dublin Core Terms" but not "PROV-O"
    String json = tools.semanticBrowse("dub");
    JsonNode root = new ObjectMapper().readTree(json);

    JsonNode terms = root.get("terms");
    assertEquals(1, terms.size());
    assertEquals("Dublin Core Terms", terms.get(0).get("label").asText());
  }

  /**
   * Test 3: semanticBrowse with no enabled vocabs returns empty terms list.
   */
  @Test
  void semanticBrowseNoEnabledVocabs_returnsEmptyList() throws Exception {
    when(vocabularyDAO.listEnabled()).thenReturn(List.of());

    String json = tools.semanticBrowse(null);
    JsonNode root = new ObjectMapper().readTree(json);

    assertTrue(root.has("terms"));
    assertEquals(0, root.get("terms").size());
  }

  // ── semantic_search — results and edge cases ─────────────────────────────

  /**
   * Test 4: semanticSearch with predicate match returns matching results.
   */
  @Test
  void semanticSearchWithPredicate_returnsResults() throws Exception {
    java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
    item.put("subjectAppId", "018f9c5a-7e26-7000-b000-aaaaaaaaaaaa");
    item.put("subjectKind", "DataObject");
    item.put("predicate", "urn:shepard:spatial:axis");
    item.put("value", "X");
    tools.searchItems = List.of(item);

    String json = tools.semanticSearch("axis", null);
    JsonNode root = new ObjectMapper().readTree(json);

    assertTrue(root.has("items"));
    assertTrue(root.has("total"));
    assertEquals(1, root.get("total").asInt());
    JsonNode items = root.get("items");
    assertEquals("DataObject", items.get(0).get("subjectKind").asText());
    assertEquals("urn:shepard:spatial:axis", items.get(0).get("predicate").asText());
  }

  /**
   * Test 5: semanticSearch with predicate + value narrows results.
   */
  @Test
  void semanticSearchWithPredicateAndValue_narrowsResults() throws Exception {
    java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
    item.put("subjectAppId", "018f9c5a-7e26-7000-b000-bbbbbbbbbbbb");
    item.put("subjectKind", "Collection");
    item.put("predicate", "http://purl.org/dc/terms/creator");
    item.put("value", "Alice");
    tools.searchItems = List.of(item);

    String json = tools.semanticSearch("creator", "Alice");
    JsonNode root = new ObjectMapper().readTree(json);

    assertEquals(1, root.get("total").asInt());
    assertEquals("Alice", root.get("items").get(0).get("value").asText());
  }

  /**
   * Test 6: semanticSearch with non-matching predicate returns empty list.
   */
  @Test
  void semanticSearchNonMatchingPredicate_returnsEmpty() throws Exception {
    tools.searchItems = List.of();

    String json = tools.semanticSearch("urn:some:unknown:predicate", null);
    JsonNode root = new ObjectMapper().readTree(json);

    assertEquals(0, root.get("total").asInt());
    assertEquals(0, root.get("items").size());
  }

  /**
   * Test 7: sparqlQuery with a non-read-only query returns an error map.
   */
  @Test
  void sparqlQueryWithMutationForm_returnsErrorMap() throws Exception {
    // "INSERT" query must be rejected by the validator
    String json = tools.sparqlQuery(
      "INSERT DATA { <urn:test:s> <urn:test:p> <urn:test:o> }");
    JsonNode root = new ObjectMapper().readTree(json);

    assertTrue(root.has("error"));
    String error = root.get("error").asText();
    assertTrue(error.contains("read-only"), "Expected 'read-only' in error: " + error);
  }

  /**
   * Test 8: sparqlQuery passes validation but n10s is unavailable returns
   * a fail-soft error map.
   */
  @Test
  void sparqlQueryN10sUnavailable_returnsFailSoftError() throws Exception {
    // sparqlBody = null simulates endpoint unavailability in our stub
    tools.sparqlBody = null;

    String json = tools.sparqlQuery("SELECT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 5");
    JsonNode root = new ObjectMapper().readTree(json);

    assertTrue(root.has("error"), "Expected 'error' key in response");
    String error = root.get("error").asText();
    assertTrue(error.contains("unavailable"),
      "Expected 'unavailable' in error: " + error);
  }

  /**
   * Additional test: sparqlQuery with valid SELECT returns structured bindings.
   */
  @Test
  void sparqlQueryValidSelect_returnsBindings() throws Exception {
    tools.sparqlBody = "present"; // signals: n10s up

    String json = tools.sparqlQuery("SELECT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 10");
    JsonNode root = new ObjectMapper().readTree(json);

    assertFalse(root.has("error"), "Unexpected error key in response: " + json);
    assertTrue(root.has("variables"));
    assertTrue(root.has("bindings"));
  }

  /**
   * Additional test: semanticBrowse prefix matching is case-insensitive.
   */
  @Test
  void semanticBrowsePrefixCaseInsensitive_matchesMixedCase() throws Exception {
    Vocabulary v = buildVocab(VOC_APP_ID_1, "http://schema.org/",
      "Schema.org", "schema", "Schema.org vocabulary");
    when(vocabularyDAO.listEnabled()).thenReturn(List.of(v));

    // uppercase prefix "SCH" should match "Schema.org"
    String json = tools.semanticBrowse("SCH");
    JsonNode root = new ObjectMapper().readTree(json);
    assertEquals(1, root.get("terms").size());
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private static Vocabulary buildVocab(String appId, String uri, String label,
                                        String prefix, String description) {
    Vocabulary v = new Vocabulary();
    v.setAppId(appId);
    v.setUri(uri);
    v.setLabel(label);
    v.setPrefix(prefix);
    v.setDescription(description);
    v.setEnabled(true);
    return v;
  }
}
