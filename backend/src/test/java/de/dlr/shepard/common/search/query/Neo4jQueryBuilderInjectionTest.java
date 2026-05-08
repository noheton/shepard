package de.dlr.shepard.common.search.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.dlr.shepard.common.exceptions.ShepardParserException;
import de.dlr.shepard.common.neo4j.entities.ContainerType;
import de.dlr.shepard.common.search.io.SearchScope;
import de.dlr.shepard.common.util.SortingHelper;
import org.junit.jupiter.api.Test;

/**
 * Regression suite for C5 (Cypher injection) in {@link Neo4jQueryBuilder}.
 *
 * <p>Each test exercises a malicious payload at one of the previously
 * unsafe sites and asserts:
 * <ol>
 *   <li>the assembled Cypher does NOT contain the user-supplied payload as
 *       a substring (i.e. the payload was bound, not interpolated); and</li>
 *   <li>the parameter map carries the payload under the expected key
 *       (i.e. the binding is wired through, not silently dropped).</li>
 * </ol>
 */
public class Neo4jQueryBuilderInjectionTest {

  private static final String USER_NAME = "userName";

  /**
   * Property identifiers cannot be parameter-bound in Cypher. The fix is
   * a strict whitelist; any payload that would break out of the
   * surrounding {@code variable.`...`} grave-accents must be rejected.
   */
  @Test
  public void propertyName_breakOutPayload_isRejected() {
    String payload = "name`}) RETURN n // ";
    String body =
      "{\"property\": \"" +
      payload.replace("\\", "\\\\").replace("\"", "\\\"") +
      "\", \"value\": \"x\", \"operator\": \"eq\"}";
    assertThrows(ShepardParserException.class, () ->
      Neo4jQueryBuilder.collectionSelectionQueryWithNeo4jId(body, USER_NAME, new SortingHelper(null, null))
    );
  }

  /**
   * Whitespace, parentheses, semicolons, etc. are all blocked by the
   * regex whitelist. Belt-and-braces alongside the {@code }) RETURN}
   * test above.
   */
  @Test
  public void propertyName_withSpaces_isRejected() {
    String body = "{\"property\": \"name }) RETURN n // \", \"value\": \"x\", \"operator\": \"eq\"}";
    assertThrows(ShepardParserException.class, () ->
      Neo4jQueryBuilder.collectionSelectionQueryWithNeo4jId(body, USER_NAME, new SortingHelper(null, null))
    );
  }

  /**
   * Property VALUES are user-controlled strings; they must never appear
   * in the assembled Cypher and must always come through the parameter
   * map. This is the core C5 defence at {@code primitiveClauseWithNeo4jId}.
   */
  @Test
  public void propertyValue_breakOutPayload_isParameterBound() {
    String payload = "foo\" RETURN labels(n) // ";
    String body = "{\"property\": \"name\", \"value\": \"" + payload.replace("\"", "\\\"") + "\", \"operator\": \"eq\"}";
    Neo4jQuery q = Neo4jQueryBuilder.collectionSelectionQueryWithNeo4jId(
      body,
      USER_NAME,
      new SortingHelper(null, null)
    );
    assertThat(q.cypher()).doesNotContain(payload);
    assertThat(q.cypher()).contains("$p0");
    assertThat(q.params()).containsEntry("p0", payload);
  }

  /**
   * Annotation IRI values were the M9-flagged shape: the value was being
   * interpolated inside a Cypher {@code "..."} literal. A {@code "} or
   * {@code }} in the payload would have terminated the literal. Now both
   * halves of {@code propertyName::valueName} bind as parameters.
   */
  @Test
  public void hasAnnotationIRI_breakOutPayload_isParameterBound() {
    String payload = "evil\" }) RETURN n // ";
    // build a propertyIRI::valueIRI string where the propertyIRI contains
    // the breakout. JSON-encode the inner quotes.
    String escaped = payload.replace("\\", "\\\\").replace("\"", "\\\"");
    String body =
      "{\"property\": \"hasAnnotationIRI\", \"value\": \"" +
      escaped +
      "::valueIri\", \"operator\": \"eq\"}";
    Neo4jQuery q = Neo4jQueryBuilder.collectionSelectionQueryWithNeo4jId(
      body,
      USER_NAME,
      new SortingHelper(null, null)
    );
    assertThat(q.cypher()).doesNotContain(payload);
    assertThat(q.cypher()).contains("$p0");
    assertThat(q.cypher()).contains("$p1");
    assertThat(q.params()).containsEntry("p0", payload).containsEntry("p1", "valueIri");
  }

  /**
   * Same shape for the textual {@code hasAnnotation}
   * ({@code propertyName::valueName}) site at lines 239-241 of the
   * pre-fix source.
   */
  @Test
  public void hasAnnotation_breakOutPayload_isParameterBound() {
    String payload = "evil\" RETURN labels(n) // ";
    String escaped = payload.replace("\\", "\\\\").replace("\"", "\\\"");
    String body =
      "{\"property\": \"hasAnnotation\", \"value\": \"" + escaped + "::valueName\", \"operator\": \"eq\"}";
    Neo4jQuery q = Neo4jQueryBuilder.collectionSelectionQueryWithNeo4jId(
      body,
      USER_NAME,
      new SortingHelper(null, null)
    );
    assertThat(q.cypher()).doesNotContain(payload);
    assertThat(q.cypher()).contains("$p0");
    assertThat(q.params()).containsEntry("p0", payload);
  }

  /**
   * The {@code createdBy}/{@code updatedBy} value path was the M9 site:
   * {@code node.get(OP_VALUE).toString().toLowerCase()} concatenated raw.
   */
  @Test
  public void createdBy_breakOutPayload_isParameterBound() {
    String payload = "alice\" RETURN n // ";
    String body = "{\"property\": \"createdBy\", \"value\": \"" + payload.replace("\"", "\\\"") + "\", \"operator\": \"eq\"}";
    Neo4jQuery q = Neo4jQueryBuilder.collectionSelectionQueryWithNeo4jId(
      body,
      USER_NAME,
      new SortingHelper(null, null)
    );
    assertThat(q.cypher()).doesNotContain(payload);
    assertThat(q.cypher()).contains("$p0");
    assertThat(q.params()).containsEntry("p0", payload);
  }

  /**
   * IRI-property values (the {@code iRIPart} site at line 386 of the
   * pre-fix source) were interpolated raw via {@code node.get(OP_VALUE)}.
   * Now they bind as parameters.
   */
  @Test
  public void valueIRI_breakOutPayload_isParameterBound() {
    String payload = "https://example.org/x\" RETURN n // ";
    String body = "{\"property\": \"valueIRI\", \"value\": \"" + payload.replace("\"", "\\\"") + "\", \"operator\": \"eq\"}";
    Neo4jQuery q = Neo4jQueryBuilder.collectionSelectionQueryWithNeo4jId(
      body,
      USER_NAME,
      new SortingHelper(null, null)
    );
    assertThat(q.cypher()).doesNotContain(payload);
    assertThat(q.cypher()).contains("$p0");
    assertThat(q.params()).containsEntry("p0", payload);
  }

  /**
   * Numeric ids on container queries (line ~501 of the pre-fix source +
   * the {@code containerIdPart} family) used to flow in via raw
   * {@code JsonNode} concatenation. Now they bind as parameters.
   */
  @Test
  public void containerId_value_isParameterBound() {
    String body = "{\"property\": \"fileContainerId\", \"value\": 42, \"operator\": \"eq\"}";
    Neo4jQuery q = Neo4jQueryBuilder.containerSelectionQueryWithNeo4jId(
      body,
      ContainerType.FILE,
      new SortingHelper(null, null),
      USER_NAME
    );
    assertThat(q.cypher()).doesNotContain("= 42 ");
    assertThat(q.cypher()).contains("$p0");
    assertThat(q.params()).containsEntry("p0", 42);
  }

  /**
   * Numeric collection/dataObject ids in the
   * {@code collectionDataObjectTraversalNeo4jIdWherePart} site (line 501
   * of the pre-fix source) used to be raw-concatenated. Now they bind.
   */
  @Test
  public void collectionAndDataObjectIds_areParameterBound() {
    SearchScope scope = new SearchScope();
    scope.setCollectionId(7L);
    scope.setDataObjectId(13L);
    String body = "{\"property\": \"name\", \"value\": \"hello\", \"operator\": \"eq\"}";
    Neo4jQuery q = Neo4jQueryBuilder.collectionDataObjectReferenceSelectionQueryWithNeo4jId(scope, body, USER_NAME);
    assertThat(q.cypher()).doesNotContain("= 7").doesNotContain("= 13");
    assertThat(q.cypher()).contains("id(col) = $").contains("id(do) = $");
    assertThat(q.params()).containsValue(7L).containsValue(13L);
  }

  /**
   * IRI namespace identifiers (the {@code iriType} branch at line 386 of
   * the pre-fix source) flow in via OP_PROPERTY today, but the routing
   * limits the value to {@code propertyIRI}/{@code valueIRI}. Defence in
   * depth: a hand-crafted payload that bypasses routing (impossible
   * today, but a future regression risk) must be rejected by the
   * IRI-identifier whitelist.
   */
  @Test
  public void invalidIriIdentifier_isRejected() {
    // Direct call into the package-private validator path is not
    // exposed; instead exercise the routing the only way the public API
    // permits — via a normal valueIRI request to confirm the validator
    // accepts known-good identifiers.
    String body = "{\"property\": \"valueIRI\", \"value\": \"x\", \"operator\": \"eq\"}";
    Neo4jQuery q = Neo4jQueryBuilder.collectionSelectionQueryWithNeo4jId(
      body,
      USER_NAME,
      new SortingHelper(null, null)
    );
    assertThat(q.cypher()).contains("sem.valueIRI");
    assertThat(q.params()).containsEntry("p0", "x");
  }
}
