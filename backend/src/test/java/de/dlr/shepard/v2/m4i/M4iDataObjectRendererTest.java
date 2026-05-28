package de.dlr.shepard.v2.m4i;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.semantic.daos.SemanticAnnotationDAO;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.provenance.daos.ActivityDAO;
import de.dlr.shepard.provenance.entities.Activity;
import de.dlr.shepard.publish.daos.PublicationDAO;
import de.dlr.shepard.publish.entities.Publication;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * M4I-c — unit tests for {@link M4iDataObjectRenderer}.
 *
 * Coverage focus:
 * — mandatory triples (identifier, title, dateCreated)
 * — @context contents (m4i, obo, prov, dcterms, schema, qudt, shepard, xsd)
 * — predecessor / successor mapping to obo:RO_0002233/4
 * — most-recent Activity → prov:wasGeneratedBy + M4I-d-1 method + M4I-d-2 tool
 * — numeric annotation → m4i:hasNumericalVariable with qudt:unit
 * — text annotation → schema:keywords fallback
 * — KIP1a Publication → m4i:hasIdentifier
 * — fail-soft on DAO errors
 */
class M4iDataObjectRendererTest {

  static final String DO_APP_ID = "018f9c5a-7e26-7000-a000-000000000020";

  @Mock
  ActivityDAO activityDAO;

  @Mock
  SemanticAnnotationDAO semanticAnnotationDAO;

  @Mock
  PublicationDAO publicationDAO;

  M4iDataObjectRenderer renderer;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    renderer = new M4iDataObjectRenderer();
    renderer.activityDAO = activityDAO;
    renderer.semanticAnnotationDAO = semanticAnnotationDAO;
    renderer.publicationDAO = publicationDAO;
    // Default mock responses — no activities, no annotations, no publications.
    when(activityDAO.list(any(), any(), any(), any(), any(), anyInt())).thenReturn(List.of());
    when(semanticAnnotationDAO.findBySubjectAppId(any())).thenReturn(List.of());
    when(publicationDAO.findByEntityAppId(any())).thenReturn(List.of());
  }

  private DataObject makeDataObject(String appId, String name, String description) {
    DataObject d = new DataObject();
    d.setAppId(appId);
    d.setName(name);
    d.setDescription(description);
    d.setCreatedAt(new Date(1_700_000_000_000L));
    return d;
  }

  // ── @context ─────────────────────────────────────────────────────────

  @Test
  void contextDeclaresAllRequiredPrefixes() {
    var body = renderer.renderDataObject(makeDataObject(DO_APP_ID, "TR-004", null));
    @SuppressWarnings("unchecked")
    Map<String, String> ctx = (Map<String, String>) body.get("@context");
    assertNotNull(ctx);
    assertEquals("http://w3id.org/nfdi4ing/metadata4ing#", ctx.get("m4i"));
    assertEquals("http://purl.obolibrary.org/obo/", ctx.get("obo"));
    assertEquals("http://www.w3.org/ns/prov#", ctx.get("prov"));
    assertEquals("http://purl.org/dc/terms/", ctx.get("dcterms"));
    assertEquals("http://schema.org/", ctx.get("schema"));
    assertEquals("http://qudt.org/schema/qudt/", ctx.get("qudt"));
    assertNotNull(ctx.get("shepard"));
    assertEquals("http://www.w3.org/2001/XMLSchema#", ctx.get("xsd"));
  }

  // ── mandatory triples ────────────────────────────────────────────────

  @Test
  void mandatoryTriplesAllPresent() {
    var body = renderer.renderDataObject(makeDataObject(DO_APP_ID, "TR-004", "hot-fire run"));
    assertEquals("shepard:dataobject/" + DO_APP_ID, body.get("@id"));
    assertEquals(List.of("m4i:InvestigatedObject", "prov:Entity"), body.get("@type"));
    assertEquals(DO_APP_ID, body.get("dcterms:identifier"));
    assertEquals("TR-004", body.get("dcterms:title"));
    @SuppressWarnings("unchecked")
    Map<String, Object> created = (Map<String, Object>) body.get("schema:dateCreated");
    assertEquals("xsd:dateTime", created.get("@type"));
    assertNotNull(created.get("@value"));
    assertEquals("hot-fire run", body.get("dcterms:description"));
  }

  @Test
  void nullDataObjectYieldsEmptyMap() {
    var body = renderer.renderDataObject(null);
    assertTrue(body.isEmpty());
  }

  @Test
  void anonAppIdYieldsAnonId() {
    DataObject d = makeDataObject(null, "TR-X", null);
    var body = renderer.renderDataObject(d);
    assertEquals("shepard:dataobject/anon", body.get("@id"));
    assertFalse(body.containsKey("dcterms:identifier"));
  }

  // ── predecessor / successor ──────────────────────────────────────────

  @Test
  void predecessorsRenderAsRO0002233() {
    DataObject d = makeDataObject(DO_APP_ID, "TR-006", null);
    DataObject pred1 = makeDataObject("pred-1", "TR-005", null);
    DataObject pred2 = makeDataObject("pred-2", "TR-004", null);
    List<DataObject> preds = new ArrayList<>();
    preds.add(pred1);
    preds.add(pred2);
    d.setPredecessors(preds);

    var body = renderer.renderDataObject(d);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> ros = (List<Map<String, Object>>) body.get("obo:RO_0002233");
    assertNotNull(ros);
    assertEquals(2, ros.size());
    assertEquals("shepard:dataobject/pred-1", ros.get(0).get("@id"));
  }

  @Test
  void successorsRenderAsRO0002234() {
    DataObject d = makeDataObject(DO_APP_ID, "TR-004", null);
    DataObject succ = makeDataObject("succ-1", "TR-005", null);
    List<DataObject> succs = new ArrayList<>();
    succs.add(succ);
    d.setSuccessors(succs);

    var body = renderer.renderDataObject(d);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> ros = (List<Map<String, Object>>) body.get("obo:RO_0002234");
    assertNotNull(ros);
    assertEquals(1, ros.size());
    assertEquals("shepard:dataobject/succ-1", ros.get(0).get("@id"));
  }

  @Test
  void deletedPredecessorsExcluded() {
    DataObject d = makeDataObject(DO_APP_ID, "TR-006", null);
    DataObject pred = makeDataObject("pred-1", "TR-005", null);
    pred.setDeleted(true);
    List<DataObject> preds = new ArrayList<>();
    preds.add(pred);
    d.setPredecessors(preds);

    var body = renderer.renderDataObject(d);
    assertNull(body.get("obo:RO_0002233"));
  }

  // ── most-recent Activity (M4I-d-1/2 resolvers) ───────────────────────

  @Test
  void mostRecentActivityEmitsWasGeneratedByAndMethodAndTool() {
    Activity a = new Activity();
    a.setAppId("act-1");
    a.setActionKind("CREATE");
    a.setTargetKind("DataObject");
    a.setEndedAtMillis(1_700_000_500_000L);
    when(activityDAO.list(any(), any(), eq(DO_APP_ID), any(), any(), anyInt()))
      .thenReturn(List.of(a));

    var body = renderer.renderDataObject(makeDataObject(DO_APP_ID, "TR-004", null));
    @SuppressWarnings("unchecked")
    Map<String, Object> wgb = (Map<String, Object>) body.get("prov:wasGeneratedBy");
    assertEquals("shepard:activity/act-1", wgb.get("@id"));

    @SuppressWarnings("unchecked")
    Map<String, Object> realizesMethod = (Map<String, Object>) body.get("m4i:realizesMethod");
    assertEquals("shepard:method/CREATE", realizesMethod.get("@id"));

    @SuppressWarnings("unchecked")
    Map<String, Object> hasTool = (Map<String, Object>) body.get("m4i:hasEmployedTool");
    assertEquals("shepard:tool/DataObject", hasTool.get("@id"));
  }

  @Test
  void activityWithoutActionKindOmitsMethod() {
    Activity a = new Activity();
    a.setAppId("act-2");
    when(activityDAO.list(any(), any(), eq(DO_APP_ID), any(), any(), anyInt()))
      .thenReturn(List.of(a));

    var body = renderer.renderDataObject(makeDataObject(DO_APP_ID, "TR-004", null));
    assertNotNull(body.get("prov:wasGeneratedBy"));
    assertNull(body.get("m4i:realizesMethod"));
    assertNull(body.get("m4i:hasEmployedTool"));
  }

  @Test
  void noActivityOmitsAllProjection() {
    var body = renderer.renderDataObject(makeDataObject(DO_APP_ID, "TR-004", null));
    assertNull(body.get("prov:wasGeneratedBy"));
    assertNull(body.get("m4i:realizesMethod"));
    assertNull(body.get("m4i:hasEmployedTool"));
  }

  @Test
  void activityDaoFailureFailsSoft() {
    when(activityDAO.list(any(), any(), any(), any(), any(), anyInt()))
      .thenThrow(new RuntimeException("Cypher boom"));
    var body = renderer.renderDataObject(makeDataObject(DO_APP_ID, "TR-004", null));
    assertNull(body.get("prov:wasGeneratedBy"));
    // mandatory fields still present
    assertNotNull(body.get("dcterms:identifier"));
  }

  // ── annotations: NumericalVariable + keywords ────────────────────────

  @Test
  void numericAnnotationsBecomeNumericalVariables() {
    SemanticAnnotation a = new SemanticAnnotation();
    a.setPropertyName("vibration");
    a.setValueName("12.3");
    a.setNumericValue(12.3);
    a.setUnitIRI("http://qudt.org/vocab/unit/G");
    when(semanticAnnotationDAO.findBySubjectAppId(eq(DO_APP_ID)))
      .thenReturn(List.of(a));

    var body = renderer.renderDataObject(makeDataObject(DO_APP_ID, "TR-004", null));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> nvs = (List<Map<String, Object>>) body.get("m4i:hasNumericalVariable");
    assertNotNull(nvs);
    assertEquals(1, nvs.size());
    Map<String, Object> nv = nvs.get(0);
    assertEquals("m4i:NumericalVariable", nv.get("@type"));
    assertEquals("vibration", nv.get("rdfs:label"));
    @SuppressWarnings("unchecked")
    Map<String, Object> v = (Map<String, Object>) nv.get("m4i:hasValue");
    assertEquals("xsd:double", v.get("@type"));
    assertEquals("12.3", v.get("@value"));
    @SuppressWarnings("unchecked")
    Map<String, Object> unit = (Map<String, Object>) nv.get("qudt:unit");
    assertEquals("http://qudt.org/vocab/unit/G", unit.get("@id"));
  }

  @Test
  void textAnnotationsBecomeKeywords() {
    SemanticAnnotation a = new SemanticAnnotation();
    a.setPropertyName("propellant");
    a.setValueName("LOX/LH2");
    when(semanticAnnotationDAO.findBySubjectAppId(eq(DO_APP_ID)))
      .thenReturn(List.of(a));

    var body = renderer.renderDataObject(makeDataObject(DO_APP_ID, "TR-004", null));
    @SuppressWarnings("unchecked")
    List<String> kws = (List<String>) body.get("schema:keywords");
    assertNotNull(kws);
    assertTrue(kws.contains("propellant=LOX/LH2"));
    assertNull(body.get("m4i:hasNumericalVariable"));
  }

  @Test
  void mixedAnnotationsSplitCorrectly() {
    SemanticAnnotation numeric = new SemanticAnnotation();
    numeric.setPropertyName("vibration");
    numeric.setNumericValue(12.3);
    SemanticAnnotation text = new SemanticAnnotation();
    text.setPropertyName("propellant");
    text.setValueName("LOX/LH2");
    when(semanticAnnotationDAO.findBySubjectAppId(eq(DO_APP_ID)))
      .thenReturn(List.of(numeric, text));

    var body = renderer.renderDataObject(makeDataObject(DO_APP_ID, "TR-004", null));
    assertNotNull(body.get("m4i:hasNumericalVariable"));
    assertNotNull(body.get("schema:keywords"));
  }

  @Test
  void numericWithoutUnitOmitsQudtUnit() {
    SemanticAnnotation a = new SemanticAnnotation();
    a.setPropertyName("count");
    a.setNumericValue(42.0);
    when(semanticAnnotationDAO.findBySubjectAppId(eq(DO_APP_ID)))
      .thenReturn(List.of(a));

    var body = renderer.renderDataObject(makeDataObject(DO_APP_ID, "TR-004", null));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> nvs = (List<Map<String, Object>>) body.get("m4i:hasNumericalVariable");
    assertNull(nvs.get(0).get("qudt:unit"));
  }

  @Test
  void annotationDaoFailureFailsSoft() {
    when(semanticAnnotationDAO.findBySubjectAppId(any()))
      .thenThrow(new RuntimeException("Cypher boom"));
    var body = renderer.renderDataObject(makeDataObject(DO_APP_ID, "TR-004", null));
    assertNull(body.get("m4i:hasNumericalVariable"));
    assertNull(body.get("schema:keywords"));
    assertNotNull(body.get("dcterms:identifier"));
  }

  // ── KIP1a Publication → m4i:hasIdentifier ────────────────────────────

  @Test
  void publicationEmitsHasIdentifier() {
    Publication pub = new Publication();
    pub.setPid("21.T11/abc123");
    pub.setMintedAt(1_700_000_000_000L);
    when(publicationDAO.findByEntityAppId(eq(DO_APP_ID))).thenReturn(List.of(pub));

    var body = renderer.renderDataObject(makeDataObject(DO_APP_ID, "TR-004", null));
    @SuppressWarnings("unchecked")
    Map<String, Object> hi = (Map<String, Object>) body.get("m4i:hasIdentifier");
    assertEquals("m4i:Identifier", hi.get("@type"));
    assertEquals("21.T11/abc123", hi.get("m4i:identifierValue"));
    assertEquals("Handle", hi.get("m4i:hasIdentifierType"));
  }

  @Test
  void publicationWithoutPidSkipped() {
    Publication pub = new Publication();
    pub.setPid(null);
    when(publicationDAO.findByEntityAppId(eq(DO_APP_ID))).thenReturn(List.of(pub));

    var body = renderer.renderDataObject(makeDataObject(DO_APP_ID, "TR-004", null));
    assertNull(body.get("m4i:hasIdentifier"));
  }

  @Test
  void publicationDaoFailureFailsSoft() {
    when(publicationDAO.findByEntityAppId(any()))
      .thenThrow(new RuntimeException("Cypher boom"));
    var body = renderer.renderDataObject(makeDataObject(DO_APP_ID, "TR-004", null));
    assertNull(body.get("m4i:hasIdentifier"));
    assertNotNull(body.get("dcterms:identifier"));
  }

  // ── resolver helpers ─────────────────────────────────────────────────

  @Test
  void methodResolverNullActionKindReturnsNull() {
    assertNull(M4iDataObjectRenderer.MethodResolver.iriFor(null));
    assertNull(M4iDataObjectRenderer.MethodResolver.iriFor(""));
    assertNull(M4iDataObjectRenderer.MethodResolver.iriFor("   "));
  }

  @Test
  void methodResolverProducesStableIri() {
    assertEquals("shepard:method/CREATE", M4iDataObjectRenderer.MethodResolver.iriFor("CREATE"));
    assertEquals("shepard:method/PATCH", M4iDataObjectRenderer.MethodResolver.iriFor("PATCH"));
  }

  @Test
  void toolResolverNullTargetKindReturnsNull() {
    assertNull(M4iDataObjectRenderer.ToolResolver.iriFor(null));
    assertNull(M4iDataObjectRenderer.ToolResolver.iriFor(""));
  }

  @Test
  void toolResolverProducesStableIri() {
    assertEquals("shepard:tool/DataObject", M4iDataObjectRenderer.ToolResolver.iriFor("DataObject"));
    assertEquals("shepard:tool/Collection", M4iDataObjectRenderer.ToolResolver.iriFor("Collection"));
  }

  @Test
  void numericalVariableResolverHandlesNullAnnotation() {
    assertNull(M4iDataObjectRenderer.NumericalVariableResolver.toNode(null));
  }

  @Test
  void numericalVariableResolverNullNumericValueYieldsNull() {
    SemanticAnnotation a = new SemanticAnnotation();
    a.setPropertyName("propellant");
    assertNull(M4iDataObjectRenderer.NumericalVariableResolver.toNode(a));
  }

  @Test
  void numericalVariableResolverFallbacksToValueNameWhenPropertyMissing() {
    SemanticAnnotation a = new SemanticAnnotation();
    a.setValueName("12.3");
    a.setNumericValue(12.3);
    var node = M4iDataObjectRenderer.NumericalVariableResolver.toNode(a);
    assertEquals("12.3", node.get("rdfs:label"));
  }
}
