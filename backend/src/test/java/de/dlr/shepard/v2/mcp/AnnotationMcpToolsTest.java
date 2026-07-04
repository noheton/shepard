package de.dlr.shepard.v2.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.context.semantic.daos.PredicateDAO;
import de.dlr.shepard.context.semantic.daos.SemanticAnnotationDAO;
import de.dlr.shepard.context.semantic.daos.VocabularyDAO;
import de.dlr.shepard.context.semantic.entities.Predicate;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.context.semantic.entities.Vocabulary;
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
 * SEMA-V6-006 — unit tests for {@link AnnotationMcpTools}.
 *
 * <p>Pattern mirrors {@link ContentMcpToolsTest}: manual CDI wiring with
 * Mockito mocks, a real {@link McpToolSupport} wired with an {@link ObjectMapper},
 * and no container startup.
 *
 * <p>Four required tests per the task spec:
 * <ol>
 *   <li>{@code listVocabulariesReturnsList} — {@code list_vocabularies} returns a list.</li>
 *   <li>{@code getAnnotationReturnsCorrectFields} — {@code get_annotation} returns all v6 fields.</li>
 *   <li>{@code createAnnotationSucceeds} — {@code create_annotation} persists and returns the annotation.</li>
 *   <li>{@code deleteAnnotationByNonAuthorReturnsForbidden} — non-author delete → error code -32002.</li>
 * </ol>
 */
class AnnotationMcpToolsTest {

  static final String ANN_APP_ID  = "018f9c5a-7e26-7000-b000-000000000001";
  static final String DO_APP_ID   = "018f9c5a-7e26-7000-b000-000000000002";
  static final String VOC_APP_ID  = "018f9c5a-7e26-7000-b000-000000000003";

  @Mock VocabularyDAO vocabularyDAO;
  @Mock PredicateDAO predicateDAO;
  @Mock SemanticAnnotationDAO annotationDAO;
  @Mock McpContextBridge contextBridge;
  @Mock AuthenticationContext authContext;

  AnnotationMcpTools tools;
  McpToolSupport support;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    support = new McpToolSupport();
    support.objectMapper = new ObjectMapper();
    // No entityIdResolver needed — annotation tools go DAO-direct without resolveOfType

    tools = new AnnotationMcpTools();
    tools.vocabularyDAO   = vocabularyDAO;
    tools.predicateDAO    = predicateDAO;
    tools.annotationDAO   = annotationDAO;
    tools.contextBridge   = contextBridge;
    tools.support         = support;
    tools.authenticationContext = authContext;
    // currentVertxRequest left null — isAiAgentRequest() falls back to false safely
  }

  // ── list_vocabularies ──────────────────────────────────────────────────────

  /**
   * Required test 1: list_vocabularies returns a list with the correct fields.
   */
  @Test
  void listVocabulariesReturnsList() throws Exception {
    Vocabulary v = new Vocabulary();
    v.setAppId(VOC_APP_ID);
    v.setUri("http://purl.org/dc/terms/");
    v.setLabel("Dublin Core Terms");
    v.setPrefix("dcterms");
    v.setDescription("Dublin Core Metadata Terms");
    when(vocabularyDAO.listEnabled()).thenReturn(List.of(v));

    String json = tools.listVocabularies();
    JsonNode root = new ObjectMapper().readTree(json);

    assertTrue(root.isArray());
    assertEquals(1, root.size());
    JsonNode row = root.get(0);
    assertEquals(VOC_APP_ID, row.get("appId").asText());
    assertEquals("http://purl.org/dc/terms/", row.get("uri").asText());
    assertEquals("Dublin Core Terms", row.get("label").asText());
    assertEquals("dcterms", row.get("prefix").asText());
  }

  @Test
  void listVocabulariesReturnsEmptyArrayWhenNone() throws Exception {
    when(vocabularyDAO.listEnabled()).thenReturn(List.of());

    String json = tools.listVocabularies();
    JsonNode root = new ObjectMapper().readTree(json);

    assertTrue(root.isArray());
    assertEquals(0, root.size());
  }

  // ── get_annotation ─────────────────────────────────────────────────────────

  /**
   * Required test 2: get_annotation returns all v6 fields correctly.
   */
  @Test
  void getAnnotationReturnsCorrectFields() throws Exception {
    SemanticAnnotation ann = buildTestAnnotation();
    when(annotationDAO.findByAppId(ANN_APP_ID)).thenReturn(ann);

    String json = tools.getAnnotation(ANN_APP_ID);
    JsonNode row = new ObjectMapper().readTree(json);

    assertEquals(ANN_APP_ID, row.get("appId").asText());
    assertEquals("http://purl.org/dc/terms/type", row.get("propertyIRI").asText());
    assertEquals("testType", row.get("valueName").asText());
    assertEquals("DataObject", row.get("subjectKind").asText());
    assertEquals(DO_APP_ID, row.get("subjectAppId").asText());
    assertEquals(VOC_APP_ID, row.get("vocabularyId").asText());
    assertEquals("ai", row.get("sourceMode").asText());
    assertEquals(0.87, row.get("confidence").asDouble(), 0.001);
    // temporal bounds
    assertEquals(1000L, row.get("validFromMillis").asLong());
    assertEquals(9999L, row.get("validUntilMillis").asLong());
  }

  @Test
  void getAnnotationThrowsInvalidParamsWhenNotFound() {
    when(annotationDAO.findByAppId(ANN_APP_ID)).thenReturn(null);

    McpException ex = assertThrows(McpException.class, () -> tools.getAnnotation(ANN_APP_ID));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    assertTrue(ex.getMessage().contains(ANN_APP_ID));
  }

  @Test
  void getAnnotationThrowsInvalidParamsOnBlankId() {
    McpException ex = assertThrows(McpException.class, () -> tools.getAnnotation(""));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  // ── create_annotation ──────────────────────────────────────────────────────

  /**
   * Required test 3: create_annotation persists the entity and returns it.
   */
  @Test
  void createAnnotationSucceeds() throws Exception {
    // DAO returns the saved entity (with appId minted by GenericDAO.createOrUpdate)
    when(annotationDAO.createOrUpdate(any(SemanticAnnotation.class))).thenAnswer(inv -> {
      SemanticAnnotation a = inv.getArgument(0);
      if (a.getAppId() == null) a.setAppId(ANN_APP_ID);
      return a;
    });

    String json = tools.createAnnotation(
      DO_APP_ID,           // subjectAppId
      "DataObject",        // subjectKind
      "http://purl.org/dc/terms/type",  // propertyIRI
      "Type",              // propertyName
      "testValue",         // valueName
      null,                // valueIRI
      null,                // numericValue
      null,                // unitIRI
      VOC_APP_ID,          // vocabularyId
      "human",             // sourceMode (explicit override)
      null                 // confidence
    );

    JsonNode row = new ObjectMapper().readTree(json);
    assertNotNull(row.get("appId"));
    assertEquals(DO_APP_ID, row.get("subjectAppId").asText());
    assertEquals("DataObject", row.get("subjectKind").asText());
    assertEquals("http://purl.org/dc/terms/type", row.get("propertyIRI").asText());
    assertEquals("testValue", row.get("valueName").asText());
    assertEquals("human", row.get("sourceMode").asText());

    verify(annotationDAO).createOrUpdate(any(SemanticAnnotation.class));
  }

  @Test
  void createAnnotationThrowsWhenNoValueSupplied() {
    McpException ex = assertThrows(McpException.class, () ->
      tools.createAnnotation(DO_APP_ID, "DataObject", "http://example.org/pred",
        null, null, null, null, null, null, null, null)
    );
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    assertTrue(ex.getMessage().contains("valueName"));
  }

  @Test
  void createAnnotationThrowsWhenPropertyIRIMissing() {
    McpException ex = assertThrows(McpException.class, () ->
      tools.createAnnotation(DO_APP_ID, "DataObject", "",
        null, "someValue", null, null, null, null, null, null)
    );
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  // ── delete_annotation ──────────────────────────────────────────────────────

  /**
   * Required test 4: delete by non-author returns -32002 (forbidden).
   *
   * <p>The v0 author check compares {@link SemanticAnnotation#getSourceActivityAppId()}
   * against the current principal. When the annotation records a different owner,
   * the tool must raise {@code McpException} with code -32002.
   */
  @Test
  void deleteAnnotationByNonAuthorReturnsForbidden() {
    SemanticAnnotation ann = buildTestAnnotation();
    // annotation's sourceActivityAppId = "original-author"
    ann.setSourceActivityAppId("original-author");
    when(annotationDAO.findByAppId(ANN_APP_ID)).thenReturn(ann);
    // Current authenticated user is someone else
    when(authContext.getCurrentUserName()).thenReturn("different-user");

    McpException ex = assertThrows(McpException.class, () -> tools.deleteAnnotation(ANN_APP_ID));
    assertEquals(-32002, ex.getJsonRpcErrorCode());
    assertTrue(ex.getMessage().contains(ANN_APP_ID));
    // Ensure the DAO delete was NOT called
    verify(annotationDAO, never()).deleteByNeo4jId(any(Long.class));
  }

  @Test
  void deleteAnnotationSucceedsForAuthor() throws Exception {
    SemanticAnnotation ann = buildTestAnnotation();
    ann.setId(42L);
    ann.setSourceActivityAppId("alice");
    when(annotationDAO.findByAppId(ANN_APP_ID)).thenReturn(ann);
    when(authContext.getCurrentUserName()).thenReturn("alice");
    when(annotationDAO.deleteByNeo4jId(42L)).thenReturn(true);

    String json = tools.deleteAnnotation(ANN_APP_ID);
    JsonNode row = new ObjectMapper().readTree(json);
    assertTrue(row.get("deleted").asBoolean());
    assertEquals(ANN_APP_ID, row.get("appId").asText());
    verify(annotationDAO).deleteByNeo4jId(42L);
  }

  @Test
  void deleteAnnotationRequiresAuthentication() {
    SemanticAnnotation ann = buildTestAnnotation();
    when(annotationDAO.findByAppId(ANN_APP_ID)).thenReturn(ann);
    when(authContext.getCurrentUserName()).thenReturn(null);

    McpException ex = assertThrows(McpException.class, () -> tools.deleteAnnotation(ANN_APP_ID));
    assertEquals(-32001, ex.getJsonRpcErrorCode());
  }

  // ── search_predicates ──────────────────────────────────────────────────────

  @Test
  void searchPredicatesReturnsRows() throws Exception {
    Predicate p = new Predicate();
    p.setAppId("pred-app-id");
    p.setUri("http://purl.org/dc/terms/creator");
    p.setLabel("Creator");
    p.setVocabularyAppId(VOC_APP_ID);
    p.setExpectedObjectType("LITERAL");
    p.setCardinality("MANY");
    when(predicateDAO.searchByText("creator", null)).thenReturn(List.of(p));

    String json = tools.searchPredicates("creator", null);
    JsonNode root = new ObjectMapper().readTree(json);

    assertEquals(1, root.size());
    JsonNode row = root.get(0);
    assertEquals("Creator", row.get("label").asText());
    assertEquals("LITERAL", row.get("expectedObjectType").asText());
  }

  // ── search_values ──────────────────────────────────────────────────────────

  @Test
  void searchValuesReturnsAggregatedValues() throws Exception {
    when(annotationDAO.aggregateValuesForPredicate(
      "http://purl.org/dc/terms/type", "test", 20
    )).thenReturn(List.of("testValue", "testType"));

    String json = tools.searchValues("http://purl.org/dc/terms/type", "test", 20);
    JsonNode root = new ObjectMapper().readTree(json);

    assertTrue(root.isArray());
    assertEquals(2, root.size());
    assertEquals("testValue", root.get(0).asText());
  }

  @Test
  void searchValuesThrowsWhenPredicateIriMissing() {
    McpException ex = assertThrows(McpException.class,
      () -> tools.searchValues("", null, null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  // ── suggest_annotations (501) ──────────────────────────────────────────────

  @Test
  void suggestAnnotationsReturns501Stub() throws Exception {
    String json = tools.suggestAnnotations(DO_APP_ID);
    JsonNode root = new ObjectMapper().readTree(json);
    assertEquals("not_implemented", root.get("status").asText());
  }

  // ── find_similar_annotated (501) ──────────────────────────────────────────

  @Test
  void findSimilarAnnotatedReturns501Stub() throws Exception {
    String json = tools.findSimilarAnnotated(DO_APP_ID);
    JsonNode root = new ObjectMapper().readTree(json);
    assertEquals("not_implemented", root.get("status").asText());
  }

  // ── find_annotated ────────────────────────────────────────────────────────

  @Test
  void findAnnotatedModeAReturnsAnnotations() throws Exception {
    SemanticAnnotation ann = buildTestAnnotation();
    when(annotationDAO.findBySubjectAppId(DO_APP_ID)).thenReturn(List.of(ann));

    String json = tools.findAnnotated(DO_APP_ID, null, null, null, null, null);
    JsonNode root = new ObjectMapper().readTree(json);

    assertTrue(root.isArray());
    assertEquals(1, root.size());
    assertEquals(ANN_APP_ID, root.get(0).get("appId").asText());
  }

  @Test
  void findAnnotatedThrowsWhenNoModeProvided() {
    McpException ex = assertThrows(McpException.class,
      () -> tools.findAnnotated(null, null, null, null, null, null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private SemanticAnnotation buildTestAnnotation() {
    SemanticAnnotation ann = new SemanticAnnotation();
    ann.setAppId(ANN_APP_ID);
    ann.setPropertyIRI("http://purl.org/dc/terms/type");
    ann.setPropertyName("Type");
    ann.setValueName("testType");
    ann.setSubjectKind("DataObject");
    ann.setSubjectAppId(DO_APP_ID);
    ann.setVocabularyId(VOC_APP_ID);
    ann.setSourceMode("ai");
    ann.setConfidence(0.87);
    ann.setValidFromMillis(1000L);
    ann.setValidUntilMillis(9999L);
    return ann;
  }

  // ── semantic_annotate_bulk (MCP-COV-05) ────────────────────────────────────

  private Map<String, Object> entry(String subjectAppId, String propertyIRI, String valueName) {
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("subjectAppId", subjectAppId);
    r.put("subjectKind", "DataObject");
    r.put("propertyIRI", propertyIRI);
    if (valueName != null) r.put("valueName", valueName);
    return r;
  }

  @Test
  void semanticAnnotateBulkSucceedsForValidRows() throws Exception {
    // Echo each created annotation back with an appId stamped on.
    when(annotationDAO.createOrUpdate(any(SemanticAnnotation.class))).thenAnswer(inv -> {
      SemanticAnnotation in = inv.getArgument(0);
      in.setAppId("created-" + in.getSubjectAppId());
      return in;
    });

    List<Map<String, Object>> rows = new ArrayList<>();
    rows.add(entry("do-1", "http://purl.org/dc/terms/creator", "Flo"));
    rows.add(entry("do-2", "http://purl.org/dc/terms/creator", "Sev"));
    String json = tools.semanticAnnotateBulk(rows);

    JsonNode root = new ObjectMapper().readTree(json);
    assertEquals(2, root.get("requested").asInt());
    assertEquals(2, root.get("succeeded").asInt());
    assertEquals(0, root.get("failed").asInt());
    var results = root.get("results");
    assertEquals(2, results.size());
    assertEquals(true, results.get(0).get("ok").asBoolean());
    assertEquals("do-1", results.get(0).get("subjectAppId").asText());
    assertEquals("created-do-1", results.get(0).get("appId").asText());
    assertEquals("created-do-2", results.get(1).get("appId").asText());
  }

  @Test
  void semanticAnnotateBulkContinuesAfterPerRowError() throws Exception {
    when(annotationDAO.createOrUpdate(any(SemanticAnnotation.class))).thenAnswer(inv -> {
      SemanticAnnotation in = inv.getArgument(0);
      in.setAppId("ok-" + in.getSubjectAppId());
      return in;
    });

    List<Map<String, Object>> rows = new ArrayList<>();
    rows.add(entry("do-1", "http://example/pred", "v1"));
    // Missing subjectAppId — should fail this row, NOT abort the batch.
    Map<String, Object> bad = new HashMap<>();
    bad.put("subjectKind", "DataObject");
    bad.put("propertyIRI", "http://example/pred");
    bad.put("valueName", "v2");
    rows.add(bad);
    rows.add(entry("do-3", "http://example/pred", "v3"));

    String json = tools.semanticAnnotateBulk(rows);
    JsonNode root = new ObjectMapper().readTree(json);
    assertEquals(3, root.get("requested").asInt());
    assertEquals(2, root.get("succeeded").asInt());
    assertEquals(1, root.get("failed").asInt());
    var results = root.get("results");
    assertEquals(true, results.get(0).get("ok").asBoolean());
    assertEquals(false, results.get(1).get("ok").asBoolean());
    assertNotNull(results.get(1).get("error"));
    assertEquals(true, results.get(2).get("ok").asBoolean());
  }

  @Test
  void semanticAnnotateBulkRejectsEmptyInput() {
    McpException ex = assertThrows(McpException.class, () -> tools.semanticAnnotateBulk(List.of()));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  @Test
  void semanticAnnotateBulkRejectsOversizedBatch() {
    List<Map<String, Object>> rows = new ArrayList<>();
    for (int i = 0; i < AnnotationMcpTools.SEMANTIC_ANNOTATE_BULK_MAX + 1; i++) {
      rows.add(entry("do-" + i, "http://example/pred", "v" + i));
    }
    McpException ex = assertThrows(McpException.class, () -> tools.semanticAnnotateBulk(rows));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  @Test
  void semanticAnnotateBulkRejectsRowsWithoutValue() throws Exception {
    when(annotationDAO.createOrUpdate(any(SemanticAnnotation.class))).thenReturn(new SemanticAnnotation());
    Map<String, Object> noValue = new HashMap<>();
    noValue.put("subjectAppId", "do-1");
    noValue.put("subjectKind", "DataObject");
    noValue.put("propertyIRI", "http://example/pred");
    // no valueName / valueIRI / numericValue

    String json = tools.semanticAnnotateBulk(List.of(noValue));
    JsonNode root = new ObjectMapper().readTree(json);
    assertEquals(1, root.get("failed").asInt());
    var first = root.get("results").get(0);
    assertEquals(false, first.get("ok").asBoolean());
    assertTrue(first.get("error").asText().toLowerCase().contains("valuename")
            || first.get("error").asText().toLowerCase().contains("required"));
  }

  @Test
  void semanticAnnotateBulkAcceptsNumericValue() throws Exception {
    when(annotationDAO.createOrUpdate(any(SemanticAnnotation.class))).thenAnswer(inv -> {
      SemanticAnnotation in = inv.getArgument(0);
      in.setAppId("num-ok");
      return in;
    });

    Map<String, Object> row = new LinkedHashMap<>();
    row.put("subjectAppId", "do-1");
    row.put("subjectKind", "DataObject");
    row.put("propertyIRI", "http://example/pred/mass");
    row.put("numericValue", 12.5);
    row.put("unitIRI", "http://qudt.org/vocab/unit/KG");

    String json = tools.semanticAnnotateBulk(List.of(row));
    JsonNode root = new ObjectMapper().readTree(json);
    assertEquals(1, root.get("succeeded").asInt());
    assertEquals("num-ok", root.get("results").get(0).get("appId").asText());
  }
}
