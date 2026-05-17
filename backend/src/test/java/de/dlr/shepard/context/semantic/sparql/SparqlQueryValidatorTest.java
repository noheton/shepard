package de.dlr.shepard.context.semantic.sparql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * N1f — unit tests for {@link SparqlQueryValidator}.
 *
 * <p>The validator guards the SPARQL proxy endpoint against mutation queries.
 * Tests cover:
 * <ul>
 *   <li>Allowed forms: SELECT, ASK</li>
 *   <li>Rejected mutation forms: CONSTRUCT, DESCRIBE, INSERT, DELETE, UPDATE,
 *       DROP, CREATE, LOAD, CLEAR, ADD, MOVE, COPY</li>
 *   <li>Edge cases: null/blank, leading whitespace, PREFIX declarations,
 *       comments, mixed-case keywords</li>
 * </ul>
 */
class SparqlQueryValidatorTest {

  // ─── SELECT ───────────────────────────────────────────────────────────────

  @Test
  void selectQuery_isAllowed() {
    var result = SparqlQueryValidator.validate("SELECT ?s ?p ?o WHERE { ?s ?p ?o }");
    assertTrue(result.isAllowed(), "SELECT should be allowed");
    assertNull(result.getErrorType());
    assertNull(result.getErrorDetail());
  }

  @Test
  void selectWithPrefixes_isAllowed() {
    String query = """
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        SELECT ?label WHERE { ?s rdfs:label ?label }
        """;
    assertTrue(SparqlQueryValidator.validate(query).isAllowed(), "SELECT with PREFIX should be allowed");
  }

  @Test
  void selectWithComments_isAllowed() {
    String query = """
        # This is a SPARQL query
        SELECT ?s WHERE { ?s ?p ?o }
        """;
    assertTrue(SparqlQueryValidator.validate(query).isAllowed());
  }

  @Test
  void selectLowercase_isAllowed() {
    assertTrue(SparqlQueryValidator.validate("select ?s where { ?s ?p ?o }").isAllowed());
  }

  @Test
  void selectMixedCase_isAllowed() {
    assertTrue(SparqlQueryValidator.validate("Select ?s Where { ?s ?p ?o }").isAllowed());
  }

  @Test
  void selectWithLeadingWhitespace_isAllowed() {
    assertTrue(SparqlQueryValidator.validate("   SELECT ?s WHERE { ?s ?p ?o }").isAllowed());
  }

  // ─── ASK ──────────────────────────────────────────────────────────────────

  @Test
  void askQuery_isAllowed() {
    assertTrue(SparqlQueryValidator.validate("ASK { ?x ?y ?z }").isAllowed(), "ASK should be allowed");
  }

  @Test
  void askLowercase_isAllowed() {
    assertTrue(SparqlQueryValidator.validate("ask { ?x ?y ?z }").isAllowed());
  }

  // ─── CONSTRUCT ────────────────────────────────────────────────────────────

  @Test
  void constructQuery_isRejected() {
    var result = SparqlQueryValidator.validate("CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }");
    assertFalse(result.isAllowed(), "CONSTRUCT should be rejected");
    assertNotNull(result.getErrorType());
    assertTrue(result.getErrorDetail().contains("CONSTRUCT"), "detail should mention CONSTRUCT");
  }

  @Test
  void constructLowercase_isRejected() {
    assertFalse(SparqlQueryValidator.validate("construct { ?s ?p ?o } WHERE { ?s ?p ?o }").isAllowed());
  }

  // ─── DESCRIBE ─────────────────────────────────────────────────────────────

  @Test
  void describeQuery_isRejected() {
    assertFalse(
      SparqlQueryValidator.validate("DESCRIBE <http://example.org/resource>").isAllowed(),
      "DESCRIBE should be rejected"
    );
  }

  // ─── INSERT ───────────────────────────────────────────────────────────────

  @Test
  void insertData_isRejected() {
    assertFalse(
      SparqlQueryValidator.validate("INSERT DATA { <http://s> <http://p> <http://o> }").isAllowed(),
      "INSERT DATA should be rejected"
    );
  }

  @Test
  void insertWhere_isRejected() {
    assertFalse(
      SparqlQueryValidator.validate("INSERT { ?s <http://p> 'val' } WHERE { ?s <http://q> ?o }").isAllowed()
    );
  }

  // ─── DELETE ───────────────────────────────────────────────────────────────

  @Test
  void deleteData_isRejected() {
    assertFalse(
      SparqlQueryValidator.validate("DELETE DATA { <http://s> <http://p> <http://o> }").isAllowed(),
      "DELETE DATA should be rejected"
    );
  }

  @Test
  void deleteWhere_isRejected() {
    assertFalse(
      SparqlQueryValidator.validate("DELETE WHERE { ?s <http://p> ?o }").isAllowed()
    );
  }

  // ─── UPDATE ───────────────────────────────────────────────────────────────

  @Test
  void updateQuery_isRejected() {
    // SPARQL Update starts with UPDATE (some variants) or INSERT/DELETE
    assertFalse(
      SparqlQueryValidator.validate("UPDATE <http://graph> SET ...").isAllowed(),
      "UPDATE should be rejected"
    );
  }

  // ─── DROP / CREATE / LOAD / CLEAR / ADD / MOVE / COPY ────────────────────

  @Test
  void dropGraph_isRejected() {
    assertFalse(SparqlQueryValidator.validate("DROP GRAPH <http://g>").isAllowed());
  }

  @Test
  void createGraph_isRejected() {
    assertFalse(SparqlQueryValidator.validate("CREATE GRAPH <http://g>").isAllowed());
  }

  @Test
  void loadData_isRejected() {
    assertFalse(SparqlQueryValidator.validate("LOAD <http://data.example.org/data.ttl>").isAllowed());
  }

  @Test
  void clearGraph_isRejected() {
    assertFalse(SparqlQueryValidator.validate("CLEAR GRAPH <http://g>").isAllowed());
  }

  @Test
  void addGraph_isRejected() {
    assertFalse(SparqlQueryValidator.validate("ADD <http://g1> TO <http://g2>").isAllowed());
  }

  @Test
  void moveGraph_isRejected() {
    assertFalse(SparqlQueryValidator.validate("MOVE <http://g1> TO <http://g2>").isAllowed());
  }

  @Test
  void copyGraph_isRejected() {
    assertFalse(SparqlQueryValidator.validate("COPY <http://g1> TO <http://g2>").isAllowed());
  }

  // ─── Edge cases ───────────────────────────────────────────────────────────

  @Test
  void nullQuery_isRejected() {
    var result = SparqlQueryValidator.validate(null);
    assertFalse(result.isAllowed());
    assertEquals("sparql.empty-query", result.getErrorType());
  }

  @Test
  void blankQuery_isRejected() {
    assertFalse(SparqlQueryValidator.validate("   ").isAllowed());
    assertEquals("sparql.empty-query", SparqlQueryValidator.validate("").getErrorType());
  }

  @Test
  void onlyComments_isRejected() {
    String query = "# just a comment\n# another comment";
    assertFalse(SparqlQueryValidator.validate(query).isAllowed());
    assertEquals("sparql.unrecognised-form", SparqlQueryValidator.validate(query).getErrorType());
  }

  @Test
  void onlyPrefixes_isRejected() {
    String query = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>";
    assertFalse(SparqlQueryValidator.validate(query).isAllowed());
    assertEquals("sparql.unrecognised-form", SparqlQueryValidator.validate(query).getErrorType());
  }

  @Test
  void prefixedByBase_thenSelect_isAllowed() {
    String query = "BASE <http://example.org/>\nSELECT ?s WHERE { ?s ?p ?o }";
    assertTrue(SparqlQueryValidator.validate(query).isAllowed());
  }

  @Test
  void prefixThenConstructInComment_butActuallySelect_isAllowed() {
    // Comment contains CONSTRUCT but real keyword is SELECT — must allow.
    String query = """
        # CONSTRUCT is not used here
        PREFIX ex: <http://example.org/>
        SELECT ?s WHERE { ?s ex:p ?o }
        """;
    assertTrue(SparqlQueryValidator.validate(query).isAllowed());
  }

  // ─── extractFirstKeyword unit tests ───────────────────────────────────────

  @Test
  void extractFirstKeyword_nullInput_returnsNull() {
    assertNull(SparqlQueryValidator.extractFirstKeyword(null));
  }

  @Test
  void extractFirstKeyword_emptyInput_returnsNull() {
    assertNull(SparqlQueryValidator.extractFirstKeyword(""));
  }

  @Test
  void extractFirstKeyword_selectLine_returnsSelect() {
    assertEquals("SELECT", SparqlQueryValidator.extractFirstKeyword("SELECT ?s WHERE { }"));
  }

  @Test
  void extractFirstKeyword_insertLine_returnsInsert() {
    assertEquals("INSERT", SparqlQueryValidator.extractFirstKeyword("INSERT DATA { }"));
  }

  @Test
  void extractFirstKeyword_skipsBlankLines() {
    assertEquals("ASK", SparqlQueryValidator.extractFirstKeyword("\n\n\nASK { }"));
  }

  @Test
  void extractFirstKeyword_skipsCommentLines() {
    assertEquals("SELECT", SparqlQueryValidator.extractFirstKeyword("# comment\nSELECT ?x WHERE { }"));
  }

  @Test
  void extractFirstKeyword_skipsPrefixDeclarations() {
    assertEquals(
      "SELECT",
      SparqlQueryValidator.extractFirstKeyword("PREFIX ex: <http://example.org/>\nSELECT ?s WHERE { }")
    );
  }

  @Test
  void extractFirstKeyword_lowercaseSelect_returnsUppercase() {
    assertEquals("SELECT", SparqlQueryValidator.extractFirstKeyword("select ?s where { }"));
  }
}
