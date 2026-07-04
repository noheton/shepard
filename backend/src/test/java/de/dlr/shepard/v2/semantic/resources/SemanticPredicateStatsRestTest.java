package de.dlr.shepard.v2.semantic.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.context.semantic.services.SemanticAnnotationService;
import de.dlr.shepard.context.semantic.services.SemanticAnnotationService.PredicateStats;
import de.dlr.shepard.v2.semantic.io.PredicateStatsIO;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * SEMA-V6-PRED-UI — unit tests for {@link SemanticPredicateStatsRest}.
 *
 * <p>Covers the URL-safe Base64 decode path (happy + malformed + blank),
 * forwarding of query-param limits to the service, and the wire shape of
 * the response IO record.
 */
class SemanticPredicateStatsRestTest {

  private SemanticAnnotationService service;
  private SemanticPredicateStatsRest rest;

  @BeforeEach
  void setUp() {
    service = mock(SemanticAnnotationService.class);
    rest = new SemanticPredicateStatsRest();
    rest.semanticAnnotationService = service;
  }

  private static String urlSafeB64(String iri) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(iri.getBytes(StandardCharsets.UTF_8));
  }

  private static Map<String, Object> topValueRow(String iri, String label, long count) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("objectIri", iri);
    m.put("objectLabel", label);
    m.put("count", count);
    return m;
  }

  private static Map<String, Object> sampleRow(String appId, String name, String type) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("appId", appId);
    m.put("name", name);
    m.put("type", type);
    return m;
  }

  // ─── Base64 decode path ──────────────────────────────────────────────────

  @Test
  void getReturns400WhenSegmentBlank() {
    Response r = rest.getPredicateStats("   ", 20, 10);
    assertEquals(400, r.getStatus());
    verify(service, never()).getPredicateStats("   ", 20, 10);
  }

  @Test
  void getReturns400WhenSegmentNull() {
    Response r = rest.getPredicateStats(null, 20, 10);
    assertEquals(400, r.getStatus());
  }

  @Test
  void getReturns400WhenSegmentNotBase64() {
    Response r = rest.getPredicateStats("!!!not-base64!!!", 20, 10);
    assertEquals(400, r.getStatus());
  }

  @Test
  void getReturns400WhenDecodedIriIsBlank() {
    // Decodes to "   " — service must never be called for a blank IRI.
    String b64 = urlSafeB64("   ");
    Response r = rest.getPredicateStats(b64, 20, 10);
    assertEquals(400, r.getStatus());
  }

  @Test
  void getDecodesUrnPredicateWithColons() {
    String iri = "urn:shepard:material:batch";
    when(service.getPredicateStats(eq(iri), eq(20), eq(10)))
      .thenReturn(new PredicateStats(0L, List.of(), List.of()));

    Response r = rest.getPredicateStats(urlSafeB64(iri), 20, 10);

    assertEquals(200, r.getStatus());
    PredicateStatsIO body = (PredicateStatsIO) r.getEntity();
    assertEquals(iri, body.predicate());
    assertEquals(0L, body.annotationCount());
    assertTrue(body.topValues().isEmpty());
    assertTrue(body.sampleEntities().isEmpty());
  }

  @Test
  void getDecodesHttpPredicateWithSlashes() {
    // The classic FAIRness case — Dublin Core uses slashes and colons.
    String iri = "http://purl.org/dc/terms/creator";
    when(service.getPredicateStats(eq(iri), eq(20), eq(10)))
      .thenReturn(new PredicateStats(3L, List.of(), List.of()));

    Response r = rest.getPredicateStats(urlSafeB64(iri), 20, 10);

    assertEquals(200, r.getStatus());
    PredicateStatsIO body = (PredicateStatsIO) r.getEntity();
    assertEquals(iri, body.predicate());
    assertEquals(3L, body.annotationCount());
  }

  // ─── Query-param forwarding ──────────────────────────────────────────────

  @Test
  void getForwardsTopValuesLimitAndSampleLimitToService() {
    String iri = "urn:shepard:test:p";
    when(service.getPredicateStats(eq(iri), eq(5), eq(3)))
      .thenReturn(new PredicateStats(0L, List.of(), List.of()));

    Response r = rest.getPredicateStats(urlSafeB64(iri), 5, 3);

    assertEquals(200, r.getStatus());
    verify(service).getPredicateStats(iri, 5, 3);
  }

  // ─── Wire shape ──────────────────────────────────────────────────────────

  @Test
  void getReturnsPopulatedStats() {
    String iri = "urn:shepard:material:batch";
    PredicateStats stats = new PredicateStats(
      87L,
      List.of(
        topValueRow("urn:lox-2024-q3", "LOX-2024-Q3", 50L),
        topValueRow(null, "LH2-literal", 37L) // literal-only annotation
      ),
      List.of(
        sampleRow("01928eaa-1111-7000-9000-aaaaaaaaaaaa", "TR-004", "DataObject"),
        sampleRow("01928eaa-2222-7000-9000-bbbbbbbbbbbb", null, "FileReference")
      )
    );
    when(service.getPredicateStats(eq(iri), eq(20), eq(10))).thenReturn(stats);

    Response r = rest.getPredicateStats(urlSafeB64(iri), 20, 10);

    assertEquals(200, r.getStatus());
    PredicateStatsIO body = (PredicateStatsIO) r.getEntity();
    assertNotNull(body);
    assertEquals(iri, body.predicate());
    assertEquals(87L, body.annotationCount());

    assertEquals(2, body.topValues().size());
    PredicateStatsIO.TopValue first = body.topValues().get(0);
    assertEquals("urn:lox-2024-q3", first.objectIri());
    assertEquals("LOX-2024-Q3", first.objectLabel());
    assertEquals(50L, first.count());

    PredicateStatsIO.TopValue second = body.topValues().get(1);
    assertNull(second.objectIri(), "literal-only annotations must carry null objectIri");
    assertEquals("LH2-literal", second.objectLabel());
    assertEquals(37L, second.count());

    assertEquals(2, body.sampleEntities().size());
    PredicateStatsIO.SampleEntity s1 = body.sampleEntities().get(0);
    assertEquals("01928eaa-1111-7000-9000-aaaaaaaaaaaa", s1.appId());
    assertEquals("TR-004", s1.name());
    assertEquals("DataObject", s1.type());

    PredicateStatsIO.SampleEntity s2 = body.sampleEntities().get(1);
    assertEquals("01928eaa-2222-7000-9000-bbbbbbbbbbbb", s2.appId());
    assertNull(s2.name(), "entities without a name fall back to null (UI shows appId)");
    assertEquals("FileReference", s2.type());
  }

  @Test
  void getMapsNumericCountFromAnyNumberFlavour() {
    // Neo4j OGM may return Integer, Long, or Double for COUNT(*) depending on
    // the row materialiser. We accept any Number — pin that contract.
    String iri = "urn:shepard:x:y";
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("objectIri", "urn:v");
    row.put("objectLabel", "V");
    row.put("count", Integer.valueOf(42)); // not Long

    when(service.getPredicateStats(eq(iri), eq(20), eq(10)))
      .thenReturn(new PredicateStats(42L, List.of(row), List.of()));

    Response r = rest.getPredicateStats(urlSafeB64(iri), 20, 10);
    PredicateStatsIO body = (PredicateStatsIO) r.getEntity();
    assertEquals(42L, body.topValues().get(0).count());
  }

  // ─── APISIMP-PREDICATE-STATS-LIMIT-PARAMS-UNDOCUMENTED regression ─────────

  @Test
  void getPredicateStats_topValuesLimitParamIsDocumented() throws Exception {
    Method method = SemanticPredicateStatsRest.class.getMethod(
        "getPredicateStats", String.class, int.class, int.class);
    java.lang.reflect.Parameter topParam = Arrays.stream(method.getParameters())
        .filter(p -> {
          QueryParam qp = p.getAnnotation(QueryParam.class);
          return qp != null && "topValuesLimit".equals(qp.value());
        })
        .findFirst()
        .orElseThrow(() -> new AssertionError(
            "No @QueryParam(\"topValuesLimit\") found on getPredicateStats"));

    Parameter annotation = topParam.getAnnotation(Parameter.class);
    assertNotNull(annotation,
        "@Parameter annotation missing on 'topValuesLimit' @QueryParam in getPredicateStats");
    assertNotNull(annotation.description(),
        "@Parameter description must be set on 'topValuesLimit'");
    assertFalse(annotation.description().isBlank(),
        "@Parameter description must not be blank on 'topValuesLimit'");
  }

  @Test
  void getPredicateStats_sampleLimitParamIsDocumented() throws Exception {
    Method method = SemanticPredicateStatsRest.class.getMethod(
        "getPredicateStats", String.class, int.class, int.class);
    java.lang.reflect.Parameter sampleParam = Arrays.stream(method.getParameters())
        .filter(p -> {
          QueryParam qp = p.getAnnotation(QueryParam.class);
          return qp != null && "sampleLimit".equals(qp.value());
        })
        .findFirst()
        .orElseThrow(() -> new AssertionError(
            "No @QueryParam(\"sampleLimit\") found on getPredicateStats"));

    Parameter annotation = sampleParam.getAnnotation(Parameter.class);
    assertNotNull(annotation,
        "@Parameter annotation missing on 'sampleLimit' @QueryParam in getPredicateStats");
    assertNotNull(annotation.description(),
        "@Parameter description must be set on 'sampleLimit'");
    assertFalse(annotation.description().isBlank(),
        "@Parameter description must not be blank on 'sampleLimit'");
  }
}
