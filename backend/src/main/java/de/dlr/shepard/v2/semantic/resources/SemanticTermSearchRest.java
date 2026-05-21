package de.dlr.shepard.v2.semantic.resources;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.v2.semantic.io.TermSuggestionIO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.neo4j.ogm.session.Session;

/**
 * N1e — semantic term autocomplete endpoint.
 *
 * <p>{@code GET /v2/semantic/terms/search?q=…&limit=20} searches the
 * {@code :Resource} nodes imported by n10s (neosemantics) and returns
 * matching term suggestions for the annotation-picker autocomplete.
 *
 * <p><b>Query strategy.</b> When the Neo4j fulltext index
 * {@code resource_labels} exists (created by {@code V44__Add_fulltext_index_Resource_labels.cypher}),
 * the endpoint uses {@code db.index.fulltext.queryNodes} for faster lookups.
 * If the index is absent (fresh database, migration not yet applied, or
 * n10s data not loaded) it falls back to a {@code CONTAINS}-based Cypher
 * scan, which is safe but slower on large ontologies. If the {@code :Resource}
 * label has no nodes at all (n10s not configured / no ontologies seeded),
 * both strategies return an empty list — never an error.
 *
 * <p><b>Auth.</b> Any authenticated shepard user may call this endpoint.
 * Term search against the INTERNAL n10s repository is a read-only catalogue
 * operation with no per-entity permission check — matching the posture of
 * {@link SemanticSparqlRest} (which also gates on authentication but not
 * per-entity permission). A missing or anonymous caller receives a 401.
 *
 * <p><b>Limit cap.</b> The caller-supplied {@code limit} is capped at 50
 * regardless of what is sent.
 *
 * @see de.dlr.shepard.context.semantic.InternalSemanticConnector
 * @see TermSuggestionIO
 */
@Path("/v2/semantic/terms/search")
@RequestScoped
@Tag(name = "Semantic term search (v2, N1e)")
public class SemanticTermSearchRest {

  /** Maximum number of results the endpoint ever returns, regardless of caller-supplied limit. */
  static final int MAX_LIMIT = 50;

  /** Minimum query length — queries shorter than this return an empty list immediately. */
  static final int MIN_QUERY_LENGTH = 2;

  // ─── RFC 7807 problem type tokens ─────────────────────────────────────────

  static final String PROBLEM_TYPE_AUTH = "urn:shepard:error:auth";
  static final String PROBLEM_TYPE_BAD_REQUEST = "urn:shepard:error:bad-request";

  // ─── Cypher queries ───────────────────────────────────────────────────────

  /**
   * Fulltext-index-backed query for n10s {@code :Resource} nodes.
   *
   * <p>Expects the {@code resource_labels} fulltext index to exist
   * (created by {@code V50__Fix_fulltext_index_Resource_labels.cypher}).
   * Uses {@code $q + "*"} for prefix matching. Returns at most {@code $limit}
   * results ordered by score (Neo4j native relevance).
   *
   * <p>n10s with {@code handleVocabUris=IGNORE} stores bare local names.
   * The fulltext index (V51) covers label properties, synonym properties, and
   * classification codes: {@code label}, {@code prefLabel}, {@code altLabel},
   * {@code name}, {@code title}, {@code hiddenLabel}, {@code notation},
   * {@code hasExactSynonym}, {@code hasRelatedSynonym}, {@code hasBroadSynonym},
   * {@code hasNarrowSynonym}, {@code alternateName}.
   * Language tags are embedded in the value as a {@code @lang} suffix
   * and are stripped by {@link #stripLangSuffix}.
   */
  static final String FULLTEXT_CYPHER =
    "CALL db.index.fulltext.queryNodes('resource_labels', $q + '*') " +
    "YIELD node AS r " +
    "WHERE r.uri IS NOT NULL " +
    "RETURN r.uri AS uri, " +
    "       coalesce(r.label[0], r.prefLabel[0], r.altLabel[0], r.name[0], r.title[0], r.uri) AS label, " +
    "       coalesce(r.comment[0], r.definition[0]) AS description " +
    "LIMIT $limit";

  /**
   * CONTAINS-based fallback used when the fulltext index is absent.
   *
   * <p>Scans all {@code :Resource} nodes with a case-insensitive
   * {@code CONTAINS} check across label and synonym properties and the URI.
   * Slower on large ontologies but always safe.
   *
   * <p>Properties searched (bare IGNORE-mode local names stored by n10s):
   * <ul>
   *   <li>{@code label} / {@code prefLabel} / {@code altLabel} / {@code name} /
   *       {@code title} — standard label properties</li>
   *   <li>{@code comment} / {@code definition} / {@code scopeNote} — descriptions</li>
   *   <li>{@code hiddenLabel} — SKOS search-only labels (abbreviations, misspellings)</li>
   *   <li>{@code hasExactSynonym} / {@code hasRelatedSynonym} / {@code hasBroadSynonym} /
   *       {@code hasNarrowSynonym} — OBO-format synonyms (Gene Ontology, CHEBI, UBERON)</li>
   *   <li>{@code alternateName} — schema.org alternate names</li>
   *   <li>{@code notation} — SKOS classification codes (e.g. NASA Thesaurus numeric IDs)</li>
   * </ul>
   *
   * <p>Note: the {@code @lang} suffix embedded in values by n10s IGNORE mode
   * (e.g. {@code "Anomaly Detected@en"}) is included in the CONTAINS match,
   * so searching for "en" would spuriously match all English-labelled terms.
   * This is acceptable for the fallback path; the fulltext index avoids it.
   */
  static final String CONTAINS_CYPHER =
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

  // ─── endpoint ─────────────────────────────────────────────────────────────

  /**
   * {@code GET /v2/semantic/terms/search?q=…&limit=20}
   *
   * <p>Returns up to {@code limit} (capped at 50) {@link TermSuggestionIO}
   * objects whose {@code rdfs:label}, {@code skos:prefLabel},
   * {@code skos:altLabel}, or URI contains the query string.
   *
   * <p>Returns 200 with an empty array when no {@code :Resource} nodes
   * exist (n10s not loaded / ontologies not seeded).
   *
   * <p>Returns 400 when {@code q} is absent or blank (or shorter than 2 chars).
   *
   * <p>Returns 401 when the caller is not authenticated.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(
    summary = "Search ontology terms in the INTERNAL semantic repository.",
    description =
      "Searches `:Resource` nodes imported by n10s (neosemantics) and returns matching " +
      "term suggestions for use in the annotation-picker autocomplete. Each suggestion " +
      "carries `uri` (the RDF URI), `label` (preferred human-readable label), and " +
      "`description` (definition or comment, may be null).\n\n" +
      "Query strategy: when the `resource_labels` fulltext index exists (created by " +
      "migration `V44__Add_fulltext_index_Resource_labels.cypher`), the endpoint uses " +
      "`db.index.fulltext.queryNodes` for fast prefix matching. If the index is absent " +
      "it falls back to a case-insensitive `CONTAINS` scan across label, synonym, and " +
      "notation properties. If no `:Resource` nodes exist at all (ontologies not seeded), " +
      "both paths return an empty array without error.\n\n" +
      "Parameters:\n" +
      "  - `q` (required) — the search string. Must be at least 2 characters. " +
      "    Short strings return 400 rather than scanning the full ontology.\n" +
      "  - `limit` (optional, default 20) — maximum number of results to return. " +
      "    Capped at 50 server-side regardless of the supplied value.\n\n" +
      "Auth: any authenticated shepard user. There is no per-entity permission check " +
      "beyond authentication — the ontology catalogue is visible to all logged-in users."
  )
  @APIResponse(
    responseCode = "200",
    description = "List of matching TermSuggestion objects (may be empty if no ontology data is loaded or no terms match). Each item has `uri`, `label`, and optionally `description`.",
    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TermSuggestionIO.class))
  )
  @APIResponse(responseCode = "400", description = "Query parameter `q` is missing, blank, or shorter than 2 characters.")
  @APIResponse(responseCode = "401", description = "Authentication required (no JWT and no X-API-KEY).")
  public Response search(
    @QueryParam("q") String q,
    @QueryParam("limit") @DefaultValue("20") int limit,
    @Context SecurityContext sc
  ) {
    // 1 — auth gate (same pattern as SemanticSparqlRest)
    String caller = sc != null && sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
    if (caller == null) {
      return problem(Status.UNAUTHORIZED, PROBLEM_TYPE_AUTH, "Authentication required.", "Term search requires an authenticated user.");
    }

    // 2 — validate query
    if (q == null || q.isBlank() || q.trim().length() < MIN_QUERY_LENGTH) {
      return problem(
        Status.BAD_REQUEST,
        PROBLEM_TYPE_BAD_REQUEST,
        "Query parameter 'q' is required and must be at least 2 characters.",
        "Received: " + (q == null ? "<null>" : "'" + q + "'")
      );
    }

    // 3 — cap limit
    int effectiveLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);

    // 4 — query
    List<TermSuggestionIO> results = runSearch(q.trim(), effectiveLimit);
    return Response.ok(results).build();
  }

  // ─── query logic ──────────────────────────────────────────────────────────

  /**
   * Run the term search against the OGM session, trying the fulltext index
   * first and falling back to the CONTAINS scan if the index is unavailable.
   * Returns an empty list on any failure — never propagates an exception to
   * the caller.
   *
   * <p>Package-private for test injection via subclass (same seam as
   * {@link SemanticSparqlRest#executeInternal}).
   */
  List<TermSuggestionIO> runSearch(String q, int limit) {
    Session session = getSession();
    if (session == null) {
      Log.warn("SemanticTermSearchRest: no OGM session available, returning empty list.");
      return Collections.emptyList();
    }

    // Try fulltext index first; fall back to CONTAINS scan if the index is absent.
    try {
      return executeQuery(session, FULLTEXT_CYPHER, q, limit);
    } catch (RuntimeException fulltextEx) {
      Log.debugf(
        "SemanticTermSearchRest: fulltext index unavailable (%s), falling back to CONTAINS scan.",
        fulltextEx.getClass().getSimpleName()
      );
      try {
        return executeQuery(session, CONTAINS_CYPHER, q, limit);
      } catch (RuntimeException containsEx) {
        Log.warnf(
          "SemanticTermSearchRest: CONTAINS fallback also failed (%s); returning empty list.",
          containsEx.getClass().getSimpleName()
        );
        return Collections.emptyList();
      }
    }
  }

  /**
   * Language-tag suffix embedded in n10s IGNORE-mode values: {@code @lang} at end of string.
   * e.g. {@code "Anomaly Detected@en"} → label {@code "Anomaly Detected"}, lang {@code en}.
   */
  private static final Pattern LANG_SUFFIX = Pattern.compile("@[a-zA-Z]{2,3}(?:-[a-zA-Z0-9]+)?$");

  /**
   * Strip a BCP-47 language suffix embedded by n10s IGNORE mode ({@code "label@en"} → {@code "label"}).
   * Returns {@code raw} unchanged if no suffix is present or {@code raw} is null/blank.
   */
  static String stripLangSuffix(String raw) {
    if (raw == null || raw.isBlank()) return raw;
    return LANG_SUFFIX.matcher(raw).replaceAll("").trim();
  }

  /**
   * Execute a Cypher query and map each row to a {@link TermSuggestionIO}.
   * Rows with a null or blank {@code uri} are silently skipped.
   * Language suffixes embedded in n10s IGNORE-mode values are stripped before returning.
   */
  private static List<TermSuggestionIO> executeQuery(Session session, String cypher, String q, int limit) {
    var result = session.query(cypher, Map.of("q", q, "limit", (long) limit));
    List<TermSuggestionIO> out = new ArrayList<>();
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
      out.add(new TermSuggestionIO(uri, label, description));
    }
    return out;
  }

  // ─── seam ─────────────────────────────────────────────────────────────────

  /**
   * Returns the OGM session from the singleton {@link de.dlr.shepard.common.neo4j.NeoConnector}.
   * Package-private and overridable for test injection (same pattern as
   * {@link de.dlr.shepard.context.semantic.InternalSemanticConnector}).
   */
  Session getSession() {
    try {
      return de.dlr.shepard.common.neo4j.NeoConnector.getInstance().getNeo4jSession();
    } catch (RuntimeException ex) {
      Log.warnf("SemanticTermSearchRest: failed to obtain OGM session (%s).", ex.getClass().getSimpleName());
      return null;
    }
  }

  // ─── helpers ──────────────────────────────────────────────────────────────

  static Response problem(Status status, String type, String title, String detail) {
    ProblemJson body = new ProblemJson(type, title, status.getStatusCode(), detail, null);
    return Response.status(status).type(MediaType.APPLICATION_JSON).entity(body).build();
  }
}
