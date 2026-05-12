package de.dlr.shepard.context.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;

/**
 * Unit tests for {@link InternalSemanticConnector}. Pure mocks — no
 * Quarkus context, no live Neo4j, no n10s plugin. The {@code @QuarkusTest}
 * style is deliberately avoided per the N1a scope-note.
 *
 * <p>Helper Result mocks are constructed <i>before</i> the
 * {@code when(session.query(...)).thenReturn(...)} stubbing to keep
 * Mockito's nested-stubbing detector happy.
 */
class InternalSemanticConnectorTest {

  /** healthCheck: n10s procedures registered → true. */
  @Test
  void healthCheck_returnsTrueWhenN10sAvailable() {
    Session session = mock(Session.class);
    Result result = singleRow(Map.of("available", Boolean.TRUE));
    when(session.query(eq(InternalSemanticConnector.HEALTH_CYPHER), any())).thenReturn(result);

    var connector = new InternalSemanticConnector(session);
    assertTrue(connector.healthCheck());
  }

  /** healthCheck: n10s procedures absent → false. */
  @Test
  void healthCheck_returnsFalseWhenN10sAbsent() {
    Session session = mock(Session.class);
    Result result = singleRow(Map.of("available", Boolean.FALSE));
    when(session.query(eq(InternalSemanticConnector.HEALTH_CYPHER), any())).thenReturn(result);

    var connector = new InternalSemanticConnector(session);
    assertFalse(connector.healthCheck());
  }

  /** healthCheck: no rows from query → false. */
  @Test
  void healthCheck_returnsFalseWhenNoRows() {
    Session session = mock(Session.class);
    Result result = emptyResult();
    when(session.query(eq(InternalSemanticConnector.HEALTH_CYPHER), any())).thenReturn(result);

    var connector = new InternalSemanticConnector(session);
    assertFalse(connector.healthCheck());
  }

  /** healthCheck: query raises → false (no exception bubbled). */
  @Test
  void healthCheck_returnsFalseOnRuntimeException() {
    Session session = mock(Session.class);
    when(session.query(eq(InternalSemanticConnector.HEALTH_CYPHER), any())).thenThrow(new RuntimeException("boom"));

    var connector = new InternalSemanticConnector(session);
    assertFalse(connector.healthCheck());
  }

  /** healthCheck: null session → false. */
  @Test
  void healthCheck_returnsFalseOnNullSession() {
    var connector = new InternalSemanticConnector((Session) null);
    assertFalse(connector.healthCheck());
  }

  /** getTerm: bare rdfs__label → empty-string lang key. */
  @Test
  void getTerm_returnsBareLabelUnderEmptyLanguage() {
    Session session = mock(Session.class);
    Map<String, Object> props = new LinkedHashMap<>();
    props.put("rdfs__label", "G-Force");
    props.put("uri", "http://qudt.org/vocab/unit/G_Earth");
    Result result = singleRow(Map.of("props", props));
    when(session.query(eq(InternalSemanticConnector.GET_TERM_CYPHER), eq(Map.of("uri", "http://example#x")))).thenReturn(
      result
    );

    var connector = new InternalSemanticConnector(session);
    var labels = connector.getTerm("http://example#x");

    assertEquals(1, labels.size());
    assertEquals("G-Force", labels.get(""));
  }

  /** getTerm: language-tagged rdfs__label keys map to per-language entries. */
  @Test
  void getTerm_extractsLanguageTaggedLabels() {
    Session session = mock(Session.class);
    Map<String, Object> props = new LinkedHashMap<>();
    props.put("rdfs__label@en", "Earth gravity");
    props.put("rdfs__label@de", "Erdbeschleunigung");
    props.put("uri", "http://qudt.org/vocab/unit/G_Earth");
    Result result = singleRow(Map.of("props", props));
    when(session.query(eq(InternalSemanticConnector.GET_TERM_CYPHER), any())).thenReturn(result);

    var connector = new InternalSemanticConnector(session);
    var labels = connector.getTerm("http://qudt.org/vocab/unit/G_Earth");

    assertEquals(2, labels.size());
    assertEquals("Earth gravity", labels.get("en"));
    assertEquals("Erdbeschleunigung", labels.get("de"));
  }

  /** getTerm: handleMultival=ARRAY yields String[] — first non-blank wins. */
  @Test
  void getTerm_picksFirstNonBlankFromArray() {
    Session session = mock(Session.class);
    Map<String, Object> props = Map.of("rdfs__label@en", new String[] { "", "  ", "Earth gravity", "duplicate" });
    Result result = singleRow(Map.of("props", props));
    when(session.query(eq(InternalSemanticConnector.GET_TERM_CYPHER), any())).thenReturn(result);

    var connector = new InternalSemanticConnector(session);
    var labels = connector.getTerm("http://example#x");

    assertEquals("Earth gravity", labels.get("en"));
  }

  /** getTerm: List<String> shape (alternate OGM materialisation) is tolerated. */
  @Test
  void getTerm_handlesListMultiValue() {
    Session session = mock(Session.class);
    Map<String, Object> props = Map.of("rdfs__label@en", List.of("Earth gravity", "alt"));
    Result result = singleRow(Map.of("props", props));
    when(session.query(eq(InternalSemanticConnector.GET_TERM_CYPHER), any())).thenReturn(result);

    var connector = new InternalSemanticConnector(session);
    var labels = connector.getTerm("http://example#x");

    assertEquals("Earth gravity", labels.get("en"));
  }

  /** getTerm: no row → empty map. */
  @Test
  void getTerm_returnsEmptyMapWhenNotFound() {
    Session session = mock(Session.class);
    Result result = emptyResult();
    when(session.query(eq(InternalSemanticConnector.GET_TERM_CYPHER), any())).thenReturn(result);

    var connector = new InternalSemanticConnector(session);
    assertTrue(connector.getTerm("http://missing").isEmpty());
  }

  /** getTerm: blank IRI is rejected without hitting the session. */
  @Test
  void getTerm_returnsEmptyOnBlankIri() {
    Session session = mock(Session.class);
    var connector = new InternalSemanticConnector(session);
    assertTrue(connector.getTerm("").isEmpty());
    assertTrue(connector.getTerm(null).isEmpty());
    verify(session, never()).query(any(String.class), any());
  }

  /** getTerm: null session → empty map. */
  @Test
  void getTerm_returnsEmptyOnNullSession() {
    var connector = new InternalSemanticConnector((Session) null);
    assertTrue(connector.getTerm("http://example#x").isEmpty());
  }

  /** getTerm: query raises → empty map (no exception bubbled). */
  @Test
  void getTerm_returnsEmptyOnRuntimeException() {
    Session session = mock(Session.class);
    when(session.query(eq(InternalSemanticConnector.GET_TERM_CYPHER), any())).thenThrow(new RuntimeException("boom"));

    var connector = new InternalSemanticConnector(session);
    assertTrue(connector.getTerm("http://example#x").isEmpty());
  }

  /** getTerm: row without props key (n10s never populated) → empty. */
  @Test
  void getTerm_returnsEmptyWhenPropsMissing() {
    Session session = mock(Session.class);
    Result result = singleRow(Map.of("other", "value"));
    when(session.query(eq(InternalSemanticConnector.GET_TERM_CYPHER), any())).thenReturn(result);

    var connector = new InternalSemanticConnector(session);
    assertTrue(connector.getTerm("http://example#x").isEmpty());
  }

  /** extractLabels: only rdfs__label* keys are considered. */
  @Test
  void extractLabels_ignoresUnrelatedProperties() {
    Map<String, Object> props = new LinkedHashMap<>();
    props.put("rdfs__label@en", "Earth gravity");
    props.put("rdf__type", "qudt__Unit");
    props.put("uri", "http://qudt.org/vocab/unit/G_Earth");
    var labels = InternalSemanticConnector.extractLabels(props);
    assertEquals(1, labels.size());
    assertTrue(labels.containsKey("en"));
  }

  /** extractLabels: empty props → empty map (no NPE). */
  @Test
  void extractLabels_handlesNullAndEmpty() {
    assertTrue(InternalSemanticConnector.extractLabels(null).isEmpty());
    assertTrue(InternalSemanticConnector.extractLabels(Collections.emptyMap()).isEmpty());
  }

  /** extractLabels: blank rdfs__label value is filtered out. */
  @Test
  void extractLabels_skipsBlankValues() {
    Map<String, Object> props = Map.of("rdfs__label", "   ");
    assertTrue(InternalSemanticConnector.extractLabels(props).isEmpty());
  }

  /** extractLabels: trailing @ with no language tag is treated as bare. */
  @Test
  void extractLabels_handlesTrailingAt() {
    Map<String, Object> props = Map.of("rdfs__label@", "naked");
    var labels = InternalSemanticConnector.extractLabels(props);
    assertEquals("naked", labels.get(""));
  }

  // ----- helpers ----------------------------------------------------------

  static Result singleRow(Map<String, Object> row) {
    Result result = mock(Result.class);
    when(result.queryResults()).thenReturn(List.of(row));
    return result;
  }

  static Result emptyResult() {
    Result result = mock(Result.class);
    when(result.queryResults()).thenReturn(Collections.emptyList());
    return result;
  }
}
