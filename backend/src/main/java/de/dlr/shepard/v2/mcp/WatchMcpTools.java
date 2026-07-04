package de.dlr.shepard.v2.mcp;

import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.v2.collectionwatchers.daos.CollectionWatcherDAO;
import de.dlr.shepard.v2.collectionwatchers.entities.CollectionWatcher;
import de.dlr.shepard.v2.collectionwatchers.io.CollectionWatcherIO;
import de.dlr.shepard.v2.collectionwatchers.services.CollectionWatcherService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotAuthorizedException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP-COV-06 — Collection-watch MCP tools (CW1).
 *
 * <p>Wraps {@link CollectionWatcherService} so an MCP caller can list their
 * own watches across the instance and add/remove a per-Collection watch.
 * The service layer enforces the Read-on-Collection gate for the add path;
 * remove is idempotent and silently succeeds when no watch exists.
 *
 * <p><b>Why CollectionWatcher and not the per-container Watch?</b> The
 * task spec says "list user's current Collection watches" — the
 * user-level Collection subscription is CW1's
 * {@link CollectionWatcher}, not the WATCH1 per-Container link in
 * {@link de.dlr.shepard.v2.watches.entities.Watch}. The two co-exist
 * because they answer different questions: CW1 is "tell me when this
 * Collection grows a new DataObject"; WATCH1 is "tell me when this
 * Container's data changes" (per-container, not exposed here).
 */
@ApplicationScoped
public class WatchMcpTools {

  @Inject
  CollectionWatcherService collectionWatcherService;

  @Inject
  CollectionWatcherDAO collectionWatcherDAO;

  @Inject
  AuthenticationContext authenticationContext;

  @Inject
  McpContextBridge contextBridge;

  @Inject
  McpToolSupport support;

  // ─── watch_list ─────────────────────────────────────────────────────────────

  @Tool(
    name = "watch_list",
    description =
      "List every Collection the caller is currently watching.\n\n" +
      "No parameters — scope is always the authenticated caller.\n\n" +
      "Each row:\n" +
      "  watcherAppId    — UUID v7 of the watch record (used internally).\n" +
      "  collectionAppId — UUID v7 of the watched Collection (use this with\n" +
      "                    `list_collections` or `get_data_object` to inspect\n" +
      "                    its contents).\n" +
      "  username        — caller's username (echoed).\n" +
      "  since           — epoch-ms timestamp the watch was added.\n\n" +
      "Returns an empty array when the caller has no active watches."
  )
  public String watchList() {
    return support.run("watch_list", () -> {
      contextBridge.bind();
      String caller = currentUser();
      List<CollectionWatcher> rows = collectionWatcherDAO.findByUsername(caller);
      List<Map<String, Object>> result = new ArrayList<>(rows.size());
      for (CollectionWatcher w : rows) {
        result.add(toRow(CollectionWatcherIO.from(w)));
      }
      return support.toJson(result);
    });
  }

  // ─── watch_add ──────────────────────────────────────────────────────────────

  @Tool(
    name = "watch_add",
    description =
      "Start watching a Collection — the caller will receive in-app " +
      "notifications when a new top-level DataObject is added.\n\n" +
      "Parameters:\n" +
      "  collectionAppId — UUID v7 of the Collection to watch.\n\n" +
      "Idempotent: if the caller already watches the Collection, the existing " +
      "record is returned unchanged.\n\n" +
      "Auth: Read on the Collection. Forbidden → -32002, not found → -32602."
  )
  public String watchAdd(
    @ToolArg(description = "UUID v7 of the Collection to start watching (from `list_collections`).") String collectionAppId
  ) {
    return support.run("watch_add", () -> {
      contextBridge.bind();
      if (collectionAppId == null || collectionAppId.isBlank()) {
        throw McpToolSupport.invalidParams("collectionAppId is required.");
      }
      String caller = currentUser();
      CollectionWatcherIO io = collectionWatcherService.watch(collectionAppId, caller);
      return support.toJson(toRow(io));
    });
  }

  // ─── watch_remove ───────────────────────────────────────────────────────────

  @Tool(
    name = "watch_remove",
    description =
      "Stop watching a Collection. Idempotent — silently succeeds when the " +
      "caller wasn't watching the Collection.\n\n" +
      "Parameters:\n" +
      "  collectionAppId — UUID v7 of the Collection to stop watching.\n\n" +
      "Returns: {removed: true, collectionAppId}.\n\n" +
      "Auth: any authenticated user (no Read check — removing a watch the " +
      "caller can't see is also a no-op)."
  )
  public String watchRemove(
    @ToolArg(description = "UUID v7 of the Collection to stop watching.") String collectionAppId
  ) {
    return support.run("watch_remove", () -> {
      contextBridge.bind();
      if (collectionAppId == null || collectionAppId.isBlank()) {
        throw McpToolSupport.invalidParams("collectionAppId is required.");
      }
      String caller = currentUser();
      collectionWatcherService.unwatch(collectionAppId, caller);
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("removed", true);
      body.put("collectionAppId", collectionAppId);
      return support.toJson(body);
    });
  }

  // ─── helpers ────────────────────────────────────────────────────────────────

  private String currentUser() {
    String name = authenticationContext.getCurrentUserName();
    if (name == null || name.isBlank()) {
      throw new NotAuthorizedException("Authentication required.");
    }
    return name;
  }

  private static Map<String, Object> toRow(CollectionWatcherIO io) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("watcherAppId", io.watcherAppId());
    row.put("collectionAppId", io.collectionAppId());
    row.put("username", io.username());
    row.put("since", io.since());
    return row;
  }
}
