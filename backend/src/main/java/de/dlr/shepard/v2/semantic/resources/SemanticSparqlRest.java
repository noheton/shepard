package de.dlr.shepard.v2.semantic.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.context.semantic.SemanticRepositoryType;
import de.dlr.shepard.context.semantic.daos.SemanticRepositoryDAO;
import de.dlr.shepard.context.semantic.entities.SemanticRepository;
import de.dlr.shepard.context.semantic.sparql.SparqlQueryValidator;
import de.dlr.shepard.context.semantic.sparql.SparqlQueryValidator.ValidationResult;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * N1f — SPARQL proxy endpoint that wraps n10s (neosemantics) and external
 * SPARQL endpoints behind shepard authentication.
 *
 * <p>Two HTTP verbs, both at {@code /v2/semantic/{repoAppId}/sparql}:
 * <ul>
 *   <li>{@code GET  ?query=<SPARQL>} — SPARQL 1.1 Protocol §2.1.2</li>
 *   <li>{@code POST} with {@code application/x-www-form-urlencoded} body
 *       {@code query=<SPARQL>} — SPARQL 1.1 Protocol §2.1.3</li>
 * </ul>
 *
 * <p><b>Auth.</b> Any authenticated shepard user may execute SELECT/ASK
 * queries against any SemanticRepository. SemanticRepositories are an
 * admin-curated catalogue visible to all authenticated users (matching
 * the v1 surface in {@code SemanticRepositoryRest} which carries no
 * per-entity permission check beyond being authenticated). If no JWT is
 * present the endpoint returns 401.
 *
 * <p><b>Read-only enforcement.</b> Only {@code SELECT} and {@code ASK}
 * queries are forwarded. Mutation forms ({@code CONSTRUCT}, {@code INSERT},
 * {@code DELETE}, {@code UPDATE}, …) are rejected 400
 * {@code sparql.read-only} before touching the backend.
 *
 * <p><b>Repository-type dispatch.</b>
 * <ul>
 *   <li>{@code INTERNAL} — proxies to the n10s HTTP SPARQL endpoint at
 *       {@code POST /rdf/neo4j/sparql} on the Neo4j HTTP port (default
 *       {@code http://localhost:7474}). Configurable via
 *       {@code shepard.semantic.internal.http-url}. Returns 503 when n10s
 *       is unreachable.</li>
 *   <li>{@code SPARQL} — proxies to the external endpoint URL stored on
 *       the entity. Any {@code Authorization} header the caller sends is
 *       stripped before forwarding to prevent credential leakage.</li>
 *   <li>{@code JSKOS} / {@code SKOSMOS} — returns 501 {@code sparql.not-supported}
 *       because these repository types do not speak the SPARQL protocol.</li>
 * </ul>
 *
 * <p><b>Response format.</b> The upstream response is passed through
 * verbatim with {@code Content-Type: application/sparql-results+json}.
 * When the upstream returns a non-200 status the endpoint maps it to a
 * 502 {@code sparql.upstream-error} RFC 7807 body.
 *
 * @see de.dlr.shepard.context.semantic.sparql.SparqlQueryValidator
 * @see de.dlr.shepard.context.semantic.N10sBootstrapHook
 */
@Path("/v2/semantic")
@RequestScoped
@Tag(name = "Semantic SPARQL proxy (v2, N1f)")
public class SemanticSparqlRest {

  // ─── Media types ──────────────────────────────────────────────────────────

  /** W3C SPARQL Results JSON — the canonical wire format for SELECT/ASK results. */
  static final String SPARQL_RESULTS_JSON = "application/sparql-results+json";

  /** SPARQL 1.1 Protocol form content-type. */
  static final String FORM_URL_ENCODED = "application/x-www-form-urlencoded";

  // ─── RFC 7807 problem type tokens ─────────────────────────────────────────

  static final String PROBLEM_TYPE_AUTH = "urn:shepard:error:auth";
  static final String PROBLEM_TYPE_NOT_FOUND = "urn:shepard:error:not-found";
  static final String PROBLEM_TYPE_READ_ONLY = "urn:shepard:error:sparql.read-only";
  static final String PROBLEM_TYPE_NOT_SUPPORTED = "urn:shepard:error:sparql.not-supported";
  static final String PROBLEM_TYPE_UPSTREAM = "urn:shepard:error:sparql.upstream-error";
  static final String PROBLEM_TYPE_UNAVAILABLE = "urn:shepard:error:sparql.unavailable";

  // ─── HTTP client timeouts ──────────────────────────────────────────────────

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

  // ─── Default n10s HTTP base URL ────────────────────────────────────────────

  /**
   * Deploy-time config key for the Neo4j HTTP port base URL. The n10s
   * SPARQL endpoint lives at {@code <base>/rdf/neo4j/sparql}. Default
   * {@code http://localhost:7474} matches the local-dev compose setup.
   */
  static final String N10S_HTTP_URL_KEY = "shepard.semantic.internal.http-url";
  static final String N10S_HTTP_URL_DEFAULT = "http://localhost:7474";

  // ─── n10s Neo4j credentials config keys ───────────────────────────────────

  static final String N10S_USERNAME_KEY = "neo4j.username";
  static final String N10S_PASSWORD_KEY = "neo4j.password";

  // ─── injected ─────────────────────────────────────────────────────────────

  @Inject
  SemanticRepositoryDAO semanticRepositoryDAO;

  private final ObjectMapper objectMapper = new ObjectMapper();

  /** Lazily created — one shared client per CDI bean lifecycle. */
  private volatile HttpClient httpClient;

  // ─── endpoints ────────────────────────────────────────────────────────────

  /**
   * {@code GET /v2/semantic/{repoAppId}/sparql?query=<SPARQL>}
   *
   * <p>SPARQL 1.1 Protocol §2.1.2 — query via HTTP GET.
   */
  @GET
  @Path("/{repoAppId}/sparql")
  @Produces({ SPARQL_RESULTS_JSON, MediaType.APPLICATION_JSON })
  @Operation(
    summary = "Execute a read-only SPARQL SELECT or ASK query against a SemanticRepository.",
    description = "Proxies the query to n10s (INTERNAL) or an external SPARQL endpoint (SPARQL). " +
    "Only SELECT and ASK are permitted (400 on mutation forms). " +
    "Authentication required (401). Any authenticated user may query any repository (no per-entity permission check beyond auth). " +
    "Returns W3C SPARQL Results JSON (application/sparql-results+json). " +
    "503 when the backend is unreachable; 502 when the backend returns an error."
  )
  @APIResponse(responseCode = "200", description = "SPARQL Results JSON (SELECT or ASK).")
  @APIResponse(responseCode = "400", description = "Empty query, or mutation form (CONSTRUCT / INSERT / DELETE / UPDATE …).")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "404", description = "No SemanticRepository with that appId.")
  @APIResponse(responseCode = "501", description = "SPARQL not supported for this repository type (JSKOS / SKOSMOS).")
  @APIResponse(responseCode = "502", description = "Upstream endpoint returned an error.")
  @APIResponse(responseCode = "503", description = "Upstream endpoint is unreachable.")
  public Response queryGet(
    @PathParam("repoAppId") String repoAppId,
    @QueryParam("query") String query,
    @Context SecurityContext sc
  ) {
    return executeSparql(repoAppId, query, sc);
  }

  /**
   * {@code POST /v2/semantic/{repoAppId}/sparql}
   * with {@code application/x-www-form-urlencoded} body {@code query=<SPARQL>}.
   *
   * <p>SPARQL 1.1 Protocol §2.1.3 — query via HTTP POST form.
   */
  @POST
  @Path("/{repoAppId}/sparql")
  @Consumes(FORM_URL_ENCODED)
  @Produces({ SPARQL_RESULTS_JSON, MediaType.APPLICATION_JSON })
  @Operation(
    summary = "Execute a read-only SPARQL SELECT or ASK query (POST form variant).",
    description = "Same semantics as the GET form. Use when the query is too long for a URL."
  )
  @APIResponse(responseCode = "200", description = "SPARQL Results JSON.")
  @APIResponse(responseCode = "400", description = "Empty query, or mutation form.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "404", description = "No SemanticRepository with that appId.")
  @APIResponse(responseCode = "501", description = "SPARQL not supported for this repository type.")
  @APIResponse(responseCode = "502", description = "Upstream returned an error.")
  @APIResponse(responseCode = "503", description = "Upstream is unreachable.")
  public Response queryPost(
    @PathParam("repoAppId") String repoAppId,
    @FormParam("query") String query,
    @Context SecurityContext sc
  ) {
    return executeSparql(repoAppId, query, sc);
  }

  // ─── core logic ───────────────────────────────────────────────────────────

  /**
   * Shared execution path for GET and POST.
   *
   * <ol>
   *   <li>Auth gate — 401 if unauthenticated.</li>
   *   <li>Look up repository by appId — 404 if missing or deleted.</li>
   *   <li>Validate query form — 400 on mutations / empty.</li>
   *   <li>Dispatch by type — proxy or 501.</li>
   * </ol>
   */
  Response executeSparql(String repoAppId, String query, SecurityContext sc) {
    // 1 — auth
    String caller = sc != null && sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
    if (caller == null) {
      return problem(Status.UNAUTHORIZED, PROBLEM_TYPE_AUTH, "Authentication required.", "SPARQL proxy requires an authenticated user.");
    }

    // 2 — look up repository
    SemanticRepository repo = semanticRepositoryDAO.findByAppId(repoAppId);
    if (repo == null || repo.isDeleted()) {
      return problem(Status.NOT_FOUND, PROBLEM_TYPE_NOT_FOUND, "Semantic repository not found.", "No repository with appId: " + repoAppId);
    }

    // 3 — validate query
    ValidationResult validation = SparqlQueryValidator.validate(query);
    if (!validation.isAllowed()) {
      return problem(Status.BAD_REQUEST, "urn:shepard:error:" + validation.getErrorType(), "SPARQL read-only policy violated.", validation.getErrorDetail());
    }

    // 4 — dispatch by type
    SemanticRepositoryType type = repo.getType();
    if (type == SemanticRepositoryType.INTERNAL) {
      return executeInternal(query);
    } else if (type == SemanticRepositoryType.SPARQL) {
      return executeExternal(repo.getEndpoint(), query);
    } else {
      // JSKOS / SKOSMOS do not implement the SPARQL protocol.
      return problem(
        Status.NOT_IMPLEMENTED,
        PROBLEM_TYPE_NOT_SUPPORTED,
        "SPARQL not supported for this repository type.",
        "Repository type " + type + " does not implement the SPARQL protocol. Only INTERNAL and SPARQL types support SPARQL queries."
      );
    }
  }

  // ─── INTERNAL (n10s) executor ─────────────────────────────────────────────

  /**
   * Forward a SPARQL SELECT/ASK to the n10s HTTP endpoint at
   * {@code POST /rdf/neo4j/sparql} with a JSON body
   * {@code {"query": "SELECT ..."}}.
   *
   * <p>n10s 4.x registers a SPARQL-over-HTTP endpoint on the Neo4j
   * HTTP port (default 7474). The endpoint accepts
   * {@code Content-Type: application/json} with a {@code query} field
   * and returns W3C SPARQL Results JSON when the client sends
   * {@code Accept: application/sparql-results+json}.
   *
   * <p>The Neo4j HTTP API requires Basic authentication. Credentials
   * are read from the same {@code neo4j.username} / {@code neo4j.password}
   * config keys used by the Bolt connector so no new deploy-time keys
   * are needed.
   *
   * <p>Package-private for test overriding via subclass (N1f test stub).
   */
  Response executeInternal(String sparqlQuery) {
    String baseUrl = readConfig(N10S_HTTP_URL_KEY, N10S_HTTP_URL_DEFAULT);
    String username = readConfig(N10S_USERNAME_KEY, "neo4j");
    String password = readConfig(N10S_PASSWORD_KEY, "");

    String endpoint = baseUrl.stripTrailing() + "/rdf/neo4j/sparql";
    String jsonBody;
    try {
      jsonBody = objectMapper.writeValueAsString(java.util.Map.of("query", sparqlQuery));
    } catch (JsonProcessingException e) {
      Log.warnf("SemanticSparqlRest: failed to serialise SPARQL query to JSON: %s", e.getMessage());
      return problem(Status.INTERNAL_SERVER_ERROR, PROBLEM_TYPE_UNAVAILABLE, "Failed to prepare SPARQL request.", e.getMessage());
    }

    String basicCreds =
      Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));

    HttpRequest request;
    try {
      request = HttpRequest.newBuilder(URI.create(endpoint))
        .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
        .header("Content-Type", "application/json")
        .header("Accept", SPARQL_RESULTS_JSON)
        .header("Authorization", "Basic " + basicCreds)
        .timeout(REQUEST_TIMEOUT)
        .build();
    } catch (IllegalArgumentException e) {
      Log.warnf("SemanticSparqlRest: invalid n10s HTTP URL '%s': %s", endpoint, e.getMessage());
      return problem(Status.SERVICE_UNAVAILABLE, PROBLEM_TYPE_UNAVAILABLE, "n10s endpoint URL is invalid.", "Check " + N10S_HTTP_URL_KEY + " config key. URL: " + endpoint);
    }

    return forwardRequest(request, "n10s INTERNAL");
  }

  // ─── SPARQL (external) executor ───────────────────────────────────────────

  /**
   * Proxy a SPARQL query to an external endpoint (Fuseki, Virtuoso, etc.)
   * using an HTTP GET with a {@code ?query=} parameter.
   *
   * <p>The caller's {@code Authorization} header is intentionally stripped
   * before forwarding — the external endpoint may use its own credential
   * model, and we must not leak the caller's shepard JWT to it.
   *
   * <p>Package-private for test overriding via subclass (N1f test stub).
   */
  Response executeExternal(String endpointUrl, String sparqlQuery) {
    if (endpointUrl == null || endpointUrl.isBlank()) {
      return problem(Status.SERVICE_UNAVAILABLE, PROBLEM_TYPE_UNAVAILABLE, "External endpoint URL is not configured.", "The SemanticRepository has no endpoint URL.");
    }

    String encodedQuery = URLEncoder.encode(sparqlQuery, StandardCharsets.UTF_8);
    String url = endpointUrl + (endpointUrl.contains("?") ? "&" : "?") + "query=" + encodedQuery;

    HttpRequest request;
    try {
      request = HttpRequest.newBuilder(URI.create(url))
        .GET()
        .header("Accept", SPARQL_RESULTS_JSON)
        .timeout(REQUEST_TIMEOUT)
        .build();
    } catch (IllegalArgumentException e) {
      Log.warnf("SemanticSparqlRest: invalid external SPARQL URL '%s': %s", url, e.getMessage());
      return problem(Status.SERVICE_UNAVAILABLE, PROBLEM_TYPE_UNAVAILABLE, "External endpoint URL is invalid.", e.getMessage());
    }

    return forwardRequest(request, "external SPARQL");
  }

  // ─── shared proxy helper ──────────────────────────────────────────────────

  /**
   * Send {@code request} via the shared {@link HttpClient}, pass through
   * a 200 response verbatim, and map non-200 to a 502/503 RFC 7807 body.
   */
  private Response forwardRequest(HttpRequest request, String backendLabel) {
    try {
      HttpResponse<String> upstreamResponse = client().send(request, HttpResponse.BodyHandlers.ofString());
      int status = upstreamResponse.statusCode();
      if (status == 200) {
        String body = upstreamResponse.body();
        // Normalise: if the upstream returned something that looks like
        // SPARQL Results JSON, pass it through; otherwise wrap.
        return Response.ok(body, SPARQL_RESULTS_JSON).build();
      }
      Log.warnf(
        "SemanticSparqlRest: %s returned HTTP %d: %s",
        backendLabel,
        status,
        truncate(upstreamResponse.body(), 200)
      );
      if (status >= 500) {
        return problem(
          Status.BAD_GATEWAY,
          PROBLEM_TYPE_UPSTREAM,
          "Upstream SPARQL endpoint returned an error.",
          backendLabel + " returned HTTP " + status + "."
        );
      }
      // 4xx from upstream — likely a bad query the validator didn't catch,
      // surface it as 400 to the caller.
      return problem(
        Status.BAD_REQUEST,
        PROBLEM_TYPE_UPSTREAM,
        "Upstream SPARQL endpoint rejected the query.",
        backendLabel + " returned HTTP " + status + ": " + truncate(upstreamResponse.body(), 200)
      );
    } catch (IOException e) {
      Log.warnf("SemanticSparqlRest: %s unreachable: %s", backendLabel, e.getMessage());
      return problem(
        Status.SERVICE_UNAVAILABLE,
        PROBLEM_TYPE_UNAVAILABLE,
        "SPARQL endpoint is unreachable.",
        backendLabel + " could not be reached: " + e.getClass().getSimpleName()
      );
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return problem(
        Status.SERVICE_UNAVAILABLE,
        PROBLEM_TYPE_UNAVAILABLE,
        "SPARQL request was interrupted.",
        backendLabel + " request interrupted."
      );
    }
  }

  // ─── helpers ──────────────────────────────────────────────────────────────

  private HttpClient client() {
    if (httpClient == null) {
      httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    }
    return httpClient;
  }

  /**
   * Build a {@code application/problem+json} response with the standard
   * RFC 7807 fields.
   */
  static Response problem(Status status, String type, String title, String detail) {
    ProblemJson body = new ProblemJson(type, title, status.getStatusCode(), detail, null);
    return Response.status(status).type(MediaType.APPLICATION_JSON).entity(body).build();
  }

  private static String readConfig(String key, String fallback) {
    try {
      return ConfigProvider.getConfig().getOptionalValue(key, String.class).orElse(fallback);
    } catch (RuntimeException e) {
      return fallback;
    }
  }

  private static String truncate(String s, int max) {
    if (s == null) return "";
    return s.length() <= max ? s : s.substring(0, max) + "…";
  }
}
