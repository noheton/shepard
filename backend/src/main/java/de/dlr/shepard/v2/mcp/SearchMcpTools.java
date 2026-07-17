package de.dlr.shepard.v2.mcp;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.common.util.AccessType;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotAuthorizedException;
import java.util.ArrayList;
import java.util.HashMap;
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
 * <p>Implementation: one UNION ALL Cypher across all requested labels,
 * run against the live OGM session. Each label arm contributes its rows
 * to a unified envelope {@code {items: [{appId, kind, name, snippet}],
 * total}} ordered by kind then by name. Substring match is
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
 * <p><b>Permission posture (SEARCH-MCP-PERMS-1).</b> Every result row is
 * resolved to its parent {@code :Collection} anchor and filtered through
 * {@link PermissionsService#isAccessTypeAllowedForUser(long, AccessType,
 * String)} with {@link AccessType#Read}; rows the caller cannot read are
 * excluded.
 *
 * <p>Anchor walks per row kind:
 * <ul>
 *   <li><b>Collection</b> → itself.</li>
 *   <li><b>DataObject</b> → parent Collection (via {@code :HAS_DATAOBJECT}).</li>
 *   <li><b>Container</b> ({@code TimeseriesContainer}, {@code FileContainer},
 *       {@code StructuredDataContainer}) → parent Collection (via
 *       {@code :HAS_CONTAINER} from the owning DataObject).</li>
 *   <li><b>Reference</b> (any of the seven Reference labels) → owning
 *       DataObject's parent Collection.</li>
 * </ul>
 *
 * <p><b>{@code total} semantics.</b> The envelope reports the
 * post-filter total (matches the caller can read), <em>not</em> the
 * unfiltered Cypher hit count. This intentionally diverges from the
 * {@code SCENEGRAPH-PERMS-1-MCP} {@code scene_list} pattern (which
 * page-then-filters and surfaces the unfiltered total): {@code search}
 * filters BEFORE paginating so {@code total} and {@code items.length}
 * stay consistent across paged calls and the caller never sees "200
 * results but only 3 visible". Rows that resolve to an unknown
 * Collection (orphaned legacy nodes) are dropped fail-closed.
 *
 * <p>Per-call cache keyed by parent-Collection appId avoids repeating
 * the walk for many rows in the same Collection — {@link PermissionsService}
 * also caches at the entity-id level, but the per-row appId→ogmId
 * resolution is the part we want to spare.
 */
@ApplicationScoped
public class SearchMcpTools {

  /** Default page size when {@code limit} is omitted. */
  static final int DEFAULT_LIMIT = 20;

  /** Hard cap on {@code limit} so a single tool call never floods the wire. */
  static final int MAX_LIMIT = 100;

  /** Default snippet length (characters) of the {@code description} excerpt. */
  static final int SNIPPET_MAX_LENGTH = 160;

  /** Hard ceiling on rows returned from the UNION ALL query before the permission pass. */
  static final int SEARCH_UNION_LIMIT = 2_000;

  /** All four supported coarse kind buckets, in canonical render order. */
  static final List<String> ALL_KINDS = List.of(
    "Collection", "DataObject", "Container", "Reference"
  );

  /**
   * Canonical render ordinal per Neo4j label — embedded as a literal in each
   * UNION ALL arm so the combined result arrives ordered without a second sort.
   */
  private static final Map<String, Integer> KIND_ORDINAL = Map.ofEntries(
    Map.entry("Collection",              0),
    Map.entry("DataObject",              1),
    Map.entry("TimeseriesContainer",     2),
    Map.entry("FileContainer",           3),
    Map.entry("StructuredDataContainer", 4),
    Map.entry("TimeseriesReference",     5),
    Map.entry("SingletonFileReference",  6),
    Map.entry("FileReference",           7),
    Map.entry("URIReference",            8),
    Map.entry("StructuredDataReference", 9),
    Map.entry("DataObjectReference",     10),
    Map.entry("CollectionReference",     11)
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

  /**
   * SEARCH-MCP-PERMS-1 — per-row Read gate. The service walks the row's
   * Collection anchor; rows the caller cannot read are excluded.
   */
  @Inject PermissionsService permissionsService;

  /**
   * SEARCH-MCP-PERMS-1 — caller identity for the per-row Read gate.
   * Bound by {@link McpAuthFilter} before any tool method runs.
   */
  @Inject AuthenticationContext authenticationContext;

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
      "for deterministic responses. `total` is the count of rows the caller can Read " +
      "AFTER per-row permission filtering, BEFORE pagination. `total > items.length` " +
      "means there are more permitted rows behind the limit.\n\n" +
      "Auth: SEARCH-MCP-PERMS-1 — every row is resolved to its parent Collection anchor " +
      "and filtered through `PermissionsService.isAccessTypeAllowedForUser(..., Read)`. " +
      "Rows the caller cannot Read are excluded silently (the agent never sees them).\n\n" +
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
      // count before pagination. One UNION ALL Cypher across all requested
      // labels replaces the former N per-label round-trips; SEARCH_UNION_LIMIT
      // caps the candidate set to a memory-safe ceiling before the permission
      // pass (for the LUMEN + MFFD scale it stays well inside the ceiling).
      List<String> labels = kinds.stream()
        .flatMap(k -> LABELS_BY_KIND.get(k).stream())
        .toList();
      List<Map<String, Object>> matches = searchUnion(labels, query);

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

      // SEARCH-MCP-PERMS-1 — filter BEFORE paginating so `total` and
      // `items.length` stay consistent across pages. Rows the caller
      // cannot Read are excluded silently. Per-Collection caching keeps
      // the cost down for queries that hit many rows in the same
      // Collection.
      String caller = requireCaller();
      Map<String, Boolean> readableCollectionCache = new HashMap<>();
      List<Map<String, Object>> permitted = new ArrayList<>(deduped.size());
      for (Map<String, Object> row : deduped) {
        if (callerCanReadRow(row, caller, readableCollectionCache)) {
          permitted.add(row);
        }
      }

      long total = permitted.size();
      List<Map<String, Object>> paged;
      if (effectiveOffset >= permitted.size()) {
        paged = List.of();
      } else {
        int end = Math.min(effectiveOffset + effectiveLimit, permitted.size());
        paged = permitted.subList(effectiveOffset, end);
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
   * Run the search as a single UNION ALL Cypher across all requested labels,
   * reducing up to 12 per-label round-trips to one. Each arm embeds a literal
   * {@code kindOrd} integer (from {@link #KIND_ORDINAL}) so the combined
   * result is ordered by kind then by name in one pass.
   *
   * <p>Label names come from the closed, curated {@link #LABELS_BY_KIND} map
   * — concatenation is safe; no user-supplied label strings reach this method.
   * The search term is passed as the bound parameter {@code $needle} — no
   * user input is concatenated into the Cypher string.
   */
  List<Map<String, Object>> searchUnion(List<String> labels, String query) {
    if (labels.isEmpty()) return List.of();
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) return List.of();

    String needle = query.toLowerCase(Locale.ROOT);
    // Build one UNION ALL arm per label. OGM 4 doesn't allow label params;
    // labels come from the closed LABELS_BY_KIND map so concatenation is safe.
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < labels.size(); i++) {
      String label = labels.get(i);
      int ord = KIND_ORDINAL.getOrDefault(label, 99);
      if (i > 0) sb.append("\nUNION ALL\n");
      sb.append("MATCH (n:`").append(label).append("`) ")
        .append("WHERE toLower(coalesce(n.name, '')) CONTAINS $needle ")
        .append("   OR toLower(coalesce(n.description, '')) CONTAINS $needle ")
        .append("RETURN n.appId AS appId, n.name AS name, n.description AS description, ")
        .append(ord).append(" AS kindOrd, '").append(label).append("' AS label");
    }
    sb.append("\nORDER BY kindOrd ASC, name ASC, appId ASC")
      .append("\nLIMIT ").append(SEARCH_UNION_LIMIT);

    List<Map<String, Object>> out = new ArrayList<>();
    try {
      Result result = live.query(sb.toString(), Map.of("needle", needle));
      if (result == null) return out;
      for (Map<String, Object> row : result.queryResults()) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("appId", asString(row.get("appId")));
        String labelVal = asString(row.get("label"));
        item.put("kind", labelVal);
        String name = asString(row.get("name"));
        item.put("name", name);
        item.put("snippet", makeSnippet(name, asString(row.get("description"))));
        out.add(item);
      }
    } catch (RuntimeException e) {
      Log.warnf(e, "search: UNION ALL query failed for labels=%s", labels);
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

  // ── SEARCH-MCP-PERMS-1 — per-row Read gate ────────────────────────────────

  /**
   * Resolve the authenticated caller; throw {@link NotAuthorizedException}
   * (mapped by {@link McpToolSupport#run} to {@code -32001}) if absent.
   */
  String requireCaller() {
    String caller = authenticationContext == null ? null : authenticationContext.getCurrentUserName();
    if (caller == null || caller.isBlank()) {
      throw new NotAuthorizedException("Authentication required for the `search` MCP tool.");
    }
    return caller;
  }

  /**
   * Per-row Read gate. Resolves the row's parent Collection appId via the
   * per-kind walks, then delegates to
   * {@link PermissionsService#isAccessTypeAllowedForUser(long, AccessType, String)}.
   * Returns {@code false} fail-closed when:
   * <ul>
   *   <li>the row's appId is null/blank (legacy un-id'd row),</li>
   *   <li>the row's parent Collection can't be resolved (orphan / cascade race),</li>
   *   <li>the caller lacks Read on the parent Collection.</li>
   * </ul>
   *
   * <p>{@code collectionReadCache} maps Collection appId → granted, so a search
   * that hits N rows in the same Collection pays one permission walk, not N.
   */
  boolean callerCanReadRow(
    Map<String, Object> row,
    String caller,
    Map<String, Boolean> collectionReadCache
  ) {
    String label = (String) row.get("kind");
    String appId = (String) row.get("appId");
    if (label == null || appId == null || appId.isBlank()) return false;

    String collectionAppId = resolveCollectionAnchor(label, appId);
    if (collectionAppId == null) return false;

    Boolean cached = collectionReadCache.get(collectionAppId);
    if (cached != null) return cached;

    boolean allowed;
    try {
      // Reuse the DataObject-appId walk: it accepts a Collection appId too
      // when invoked against the Collection chain. To keep semantics narrow,
      // resolve the Collection's OGM id and call the canonical per-entity
      // gate so the per-iat cache key matches the rest of the v2 stack.
      Long ogmId = lookupCollectionOgmId(collectionAppId);
      if (ogmId == null) {
        allowed = false;
      } else {
        allowed = permissionsService.isAccessTypeAllowedForUser(ogmId, AccessType.Read, caller);
      }
    } catch (RuntimeException e) {
      // Fail-soft on the per-row gate: log + deny, don't poison the whole
      // search response.
      Log.debugf(e, "SEARCH-MCP-PERMS-1: permission walk failed for collection=%s", collectionAppId);
      allowed = false;
    }
    collectionReadCache.put(collectionAppId, allowed);
    return allowed;
  }

  /**
   * Resolve the parent Collection appId for a row, per the per-kind walk.
   * Returns {@code null} when the row is orphaned or the lookup fails.
   *
   * <p>One Cypher round-trip per row that isn't a Collection itself.
   * Backed by {@code collectionReadCache} so multiple rows in the same
   * Collection share the permission decision (but each still pays one
   * anchor-resolve hop — acceptable for typical search result sizes).
   */
  String resolveCollectionAnchor(String label, String appId) {
    if ("Collection".equals(label)) return appId;
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) return null;

    String cypher = anchorCypherFor(label);
    if (cypher == null) return null;

    try {
      Result result = live.query(cypher, Map.of("appId", appId));
      if (result == null) return null;
      Iterable<Map<String, Object>> rows = result.queryResults();
      if (rows == null) return null;
      for (Map<String, Object> r : rows) {
        Object v = r.get("collectionAppId");
        if (v != null) return v.toString();
      }
    } catch (RuntimeException e) {
      Log.debugf(e, "SEARCH-MCP-PERMS-1: anchor resolve failed for %s appId=%s", label, appId);
    }
    return null;
  }

  /**
   * Cypher for the per-label walk to the parent Collection's appId.
   * Returns {@code null} for unknown labels (defensive — every label in
   * {@link #LABELS_BY_KIND} is covered).
   *
   * <p>The DataObject walk uses {@code :HAS_DATAOBJECT}; the container walk
   * uses {@code :HAS_DATAOBJECT} + {@code :HAS_CONTAINER}; the reference
   * walks add a third hop from the reference label up to the DataObject.
   * Constants are not used here because labels and relationship types are
   * baked into the curated {@link #LABELS_BY_KIND} surface and the
   * relationship constants live elsewhere; the strings are local to this
   * class and reviewed alongside the label list.
   */
  static String anchorCypherFor(String label) {
    return switch (label) {
      case "DataObject" ->
        "MATCH (c:Collection)-[:has_dataobject]->(d:DataObject {appId: $appId}) " +
        "RETURN c.appId AS collectionAppId LIMIT 1";
      case "TimeseriesContainer", "FileContainer", "StructuredDataContainer" ->
        // Containers are reached via the Reference that lives in them:
        //   :Collection -[:has_dataobject]-> :DataObject
        //     -[:has_reference]-> :*Reference
        //     -[:is_in_container]-> :*Container
        // We anchor to any Collection whose DataObject chain ends in this
        // container; multiple Collections may share a Container only on
        // legacy rows — LIMIT 1 picks one for the gate (acceptable: if any
        // Collection allows Read, surfacing the row is correct).
        //
        // Note: a Container with zero Reference rows pointing in
        // (freshly minted, no payload yet) fails-closed via this walk
        // because the chain can't complete. That's intentional for v1
        // — an empty container has no payload value for an agent search.
        // If a future use-case needs to surface freshly-minted
        // containers via search, add a direct {@code :DataObject}
        // ownership edge and re-anchor on it.
        "MATCH (c:Collection)-[:has_dataobject]->(:DataObject)-[:has_reference]->" +
        "()-[:is_in_container]->(n:`" + label + "` {appId: $appId}) " +
        "RETURN c.appId AS collectionAppId LIMIT 1";
      case "TimeseriesReference",
        "FileReference",
        "SingletonFileReference",
        "URIReference",
        "StructuredDataReference",
        "DataObjectReference",
        "CollectionReference" ->
        "MATCH (c:Collection)-[:has_dataobject]->(:DataObject)-[:has_reference]->" +
        "(n:`" + label + "` {appId: $appId}) " +
        "RETURN c.appId AS collectionAppId LIMIT 1";
      default -> null;
    };
  }

  /**
   * Look up the OGM Long id for a Collection by appId. Returns
   * {@code null} when the Collection is gone (delete-cascade race) or
   * the Neo4j session is unavailable.
   */
  Long lookupCollectionOgmId(String collectionAppId) {
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) return null;
    String cypher = "MATCH (c:Collection {appId: $appId}) RETURN id(c) AS ogmId LIMIT 1";
    try {
      Result result = live.query(cypher, Map.of("appId", collectionAppId));
      if (result == null) return null;
      Iterable<Map<String, Object>> rows = result.queryResults();
      if (rows == null) return null;
      for (Map<String, Object> r : rows) {
        Object v = r.get("ogmId");
        if (v instanceof Number n) return n.longValue();
        if (v != null) {
          try { return Long.parseLong(v.toString()); }
          catch (NumberFormatException ignored) { /* fall through */ }
        }
      }
    } catch (RuntimeException e) {
      Log.debugf(e, "SEARCH-MCP-PERMS-1: ogmId lookup failed for collection appId=%s", collectionAppId);
    }
    return null;
  }
}
