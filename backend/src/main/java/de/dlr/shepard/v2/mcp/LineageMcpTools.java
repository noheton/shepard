package de.dlr.shepard.v2.mcp;

import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.DataObject;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP-COV-11 — closed-form lineage walkers for the
 * {@code has_successor} chain.
 *
 * <p>Two tools:
 * <ul>
 *   <li>{@code get_predecessor_chain} — transitive walk of every
 *       predecessor reachable in ≤ {@code depth} hops.</li>
 *   <li>{@code get_successor_chain} — transitive walk forward.</li>
 * </ul>
 *
 * <p>Wraps the existing
 * {@link DataObjectDAO#findPredecessorChain(String, int)} and
 * {@link DataObjectDAO#findSuccessorChain(String, int)} methods (which
 * already back the matching REST endpoints under
 * {@code /v2/collections/{collId}/data-objects/{appId}/predecessor-chain}
 * + {@code .../successor-chain}). The DAO clamps {@code depth} to
 * {@code [1, 50]}.
 *
 * <p>This is the chain-walking counterpart to {@code get_data_object},
 * which already exposes the first-level
 * {@code predecessorAppIds[]} / {@code successorAppIds[]}: a caller no
 * longer pays N round-trips to walk to the root of an investigation
 * sub-tree (TR-004 → investigation → TR-005 → TR-006).
 *
 * <p>Permission posture: read-only, no per-Collection gate today —
 * the DAO's Cypher is already deleted-aware. Consistent with the
 * other MCP read tools that surface DataObjects.
 */
@ApplicationScoped
public class LineageMcpTools {

  /** Default depth when the caller omits {@code depth}. Matches the REST default. */
  static final int DEFAULT_DEPTH = 10;

  /** Hard server-side ceiling — mirrors {@code DataObjectDAO} clamping. */
  static final int MAX_DEPTH = 50;

  @Inject
  DataObjectDAO dataObjectDAO;

  @Inject
  McpContextBridge contextBridge;

  @Inject
  McpToolSupport support;

  @Tool(
    name = "get_predecessor_chain",
    description =
      "Walk every predecessor reachable from a DataObject up to `depth` hops on the " +
      "`has_successor` graph edges. Returns one entry per reachable, non-deleted " +
      "predecessor, ordered by shepardId. Excludes the start DataObject itself.\n\n" +
      "Use this instead of N successive `get_data_object` round-trips when navigating " +
      "investigation lineage, rework chains, or any other depth-first predecessor walk " +
      "(e.g. TR-006 → TR-005 hold → TR-004 anomaly → TR-003 nominal).\n\n" +
      "Parameters:\n" +
      "  dataObjectAppId — UUID v7 of the start DataObject.\n" +
      "  depth           — optional max hops; default " + DEFAULT_DEPTH +
      ", clamped server-side to [1, " + MAX_DEPTH + "].\n\n" +
      "Response shape:\n" +
      "{\n" +
      "  \"dataObjectAppId\": \"...\",\n" +
      "  \"direction\":       \"predecessor\",\n" +
      "  \"depth\":           <effective-clamped-depth>,\n" +
      "  \"count\":           <int>,\n" +
      "  \"chain\": [{ \"appId\": \"...\", \"name\": \"...\", \"status\": \"...\" }, ...]\n" +
      "}"
  )
  public String getPredecessorChain(
    @ToolArg(description = "UUID v7 of the DataObject to walk predecessors from.") String dataObjectAppId,
    @ToolArg(required = false, description = "Max hop count (default " + DEFAULT_DEPTH + ", max " + MAX_DEPTH + ").") Integer depth
  ) {
    return walk("get_predecessor_chain", dataObjectAppId, depth, true);
  }

  @Tool(
    name = "get_successor_chain",
    description =
      "Walk every successor reachable from a DataObject up to `depth` hops on the " +
      "`has_successor` graph edges. Returns one entry per reachable, non-deleted " +
      "successor, ordered by shepardId. Excludes the start DataObject itself.\n\n" +
      "Use this instead of N successive `get_data_object` round-trips when navigating " +
      "forward lineage from a root nominal run to every downstream investigation / " +
      "repair / re-test that descends from it.\n\n" +
      "Parameters:\n" +
      "  dataObjectAppId — UUID v7 of the start DataObject.\n" +
      "  depth           — optional max hops; default " + DEFAULT_DEPTH +
      ", clamped server-side to [1, " + MAX_DEPTH + "].\n\n" +
      "Response shape:\n" +
      "{\n" +
      "  \"dataObjectAppId\": \"...\",\n" +
      "  \"direction\":       \"successor\",\n" +
      "  \"depth\":           <effective-clamped-depth>,\n" +
      "  \"count\":           <int>,\n" +
      "  \"chain\": [{ \"appId\": \"...\", \"name\": \"...\", \"status\": \"...\" }, ...]\n" +
      "}"
  )
  public String getSuccessorChain(
    @ToolArg(description = "UUID v7 of the DataObject to walk successors from.") String dataObjectAppId,
    @ToolArg(required = false, description = "Max hop count (default " + DEFAULT_DEPTH + ", max " + MAX_DEPTH + ").") Integer depth
  ) {
    return walk("get_successor_chain", dataObjectAppId, depth, false);
  }

  private String walk(String toolName, String dataObjectAppId, Integer depth, boolean predecessor) {
    return support.run(toolName, () -> {
      contextBridge.bind();
      // Type-check before hitting the DAO so a wrong-kind appId gives a clean
      // -32602 instead of an empty chain (which would silently look like
      // "no predecessors" to the agent).
      support.resolveOfType(dataObjectAppId, "DataObject", "dataObjectAppId");

      int requestedDepth = depth == null ? DEFAULT_DEPTH : depth;
      int effectiveDepth = Math.max(1, Math.min(requestedDepth, MAX_DEPTH));

      List<DataObject> chain = predecessor
        ? dataObjectDAO.findPredecessorChain(dataObjectAppId, effectiveDepth)
        : dataObjectDAO.findSuccessorChain(dataObjectAppId, effectiveDepth);

      List<Map<String, Object>> rows = new ArrayList<>(chain.size());
      for (DataObject d : chain) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("appId", d.getAppId());
        row.put("name", d.getName());
        row.put("status", d.getStatus());
        rows.add(row);
      }

      Map<String, Object> body = new LinkedHashMap<>();
      body.put("dataObjectAppId", dataObjectAppId);
      body.put("direction", predecessor ? "predecessor" : "successor");
      body.put("depth", effectiveDepth);
      body.put("count", rows.size());
      body.put("chain", rows);
      return support.toJson(body);
    });
  }
}
