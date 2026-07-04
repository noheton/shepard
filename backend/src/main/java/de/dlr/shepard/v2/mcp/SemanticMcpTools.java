package de.dlr.shepard.v2.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.context.semantic.SemanticRepositoryType;
import de.dlr.shepard.context.semantic.daos.PredicateDAO;
import de.dlr.shepard.context.semantic.daos.SemanticAnnotationDAO;
import de.dlr.shepard.context.semantic.daos.SemanticRepositoryDAO;
import de.dlr.shepard.context.semantic.daos.VocabularyDAO;
import de.dlr.shepard.context.semantic.entities.Predicate;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.context.semantic.entities.SemanticRepository;
import de.dlr.shepard.context.semantic.entities.Vocabulary;
import de.dlr.shepard.context.semantic.sparql.SparqlQueryValidator;
import io.quarkiverse.mcp.server.McpException;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * MCP-COV-07-SEMANTIC-SPARQL — three MCP tools covering the read-only SPARQL
 * and vocabulary-browse surface.
 *
 * <ul>
 *   <li>{@code sparql_query} — execute a SELECT or ASK query against the
 *       internal n10s graph or an external SPARQL endpoint.</li>
 *   <li>{@code semantic_browse} — list vocabularies and their predicates,
 *       optionally filtered by namespace prefix.</li>
 *   <li>{@code semantic_search} — find annotations by predicate IRI and
 *       optional value across the whole instance.</li>
 * </ul>
 *
 * <p>Auth posture: any authenticated shepard user. Read-only throughout —
 * no write operations are exposed here. Mutations travel through the
 * annotation CRUD tools in {@link AnnotationMcpTools}.
 *
 * <p>SPARQL execution mirrors {@link de.dlr.shepard.v2.semantic.resources.SemanticSparqlRest}:
 * the same config keys, the same HTTP client setup, and the same read-only
 * enforcement via {@link SparqlQueryValidator}. The tool surface wraps the
 * REST logic so an agent can query the graph without needing a URL or auth
 * header.
 */
@ApplicationScoped
public class SemanticMcpTools {

  // ─── timeouts ─────────────────────────────────────────────────────────────
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

  // ─── config keys (must match SemanticSparqlRest) ──────────────────────────
  private static final String N10S_HTTP_URL_KEY = "shepard.semantic.internal.http-url";
  private static final String N10S_HTTP_URL_DEFAULT = "http://localhost:7474";
  private static final String N10S_USERNAME_KEY = "neo4j.username";
  private static final String N10S_PASSWORD_KEY = "neo4j.password";

  private static final String SPARQL_RESULTS_JSON = "application/sparql-results+json";

  /** Reserved alias for the bootstrapped internal n10s repository (matches SemanticSparqlRest). */
  private static final String INTERNAL_ALIAS = "internal";

  private static final int DEFAULT_LIMIT = 20;
  private static final int MAX_LIMIT = 100;

  @Inject
  VocabularyDAO vocabularyDAO;

  @Inject
  PredicateDAO predicateDAO;

  @Inject
  SemanticAnnotationDAO annotationDAO;

  @Inject
  SemanticRepositoryDAO semanticRepositoryDAO;

  @Inject
  McpContextBridge contextBridge;

  @Inject
  McpToolSupport support;

  private final ObjectMapper objectMapper = new ObjectMapper();

  /** Shared HTTP client (thread-safe; lazy-init on first SPARQL call). */
  private volatile HttpClient httpClient;

  // ─── sparql_query ──────────────────────────────────────────────────────────

  @Tool(
    name = "sparql_query",
    description =
      "Execute a read-only SPARQL SELECT or ASK query against a semantic repository and " +
      "return W3C SPARQL Results JSON.\n\n" +
      "Only SELECT and ASK query forms are permitted. Mutation forms (CONSTRUCT, INSERT, " +
      "DELETE, UPDATE, DROP, …) are rejected before reaching the backend.\n\n" +
      "Parameters:\n" +
      "  query     — required. SPARQL query string (SELECT or ASK). PREFIX declarations " +
      "              are supported and stripped before form detection.\n" +
      "  repoAppId — optional. appId of a :SemanticRepository node, or the reserved alias " +
      "              'internal' to query the n10s (neosemantics) RDF layer on top of the " +
      "              Neo4j graph. Defaults to 'internal'.\n\n" +
      "Returns W3C SPARQL Results JSON with `head.vars` and `results.bindings`. " +
      "Use this to query the annotation graph, the provenance layer, or any external " +
      "Fuseki/Virtuoso endpoint registered as a :SemanticRepository.\n\n" +
      "Common patterns:\n" +
      "  SELECT * WHERE { ?s ?p ?o } LIMIT 10   — sample triples\n" +
      "  ASK { ?s a <http://example.org/Concept> }  — existence check\n\n" +
      "Auth: any authenticated user."
  )
  public String sparqlQuery(
    @ToolArg(name = "query", description = "SPARQL SELECT or ASK query string. PREFIX declarations are supported.") String query,
    @ToolArg(name = "repoAppId", description = "Semantic repository appId or 'internal' (default). Leave null to use the internal n10s graph.", required = false) String repoAppId
  ) {
    return support.run("sparql_query", () -> {
      contextBridge.bind();

      // Validate query form (read-only enforcement).
      SparqlQueryValidator.ValidationResult validation = SparqlQueryValidator.validate(query);
      if (!validation.isAllowed()) {
        throw McpToolSupport.invalidParams(
          "sparql_query: query rejected — " + validation.getErrorDetail()
        );
      }

      // Resolve repository.
      String effectiveRepoId = (repoAppId == null || repoAppId.isBlank()) ? INTERNAL_ALIAS : repoAppId;
      SemanticRepository repo;
      if (INTERNAL_ALIAS.equalsIgnoreCase(effectiveRepoId)) {
        repo = semanticRepositoryDAO.findInternal();
      } else {
        repo = semanticRepositoryDAO.findByAppId(effectiveRepoId);
      }
      if (repo == null) {
        throw McpToolSupport.invalidParams(
          "sparql_query: no semantic repository found for repoAppId='" + effectiveRepoId + "'. " +
          "Use repoAppId='internal' to query the built-in n10s graph."
        );
      }

      SemanticRepositoryType type = repo.getType();
      if (type == SemanticRepositoryType.INTERNAL) {
        return executeInternal(query);
      } else if (type == SemanticRepositoryType.SPARQL) {
        return executeExternal(repo.getEndpoint(), query);
      } else {
        throw McpToolSupport.invalidParams(
          "sparql_query: repository type " + type + " does not implement the SPARQL protocol. " +
          "Only INTERNAL and SPARQL repository types support sparql_query."
        );
      }
    });
  }

  // ─── semantic_browse ───────────────────────────────────────────────────────

  @Tool(
    name = "semantic_browse",
    description =
      "List vocabularies known to this Shepard instance together with their predicates. " +
      "Optionally filter by namespace prefix.\n\n" +
      "A Vocabulary is a named namespace (e.g. 'dcterms', 'prov', 'qudt') that groups " +
      "Predicates. Each Predicate is an annotation property defined by that vocabulary " +
      "(e.g. 'dcterms:creator', 'dcterms:title'). Use this tool to discover available " +
      "annotation vocabulary terms before calling `create_annotation` or `sparql_query`.\n\n" +
      "Parameters:\n" +
      "  prefix — optional. Filter to the vocabulary whose prefix matches this string " +
      "           (case-insensitive prefix match). Omit to return all vocabularies.\n\n" +
      "Each result entry:\n" +
      "  appId       — stable UUID v7 identifier.\n" +
      "  uri         — canonical namespace IRI (e.g. 'http://purl.org/dc/terms/').\n" +
      "  label       — human-readable name (e.g. 'Dublin Core Terms').\n" +
      "  prefix      — short prefix (e.g. 'dcterms').\n" +
      "  description — free-text description (may be null).\n" +
      "  enabled     — whether this vocabulary appears in annotation autocomplete.\n" +
      "  predicates  — list of predicates in this vocabulary:\n" +
      "      appId             — predicate UUID v7.\n" +
      "      uri               — property IRI (use as propertyIRI in create_annotation).\n" +
      "      label             — human-readable property name.\n" +
      "      expectedObjectType — 'TEXT', 'IRI', 'NUMERIC', 'DATA_OBJECT_APPID', " +
      "                           'CONTAINER_APPID', or null.\n" +
      "      cardinality       — 'SINGLE' or 'MULTI' (null = not specified).\n" +
      "      required          — true when the predicate is mandatory.\n\n" +
      "Auth: any authenticated user."
  )
  public String semanticBrowse(
    @ToolArg(name = "prefix", description = "Vocabulary prefix to filter by (e.g. 'dcterms', 'prov'). Case-insensitive. Omit to return all vocabularies.", required = false) String prefix
  ) {
    return support.run("semantic_browse", () -> {
      contextBridge.bind();

      List<Vocabulary> vocabs = vocabularyDAO.listAll();

      // Filter by prefix when supplied.
      if (prefix != null && !prefix.isBlank()) {
        String needle = prefix.strip().toLowerCase(java.util.Locale.ROOT);
        vocabs = vocabs.stream()
          .filter(v -> v.getPrefix() != null && v.getPrefix().toLowerCase(java.util.Locale.ROOT).startsWith(needle))
          .toList();
      }

      List<Map<String, Object>> result = new ArrayList<>(vocabs.size());
      for (Vocabulary v : vocabs) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("appId", v.getAppId());
        row.put("uri", v.getUri());
        row.put("label", v.getLabel());
        row.put("prefix", v.getPrefix());
        row.put("description", v.getDescription());
        row.put("enabled", v.isEnabled());

        // Fetch predicates for this vocabulary.
        List<Predicate> preds = predicateDAO.listByVocabulary(v.getAppId());
        List<Map<String, Object>> predList = new ArrayList<>(preds.size());
        for (Predicate p : preds) {
          Map<String, Object> pred = new LinkedHashMap<>();
          pred.put("appId", p.getAppId());
          pred.put("uri", p.getUri());
          pred.put("label", p.getLabel());
          pred.put("expectedObjectType", p.getExpectedObjectType());
          pred.put("cardinality", p.getCardinality());
          pred.put("required", p.isRequired());
          predList.add(pred);
        }
        row.put("predicates", predList);
        result.add(row);
      }

      return support.toJson(result);
    });
  }

  // ─── semantic_search ───────────────────────────────────────────────────────

  @Tool(
    name = "semantic_search",
    description =
      "Find SemanticAnnotations by predicate IRI and optional value across the whole " +
      "Shepard instance.\n\n" +
      "This is a focused cross-entity search: given a predicate IRI (e.g. " +
      "'http://purl.org/dc/terms/creator'), return every annotation that uses that " +
      "predicate — optionally filtered to a specific value text or IRI. Use this to " +
      "discover which entities are tagged with a particular term before running a " +
      "full SPARQL query.\n\n" +
      "Parameters:\n" +
      "  predicateIri — required. The property IRI to search by " +
      "                 (e.g. 'http://purl.org/dc/terms/creator'). Obtain IRIs from " +
      "                 `semantic_browse` or `search_predicates`.\n" +
      "  value        — optional. Value text or IRI to narrow the match. " +
      "                 Matches against both `valueName` and `valueIRI`. Omit to return " +
      "                 all annotations for the predicate regardless of value.\n" +
      "  limit        — optional. Maximum number of annotations to return (1–100, " +
      "                 default 20).\n\n" +
      "Each result:\n" +
      "  appId           — annotation UUID v7.\n" +
      "  subjectAppId    — UUID v7 of the annotated entity (DataObject, Collection, …).\n" +
      "  subjectKind     — entity kind label (e.g. 'DataObject').\n" +
      "  propertyIRI     — predicate IRI.\n" +
      "  propertyName    — human-readable predicate label.\n" +
      "  valueName       — plain-text value (may be null).\n" +
      "  valueIRI        — controlled-vocabulary IRI value (may be null).\n" +
      "  numericValue    — numeric quantity (may be null).\n" +
      "  unitIRI         — QUDT unit IRI (may be null).\n" +
      "  vocabularyId    — appId of the controlling vocabulary (may be null).\n" +
      "  sourceMode      — 'human' | 'ai' | 'collaborative'.\n" +
      "  confidence      — AI confidence [0.0, 1.0] (null for human annotations).\n\n" +
      "Auth: any authenticated user."
  )
  public String semanticSearch(
    @ToolArg(name = "predicateIri", description = "Property IRI to search by (e.g. 'http://purl.org/dc/terms/creator'). Use `semantic_browse` to discover valid IRIs.") String predicateIri,
    @ToolArg(name = "value", description = "Optional value text or IRI to filter by. Omit to return all annotations for the predicate.", required = false) String value,
    @ToolArg(name = "limit", description = "Max annotations to return (1–100, default 20).", required = false) Integer limit
  ) {
    return support.run("semantic_search", () -> {
      contextBridge.bind();

      if (predicateIri == null || predicateIri.isBlank()) {
        throw McpToolSupport.invalidParams(
          "semantic_search: predicateIri is required. Obtain predicate IRIs from `semantic_browse`."
        );
      }

      int effectiveLimit = (limit == null || limit < 1) ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
      String effectiveValue = (value == null || value.isBlank()) ? null : value.strip();

      List<SemanticAnnotation> annotations = annotationDAO.findByPredicateAndValue(
        predicateIri.strip(), effectiveValue, null, 0, effectiveLimit
      );

      List<Map<String, Object>> result = new ArrayList<>(annotations.size());
      for (SemanticAnnotation a : annotations) {
        result.add(toAnnotationMap(a));
      }
      return support.toJson(Map.of(
        "predicateIri", predicateIri.strip(),
        "value", effectiveValue != null ? effectiveValue : "",
        "count", result.size(),
        "limit", effectiveLimit,
        "annotations", result
      ));
    });
  }

  // ─── SPARQL execution (mirrors SemanticSparqlRest) ─────────────────────────

  /**
   * Forward a SPARQL query to the n10s HTTP endpoint at {@code POST /rdf/neo4j/sparql}.
   * Returns the raw W3C SPARQL Results JSON string.
   */
  String executeInternal(String sparqlQuery) {
    String baseUrl = readConfig(N10S_HTTP_URL_KEY, N10S_HTTP_URL_DEFAULT);
    String username = readConfig(N10S_USERNAME_KEY, "neo4j");
    String password = readConfig(N10S_PASSWORD_KEY, "");

    String endpoint = baseUrl.stripTrailing() + "/rdf/neo4j/sparql";
    String jsonBody;
    try {
      jsonBody = objectMapper.writeValueAsString(Map.of("query", sparqlQuery));
    } catch (JsonProcessingException e) {
      throw new McpException(
        "sparql_query: failed to serialise query: " + e.getMessage(),
        McpToolSupport.INTERNAL_ERROR
      );
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
      throw new McpException(
        "sparql_query: n10s endpoint URL is invalid (" + endpoint + "): " + e.getMessage(),
        McpToolSupport.INTERNAL_ERROR
      );
    }

    return sendAndExtract(request, "n10s INTERNAL");
  }

  /**
   * Proxy a SPARQL query to an external endpoint via HTTP GET {@code ?query=<encoded>}.
   */
  String executeExternal(String endpointUrl, String sparqlQuery) {
    if (endpointUrl == null || endpointUrl.isBlank()) {
      throw new McpException(
        "sparql_query: the external SemanticRepository has no endpoint URL configured.",
        McpToolSupport.INVALID_PARAMS
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
      throw new McpException(
        "sparql_query: external endpoint URL is invalid: " + e.getMessage(),
        McpToolSupport.INVALID_PARAMS
      );
    }

    return sendAndExtract(request, "external SPARQL");
  }

  /**
   * Send the request, pass through the body on 200, or throw on non-200/error.
   */
  private String sendAndExtract(HttpRequest request, String backendLabel) {
    try {
      HttpResponse<String> resp = client().send(request, HttpResponse.BodyHandlers.ofString());
      int status = resp.statusCode();
      if (status == 200) {
        return resp.body();
      }
      Log.warnf("SemanticMcpTools: %s returned HTTP %d", backendLabel, status);
      if (status >= 500) {
        throw new McpException(
          "sparql_query: " + backendLabel + " returned HTTP " + status + " (upstream error).",
          McpToolSupport.INTERNAL_ERROR
        );
      }
      throw new McpException(
        "sparql_query: " + backendLabel + " rejected the query (HTTP " + status + "): " +
        truncate(resp.body(), 200),
        McpToolSupport.INVALID_PARAMS
      );
    } catch (McpException e) {
      throw e;
    } catch (IOException e) {
      Log.warnf("SemanticMcpTools: %s unreachable: %s", backendLabel, e.getMessage());
      throw new McpException(
        "sparql_query: " + backendLabel + " is unreachable (" + e.getClass().getSimpleName() + ").",
        McpToolSupport.INTERNAL_ERROR
      );
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new McpException(
        "sparql_query: request to " + backendLabel + " was interrupted.",
        McpToolSupport.INTERNAL_ERROR
      );
    }
  }

  // ─── helpers ───────────────────────────────────────────────────────────────

  private HttpClient client() {
    if (httpClient == null) {
      httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    }
    return httpClient;
  }

  private static Map<String, Object> toAnnotationMap(SemanticAnnotation a) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("appId", a.getAppId());
    row.put("subjectAppId", a.getSubjectAppId());
    row.put("subjectKind", a.getSubjectKind());
    row.put("propertyIRI", a.getPropertyIRI());
    row.put("propertyName", a.getPropertyName());
    row.put("valueName", a.getValueName());
    row.put("valueIRI", a.getValueIRI());
    row.put("numericValue", a.getNumericValue());
    row.put("unitIRI", a.getUnitIRI());
    row.put("vocabularyId", a.getVocabularyId());
    row.put("sourceMode", a.getSourceMode());
    row.put("confidence", a.getConfidence());
    row.put("sourceActivityAppId", a.getSourceActivityAppId());
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
}
