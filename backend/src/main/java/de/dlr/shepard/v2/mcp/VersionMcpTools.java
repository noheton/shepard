package de.dlr.shepard.v2.mcp;

import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.context.version.entities.Version;
import de.dlr.shepard.context.version.services.VersionService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MCP-COV-06 — Version MCP tools.
 *
 * <p>Versions are <b>Collection-scoped</b> in shepard's data model — the
 * {@link VersionService} indexes by {@code collectionId}, not by an
 * arbitrary entity id. The task spec's {@code version_list(entityAppId)}
 * shape resolves {@code entityAppId} as a Collection appId; non-Collection
 * appIds are rejected with -32602 INVALID_PARAMS.
 *
 * <p>Version identity uses the OGM-managed {@code uid} (UUID), exposed
 * on the wire as {@code versionUid}. There is also an additive
 * {@code appId} field on the entity (L2a) which is surfaced on every
 * row for forward-compatibility.
 */
@ApplicationScoped
public class VersionMcpTools {

  @Inject
  VersionService versionService;

  @Inject
  EntityIdResolver entityIdResolver;

  @Inject
  McpContextBridge contextBridge;

  @Inject
  McpToolSupport support;

  // ─── version_list ───────────────────────────────────────────────────────────

  @Tool(
    name = "version_list",
    description =
      "List all Versions of a Collection.\n\n" +
      "Parameters:\n" +
      "  entityAppId — UUID v7 of a Collection. Versions are Collection-scoped, so " +
      "                a non-Collection appId is rejected with -32602.\n\n" +
      "Each row:\n" +
      "  uid           — UUID identifier (use with `version_get` and the\n" +
      "                  /shepard/api/versions/{uid} REST endpoint).\n" +
      "  appId         — fork-additive L2a UUID v7 (may be null on legacy rows).\n" +
      "  name, description.\n" +
      "  isHEADVersion — true for the current head of the version chain.\n" +
      "  createdAt, createdBy.\n\n" +
      "Auth: any authenticated user with Read on the underlying Collection."
  )
  public String versionList(
    @ToolArg(description = "UUID v7 of the Collection to list versions for.") String entityAppId
  ) {
    return support.run("version_list", () -> {
      contextBridge.bind();
      if (entityAppId == null || entityAppId.isBlank()) {
        throw McpToolSupport.invalidParams("entityAppId is required.");
      }
      long ogmId = support.resolveOfType(entityAppId, "Collection", "entityAppId");
      List<Version> versions = versionService.getAllVersions(ogmId);
      List<Map<String, Object>> result = new ArrayList<>(versions.size());
      for (Version v : versions) {
        result.add(toRow(v));
      }
      return support.toJson(result);
    });
  }

  // ─── version_get ────────────────────────────────────────────────────────────

  @Tool(
    name = "version_get",
    description =
      "Retrieve a single Version by its UUID.\n\n" +
      "Parameters:\n" +
      "  versionUid — UUID of the Version (from `version_list → uid`).\n\n" +
      "Returns the row shape from `version_list` plus:\n" +
      "  predecessorUid — UID of the previous Version in the chain (may be null " +
      "                   for the original).\n\n" +
      "Errors:\n" +
      "  -32602 — versionUid missing, malformed UUID, or no matching Version."
  )
  public String versionGet(
    @ToolArg(description = "UUID of the Version (from `version_list → uid`).") String versionUid
  ) {
    return support.run("version_get", () -> {
      contextBridge.bind();
      if (versionUid == null || versionUid.isBlank()) {
        throw McpToolSupport.invalidParams("versionUid is required.");
      }
      UUID uid;
      try {
        uid = UUID.fromString(versionUid);
      } catch (IllegalArgumentException iae) {
        throw McpToolSupport.invalidParams("versionUid is not a valid UUID: " + versionUid);
      }
      Version v;
      try {
        v = versionService.getVersion(uid);
      } catch (NotFoundException nfe) {
        throw McpToolSupport.invalidParams("No Version with uid " + versionUid);
      }
      if (v == null) {
        throw McpToolSupport.invalidParams("No Version with uid " + versionUid);
      }
      Map<String, Object> row = toRow(v);
      row.put(
        "predecessorUid",
        v.getPredecessor() == null ? null : v.getPredecessor().getUid().toString()
      );
      return support.toJson(row);
    });
  }

  // ─── helpers ────────────────────────────────────────────────────────────────

  private static Map<String, Object> toRow(Version v) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("uid", v.getUid() == null ? null : v.getUid().toString());
    row.put("appId", v.getAppId());
    row.put("name", v.getName());
    row.put("description", v.getDescription());
    row.put("isHEADVersion", v.isHEADVersion());
    row.put("createdAt", v.getCreatedAt());
    row.put(
      "createdBy",
      v.getCreatedBy() == null ? null : v.getCreatedBy().getUsername()
    );
    return row;
  }
}
