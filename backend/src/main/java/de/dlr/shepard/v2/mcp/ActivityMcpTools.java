package de.dlr.shepard.v2.mcp;

import de.dlr.shepard.provenance.daos.ActivityDAO;
import de.dlr.shepard.provenance.entities.Activity;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP-COV-11-PROV — provenance query and activity list tools.
 *
 * <p>Two tools:
 * <ul>
 *   <li>{@code prov_query} — returns the PROV-O activity chain around a
 *       given entity (GENERATED + USED edges) up to {@code depth} hops.</li>
 *   <li>{@code activity_list} — returns a paginated list of recent
 *       {@code :Activity} nodes, optionally filtered by userId,
 *       resourcePath, or httpMethod.</li>
 * </ul>
 *
 * <p>Both tools are read-only — no {@code :Activity} is recorded for a
 * provenance query itself (reading provenance is not itself a mutation).
 */
@ApplicationScoped
public class ActivityMcpTools {

  static final int PROV_DEFAULT_DEPTH = 3;
  static final int PROV_MAX_DEPTH = 10;
  static final int LIST_DEFAULT_LIMIT = 20;
  static final int LIST_MAX_LIMIT = 100;

  @Inject
  ActivityDAO activityDAO;

  @Inject
  McpContextBridge contextBridge;

  @Inject
  McpToolSupport support;

  @Tool(
    name = "prov_query",
    description =
      "Return the PROV-O activity chain around a given entity — every " +
      ":Activity that GENERATED or USED the entity, ordered newest-first.\n\n" +
      "Use this to answer 'who created this DataObject?', 'who last modified " +
      "this FileReference?', or 'which AI agent annotated this entity?'\n\n" +
      "Parameters:\n" +
      "  entityAppId — UUID v7 of any Shepard entity (DataObject, Collection,\n" +
      "                FileReference, SemanticAnnotation, …).\n" +
      "  depth       — optional scale factor for the result-set cap; default " + PROV_DEFAULT_DEPTH +
      ", max " + PROV_MAX_DEPTH + ".\n\n" +
      "Response shape:\n" +
      "{\n" +
      "  \"entityAppId\": \"...\",\n" +
      "  \"depth\": <int>,\n" +
      "  \"count\": <int>,\n" +
      "  \"activities\": [\n" +
      "    {\n" +
      "      \"appId\": \"...\",\n" +
      "      \"relation\": \"GENERATED|USED\",\n" +
      "      \"userId\": \"...\",\n" +
      "      \"actionKind\": \"...\",\n" +
      "      \"summary\": \"...\",\n" +
      "      \"resourcePath\": \"...\",\n" +
      "      \"httpMethod\": \"...\",\n" +
      "      \"startedAtMillis\": <long>,\n" +
      "      \"sourceMode\": \"human|ai\"\n" +
      "    }, ...\n" +
      "  ]\n" +
      "}"
  )
  public String provQuery(
    @ToolArg(description = "UUID v7 of the entity whose PROV-O chain to fetch.") String entityAppId,
    @ToolArg(required = false, description = "Result-set scale factor (default " + PROV_DEFAULT_DEPTH + ", max " + PROV_MAX_DEPTH + ").") Integer depth
  ) {
    return support.run("prov_query", () -> {
      contextBridge.bind();
      if (entityAppId == null || entityAppId.isBlank()) {
        throw McpToolSupport.invalidParams("entityAppId is required (UUID v7).");
      }

      int effectiveDepth = Math.max(1, Math.min(depth == null ? PROV_DEFAULT_DEPTH : depth, PROV_MAX_DEPTH));
      List<ActivityDAO.ActivityEdgeRow> rows = activityDAO.findByEntityAppId(entityAppId, effectiveDepth);

      List<Map<String, Object>> activities = new ArrayList<>(rows.size());
      for (ActivityDAO.ActivityEdgeRow row : rows) {
        activities.add(toActivityMap(row.activity(), row.relation()));
      }

      Map<String, Object> body = new LinkedHashMap<>();
      body.put("entityAppId", entityAppId);
      body.put("depth", effectiveDepth);
      body.put("count", activities.size());
      body.put("activities", activities);
      return support.toJson(body);
    });
  }

  @Tool(
    name = "activity_list",
    description =
      "Return a paginated list of recent :Activity nodes, newest-first.\n\n" +
      "Use this to answer 'what happened recently?', 'what did user X do?', or " +
      "'which requests hit /v2/dataobjects today?'\n\n" +
      "Parameters:\n" +
      "  userId       — optional: filter to activities by this agent username.\n" +
      "  resourcePath — optional: prefix-match on the request path " +
      "(e.g. '/v2/collections/' matches all collection mutations).\n" +
      "  httpMethod   — optional: filter by HTTP method (GET, POST, PUT, PATCH, DELETE).\n" +
      "  limit        — optional: max results; default " + LIST_DEFAULT_LIMIT +
      ", max " + LIST_MAX_LIMIT + ".\n\n" +
      "Response shape:\n" +
      "{\n" +
      "  \"filter\": { \"userId\": \"...\", \"resourcePath\": \"...\", \"httpMethod\": \"...\" },\n" +
      "  \"limit\": <int>,\n" +
      "  \"count\": <int>,\n" +
      "  \"activities\": [\n" +
      "    {\n" +
      "      \"appId\": \"...\",\n" +
      "      \"userId\": \"...\",\n" +
      "      \"actionKind\": \"...\",\n" +
      "      \"summary\": \"...\",\n" +
      "      \"resourcePath\": \"...\",\n" +
      "      \"httpMethod\": \"...\",\n" +
      "      \"startedAtMillis\": <long>,\n" +
      "      \"sourceMode\": \"human|ai\"\n" +
      "    }, ...\n" +
      "  ]\n" +
      "}"
  )
  public String activityList(
    @ToolArg(required = false, description = "Filter to activities by this agent username.") String userId,
    @ToolArg(required = false, description = "Prefix-match filter on the request path (e.g. '/v2/collections/').") String resourcePath,
    @ToolArg(required = false, description = "Filter by HTTP method: GET, POST, PUT, PATCH, DELETE.") String httpMethod,
    @ToolArg(required = false, description = "Max results (default " + LIST_DEFAULT_LIMIT + ", max " + LIST_MAX_LIMIT + ").") Integer limit
  ) {
    return support.run("activity_list", () -> {
      contextBridge.bind();
      int effectiveLimit = Math.max(1, Math.min(limit == null ? LIST_DEFAULT_LIMIT : limit, LIST_MAX_LIMIT));
      List<Activity> rows = activityDAO.listForMcp(userId, resourcePath, httpMethod, effectiveLimit);

      List<Map<String, Object>> activities = new ArrayList<>(rows.size());
      for (Activity a : rows) {
        activities.add(toActivityMap(a, null));
      }

      Map<String, Object> filterMap = new LinkedHashMap<>();
      filterMap.put("userId", userId);
      filterMap.put("resourcePath", resourcePath);
      filterMap.put("httpMethod", httpMethod);

      Map<String, Object> body = new LinkedHashMap<>();
      body.put("filter", filterMap);
      body.put("limit", effectiveLimit);
      body.put("count", activities.size());
      body.put("activities", activities);
      return support.toJson(body);
    });
  }

  private static Map<String, Object> toActivityMap(Activity a, String relation) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("appId", a.getAppId());
    if (relation != null) m.put("relation", relation);
    m.put("userId", a.getAgentUsername());
    m.put("actionKind", a.getActionKind());
    m.put("summary", a.getSummary());
    m.put("resourcePath", a.getPath());
    m.put("httpMethod", a.getMethod());
    m.put("startedAtMillis", a.getStartedAtMillis());
    m.put("sourceMode", a.getSourceMode());
    return m;
  }
}
