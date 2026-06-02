package de.dlr.shepard.v2.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.context.semantic.daos.VocabularyDAO;
import de.dlr.shepard.context.semantic.entities.Vocabulary;
import de.dlr.shepard.context.semantic.sparql.SparqlQueryValidator;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.config.ConfigProvider;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;
import de.dlr.shepard.common.neo4j.NeoConnector;

/**
 * MCP-COV-07-SEMANTIC-SPARQL — three read-only semantic MCP tools.
 *
 * <p>Tools are discovered by the Quarkus MCP server framework via CDI.
 * All three tools are read-only; no mutations are performed.
 *
 * <ul>
 *   <li>{@code semantic_browse} — browse enabled vocabulary terms, optionally
 *       filtered by label prefix (case-insensitive).</li>
 *   <li>{@code semantic_search} — find entities annotated with a specific
 *       predicate and/or value via a Cypher query on {@code :SemanticAnnotation}
 *       nodes.</li>
 *   <li>{@code sparql_query} — execute a read-only SPARQL SELECT/ASK query
 *       against the internal n10s semantic repository (fail-soft: returns an
 *       error map when the endpoint is unreachable, per the CLAUDE.md
 *       "registries are fail-soft" rule).</li>
 * </ul>
 *
 * <p>Auth posture: identical to the existing annotation MCP tools — any
 * authenticated user may call these; the MCP auth gate ({@link McpAuthFilter})
 * enforces bearer-token validation upstream.
 *
 * <p>Fail-soft contract for {@code sparql_query}: SPARQL endpoint
 * unavailability returns {@code {error: "SPARQL endpoint unavailable…"}}
 * instead of throwing. Callers should inspect the {@code error} key before
 * treating the result as query output.
 */
@ApplicationScoped
public class SemanticMcpTools {

  // ─── n10s config (mirrors SemanticSparqlRest constants) ──────────────────

  /** Deploy-time config key for the Neo4j HTTP port base URL. */
  static final String N10S_HTTP_URL_KEY = "shepard.semantic.internal.http-url";
  static final String N10S_HTTP_URL_DEFAULT = "http://localhost:7474";

  static final String N10S_USERNAME_KEY = "neo4j.username";
  static final String N10S_PASSWORD_KEY = "neo4j.password";

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

  /** W3C SPARQL Results JSON media type. */
  static final String SPARQL_RESULTS_JSON = "application/sparql-results+json";

  // ─── injected ─────────────────────────────────────────────────────────────

  @Inject
  VocabularyDAO vocabularyDAO;

  @Inject
  McpContextBridge contextBridge;

  @Inject
  McpToolSupport support;

  // ObjectMapper for SPARQL JSON parsing — lazily initialised.
  private final ObjectMapper objectMapper = new ObjectMapper();

  // ─── semantic_browse ──────────────────────────────────────────────────────

  @Tool(
    name = "semantic_browse",
    description =
      "Browse semantic vocabulary terms from enabled controlled vocabularies.\n\n" +
      "Optionally filter by a label prefix (case-insensitive). Returns the list of " +
      "vocabularies as term rows: {iri, label, description}.\n\n" +
      "Parameters:\n" +
      "  prefix — optional label prefix to filter vocabularies. Empty = return all " +
      "            enabled vocabularies, up to 200 rows.\n\n" +
      "Each row:\n" +
      "  iri         — canonical namespace URI of the vocabulary\n" +
      "                (e.g. 'http://purl.org/dc/terms/').\n" +
      "  label       — human-readable vocabulary name\n" +
      "                (e.g. 'Dublin Core Terms').\n" +
      "  description — optional free-text description (may be null).\n\n" +
      "Use `list_vocabularies` for the full vocabulary shape including appId and prefix. " +
      "Use `semantic_browse` when you need a quick IRI → label lookup list filtered by name."
  )
  public String semanticBrowse(
    @ToolArg(
      name = "prefix",
      description = "Optional label prefix to filter vocabulary terms (case-insensitive). " +
        "Empty = return all enabled vocabularies up to 200.",
      required = false
    ) String prefix
  ) {
    return support.run("semantic_browse", () -> {
      contextBridge.bind();

      List<Vocabulary> enabled = vocabularyDAO.listEnabled();
      List<Map<String, Object>> terms = new ArrayList<>();
      for (Vocabulary v : enabled) {
        if (terms.size() >= 200) break;
        if (prefix != null && !prefix.isBlank()) {
          String label = v.getLabel() == null ? "" : v.getLabel();
          if (!label.toLowerCase(java.util.Locale.ROOT)
              .startsWith(prefix.toLowerCase(java.util.Locale.ROOT))) {
            continue;
          }
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("iri", v.getUri());
        row.put("label", v.getLabel());
        row.put("description", v.getDescription());
        terms.add(row);
      }

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("terms", terms);
      return support.toJson(result);
    });
  }

  // ─── semantic_search ──────────────────────────────────────────────────────

  @Tool(
    name = "semantic_search",
    description =
      "Find entities annotated with a specific semantic predicate and/or value.\n\n" +
      "Searches :SemanticAnnotation nodes in Neo4j using case-insensitive substring " +
      "matching on the predicate IRI or label, and optionally on the value.\n\n" +
      "Parameters:\n" +
      "  predicate — predicate IRI or short label to search for\n" +
      "              (e.g. 'urn:shepard:spatial:axis' or 'axis'). Required.\n" +
      "  value     — optional value to match (case-insensitive substring). " +
      "              Omit to match any value for this predicate.\n\n" +
      "Returns:\n" +
      "  items — list of {subjectAppId, subjectKind, predicate, value} maps, up to 100.\n" +
      "  total — number of items returned.\n\n" +
      "To reach annotation details, pass the subjectAppId to `find_annotated` (Mode A)."
  )
  public String semanticSearch(
    @ToolArg(
      name = "predicate",
      description = "Predicate IRI or short label to search for " +
        "(e.g. 'urn:shepard:spatial:axis' or 'axis'). Case-insensitive substring match."
    ) String predicate,
    @ToolArg(
      name = "value",
      description = "Value to match (case-insensitive substring match). " +
        "Omit to match any annotation with this predicate.",
      required = false
    ) String value
  ) {
    return support.run("semantic_search", () -> {
      contextBridge.bind();

      if (predicate == null || predicate.isBlank()) {
        throw McpToolSupport.invalidParams("predicate is required.");
      }

      String cypher =
        "MATCH (a:SemanticAnnotation) " +
        "WHERE toLower(a.propertyIRI) CONTAINS toLower($pred) " +
        "  AND ($val IS NULL OR toLower(coalesce(a.valueName, '')) CONTAINS toLower($val)) " +
        "RETURN a.subjectAppId AS subjectAppId, " +
        "       a.subjectKind AS subjectKind, " +
        "       a.propertyIRI AS predicate, " +
        "       a.valueName AS value " +
        "LIMIT 100";

      Map<String, Object> params = new LinkedHashMap<>();
      params.put("pred", predicate);
      params.put("val", (value != null && !value.isBlank()) ? value : null);

      List<Map<String, Object>> items = new ArrayList<>();
      try {
        Session session = NeoConnector.getInstance().getNeo4jSession();
        Result queryResult = session.query(cypher, params);
        for (Map<String, Object> row : queryResult.queryResults()) {
          Map<String, Object> item = new LinkedHashMap<>();
          item.put("subjectAppId", row.get("subjectAppId"));
          item.put("subjectKind", row.get("subjectKind"));
          item.put("predicate", row.get("predicate"));
          item.put("value", row.get("value"));
          items.add(item);
        }
      } catch (RuntimeException e) {
        Log.warnf("semantic_search: Neo4j query failed: %s", e.getMessage());
        // Fail-soft: return empty result rather than propagating
      }

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("items", items);
      result.put("total", items.size());
      return support.toJson(result);
    });
  }

  // ─── sparql_query ─────────────────────────────────────────────────────────

  @Tool(
    name = "sparql_query",
    description =
      "Execute a read-only SPARQL SELECT or ASK query against the internal semantic " +
      "repository (n10s / neosemantics on Neo4j).\n\n" +
      "Only SELECT and ASK queries are permitted — CONSTRUCT, DESCRIBE, INSERT, " +
      "DELETE, UPDATE and all other mutation forms are rejected.\n\n" +
      "Parameters:\n" +
      "  query — SPARQL query string. A LIMIT clause is strongly recommended " +
      "           to avoid large result sets.\n\n" +
      "Returns on success:\n" +
      "  bindings  — list of binding maps (variable name → value).\n" +
      "  variables — list of variable names in the SELECT clause.\n\n" +
      "Returns on failure:\n" +
      "  error — human-readable error message describing the problem:\n" +
      "    'Query must be read-only (SELECT, ASK)' — mutation form rejected.\n" +
      "    'SPARQL endpoint unavailable — check Neo4j n10s configuration' " +
      "      — n10s unreachable (the plugin is not installed or Neo4j HTTP is down).\n\n" +
      "Admin note: SPARQL support requires the Neo4j n10s (neosemantics) plugin to be " +
      "active and the HTTP port (default 7474) to be reachable from the backend container."
  )
  public String sparqlQuery(
    @ToolArg(
      description = "SPARQL query string (must be read-only — SELECT or ASK). " +
        "A LIMIT clause is strongly recommended."
    ) String query
  ) {
    return support.run("sparql_query", () -> {
      // 1 — Validate the query is read-only
      SparqlQueryValidator.ValidationResult validation =
        SparqlQueryValidator.validate(query);

      if (!validation.isAllowed()) {
        Map<String, Object> errResult = new LinkedHashMap<>();
        errResult.put("error", "Query must be read-only (SELECT, ASK). " +
          validation.getErrorDetail());
        return support.toJson(errResult);
      }

      // 2 — Forward to the n10s HTTP SPARQL endpoint (same logic as
      //     SemanticSparqlRest.executeInternal, but returns structured map)
      String baseUrl = readConfig(N10S_HTTP_URL_KEY, N10S_HTTP_URL_DEFAULT);
      String username = readConfig(N10S_USERNAME_KEY, "neo4j");
      String password = readConfig(N10S_PASSWORD_KEY, "");

      String endpoint = baseUrl.stripTrailing() + "/rdf/neo4j/sparql";

      // Build request body as JSON {"query": "..."}
      String jsonBody;
      try {
        jsonBody = objectMapper.writeValueAsString(Map.of("query", query));
      } catch (Exception e) {
        Log.warnf("sparql_query: failed to serialise SPARQL query: %s", e.getMessage());
        Map<String, Object> errResult = new LinkedHashMap<>();
        errResult.put("error", "SPARQL endpoint unavailable — check Neo4j n10s configuration");
        return support.toJson(errResult);
      }

      String basicCreds = Base64.getEncoder().encodeToString(
        (username + ":" + password).getBytes(StandardCharsets.UTF_8));

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
        Log.warnf("sparql_query: invalid n10s URL '%s': %s", endpoint, e.getMessage());
        Map<String, Object> errResult = new LinkedHashMap<>();
        errResult.put("error", "SPARQL endpoint unavailable — check Neo4j n10s configuration");
        return support.toJson(errResult);
      }

      // 3 — Execute and parse response (fail-soft)
      try {
        HttpClient client = HttpClient.newBuilder()
          .connectTimeout(CONNECT_TIMEOUT)
          .build();
        HttpResponse<String> response = client.send(
          request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
          Log.warnf("sparql_query: n10s returned HTTP %d: %s",
            response.statusCode(), truncate(response.body(), 200));
          Map<String, Object> errResult = new LinkedHashMap<>();
          errResult.put("error", "SPARQL endpoint unavailable — check Neo4j n10s configuration");
          return support.toJson(errResult);
        }

        // Parse W3C SPARQL Results JSON
        return parseSparqlResults(response.body());

      } catch (java.io.IOException | InterruptedException e) {
        if (e instanceof InterruptedException) {
          Thread.currentThread().interrupt();
        }
        Log.warnf("sparql_query: n10s unreachable: %s", e.getMessage());
        Map<String, Object> errResult = new LinkedHashMap<>();
        errResult.put("error", "SPARQL endpoint unavailable — check Neo4j n10s configuration");
        return support.toJson(errResult);
      }
    });
  }

  // ─── helpers ──────────────────────────────────────────────────────────────

  /**
   * Parse a W3C SPARQL Results JSON response body into a map with
   * {@code variables} (list of variable names) and {@code bindings}
   * (list of variable→value maps).
   *
   * <p>On parse failure (e.g. non-standard n10s response format) the raw
   * body is returned under the {@code raw} key so the caller can still
   * inspect the data.
   */
  private String parseSparqlResults(String body) {
    try {
      JsonNode root = objectMapper.readTree(body);
      List<String> variables = new ArrayList<>();
      List<Map<String, Object>> bindings = new ArrayList<>();

      JsonNode head = root.path("head");
      JsonNode vars = head.path("vars");
      if (vars.isArray()) {
        for (JsonNode v : vars) {
          variables.add(v.asText());
        }
      }

      JsonNode results = root.path("results");
      JsonNode bindingsNode = results.path("bindings");
      if (bindingsNode.isArray()) {
        for (JsonNode bindingRow : bindingsNode) {
          Map<String, Object> rowMap = new LinkedHashMap<>();
          bindingRow.fields().forEachRemaining(entry -> {
            JsonNode term = entry.getValue();
            String type = term.path("type").asText("");
            String value = term.path("value").asText("");
            // Simplify: expose just the value string; callers can inspect type
            // via the raw response if needed. For a MCP tool this is sufficient.
            rowMap.put(entry.getKey(), value);
          });
          bindings.add(rowMap);
        }
      }

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("variables", variables);
      result.put("bindings", bindings);
      return support.toJson(result);

    } catch (Exception e) {
      Log.warnf("sparql_query: failed to parse SPARQL JSON response: %s", e.getMessage());
      // Fail-soft: return raw body under "raw" key
      Map<String, Object> fallback = new LinkedHashMap<>();
      fallback.put("raw", body);
      return support.toJson(fallback);
    }
  }

  private static String readConfig(String key, String fallback) {
    try {
      return ConfigProvider.getConfig()
        .getOptionalValue(key, String.class)
        .orElse(fallback);
    } catch (RuntimeException e) {
      return fallback;
    }
  }

  private static String truncate(String s, int max) {
    if (s == null) return "";
    return s.length() <= max ? s : s.substring(0, max) + "…";
  }
}
