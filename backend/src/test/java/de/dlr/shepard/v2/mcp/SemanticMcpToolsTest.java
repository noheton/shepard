package de.dlr.shepard.v2.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.context.semantic.SemanticRepositoryType;
import de.dlr.shepard.context.semantic.daos.PredicateDAO;
import de.dlr.shepard.context.semantic.daos.SemanticAnnotationDAO;
import de.dlr.shepard.context.semantic.daos.SemanticRepositoryDAO;
import de.dlr.shepard.context.semantic.daos.VocabularyDAO;
import de.dlr.shepard.context.semantic.entities.Predicate;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.context.semantic.entities.SemanticRepository;
import de.dlr.shepard.context.semantic.entities.Vocabulary;
import io.quarkiverse.mcp.server.McpException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * MCP-COV-07 — unit tests for {@link SemanticMcpTools}.
 *
 * <p>Pattern mirrors {@link AnnotationMcpToolsTest}: manual CDI wiring with
 * Mockito mocks, a real {@link McpToolSupport} + {@link ObjectMapper},
 * and no container startup.
 *
 * <p>HTTP-dependent paths ({@code executeInternal} / {@code executeExternal})
 * are covered via a subclass stub so the test suite needs no network or n10s.
 */
class SemanticMcpToolsTest {

  static final String VOC_APP_ID  = "018f9c5a-0000-7000-b001-000000000001";
  static final String PRED_APP_ID = "018f9c5a-0000-7000-b001-000000000002";
  static final String ANN_APP_ID  = "018f9c5a-0000-7000-b001-000000000003";
  static final String DO_APP_ID   = "018f9c5a-0000-7000-b001-000000000004";
  static final String REPO_APP_ID = "018f9c5a-0000-7000-b001-000000000005";

  static final String SELECT_QUERY = "SELECT * WHERE { ?s ?p ?o } LIMIT 1";
  static final String INSERT_QUERY = "INSERT DATA { <urn:a> <urn:b> <urn:c> }";

  @Mock VocabularyDAO vocabularyDAO;
  @Mock PredicateDAO predicateDAO;
  @Mock SemanticAnnotationDAO annotationDAO;
  @Mock SemanticRepositoryDAO semanticRepositoryDAO;
  @Mock McpContextBridge contextBridge;

  SemanticMcpTools tools;
  McpToolSupport support;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    support = new McpToolSupport();
    support.objectMapper = new ObjectMapper();

    // Subclass stub that captures executeInternal/executeExternal calls
    // without making network calls.
    tools = new SemanticMcpToolsWithHttpStub();
    tools.vocabularyDAO         = vocabularyDAO;
    tools.predicateDAO          = predicateDAO;
    tools.annotationDAO         = annotationDAO;
    tools.semanticRepositoryDAO = semanticRepositoryDAO;
    tools.contextBridge         = contextBridge;
    tools.support               = support;
  }

  // ─── sparql_query: validation ─────────────────────────────────────────────

  /**
   * A mutation query (INSERT) must be rejected with INVALID_PARAMS before
   * any repository lookup or HTTP call.
   */
  @Test
  void sparqlQueryRejectsMutation() {
    SemanticRepository repo = internalRepo();
    when(semanticRepositoryDAO.findInternal()).thenReturn(repo);

    McpException ex = assertThrows(McpException.class,
      () -> tools.sparqlQuery(INSERT_QUERY, null));

    assertEquals(-32602, ex.getJsonRpcErrorCode());
    assertTrue(ex.getMessage().contains("query rejected"));
  }

  /**
   * An empty/null query must be rejected with INVALID_PARAMS.
   */
  @Test
  void sparqlQueryRejectsEmptyQuery() {
    McpException ex = assertThrows(McpException.class,
      () -> tools.sparqlQuery("", null));

    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  /**
   * A null query must be rejected with INVALID_PARAMS.
   */
  @Test
  void sparqlQueryRejectsNullQuery() {
    McpException ex = assertThrows(McpException.class,
      () -> tools.sparqlQuery(null, null));

    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  // ─── sparql_query: repository resolution ──────────────────────────────────

  /**
   * When no repository is found (findInternal returns null), the tool
   * must throw INVALID_PARAMS with a helpful message.
   */
  @Test
  void sparqlQueryThrowsWhenNoInternalRepo() {
    when(semanticRepositoryDAO.findInternal()).thenReturn(null);

    McpException ex = assertThrows(McpException.class,
      () -> tools.sparqlQuery(SELECT_QUERY, null));

    assertEquals(-32602, ex.getJsonRpcErrorCode());
    assertTrue(ex.getMessage().contains("no semantic repository"));
  }

  /**
   * When repoAppId is explicitly "internal", resolves via findInternal().
   * The HTTP stub returns a fixed SPARQL Results JSON string.
   */
  @Test
  void sparqlQueryResolvesInternalAlias() throws Exception {
    when(semanticRepositoryDAO.findInternal()).thenReturn(internalRepo());

    String result = tools.sparqlQuery(SELECT_QUERY, "internal");
    JsonNode root = new ObjectMapper().readTree(result);

    assertNotNull(root);
    // The stub returns {"head":{"vars":[]}, "results":{"bindings":[]}}
    assertTrue(root.has("results") || root.has("head"));
  }

  /**
   * A JSKOS repository must be rejected (not a SPARQL endpoint).
   */
  @Test
  void sparqlQueryRejectsJskosRepo() {
    SemanticRepository jskos = new SemanticRepository();
    jskos.setType(SemanticRepositoryType.JSKOS);
    when(semanticRepositoryDAO.findByAppId(REPO_APP_ID)).thenReturn(jskos);

    McpException ex = assertThrows(McpException.class,
      () -> tools.sparqlQuery(SELECT_QUERY, REPO_APP_ID));

    assertEquals(-32602, ex.getJsonRpcErrorCode());
    assertTrue(ex.getMessage().contains("JSKOS"));
  }

  // ─── semantic_browse ──────────────────────────────────────────────────────

  /**
   * semantic_browse with no prefix returns all vocabularies with predicates.
   */
  @Test
  void semanticBrowseReturnsAllVocabs() throws Exception {
    Vocabulary v = vocab("dcterms", "http://purl.org/dc/terms/");
    Predicate p = pred("http://purl.org/dc/terms/creator", "Creator");
    when(vocabularyDAO.listAll()).thenReturn(List.of(v));
    when(predicateDAO.listByVocabulary(VOC_APP_ID)).thenReturn(List.of(p));

    String json = tools.semanticBrowse(null);
    JsonNode root = new ObjectMapper().readTree(json);

    assertTrue(root.isArray());
    assertEquals(1, root.size());

    JsonNode entry = root.get(0);
    assertEquals(VOC_APP_ID, entry.get("appId").asText());
    assertEquals("dcterms", entry.get("prefix").asText());
    assertTrue(entry.has("predicates"));
    assertEquals(1, entry.get("predicates").size());
    assertEquals("http://purl.org/dc/terms/creator", entry.get("predicates").get(0).get("uri").asText());
  }

  /**
   * semantic_browse with a prefix filters to matching vocabulary only.
   */
  @Test
  void semanticBrowseFiltersByPrefix() throws Exception {
    Vocabulary dc  = vocab("dcterms", "http://purl.org/dc/terms/");
    Vocabulary prov = vocabWithId("018f9c5a-0000-7000-b001-000000000099", "prov", "http://www.w3.org/ns/prov#");
    when(vocabularyDAO.listAll()).thenReturn(List.of(dc, prov));
    when(predicateDAO.listByVocabulary(VOC_APP_ID)).thenReturn(List.of());
    when(predicateDAO.listByVocabulary("018f9c5a-0000-7000-b001-000000000099")).thenReturn(List.of());

    String json = tools.semanticBrowse("prov");
    JsonNode root = new ObjectMapper().readTree(json);

    assertEquals(1, root.size());
    assertEquals("prov", root.get(0).get("prefix").asText());
  }

  /**
   * semantic_browse with a non-matching prefix returns an empty list (not an error).
   */
  @Test
  void semanticBrowseEmptyOnNonMatchingPrefix() throws Exception {
    when(vocabularyDAO.listAll()).thenReturn(List.of(vocab("dcterms", "http://purl.org/dc/terms/")));
    when(predicateDAO.listByVocabulary(anyString())).thenReturn(List.of());

    String json = tools.semanticBrowse("qudt");
    JsonNode root = new ObjectMapper().readTree(json);

    assertTrue(root.isArray());
    assertEquals(0, root.size());
  }

  // ─── semantic_search ──────────────────────────────────────────────────────

  /**
   * semantic_search with a matching predicateIri returns annotations.
   */
  @Test
  void semanticSearchReturnsMappedAnnotations() throws Exception {
    String iri = "http://purl.org/dc/terms/creator";
    SemanticAnnotation ann = annotation(ANN_APP_ID, DO_APP_ID, iri, "Alice");
    when(annotationDAO.findByPredicateAndValue(eq(iri), isNull(), isNull(), eq(0), eq(20)))
      .thenReturn(List.of(ann));

    String json = tools.semanticSearch(iri, null, null);
    JsonNode root = new ObjectMapper().readTree(json);

    assertTrue(root.has("annotations"));
    JsonNode annotations = root.get("annotations");
    assertEquals(1, annotations.size());

    JsonNode a = annotations.get(0);
    assertEquals(ANN_APP_ID, a.get("appId").asText());
    assertEquals(DO_APP_ID, a.get("subjectAppId").asText());
    assertEquals(iri, a.get("propertyIRI").asText());
    assertEquals("Alice", a.get("valueName").asText());
  }

  /**
   * semantic_search with value filter passes the value to the DAO.
   */
  @Test
  void semanticSearchPassesValueFilter() throws Exception {
    String iri = "http://purl.org/dc/terms/creator";
    when(annotationDAO.findByPredicateAndValue(eq(iri), eq("Alice"), isNull(), eq(0), anyInt()))
      .thenReturn(List.of());

    String json = tools.semanticSearch(iri, "Alice", null);
    JsonNode root = new ObjectMapper().readTree(json);

    assertEquals("Alice", root.get("value").asText());
    assertEquals(0, root.get("annotations").size());
  }

  /**
   * semantic_search without a predicateIri must throw INVALID_PARAMS.
   */
  @Test
  void semanticSearchRequiresPredicateIri() {
    McpException ex = assertThrows(McpException.class,
      () -> tools.semanticSearch(null, null, null));

    assertEquals(-32602, ex.getJsonRpcErrorCode());
    assertTrue(ex.getMessage().contains("predicateIri is required"));
  }

  /**
   * The limit parameter is capped at 100.
   */
  @Test
  void semanticSearchCapsLimitAt100() throws Exception {
    String iri = "http://purl.org/dc/terms/creator";
    when(annotationDAO.findByPredicateAndValue(eq(iri), isNull(), isNull(), eq(0), eq(100)))
      .thenReturn(List.of());

    String json = tools.semanticSearch(iri, null, 9999);
    JsonNode root = new ObjectMapper().readTree(json);

    assertEquals(100, root.get("limit").asInt());
  }

  // ─── stub ─────────────────────────────────────────────────────────────────

  /**
   * Subclass that overrides the HTTP-bound methods so tests need no network.
   */
  static class SemanticMcpToolsWithHttpStub extends SemanticMcpTools {

    static final String STUB_RESULT =
      "{\"head\":{\"vars\":[]},\"results\":{\"bindings\":[]}}";

    @Override
    String executeInternal(String sparqlQuery) {
      return STUB_RESULT;
    }

    @Override
    String executeExternal(String endpointUrl, String sparqlQuery) {
      return STUB_RESULT;
    }
  }

  // ─── factories ────────────────────────────────────────────────────────────

  private static SemanticRepository internalRepo() {
    SemanticRepository r = new SemanticRepository();
    r.setType(SemanticRepositoryType.INTERNAL);
    return r;
  }

  private static Vocabulary vocab(String prefix, String uri) {
    Vocabulary v = new Vocabulary();
    v.setAppId(VOC_APP_ID);
    v.setUri(uri);
    v.setLabel(prefix.toUpperCase());
    v.setPrefix(prefix);
    v.setEnabled(true);
    return v;
  }

  private static Vocabulary vocabWithId(String appId, String prefix, String uri) {
    Vocabulary v = new Vocabulary();
    v.setAppId(appId);
    v.setUri(uri);
    v.setLabel(prefix.toUpperCase());
    v.setPrefix(prefix);
    v.setEnabled(true);
    return v;
  }

  private static Predicate pred(String uri, String label) {
    Predicate p = new Predicate();
    p.setAppId(PRED_APP_ID);
    p.setUri(uri);
    p.setLabel(label);
    p.setVocabularyAppId(VOC_APP_ID);
    return p;
  }

  private static SemanticAnnotation annotation(String annId, String subjectId, String iri, String value) {
    SemanticAnnotation a = new SemanticAnnotation();
    a.setAppId(annId);
    a.setSubjectAppId(subjectId);
    a.setSubjectKind("DataObject");
    a.setPropertyIRI(iri);
    a.setPropertyName("Creator");
    a.setValueName(value);
    a.setSourceMode("human");
    return a;
  }
}
