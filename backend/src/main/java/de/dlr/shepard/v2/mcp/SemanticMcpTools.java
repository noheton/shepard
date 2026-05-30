package de.dlr.shepard.v2.mcp;

import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.context.semantic.daos.SemanticRepositoryDAO;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.context.semantic.entities.SemanticRepository;
import de.dlr.shepard.context.semantic.sparql.SparqlQueryValidator;
import de.dlr.shepard.context.semantic.sparql.SparqlQueryValidator.ValidationResult;
import de.dlr.shepard.v2.annotations.daos.SemanticAnnotationV2DAO;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.config.ConfigProvider;
import org.neo4j.ogm.session.Session;

/**
 * MCP-COV-07-SEMANTIC-SPARQL — three MCP tools that expose the semantic layer:
 *
 * <ul>
 *   <li>{@code semantic_browse} — search loaded ontology terms (n10s :Resource nodes).</li>
 *   <li>{@code semantic_search} — find entities annotated with a predicate / value.</li>
 *   <li>{@code sparql_query}   — execute a read-only SPARQL SELECT or ASK query against
 *       a SemanticRepository identified by its appId.</li>
 * </ul>
 *
 * <p>All three tools follow the {@link McpToolSupport#run} pattern so every
 * caller-facing exception maps to a clean JSON-RPC error code with a human-readable
 * message an agent can use to self-correct.
 *
 * <p><b>Permission posture:</b> read-only tools — any authenticated user.
 * Auth is propagated via {@link McpContextBridge#bind()} at the top of each tool
 * body (same pattern as {@link AnnotationMcpTools}).
 */
@ApplicationScoped
public class SemanticMcpTools {

  // ─── n10s HTTP config keys (shared with SemanticSparqlRest) ───────────────

  private static final String N10S_HTTP_URL_KEY = "shepard.semantic.internal.http-url";
  private static final String N10S_HTTP_URL_DEFAULT = "http://localhost:7474";
  private static final String N10S_USERNAME_KEY = "neo4j.username";
  private static final String N10S_PASSWORD_KEY = "neo4j.password";

  /** W3C SPARQL Results JSON media type. */
  private static final String SPARQL_RESULTS_JSON = "application/sparql-results+json";

  /** Hard cap on SPARQL result characters returned to the agent. */
  private static final int SPARQL_RESULT_MAX_CHARS = 10_000;

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

  // ─── Cypher queries (borrowed from SemanticTermSearchRest) ────────────────

  private static final String FULLTEXT_CYPHER =
    "CALL db.index.fulltext.queryNodes('resource_labels', $q + '*') " +
    "YIELD node AS r " +
    "WHERE r.uri IS NOT NULL " +
    "RETURN r.uri AS uri, " +
    "       coalesce(r.label[0], r.prefLabel[0], r.altLabel[0], r.name[0], r.title[0], r.uri) AS label, " +
    "       coalesce(r.comment[0], r.definition[0]) AS description " +
    "LIMIT $limit";

  private static final String CONTAINS_CYPHER =
    "MATCH (r:Resource) " +
    "WHERE r.uri IS NOT NULL " +
    "  AND (" +
    "    any(v IN coalesce(r.label, []) WHERE toLower(v) CONTAINS toLower($q)) " +
    "    OR any(v IN coalesce(r.prefLabel, []) WHERE toLower(v) CONTAINS toLower($q)) " +
    "    OR any(v IN coalesce(r.altLabel, []) WHERE toLower(v) CONTAINS toLower($q)) " +
    "    OR any(v IN coalesce(r.name, []) WHERE toLower(v) CONTAINS toLower($q)) " +
    "    OR any(v IN coalesce(r.title, []) WHERE toLower(v) CONTAINS toLower($q)) " +
    "    OR any(v IN coalesce(r.comment, []) WHERE toLower(v) CONTAINS toLower($q)) " +
    "    OR any(v IN coalesce(r.definition, []) WHERE toLower(v) CONTAINS toLower($q)) " +
    "    OR any(v IN coalesce(r.hiddenLabel, []) WHERE toLower(v) CONTAINS toLower($q)) " +
    "    OR any(v IN coalesce(r.hasExactSynonym, []) WHERE toLower(v) CONTAINS toLower($q)) " +
    "    OR any(v IN coalesce(r.hasRelatedSynonym, []) WHERE toLower(v) CONTAINS toLower($q)) " +
    "    OR any(v IN coalesce(r.hasBroadSynonym, []) WHERE toLower(v) CONTAINS toLower($q)) " +
    "    OR any(v IN coalesce(r.hasNarrowSynonym, []) WHERE toLower(v) CONTAINS toLower($q)) " +
    "    OR any(v IN coalesce(r.alternateName, []) WHERE toLower(v) CONTAINS toLower($q)) " +
    "    OR any(v IN coalesce(r.notation, []) WHERE toLower(v) CONTAINS toLower($q)) " +
    "    OR any(v IN coalesce(r.scopeNote, []) WHERE toLower(v) CONTAINS toLower($q)) " +
    "    OR toLower(r.uri) CONTAINS toLower($q) " +
    "  ) " +
    "RETURN r.uri AS uri, " +
    "       coalesce(r.label[0], r.prefLabel[0], r.altLabel[0], r.name[0], r.title[0], r.uri) AS label, " +
    "       coalesce(r.comment[0], r.definition[0]) AS description " +
    "LIMIT $limit";

  // ─── injected ─────────────────────────────────────────────────────────────

  @Inject
  McpContextBridge contextBridge;

  @Inject
  McpToolSupport support;

  @Inject
  SemanticAnnotationV2DAO annotationV2DAO;

  @Inject
  SemanticRepositoryDAO semanticRepositoryDAO;

  /** Lazily created — one shared client per CDI bean lifecycle. */
  private volatile HttpClient httpClient;

  // ─── semantic_browse ──────────────────────────────────────────────────────

  @Tool(
    name = "semantic_browse",
    description =
      "Search available semantic vocabulary terms (ontology classes and properties) " +
      "loaded into Shepard's internal n10s (neosemantics) repository.\n\n" +
      "Returns up to `limit` (default 10, max 50) matching `:Resource` nodes with their " +
      "IRI, label, and description. Use this to discover `predicateIRI` values before " +
      "calling `create_annotation` or `semantic_annotate_bulk`.\n\n" +
      "Parameters:\n" +
      "  prefix — search prefix or substring to match (required, min 1 character).\n" +
      "  limit  — max results to return (default 10, max 50).\n\n" +
      "Returns an array of term objects, each with:\n" +
      "  uri         — canonical IRI of the ontology term (use this as `predicateIRI`).\n" +
      "  label       — human-readable label (rdfs:label / skos:prefLabel).\n" +
      "  description — definition or comment (may be null).\n\n" +
      "Returns an empty array when no ontology data is loaded or no terms match. " +
      "Never returns an error for an empty result — missing ontologies are treated " +
      "as an empty catalogue."
  )
  public String semanticBrowse(
    @ToolArg(
      name = "prefix",
      description = "Search prefix or substring to match against IRI, label, synonym, and notation fields (case-insensitive). Must be at least 1 character."
    ) String prefix,
    @ToolArg(
      name = "limit",
      description = "Maximum number of results to return (default 10, max 50).",
      required = false
    ) Integer limit
  ) {
    return support.run("semantic_browse", () -> {
      contextBridge.bind();

      if (prefix == null || prefix.isBlank()) {
        throw McpToolSupport.invalidParams("prefix is required (min 1 character).");
      }
      int effectiveLimit = (limit == null || limit < 1) ? 10 : Math.min(limit, 50);

      List<Map<String, Object>> results = runTermSearch(prefix.trim(), effectiveLimit);
      return support.toJson(results);
    });
  }

  // ─── semantic_search ──────────────────────────────────────────────────────

  @Tool(
    name = "semantic_search",
    description =
      "Find entities annotated with a specific predicate IRI and optional value substring.\n\n" +
      "Returns matching annotations with their subjectAppId, subjectKind, value fields, " +
      "and annotation appId. Use `semantic_browse` to discover predicate IRIs first.\n\n" +
      "Required:\n" +
      "  predicateIri — the predicate IRI to search for (from `semantic_browse → uri` " +
      "                 or `search_predicates → uri`).\n\n" +
      "Optional filters:\n" +
      "  value       — substring filter on the annotation's valueName or valueIRI " +
      "                (case-sensitive, exact substring match).\n" +
      "  subjectKind — restrict to a specific entity kind (e.g. 'DataObject', " +
      "                'Collection', 'FileReference').\n" +
      "  page        — zero-based page index (default 0).\n" +
      "  size        — page size (default 20, max 100).\n\n" +
      "Returns an array of annotation maps, each with:\n" +
      "  annotationAppId — UUID v7 of the annotation.\n" +
      "  subjectAppId    — UUID v7 of the annotated entity.\n" +
      "  subjectKind     — kind label of the annotated entity.\n" +
      "  predicateIri    — the predicate IRI.\n" +
      "  valueName       — plain-text value (may be null).\n" +
      "  valueIri        — controlled-vocabulary value IRI (may be null).\n" +
      "  numericValue    — numeric quantity (may be null).\n" +
      "  vocabularyId    — controlling vocabulary appId (may be null)."
  )
  public String semanticSearch(
    @ToolArg(
      name = "predicateIri",
      description = "Canonical IRI of the predicate to search for (from `semantic_browse → uri`)."
    ) String predicateIri,
    @ToolArg(
      name = "value",
      description = "Optional substring filter on the annotation value (valueName or valueIRI). Leave blank to return all annotations for the predicate.",
      required = false
    ) String value,
    @ToolArg(
      name = "subjectKind",
      description = "Optional entity kind to restrict results (e.g. 'DataObject', 'Collection').",
      required = false
    ) String subjectKind,
    @ToolArg(
      name = "page",
      description = "Zero-based page index (default 0).",
      required = false
    ) Integer page,
    @ToolArg(
      name = "size",
      description = "Page size, max 100 (default 20).",
      required = false
    ) Integer size
  ) {
    return support.run("semantic_search", () -> {
      contextBridge.bind();

      if (predicateIri == null || predicateIri.isBlank()) {
        throw McpToolSupport.invalidParams("predicateIri is required.");
      }

      int effectivePage = (page == null || page < 0) ? 0 : page;
      int effectiveSize = (size == null || size < 1) ? 20 : Math.min(size, 100);

      // If a value filter is given, use text search; otherwise use findFiltered.
      List<SemanticAnnotation> annotations;
      if (value != null && !value.isBlank()) {
        // Use findFiltered which supports predicateIri filter, then filter by value client-side.
        // findFiltered supports predicateIri but not a value substring — fetch a broader set
        // scoped by predicateIri and kind, then filter in-memory for the value substring.
        List<SemanticAnnotation> byPredicate = annotationV2DAO.findFiltered(
          null, subjectKind, predicateIri, null, effectivePage, effectiveSize * 5
        );
        String valueLower = value.toLowerCase(java.util.Locale.ROOT);
        annotations = byPredicate.stream()
          .filter(a ->
            (a.getValueName() != null && a.getValueName().toLowerCase(java.util.Locale.ROOT).contains(valueLower)) ||
            (a.getValueIRI() != null && a.getValueIRI().toLowerCase(java.util.Locale.ROOT).contains(valueLower))
          )
          .limit(effectiveSize)
          .toList();
      } else {
        annotations = annotationV2DAO.findFiltered(
          null, subjectKind, predicateIri, null, effectivePage, effectiveSize
        );
      }

      List<Map<String, Object>> result = new ArrayList<>(annotations.size());
      for (SemanticAnnotation a : annotations) {
        result.add(toSearchRow(a));
      }
      return support.toJson(result);
    });
  }

  // ─── sparql_query ─────────────────────────────────────────────────────────

  @Tool(
    name = "sparql_query",
    description =
      "Execute a read-only SPARQL SELECT or ASK query against a SemanticRepository in Shepard.\n\n" +
      "The `repoAppId` identifies which n10s/SPARQL repository to query. To find a " +
      "repoAppId, ask an admin or use the Shepard UI (Admin → Semantic Repositories). " +
      "The internal n10s repository (loaded ontologies) is of type INTERNAL; external " +
      "Fuseki/Virtuoso endpoints are type SPARQL.\n\n" +
      "Only SELECT and ASK queries are permitted — mutation forms (UPDATE, INSERT, " +
      "DELETE, DROP, CONSTRUCT, DESCRIBE, LOAD, …) are rejected before reaching the " +
      "backend.\n\n" +
      "Required:\n" +
      "  repoAppId — UUID v7 of the SemanticRepository to query.\n" +
      "  query     — SPARQL 1.1 SELECT or ASK query string. PREFIX declarations are " +
      "              supported and processed correctly.\n\n" +
      "Returns: the raw W3C SPARQL Results JSON string from the backend (SELECT returns " +
      "a `results.bindings` array; ASK returns a `boolean` field). Responses longer than " +
      "10 000 characters are truncated with a `[TRUNCATED]` suffix — use LIMIT in the " +
      "query to avoid truncation.\n\n" +
      "Error codes:\n" +
      "  -32602 — repo not found, query is empty, or is a mutation form.\n" +
      "  -32603 — backend unreachable or returned an unexpected error."
  )
  public String sparqlQuery(
    @ToolArg(
      name = "repoAppId",
      description = "UUID v7 of the SemanticRepository to query (from Admin → Semantic Repositories)."
    ) String repoAppId,
    @ToolArg(
      name = "query",
      description = "SPARQL 1.1 SELECT or ASK query. Only read-only forms are accepted."
    ) String query
  ) {
    return support.run("sparql_query", () -> {
      contextBridge.bind();

      // Validate required params
      if (repoAppId == null || repoAppId.isBlank()) {
        throw McpToolSupport.invalidParams("repoAppId is required (UUID v7 of a SemanticRepository).");
      }

      // Validate query (read-only gate)
      ValidationResult validation = SparqlQueryValidator.validate(query);
      if (!validation.isAllowed()) {
        throw McpToolSupport.invalidParams(
          "SPARQL read-only policy: " + validation.getErrorDetail()
        );
      }

      // Resolve the repository
      SemanticRepository repo = semanticRepositoryDAO.findByAppId(repoAppId);
      if (repo == null || repo.isDeleted()) {
        throw McpToolSupport.invalidParams(
          "No SemanticRepository found for appId: " + repoAppId +
          ". Ask an admin for the correct appId or check Admin → Semantic Repositories."
        );
      }

      // Dispatch by type
      String raw = switch (repo.getType()) {
        case INTERNAL -> executeInternalSparql(query);
        case SPARQL -> executeExternalSparql(repo.getEndpoint(), query);
        default -> throw McpToolSupport.invalidParams(
          "SemanticRepository type " + repo.getType() +
          " does not support SPARQL. Only INTERNAL and SPARQL repository types accept SPARQL queries."
        );
      };

      // Truncate long responses
      if (raw != null && raw.length() > SPARQL_RESULT_MAX_CHARS) {
        raw = raw.substring(0, SPARQL_RESULT_MAX_CHARS) +
          "\n[TRUNCATED — add LIMIT to your query to reduce result size]";
      }

      // Return as a JSON string value so the agent gets the SPARQL JSON result
      return support.toJson(raw);
    });
  }

  // ─── Term search helpers (mirrors SemanticTermSearchRest) ─────────────────

  /**
   * Run the ontology term search, trying the fulltext index first and falling
   * back to a CONTAINS scan. Returns an empty list on any failure — fail-soft.
   */
  private List<Map<String, Object>> runTermSearch(String q, int limit) {
    Session session = getOgmSession();
    if (session == null) {
      Log.warn("SemanticMcpTools.semantic_browse: no OGM session available, returning empty list.");
      return Collections.emptyList();
    }
    try {
      return executeTermQuery(session, FULLTEXT_CYPHER, q, limit);
    } catch (RuntimeException fulltextEx) {
      Log.debugf(
        "SemanticMcpTools.semantic_browse: fulltext index unavailable (%s), falling back to CONTAINS scan.",
        fulltextEx.getClass().getSimpleName()
      );
      try {
        return executeTermQuery(session, CONTAINS_CYPHER, q, limit);
      } catch (RuntimeException containsEx) {
        Log.warnf(
          "SemanticMcpTools.semantic_browse: CONTAINS fallback also failed (%s); returning empty list.",
          containsEx.getClass().getSimpleName()
        );
        return Collections.emptyList();
      }
    }
  }

  /** Execute a Cypher term-search query and map each row to a result map. */
  private static List<Map<String, Object>> executeTermQuery(Session session, String cypher, String q, int limit) {
    var result = session.query(cypher, Map.of("q", q, "limit", (long) limit));
    List<Map<String, Object>> out = new ArrayList<>();
    for (Map<String, Object> row : result.queryResults()) {
      Object uriRaw = row.get("uri");
      if (uriRaw == null) continue;
      String uri = uriRaw.toString();
      if (uri.isBlank()) continue;
      Object labelRaw = row.get("label");
      String label = labelRaw != null ? stripLangSuffix(labelRaw.toString()) : uri;
      if (label == null || label.isBlank()) label = uri;
      Object descRaw = row.get("description");
      String description = descRaw != null ? stripLangSuffix(descRaw.toString()) : null;
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("uri", uri);
      entry.put("label", label);
      entry.put("description", description);
      out.add(entry);
    }
    return out;
  }

  /** Strip BCP-47 language suffix embedded by n10s IGNORE mode (e.g. {@code "label@en"} → {@code "label"}). */
  private static String stripLangSuffix(String raw) {
    if (raw == null || raw.isBlank()) return raw;
    return raw.replaceAll("@[a-zA-Z]{2,3}(?:-[a-zA-Z0-9]+)?$", "").trim();
  }

  private Session getOgmSession() {
    try {
      return NeoConnector.getInstance().getNeo4jSession();
    } catch (RuntimeException ex) {
      Log.warnf("SemanticMcpTools: failed to obtain OGM session (%s).", ex.getClass().getSimpleName());
      return null;
    }
  }

  // ─── SPARQL execution helpers ──────────────────────────────────────────────

  /**
   * Forward a SPARQL query to the n10s HTTP SPARQL endpoint on the Neo4j HTTP port.
   * Mirrors {@code SemanticSparqlRest#executeInternal}.
   */
  private String executeInternalSparql(String sparqlQuery) {
    String baseUrl = readConfig(N10S_HTTP_URL_KEY, N10S_HTTP_URL_DEFAULT);
    String username = readConfig(N10S_USERNAME_KEY, "neo4j");
    String password = readConfig(N10S_PASSWORD_KEY, "");
    String endpoint = baseUrl.stripTrailing() + "/rdf/neo4j/sparql";

    // Encode as JSON body for the n10s HTTP endpoint
    String jsonBody;
    try {
      jsonBody = "{\"query\":" + quoteJson(sparqlQuery) + "}";
    } catch (Exception e) {
      throw new RuntimeException("Failed to prepare SPARQL request body: " + e.getMessage(), e);
    }

    String basicCreds = Base64.getEncoder().encodeToString(
      (username + ":" + password).getBytes(StandardCharsets.UTF_8)
    );

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
      throw McpToolSupport.invalidParams(
        "n10s endpoint URL is invalid — check " + N10S_HTTP_URL_KEY + " config key. URL: " + endpoint
      );
    }

    return sendSparqlRequest(request, "n10s INTERNAL");
  }

  /**
   * Proxy a SPARQL query to an external endpoint (Fuseki, Virtuoso, etc.).
   * Mirrors {@code SemanticSparqlRest#executeExternal}.
   */
  private String executeExternalSparql(String endpointUrl, String sparqlQuery) {
    if (endpointUrl == null || endpointUrl.isBlank()) {
      throw McpToolSupport.invalidParams(
        "The SemanticRepository has no endpoint URL configured. Ask an admin to set the endpoint."
      );
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
      throw McpToolSupport.invalidParams(
        "External SPARQL endpoint URL is invalid: " + e.getMessage()
      );
    }

    return sendSparqlRequest(request, "external SPARQL");
  }

  /**
   * Send {@code request} and return the response body string.
   * Maps HTTP errors to appropriate MCP exceptions.
   */
  private String sendSparqlRequest(HttpRequest request, String backendLabel) {
    try {
      HttpResponse<String> resp = httpClient().send(request, HttpResponse.BodyHandlers.ofString());
      int status = resp.statusCode();
      if (status == 200) {
        return resp.body();
      }
      Log.warnf(
        "SemanticMcpTools.sparql_query: %s returned HTTP %d: %s",
        backendLabel, status, truncate(resp.body(), 200)
      );
      if (status >= 500) {
        throw new RuntimeException(
          backendLabel + " SPARQL backend returned HTTP " + status + " (server error)."
        );
      }
      // 4xx — surface as invalid params (bad query)
      throw McpToolSupport.invalidParams(
        backendLabel + " rejected the query (HTTP " + status + "): " + truncate(resp.body(), 200)
      );
    } catch (io.quarkiverse.mcp.server.McpException e) {
      throw e; // re-throw MCP exceptions unmodified
    } catch (IOException e) {
      Log.warnf("SemanticMcpTools.sparql_query: %s unreachable: %s", backendLabel, e.getMessage());
      throw new RuntimeException(
        backendLabel + " SPARQL endpoint is unreachable: " + e.getClass().getSimpleName()
      );
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(backendLabel + " SPARQL request was interrupted.");
    }
  }

  private HttpClient httpClient() {
    if (httpClient == null) {
      httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    }
    return httpClient;
  }

  // ─── helpers ──────────────────────────────────────────────────────────────

  /** Compact result map for {@code semantic_search} rows. */
  private static Map<String, Object> toSearchRow(SemanticAnnotation a) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("annotationAppId", a.getAppId());
    row.put("subjectAppId", a.getSubjectAppId());
    row.put("subjectKind", a.getSubjectKind());
    row.put("predicateIri", a.getPropertyIRI());
    row.put("valueName", a.getValueName());
    row.put("valueIri", a.getValueIRI());
    row.put("numericValue", a.getNumericValue());
    row.put("vocabularyId", a.getVocabularyId());
    return row;
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

  /**
   * Produce a JSON-safe quoted string for embedding in a JSON object body.
   * Uses only the characters that need escaping per RFC 8259 — no external library needed.
   */
  private static String quoteJson(String value) {
    if (value == null) return "null";
    StringBuilder sb = new StringBuilder("\"");
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\b' -> sb.append("\\b");
        case '\f' -> sb.append("\\f");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    sb.append('"');
    return sb.toString();
  }
}
