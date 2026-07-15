package de.dlr.shepard.v2.mcp;

import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.context.snapshot.entities.Snapshot;
import de.dlr.shepard.context.snapshot.io.SnapshotDiffIO;
import de.dlr.shepard.context.snapshot.io.SnapshotDiffIO.DiffEntry;
import de.dlr.shepard.context.snapshot.io.SnapshotIO;
import de.dlr.shepard.context.snapshot.io.SnapshotListItemIO;
import de.dlr.shepard.context.snapshot.services.SnapshotService;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MCP-COV-06-SNAPSHOTS — Snapshot CRUD + diff tools.
 *
 * <p>Exposes five tools over the MCP surface at {@code /v2/mcp/sse}:
 * <ul>
 *   <li>{@code snapshot_list} — paginated list of snapshots for a Collection.</li>
 *   <li>{@code snapshot_get} — metadata for one snapshot by appId.</li>
 *   <li>{@code snapshot_create} — create a new snapshot of a Collection.</li>
 *   <li>{@code snapshot_delete} — soft-delete a snapshot.</li>
 *   <li>{@code snapshot_diff} — compare two snapshots entity-by-entity.</li>
 * </ul>
 *
 * <p>Auth is enforced by {@link McpAuthFilter} upstream; every tool calls
 * {@link McpContextBridge#bind()} to propagate the JWT principal into the
 * request-scoped {@link AuthenticationContext} before any service call.
 *
 * <p>Cross-references: {@code aidocs/41} §3–7; {@code aidocs/16} MCP-COV-06.
 */
@ApplicationScoped
public class SnapshotMcpTools {

  @Inject
  SnapshotService snapshotService;

  @Inject
  AuthenticationContext authenticationContext;

  @Inject
  McpContextBridge contextBridge;

  @Inject
  McpToolSupport support;

  // ─── snapshot_list ─────────────────────────────────────────────────────────

  @Tool(
    name = "snapshot_list",
    description =
      "List snapshots for a Collection, newest first.\n\n" +
      "A Snapshot is a point-in-time record of the revision counters of every " +
      "VersionableEntity (DataObject, Reference, …) reachable from the Collection. " +
      "Use snapshots to track when data changed, compare two points in time, or " +
      "anchor a publication state.\n\n" +
      "Each row carries:\n" +
      "  appId (UUID v7) — pass to `snapshot_get` or `snapshot_diff`\n" +
      "  name — operator-chosen label (e.g. \"v1.0\", \"pre-publication\")\n" +
      "  createdAt — ISO-8601 instant at which the snapshot was captured\n" +
      "  collectionAppId — parent Collection's appId\n" +
      "  collectionName — parent Collection's name at list time\n\n" +
      "Pagination: `page` (0-indexed, default 0) × `size` (default 20, max 100). " +
      "Increase `size` before paging when scanning a large snapshot history.\n\n" +
      "Empty result is normal — the Collection may have no snapshots yet."
  )
  public String snapshotList(
    @ToolArg(description = "UUID v7 of the parent Collection (from `list_collections`).") String collectionAppId,
    @ToolArg(required = false, description = "Zero-based page index. Default 0.") Integer page,
    @ToolArg(required = false, description = "Page size, capped at 100. Default 20.") Integer size
  ) {
    return support.run("snapshot_list", () -> {
      contextBridge.bind();
      if (collectionAppId == null || collectionAppId.isBlank()) {
        throw McpToolSupport.invalidParams("collectionAppId is required (UUID v7 appId).");
      }
      int safePage = page != null ? Math.max(page, 0) : 0;
      int safeSize = size != null ? Math.min(Math.max(size, 1), 100) : 20;

      List<Snapshot> snapshots = snapshotService.listByCollection(collectionAppId, safePage, safeSize);
      List<SnapshotListItemIO> items = snapshots.stream()
        .map(SnapshotListItemIO::from)
        .toList();
      return support.toJson(items);
    });
  }

  // ─── snapshot_get ──────────────────────────────────────────────────────────

  @Tool(
    name = "snapshot_get",
    description =
      "Get metadata for one snapshot by its appId.\n\n" +
      "Returns the full snapshot metadata:\n" +
      "  appId — UUID v7 identifier\n" +
      "  name — operator-chosen label\n" +
      "  description — optional free-text description\n" +
      "  snapshotCapturedAt — ISO-8601 instant of capture\n" +
      "  snapshotCreatedByUsername — username of the caller who created it\n" +
      "  collectionAppId — root Collection that was walked\n" +
      "  entryCount — number of VersionableEntity nodes recorded in the manifest\n\n" +
      "To list snapshot entries (the full entity×revision manifest), use " +
      "`GET /v2/snapshots/{appId}/manifest` via the REST surface.\n\n" +
      "To compare two snapshots call `snapshot_diff`."
  )
  public String snapshotGet(
    @ToolArg(description = "UUID v7 of the snapshot (from `snapshot_list`).") String snapshotAppId
  ) {
    return support.run("snapshot_get", () -> {
      contextBridge.bind();
      if (snapshotAppId == null || snapshotAppId.isBlank()) {
        throw McpToolSupport.invalidParams("snapshotAppId is required (UUID v7 appId).");
      }
      Snapshot snapshot = snapshotService.findByAppId(snapshotAppId);
      if (snapshot == null) {
        throw new McpException("No Snapshot found with appId: " + snapshotAppId, McpToolSupport.INVALID_PARAMS);
      }
      return support.toJson(new SnapshotIO(snapshot));
    });
  }

  // ─── snapshot_create ───────────────────────────────────────────────────────

  @Tool(
    name = "snapshot_create",
    description =
      "Create a point-in-time snapshot of a Collection's current state.\n\n" +
      "The server walks the Collection subtree up to 15 hops deep and records " +
      "the current `revision` counter of every VersionableEntity (DataObject, " +
      "Reference, …) it reaches. Future writes to the live entities do NOT " +
      "affect the snapshot; its entries remain fixed.\n\n" +
      "The caller must have Write permission on the Collection.\n\n" +
      "Returned snapshot fields:\n" +
      "  appId — UUID v7 identifier (save this for `snapshot_diff` and `snapshot_get`)\n" +
      "  name, description — as supplied\n" +
      "  snapshotCapturedAt — server-stamped capture instant\n" +
      "  entryCount — number of entities recorded in the manifest\n\n" +
      "Common use cases: lock a state before a publication, mark a pre-repair " +
      "baseline, or checkpoint a campaign mid-run.\n\n" +
      "Next steps: `snapshot_diff` to compare this snapshot against a later one; " +
      "`snapshot_list` to see the full snapshot history."
  )
  public String snapshotCreate(
    @ToolArg(description = "UUID v7 of the Collection to snapshot (from `list_collections`).") String collectionAppId,
    @ToolArg(description = "User-visible label for the snapshot, e.g. \"v1.0\" or \"pre-publication\".") String name,
    @ToolArg(required = false, description = "Optional free-text description. CommonMark/GFM accepted.") String description
  ) {
    return support.run("snapshot_create", () -> {
      contextBridge.bind();
      if (collectionAppId == null || collectionAppId.isBlank()) {
        throw McpToolSupport.invalidParams("collectionAppId is required (UUID v7 appId).");
      }
      if (name == null || name.isBlank()) {
        throw McpToolSupport.invalidParams("name is required and must be non-blank.");
      }

      String callerUsername = authenticationContext.getCurrentUserName();
      if (callerUsername == null) {
        throw new McpException("Authentication required: no principal in request context.", McpToolSupport.AUTH_REQUIRED);
      }

      Snapshot snapshot = snapshotService.createSnapshot(collectionAppId, name, description, callerUsername);
      return support.toJson(new SnapshotIO(snapshot));
    });
  }

  // ─── snapshot_delete ───────────────────────────────────────────────────────

  @Tool(
    name = "snapshot_delete",
    description =
      "Soft-delete a snapshot and all its manifest entries.\n\n" +
      "The underlying entity data (DataObjects, References, payloads) is " +
      "NOT affected — only the snapshot metadata and its SnapshotEntry rows are " +
      "marked deleted. The deletion is not reversible via the API.\n\n" +
      "The caller must have Write permission on the root Collection.\n\n" +
      "Returns a plain confirmation string on success, or raises an error " +
      "when the snapshot is not found."
  )
  public String snapshotDelete(
    @ToolArg(description = "UUID v7 of the snapshot to delete (from `snapshot_list`).") String snapshotAppId
  ) {
    return support.run("snapshot_delete", () -> {
      contextBridge.bind();
      if (snapshotAppId == null || snapshotAppId.isBlank()) {
        throw McpToolSupport.invalidParams("snapshotAppId is required (UUID v7 appId).");
      }
      boolean deleted = snapshotService.deleteSnapshot(snapshotAppId);
      if (!deleted) {
        throw new McpException("No Snapshot found with appId: " + snapshotAppId, McpToolSupport.INVALID_PARAMS);
      }
      return "Snapshot " + snapshotAppId + " deleted successfully.";
    });
  }

  // ─── snapshot_diff ─────────────────────────────────────────────────────────

  @Tool(
    name = "snapshot_diff",
    description =
      "Compare two snapshots (A = base, B = head) entity-by-entity.\n\n" +
      "Returns a structured diff showing which entities were:\n" +
      "  added — present in B but not in A (created between the snapshots)\n" +
      "  removed — present in A but not in B (deleted or out of scope in B)\n" +
      "  changed — present in both but with a different revision value " +
      "             (i.e. the entity was written to between the two snapshot points)\n" +
      "  unchangedCount — count of entities present in both with identical revisions\n" +
      "                   (not listed individually to avoid large payloads)\n\n" +
      "All lists are sorted by entityAppId ascending for deterministic output. " +
      "A `changed` entry carries `entityAppId`, `revisionA`, and `revisionB` so " +
      "you can see the magnitude of the change.\n\n" +
      "Common use case: compare a pre-fix baseline (A) against a post-fix snapshot " +
      "(B) to confirm exactly which DataObjects changed.\n\n" +
      "Note: snapshotAppIdA and snapshotAppIdB must be different (self-diff is rejected)."
  )
  public String snapshotDiff(
    @ToolArg(description = "UUID v7 of the base snapshot (A). Get this from `snapshot_list`.") String snapshotAppIdA,
    @ToolArg(description = "UUID v7 of the head snapshot (B). Get this from `snapshot_list`.") String snapshotAppIdB
  ) {
    return support.run("snapshot_diff", () -> {
      contextBridge.bind();
      if (snapshotAppIdA == null || snapshotAppIdA.isBlank()) {
        throw McpToolSupport.invalidParams("snapshotAppIdA is required (UUID v7 appId).");
      }
      if (snapshotAppIdB == null || snapshotAppIdB.isBlank()) {
        throw McpToolSupport.invalidParams("snapshotAppIdB is required (UUID v7 appId).");
      }
      if (snapshotAppIdA.equals(snapshotAppIdB)) {
        throw McpToolSupport.invalidParams(
          "snapshotAppIdA and snapshotAppIdB must be different (self-diff is not useful)."
        );
      }

      Snapshot snapshotA = snapshotService.findByAppId(snapshotAppIdA);
      if (snapshotA == null) {
        throw new McpException(
          "No Snapshot found for snapshotAppIdA: " + snapshotAppIdA, McpToolSupport.INVALID_PARAMS
        );
      }
      Snapshot snapshotB = snapshotService.findByAppId(snapshotAppIdB);
      if (snapshotB == null) {
        throw new McpException(
          "No Snapshot found for snapshotAppIdB: " + snapshotAppIdB, McpToolSupport.INVALID_PARAMS
        );
      }

      Map<String, Long> mapA = snapshotService.getEntryRevisionMap(snapshotA.getId());
      Map<String, Long> mapB = snapshotService.getEntryRevisionMap(snapshotB.getId());

      List<String> added = new ArrayList<>();
      List<String> removed = new ArrayList<>();
      List<DiffEntry> changed = new ArrayList<>();
      int unchangedCount = 0;

      for (Map.Entry<String, Long> entryA : mapA.entrySet()) {
        String entityAppId = entryA.getKey();
        long revA = entryA.getValue();
        if (mapB.containsKey(entityAppId)) {
          long revB = mapB.get(entityAppId);
          if (revA == revB) {
            unchangedCount++;
          } else {
            changed.add(new DiffEntry(entityAppId, revA, revB));
          }
        } else {
          removed.add(entityAppId);
        }
      }
      for (String entityAppId : mapB.keySet()) {
        if (!mapA.containsKey(entityAppId)) {
          added.add(entityAppId);
        }
      }

      added.sort(String::compareTo);
      removed.sort(String::compareTo);
      changed.sort((x, y) -> x.entityAppId().compareTo(y.entityAppId()));

      SnapshotDiffIO diff = new SnapshotDiffIO(
        snapshotA.getAppId(),
        snapshotB.getAppId(),
        Instant.ofEpochMilli(snapshotA.getSnapshotCapturedAtMs()).toString(),
        Instant.ofEpochMilli(snapshotB.getSnapshotCapturedAtMs()).toString(),
        added,
        removed,
        changed,
        unchangedCount,
        added.size(),
        removed.size(),
        changed.size(),
        false
      );
      return support.toJson(diff);
    });
  }
}
