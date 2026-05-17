package de.dlr.shepard.v2.semantic.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.context.semantic.SemanticRepositoryType;
import de.dlr.shepard.context.semantic.daos.SemanticRepositoryDAO;
import de.dlr.shepard.context.semantic.entities.SemanticRepository;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * N1f — unit tests for {@link SemanticSparqlRest}.
 *
 * <p>All HTTP execution is bypassed via a subclass that overrides the
 * package-private {@code executeInternal} and {@code executeExternal}
 * methods. This lets us test the authentication gate, the repository
 * lookup, the query-validator integration, and the type-dispatch table
 * without a running Neo4j or Fuseki.
 *
 * <p>The happy-path integration (actual n10s / external HTTP round-trip)
 * is deferred to testcontainer-based integration tests tracked in
 * {@code aidocs/34} N1f row.
 */
class SemanticSparqlRestTest {

  private static final String REPO_APP_ID = "018f9c5a-7e26-7000-a000-000000000099";
  private static final String CALLER = "researcher@example.org";
  private static final String VALID_SELECT = "SELECT ?s ?p ?o WHERE { ?s ?p ?o }";
  private static final String FAKE_RESULTS_JSON =
    "{\"head\":{\"vars\":[\"s\",\"p\",\"o\"]},\"results\":{\"bindings\":[]}}";

  private SemanticRepositoryDAO dao;
  private SecurityContext sc;
  private SemanticSparqlRest rest;

  @BeforeEach
  void setUp() {
    dao = mock(SemanticRepositoryDAO.class);
    sc = mock(SecurityContext.class);
    Principal principal = () -> CALLER;
    when(sc.getUserPrincipal()).thenReturn(principal);

    // Use a stub that bypasses HTTP calls.
    rest = new StubRest(dao);
  }

  // ─── Class-level annotations ──────────────────────────────────────────────

  @Test
  void classCarriesV2PathAnnotation() {
    Path p = SemanticSparqlRest.class.getAnnotation(Path.class);
    assertNotNull(p, "SemanticSparqlRest must be @Path-annotated");
    assertTrue(
      p.value().startsWith("/v2/"),
      "@Path must start with /v2/ per CLAUDE.md policy — got: " + p.value()
    );
  }

  // ─── Auth gate ────────────────────────────────────────────────────────────

  @Test
  void get_noPrincipal_returns401() {
    when(sc.getUserPrincipal()).thenReturn(null);
    Response r = rest.queryGet(REPO_APP_ID, VALID_SELECT, sc);
    assertEquals(401, r.getStatus());
    assertProblemJson(r, SemanticSparqlRest.PROBLEM_TYPE_AUTH, 401);
  }

  @Test
  void post_noPrincipal_returns401() {
    when(sc.getUserPrincipal()).thenReturn(null);
    Response r = rest.queryPost(REPO_APP_ID, VALID_SELECT, sc);
    assertEquals(401, r.getStatus());
    assertProblemJson(r, SemanticSparqlRest.PROBLEM_TYPE_AUTH, 401);
  }

  @Test
  void nullSecurityContext_returns401() {
    Response r = rest.queryGet(REPO_APP_ID, VALID_SELECT, null);
    assertEquals(401, r.getStatus());
  }

  // ─── Repository not found ─────────────────────────────────────────────────

  @Test
  void get_unknownAppId_returns404() {
    when(dao.findByAppId(eq("nonexistent"))).thenReturn(null);
    Response r = rest.queryGet("nonexistent", VALID_SELECT, sc);
    assertEquals(404, r.getStatus());
    assertProblemJson(r, SemanticSparqlRest.PROBLEM_TYPE_NOT_FOUND, 404);
  }

  @Test
  void get_deletedRepo_returns404() {
    SemanticRepository deleted = new SemanticRepository(42L);
    deleted.setDeleted(true);
    deleted.setType(SemanticRepositoryType.INTERNAL);
    when(dao.findByAppId(eq(REPO_APP_ID))).thenReturn(deleted);
    Response r = rest.queryGet(REPO_APP_ID, VALID_SELECT, sc);
    assertEquals(404, r.getStatus());
  }

  // ─── Query validation gate ────────────────────────────────────────────────

  @Test
  void get_emptyQuery_returns400() {
    stubRepo(SemanticRepositoryType.INTERNAL, null);
    Response r = rest.queryGet(REPO_APP_ID, "", sc);
    assertEquals(400, r.getStatus());
  }

  @Test
  void get_nullQuery_returns400() {
    stubRepo(SemanticRepositoryType.INTERNAL, null);
    Response r = rest.queryGet(REPO_APP_ID, null, sc);
    assertEquals(400, r.getStatus());
  }

  @Test
  void get_constructQuery_returns400_readOnly() {
    stubRepo(SemanticRepositoryType.INTERNAL, null);
    Response r = rest.queryGet(REPO_APP_ID, "CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }", sc);
    assertEquals(400, r.getStatus());
    ProblemJson body = (ProblemJson) r.getEntity();
    assertNotNull(body);
    assertTrue(
      body.type().contains("sparql.read-only") || (body.detail() != null && body.detail().contains("CONSTRUCT")),
      "Problem body must reference sparql.read-only or CONSTRUCT keyword"
    );
  }

  @Test
  void get_insertQuery_returns400() {
    stubRepo(SemanticRepositoryType.INTERNAL, null);
    Response r = rest.queryGet(REPO_APP_ID, "INSERT DATA { <s> <p> <o> }", sc);
    assertEquals(400, r.getStatus());
  }

  @Test
  void get_deleteQuery_returns400() {
    stubRepo(SemanticRepositoryType.INTERNAL, null);
    Response r = rest.queryGet(REPO_APP_ID, "DELETE WHERE { ?s ?p ?o }", sc);
    assertEquals(400, r.getStatus());
  }

  @Test
  void get_updateQuery_returns400() {
    stubRepo(SemanticRepositoryType.INTERNAL, null);
    Response r = rest.queryGet(REPO_APP_ID, "UPDATE <g> SET ...", sc);
    assertEquals(400, r.getStatus());
  }

  @Test
  void get_dropQuery_returns400() {
    stubRepo(SemanticRepositoryType.INTERNAL, null);
    Response r = rest.queryGet(REPO_APP_ID, "DROP GRAPH <http://g>", sc);
    assertEquals(400, r.getStatus());
  }

  // ─── Type dispatch ────────────────────────────────────────────────────────

  @Test
  void get_jskosRepo_returns501() {
    stubRepo(SemanticRepositoryType.JSKOS, "http://skosmos.example.org/api");
    Response r = rest.queryGet(REPO_APP_ID, VALID_SELECT, sc);
    assertEquals(501, r.getStatus());
    assertProblemJson(r, SemanticSparqlRest.PROBLEM_TYPE_NOT_SUPPORTED, 501);
  }

  @Test
  void get_skosmosRepo_returns501() {
    stubRepo(SemanticRepositoryType.SKOSMOS, "http://skosmos.example.org/api");
    Response r = rest.queryGet(REPO_APP_ID, VALID_SELECT, sc);
    assertEquals(501, r.getStatus());
    assertProblemJson(r, SemanticSparqlRest.PROBLEM_TYPE_NOT_SUPPORTED, 501);
  }

  @Test
  void get_internalRepo_delegatesToInternal_returns200() {
    stubRepo(SemanticRepositoryType.INTERNAL, null);
    Response r = rest.queryGet(REPO_APP_ID, VALID_SELECT, sc);
    assertEquals(200, r.getStatus());
    assertEquals(FAKE_RESULTS_JSON, r.getEntity());
  }

  @Test
  void get_sparqlRepo_delegatesToExternal_returns200() {
    stubRepo(SemanticRepositoryType.SPARQL, "http://fuseki.example.org/ds/sparql");
    Response r = rest.queryGet(REPO_APP_ID, VALID_SELECT, sc);
    assertEquals(200, r.getStatus());
    assertEquals(FAKE_RESULTS_JSON, r.getEntity());
  }

  // ─── POST form ────────────────────────────────────────────────────────────

  @Test
  void post_validSelect_internalRepo_returns200() {
    stubRepo(SemanticRepositoryType.INTERNAL, null);
    Response r = rest.queryPost(REPO_APP_ID, VALID_SELECT, sc);
    assertEquals(200, r.getStatus());
  }

  @Test
  void post_mutationQuery_returns400() {
    stubRepo(SemanticRepositoryType.INTERNAL, null);
    Response r = rest.queryPost(REPO_APP_ID, "DROP GRAPH <http://g>", sc);
    assertEquals(400, r.getStatus());
  }

  // ─── ASK query ────────────────────────────────────────────────────────────

  @Test
  void get_askQuery_internalRepo_returns200() {
    stubRepo(SemanticRepositoryType.INTERNAL, null);
    Response r = rest.queryGet(REPO_APP_ID, "ASK { ?x ?y ?z }", sc);
    assertEquals(200, r.getStatus());
  }

  // ─── SELECT with PREFIX ───────────────────────────────────────────────────

  @Test
  void get_selectWithPrefixes_internalRepo_returns200() {
    stubRepo(SemanticRepositoryType.INTERNAL, null);
    String q = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\nSELECT ?s WHERE { ?s rdfs:label ?l }";
    Response r = rest.queryGet(REPO_APP_ID, q, sc);
    assertEquals(200, r.getStatus());
  }

  // ─── helpers ──────────────────────────────────────────────────────────────

  private void stubRepo(SemanticRepositoryType type, String endpoint) {
    SemanticRepository repo = new SemanticRepository(1L);
    repo.setType(type);
    repo.setEndpoint(endpoint);
    when(dao.findByAppId(eq(REPO_APP_ID))).thenReturn(repo);
  }

  private static void assertProblemJson(Response r, String expectedType, int expectedStatus) {
    ProblemJson body = (ProblemJson) r.getEntity();
    assertNotNull(body, "Response body must be a ProblemJson");
    assertEquals(expectedType, body.type(), "problem type mismatch");
    assertEquals(expectedStatus, body.status(), "HTTP status in body mismatch");
  }

  // ─── test double ──────────────────────────────────────────────────────────

  /**
   * Subclass of {@link SemanticSparqlRest} that overrides the package-private
   * HTTP-calling methods so unit tests can verify auth / validation / dispatch
   * logic without a running Neo4j or Fuseki. Returns a canned 200 with a
   * minimal SPARQL Results JSON body.
   */
  static class StubRest extends SemanticSparqlRest {

    StubRest(SemanticRepositoryDAO dao) {
      this.semanticRepositoryDAO = dao;
    }

    @Override
    Response executeInternal(String sparqlQuery) {
      return Response.ok(FAKE_RESULTS_JSON, SPARQL_RESULTS_JSON).build();
    }

    @Override
    Response executeExternal(String endpointUrl, String sparqlQuery) {
      return Response.ok(FAKE_RESULTS_JSON, SPARQL_RESULTS_JSON).build();
    }
  }
}
