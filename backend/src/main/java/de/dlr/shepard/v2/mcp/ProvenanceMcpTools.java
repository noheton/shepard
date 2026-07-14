package de.dlr.shepard.v2.mcp;

import de.dlr.shepard.auth.users.services.DisplayNameResolver;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.provenance.daos.ActivityDAO;
import de.dlr.shepard.provenance.entities.Activity;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP-COV-11-PROV — provenance query tools over the {@code :Activity} log.
 *
 * <p>Two tools:
 * <ul>
 *   <li>{@code prov_query} — activities targeting one specific entity
 *       (e.g. "what happened to TR-004?").</li>
 *   <li>{@code activity_list} — global activity feed with every available
 *       filter: agent username, target kind, time window, row limit.</li>
 * </ul>
 *
 * <p>Both tools return activities newest-first (descending
 * {@code startedAt}), matching the REST
 * {@code GET /v2/provenance/activities} default.
 *
 * <p>Agent-username values that look like raw Keycloak UUIDs are redacted
 * to their first-8-char prefix via {@link DisplayNameResolver#redactUsername}
 * — same policy as the REST surface.
 */
@ApplicationScoped
public class ProvenanceMcpTools {

  /** Default row limit when the caller omits {@code limit}. */
  static final int DEFAULT_LIMIT = 50;

  /**
   * Hard ceiling on rows returned per call. Matches the DAO's own cap
   * (1000) but tuned down for MCP context-window budget.
   */
  static final int MAX_LIMIT = 200;

  @Inject
  ActivityDAO activityDAO;

  @Inject
  McpContextBridge contextBridge;

  @Inject
  McpToolSupport support;

  @Inject
  EntityIdResolver entityIdResolver;

  // ── prov_query ────────────────────────────────────────────────────────────

  @Tool(
    name = "prov_query",
    description =
      "Return provenance activities (audit-log rows) that targeted a specific Shepard " +
      "entity — identified by its `entityAppId`. Use this when you need to understand " +
      "what happened to a DataObject, Collection, or any other entity: who created it, " +
      "who modified it, what HTTP method was used, and whether the call came from a " +
      "human or an AI agent (the `sourceMode` field).\n\n" +
      "The response is ordered newest-first (descending `startedAt`).\n\n" +
      "Parameters:\n" +
      "  entityAppId — UUID v7 of any Shepard entity (DataObject, Collection, Container, " +
      "Reference, …). Must exist in the graph — a wrong appId produces a -32602 error.\n" +
      "  limit       — optional; max rows to return. Default " + DEFAULT_LIMIT +
      ", max " + MAX_LIMIT + ".\n\n" +
      "Response shape:\n" +
      "{\n" +
      "  \"entityAppId\":  \"<uuid-v7>\",\n" +
      "  \"limit\":        <int>,\n" +
      "  \"count\":        <int>,\n" +
      "  \"activities\": [\n" +
      "    {\n" +
      "      \"appId\":           \"<activity-uuid>\",\n" +
      "      \"actionKind\":      \"CREATE|READ|UPDATE|DELETE|EXECUTE\",\n" +
      "      \"targetKind\":      \"<OGM label, e.g. DataObject>\",\n" +
      "      \"targetAppId\":     \"<entity-uuid>\",\n" +
      "      \"agentUsername\":   \"<display-name or redacted prefix>\",\n" +
      "      \"summary\":         \"<≤ 256-char human description>\",\n" +
      "      \"startedAt\":       \"<ISO 8601 UTC, e.g. 2026-01-01T12:00:00Z>\",\n" +
      "      \"sourceMode\":      \"human|ai|null\"\n" +
      "    }, ...\n" +
      "  ]\n" +
      "}"
  )
  public String provQuery(
    @ToolArg(description = "UUID v7 appId of the Shepard entity to fetch provenance for.") String entityAppId,
    @ToolArg(required = false, description = "Max rows (default " + DEFAULT_LIMIT + ", max " + MAX_LIMIT + ").") Integer limit
  ) {
    return support.run("prov_query", () -> {
      contextBridge.bind();
      if (entityAppId == null || entityAppId.isBlank()) {
        throw McpToolSupport.invalidParams("entityAppId is required (UUID v7 appId).");
      }
      try {
        entityIdResolver.resolveWithLabels(entityAppId);
      } catch (NotFoundException e) {
        throw McpToolSupport.invalidParams("No entity found for entityAppId=" + entityAppId);
      }
      int effectiveLimit = clamp(limit);
      List<Activity> rows = activityDAO.list(null, null, entityAppId, null, null, effectiveLimit);

      Map<String, Object> body = new LinkedHashMap<>();
      body.put("entityAppId", entityAppId);
      body.put("limit", effectiveLimit);
      body.put("count", rows.size());
      body.put("activities", toActivityMaps(rows));
      return support.toJson(body);
    });
  }

  // ── activity_list ─────────────────────────────────────────────────────────

  @Tool(
    name = "activity_list",
    description =
      "Return the global provenance activity feed with optional filters. All parameters " +
      "are optional — omitting all of them returns the most recent " + DEFAULT_LIMIT +
      " activities across the entire instance.\n\n" +
      "Use this to:\n" +
      "  • Find all actions by a specific user: set agentUsername.\n" +
      "  • Find all mutations on a particular entity kind: set targetKind.\n" +
      "  • Audit a time window: set since + until (ISO 8601 UTC, e.g. \"2026-01-01T00:00:00Z\").\n" +
      "  • Combine filters for surgical queries (e.g. all DELETE operations on " +
      "Collections in the last hour).\n\n" +
      "The response is ordered newest-first (descending `startedAt`).\n\n" +
      "Parameters (all optional):\n" +
      "  agentUsername — filter to activities by this agent.\n" +
      "  targetKind    — filter to activities whose target is this entity kind " +
      "(e.g. `Collection`, `DataObject`, `FileReference`).\n" +
      "  targetAppId   — filter to activities targeting this specific entity UUID.\n" +
      "  since         — inclusive lower bound on startedAt (ISO 8601 UTC, e.g. 2026-01-01T00:00:00Z).\n" +
      "  until         — inclusive upper bound on startedAt (ISO 8601 UTC, e.g. 2026-01-01T23:59:59Z).\n" +
      "  limit         — max rows. Default " + DEFAULT_LIMIT + ", max " + MAX_LIMIT + ".\n\n" +
      "Response shape:\n" +
      "{\n" +
      "  \"limit\":  <int>,\n" +
      "  \"count\":  <int>,\n" +
      "  \"activities\": [\n" +
      "    {\n" +
      "      \"appId\":           \"<activity-uuid>\",\n" +
      "      \"actionKind\":      \"CREATE|READ|UPDATE|DELETE|EXECUTE\",\n" +
      "      \"targetKind\":      \"<OGM label>\",\n" +
      "      \"targetAppId\":     \"<entity-uuid>\",\n" +
      "      \"agentUsername\":   \"<display-name or redacted prefix>\",\n" +
      "      \"summary\":         \"<≤ 256-char description>\",\n" +
      "      \"startedAt\":       \"<ISO 8601 UTC, e.g. 2026-01-01T12:00:00Z>\",\n" +
      "      \"sourceMode\":      \"human|ai|null\",\n" +
      "      \"agentId\":         \"<ai-model-id or null>\"\n" +
      "    }, ...\n" +
      "  ]\n" +
      "}"
  )
  public String activityList(
    @ToolArg(required = false, description = "Filter to activities by this agent username.") String agentUsername,
    @ToolArg(required = false, description = "Filter to activities whose target is this entity kind (e.g. `DataObject`).") String targetKind,
    @ToolArg(required = false, description = "Filter to activities targeting this specific entity UUID.") String targetAppId,
    @ToolArg(required = false, description = "Inclusive lower bound on startedAt (ISO 8601 UTC, e.g. 2026-01-01T00:00:00Z).") String since,
    @ToolArg(required = false, description = "Inclusive upper bound on startedAt (ISO 8601 UTC, e.g. 2026-01-01T23:59:59Z).") String until,
    @ToolArg(required = false, description = "Max rows (default " + DEFAULT_LIMIT + ", max " + MAX_LIMIT + ").") Integer limit
  ) {
    return support.run("activity_list", () -> {
      contextBridge.bind();
      String agent = blankToNull(agentUsername);
      String kind = blankToNull(targetKind);
      String tAppId = blankToNull(targetAppId);
      int effectiveLimit = clamp(limit);

      Long sinceMs = parseIso(since);
      Long untilMs = parseIso(until);
      List<Activity> rows = activityDAO.list(agent, kind, tAppId, sinceMs, untilMs, effectiveLimit);

      Map<String, Object> body = new LinkedHashMap<>();
      if (agent != null) body.put("agentUsername", agent);
      if (kind != null) body.put("targetKind", kind);
      if (tAppId != null) body.put("targetAppId", tAppId);
      if (sinceMs != null) body.put("since", since.trim());
      if (untilMs != null) body.put("until", until.trim());
      body.put("limit", effectiveLimit);
      body.put("count", rows.size());
      body.put("activities", toActivityMaps(rows));
      return support.toJson(body);
    });
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private int clamp(Integer limit) {
    if (limit == null) return DEFAULT_LIMIT;
    return Math.max(1, Math.min(limit, MAX_LIMIT));
  }

  private static String blankToNull(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }

  private static Long parseIso(String iso) {
    if (iso == null || iso.isBlank()) return null;
    try {
      return Instant.parse(iso.trim()).toEpochMilli();
    } catch (DateTimeParseException e) {
      throw McpToolSupport.invalidParams("Invalid ISO 8601 date: \"" + iso.trim() + "\". Expected format: 2026-01-01T00:00:00Z");
    }
  }

  private static String toIso(Long ms) {
    if (ms == null) return null;
    return DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(ms));
  }

  private static List<Map<String, Object>> toActivityMaps(List<Activity> rows) {
    List<Map<String, Object>> out = new ArrayList<>(rows.size());
    for (Activity a : rows) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("appId", a.getAppId());
      m.put("actionKind", a.getActionKind());
      if (a.getTargetKind() != null) m.put("targetKind", a.getTargetKind());
      if (a.getTargetAppId() != null) m.put("targetAppId", a.getTargetAppId());
      m.put("agentUsername", DisplayNameResolver.redactUsername(a.getAgentUsername()));
      m.put("summary", a.getSummary());
      m.put("startedAt", toIso(a.getStartedAtMillis()));
      if (a.getEndedAtMillis() != null) m.put("endedAt", toIso(a.getEndedAtMillis()));
      if (a.getMethod() != null) m.put("method", a.getMethod());
      if (a.getPath() != null) m.put("path", a.getPath());
      if (a.getStatus() != null) m.put("status", a.getStatus());
      if (a.getSourceMode() != null) m.put("sourceMode", a.getSourceMode());
      if (a.getAgentId() != null) m.put("agentId", a.getAgentId());
      out.add(m);
    }
    return out;
  }
}
