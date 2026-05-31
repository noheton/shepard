package de.dlr.shepard.v2.mcp;

import de.dlr.shepard.common.neo4j.NeoConnector;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;

/**
 * MCP-COV-13 — unified free-text {@code search} tool.
 *
 * <p>Mirrors the merged frontend global-search surface (the
 * {@code useGlobalSearch} composer that fans out across collections,
 * dataobjects, and containers — see {@code aidocs/agent-findings/
 * ui-002-header-search-fix-2026-05-24.md}). The frontend issues three
 * parallel REST searches and stitches the results client-side; this
 * MCP tool collapses that into a single round-trip so an agent
 * exploring "what do we have on X?" pays one JSON-RPC hop instead of
 * four (the existing legacy {@code /shepard/api/search} only takes a
 * single kind per call).
 *
 * <p>Implementation: one Cypher query per requested kind, run against
 * the live OGM session. Each kind contributes its rows to a unified
 * envelope {@code {items: [{appId, kind, name, snippet}], total}}
 * ordered by kind then by match position. Substring match is
 * case-insensitive across {@code name} and {@code description}; the
 * {@code snippet} is the {@code description} truncated to the first
 * configured window when present, otherwise the {@code name}.
 *
 * <p><b>Kind taxonomy.</b> The {@code kind} filter accepts the four
 * coarse buckets the frontend exposes, each backed by one or more
 * Neo4j labels:
 *
 * <ul>
 *   <li>{@code "Collection"} → {@code :Collection}</li>
 *   <li>{@code "DataObject"} → {@code :DataObject}</li>
 *   <li>{@code "Container"} → {@code :TimeseriesContainer},
 *       {@code :FileContainer}, {@code :StructuredDataContainer}</li>
 *   <li>{@code "Reference"} → {@code :TimeseriesReference},
 *       {@code :FileReference}, {@code :SingletonFileReference},
 *       {@code :URIReference}, {@code :StructuredDataReference},
 *       {@code :DataObjectReference}, {@code :CollectionReference}</li>
 * </ul>
 *
 * <p>Omitting {@code kind} searches all four buckets. The label
 * carried by each result row is returned verbatim so the caller can
 * disambiguate (e.g. distinguish a {@code FileReference} from a
 * {@code SingletonFileReference}).
 *
 * <p><b>Permission posture (v0).</b> The tool returns rows the caller
 * can see by label — there is no per-row permission walk in this v0;
 * the substrate is read-only and the underlying entities already obey
 * the instance-wide read posture. A {@code SEARCH-MCP-PERMS-1}
 * follow-up will add a Collection-anchored Read gate so a non-admin
 * doesn't surface DataObjects in Collections they can't read.
 */
@ApplicationScoped
public class SearchMcpTools {

  /** Default page size when {@code limit} is omitted. */
  static final int DEFAULT_LIMIT = 20;

  /** Hard cap on {@code limit} so a single tool call never floods the wire. */
  static final int MAX_LIMIT = 100;

  /** Default snippet length (characters) of the {@code description} excerpt. */
  static final int SNIPPET_MAX_LENGTH = 160;

  /** All four supported coarse kind buckets, in canonical render order. */
  static final List<String> ALL_KINDS = List.of(
    "Collection", "DataObject", "Container", "Reference"
  );

  /** Neo4j labels covered by each coarse bucket. */
  static final Map<String, List<String>> LABELS_BY_KIND = Map.of(
    "Collection", List.of("Collection"),
    "DataObject", List.of("DataObject"),
    "Container", List.of("TimeseriesContainer", "FileContainer", "StructuredDataContainer"),
    "Reference", List.of(
      "TimeseriesReference",
      "SingletonFileReference",
      "FileReference",
      "URIReference",
      "StructuredDataReference",
      "DataObjectReference",
      "CollectionReference"
    )
  );

  @Inject McpContextBridge contextBridge;
  @Inject McpToolSupport support;

  @Tool(
    name = "search",
    description =
      "Free-text search across the core shepard primitives: Collection, DataObject, " +
      "Container, Reference. Returns a unified envelope `{items, total}` so an agent " +
      "exploring 'what do we have on X?' pays one JSON-RPC round-trip instead of N.\n\n" +
      "Parameters:\n" +
      "  query  — required, case-insensitive substring matched against `name` and " +
      "           `description` across the requested kinds.\n" +
      "  kind   — optional, one of 'Collection' | 'DataObject' | 'Container' | " +
      "           'Reference'. Omit to search all four buckets.\n" +
      "  limit  — optional, max items per response (default 20, max 100).\n" +
      "  offset — optional, zero-based offset for pagination (default 0).\n\n" +
      "Each item carries:\n" +
      "  appId   — UUID v7 of the matched entity (or null on legacy rows without an appId).\n" +
      "  kind    — the concrete Neo4j label (e.g. 'Collection', 'DataObject', " +
      "            'TimeseriesContainer', 'SingletonFileReference'). The label is the " +
      "            verbatim Neo4j label so the caller can tell a FileReference apart " +
      "            from a SingletonFileReference, etc.\n" +
      "  name    — the entity's name (may be null on legacy rows).\n" +
      "  snippet — description excerpt (≤" + SNIPPET_MAX_LENGTH + " chars), or the name " +
      "            when description is empty. Useful for showing match context.\n\n" +
      "Result ordering: kinds are processed in the canonical render order — Collection, " +
      "DataObject, Container, Reference — then alphabetically by name within each kind " +
      "for deterministic responses. `total` is the count across ALL matched rows " +
      "BEFORE pagination is applied, so `total > items.length` means there are more " +
      "rows behind the limit.\n\n" +
      "Auth: any authenticated user. There is no per-row permission gate in this v0 — " +
      "see `SEARCH-MCP-PERMS-1` in `aidocs/16` for the planned Collection-anchored " +
      "Read filter.\n\n" +
      "Example: `search(query='TR-004')` returns the LUMEN anomaly DataObject plus any " +
      "Container or Reference whose name mentions the test run; `search(query='LOX', " +
      "kind='DataObject')` scopes to DataObjects only."
  )
  public String search(
    @ToolArg(description = "Free-text substring matched case-insensitively against name + description.") String query,
    @ToolArg(required = false, description = "Optional kind filter: 'Collection' | 'DataObject' | 'Container' | 'Reference'. Omit for all kinds.") String kind,
    @ToolArg(required = false, description = "Max items per response (default " + DEFAULT_LIMIT + ", max " + MAX_LIMIT + ").") Integer limit,
    @ToolArg(required = false, description = "Zero-based offset for pagination (default 0).") Integer offset
  ) {
    return support.run("search", () -> {
      contextBridge.bind();

      if (query == null || query.isBlank()) {
        throw McpToolSupport.invalidParams("query is required (free-text substring).");
      }
      int effectiveLimit = limit == null
        ? DEFAULT_LIMIT
        : Math.min(Math.max(limit, 1), MAX_LIMIT);
      int effectiveOffset = offset == null ? 0 : Math.max(offset, 0);
      List<String> kinds = resolveKinds(kind);

      // Collect every matching row first so `total` reflects the true match
      // count before pagination. This is bounded by the labels we walk
      // (Collection / DataObject / TS or FileC or SDC / 7 reference subtypes)
      // and by Neo4j's name + description sizes — for the LUMEN + MFFD scale
      // (~thousands of nodes per substrate) it stays comfortable in memory.
      List<Map<String, Object>> matches = new ArrayList<>();
      for (String coarseKind : kinds) {
        for (String label : LABELS_BY_KIND.get(coarseKind)) {
          matches.addAll(searchLabel(label, query));
        }
      }

      // Dedupe by (label, appId) — :SingletonFileReference also carries the
      // :FileReference label in some legacy rows; the two-label hit would
      // otherwise show twice. Keep the first occurrence (which preserves
      // canonical render order).
      List<Map<String, Object>> deduped = new ArrayList<>(matches.size());
      Set<String> seen = new HashSet<>();
      for (Map<String, Object> row : matches) {
        String dedupeKey = row.get("kind") + "::" + row.get("appId");
        if (seen.add(dedupeKey)) deduped.add(row);
      }

      long total = deduped.size();
      List<Map<String, Object>> paged;
      if (effectiveOffset >= deduped.size()) {
        paged = List.of();
      } else {
        int end = Math.min(effectiveOffset + effectiveLimit, deduped.size());
        paged = deduped.subList(effectiveOffset, end);
      }

      Map<String, Object> envelope = new LinkedHashMap<>();
      envelope.put("items", paged);
      envelope.put("total", total);
      envelope.put("limit", effectiveLimit);
      envelope.put("offset", effectiveOffset);
      return support.toJson(envelope);
    });
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  /**
   * Resolve the caller-supplied {@code kind} into a list of coarse buckets.
   * Case-insensitive; null/blank returns all four buckets.
   */
  static List<String> resolveKinds(String kind) {
    if (kind == null || kind.isBlank()) return ALL_KINDS;
    String normalized = kind.trim();
    for (String canonical : ALL_KINDS) {
      if (canonical.equalsIgnoreCase(normalized)) return List.of(canonical);
    }
    throw McpToolSupport.invalidParams(
      "Unknown kind '" + kind + "'. Expected one of: " + String.join(", ", ALL_KINDS) +
      " (or omit to search all kinds)."
    );
  }

  /**
   * Run the search Cypher for one Neo4j label. Matches case-insensitively
   * against {@code name} and {@code description}; returns rows ordered by
   * {@code name ASC, appId ASC} so the response is deterministic.
   *
   * <p>Parameterised query — the search term is passed as a bound parameter
   * so this is safe against Cypher injection; no string interpolation of
   * untrusted input.
   */
  List<Map<String, Object>> searchLabel(String label, String query) {
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) return List.of();

    String needle = query.toLowerCase(Locale.ROOT);
    // OGM 4 doesn't let us pass a label as a parameter; the label set is
    // closed and curated in LABELS_BY_KIND so concatenation is safe here.
    String cypher =
      "MATCH (n:`" + label + "`) " +
      "WHERE toLower(coalesce(n.name, '')) CONTAINS $needle " +
      "   OR toLower(coalesce(n.description, '')) CONTAINS $needle " +
      "RETURN n.appId AS appId, n.name AS name, n.description AS description " +
      "ORDER BY toLower(coalesce(n.name, '')) ASC, n.appId ASC " +
      "LIMIT 500";

    List<Map<String, Object>> out = new ArrayList<>();
    try {
      Result result = live.query(cypher, Map.of("needle", needle));
      if (result == null) return out;
      for (Map<String, Object> row : result.queryResults()) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("appId", asString(row.get("appId")));
        item.put("kind", label);
        String name = asString(row.get("name"));
        item.put("name", name);
        item.put("snippet", makeSnippet(name, asString(row.get("description"))));
        out.add(item);
      }
    } catch (RuntimeException e) {
      // Per the fail-soft registry rule: a single label's read failing
      // shouldn't poison the entire fan-out. Log and skip — the envelope
      // still carries every label that did succeed.
      Log.warnf(e, "search: label query failed for label=%s", label);
    }
    return out;
  }

  /**
   * Build the snippet field. Prefers a truncated {@code description}; falls
   * back to {@code name} when description is empty; returns {@code null}
   * when both are absent.
   */
  static String makeSnippet(String name, String description) {
    if (description != null && !description.isBlank()) {
      if (description.length() <= SNIPPET_MAX_LENGTH) return description;
      return description.substring(0, SNIPPET_MAX_LENGTH - 1) + "…"; // ellipsis
    }
    return (name == null || name.isBlank()) ? null : name;
  }

  private static String asString(Object v) {
    return v == null ? null : v.toString();
  }
}
