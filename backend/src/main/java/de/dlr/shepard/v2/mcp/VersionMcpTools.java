package de.dlr.shepard.v2.mcp;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.version.entities.Version;
import de.dlr.shepard.context.version.services.VersionService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;

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
 *
 * <p><b>Permission posture (MCP-PERMS-AUDIT-2).</b> {@link VersionService}
 * does not perform a {@link PermissionsService} walk of its own, so this
 * tool surface gates explicitly: {@code version_list} requires Read on
 * the supplied Collection; {@code version_get} walks the Version's parent
 * Collection (via the {@code (:Collection)-[:has_version]->(:Version)}
 * edge) and requires Read on it. Callers without Read on the parent
 * Collection get -32002 (forbidden), not an empty list — so the agent
 * can self-correct rather than silently miss data.
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

  /** MCP-PERMS-AUDIT-2 — per-row Collection-Read gate. */
  @Inject
  PermissionsService permissionsService;

  /** MCP-PERMS-AUDIT-2 — caller identity for the Read gate. */
  @Inject
  AuthenticationContext authenticationContext;

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
      // MCP-PERMS-AUDIT-2 — VersionService does not gate; gate explicitly.
      String caller = requireCaller();
      if (!permissionsService.isAccessTypeAllowedForUser(ogmId, AccessType.Read, caller)) {
        throw new ForbiddenException("Caller lacks Read on collection " + entityAppId);
      }
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
      // MCP-PERMS-AUDIT-2 — walk Version -> parent Collection and gate Read.
      String caller = requireCaller();
      Long parentCollectionOgmId = findParentCollectionOgmId(uid);
      if (parentCollectionOgmId == null) {
        // Orphan (no Collection edge) — fail-closed: treat as not visible.
        throw McpToolSupport.invalidParams("No Version with uid " + versionUid);
      }
      if (!permissionsService.isAccessTypeAllowedForUser(parentCollectionOgmId, AccessType.Read, caller)) {
        throw new ForbiddenException("Caller lacks Read on the Version's parent Collection.");
      }
      Map<String, Object> row = toRow(v);
      row.put(
        "predecessorUid",
        v.getPredecessor() == null ? null : v.getPredecessor().getUid().toString()
      );
      return support.toJson(row);
    });
  }

  // ─── helpers — MCP-PERMS-AUDIT-2 ────────────────────────────────────────────

  /**
   * Resolve the authenticated caller; throw {@link NotAuthorizedException}
   * (mapped by {@link McpToolSupport#run} to {@code -32001}) if absent.
   */
  private String requireCaller() {
    String caller = authenticationContext == null ? null : authenticationContext.getCurrentUserName();
    if (caller == null || caller.isBlank()) {
      throw new NotAuthorizedException("Authentication required for version MCP tools.");
    }
    return caller;
  }

  /**
   * Cypher walk Version({uid}) -> parent Collection ogmId. Returns
   * {@code null} when the Version is gone, orphaned, or the Neo4j session
   * is unavailable (fail-closed by caller).
   */
  Long findParentCollectionOgmId(UUID versionUid) {
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) return null;
    String cypher =
      "MATCH (c:Collection)-[:has_version]->(v:Version {uid: $uid}) " +
      "RETURN id(c) AS ogmId LIMIT 1";
    try {
      Result result = live.query(cypher, Map.of("uid", versionUid.toString()));
      if (result == null) return null;
      Iterable<Map<String, Object>> rows = result.queryResults();
      if (rows == null) return null;
      for (Map<String, Object> row : rows) {
        Object v = row.get("ogmId");
        if (v instanceof Number n) return n.longValue();
        if (v != null) {
          try { return Long.parseLong(v.toString()); }
          catch (NumberFormatException ignored) { /* fall through */ }
        }
      }
    } catch (RuntimeException e) {
      Log.debugf(e, "MCP-PERMS-AUDIT-2: ogmId lookup failed for Version uid=%s", versionUid);
    }
    return null;
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
