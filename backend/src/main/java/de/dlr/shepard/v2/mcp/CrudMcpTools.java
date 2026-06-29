package de.dlr.shepard.v2.mcp;

import de.dlr.shepard.context.collection.daos.CollectionDAO;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.context.collection.services.DataObjectService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * MCP-COV-02-CORE-CRUD slice 1 — create + update tools for Collection and DataObject.
 *
 * <p>These four tools close the "read-only MCP surface" gap identified by the
 * MCP-COV-01 audit. They wrap the same service layer used by the v2 REST
 * endpoints ({@code POST/PATCH /v2/collections}, {@code POST/PATCH /v2/…/data-objects}).
 *
 * <p>Auth is enforced upstream by {@link McpAuthFilter}. The service layer
 * applies the same WRITE permission check as the REST surface.
 *
 * <p>Update tools use a read-then-merge pattern: the existing entity is
 * fetched first so that fields not supplied by the caller are preserved.
 * Only name, description, and status are exposed as mutable via MCP v0;
 * attributes, license, accessRights, and relationship (predecessor/parent)
 * changes are carried through from the existing entity to avoid clearing them.
 *
 * <p>Slice 2 ({@code MCP-COV-02-2}): delete tools + reference_create kind-dispatch.
 */
@ApplicationScoped
public class CrudMcpTools {

  @Inject
  CollectionService collectionService;

  @Inject
  CollectionDAO collectionDAO;

  @Inject
  DataObjectService dataObjectService;

  @Inject
  DataObjectDAO dataObjectDAO;

  @Inject
  McpContextBridge contextBridge;

  @Inject
  McpToolSupport support;

  // ── collection_create ──────────────────────────────────────────────────────

  @Tool(
    name = "collection_create",
    description =
      "Create a new Collection (top-level research-dataset bucket).\n\n" +
      "Required:\n" +
      "  name — human-readable label for the Collection (e.g. 'LUMEN Campaign 2024').\n\n" +
      "Optional:\n" +
      "  description — free-text description.\n" +
      "  status      — lifecycle status. Allowed: DRAFT, IN_REVIEW, READY, PUBLISHED, " +
      "ARCHIVED, NCR_OPEN, ON_HOLD, REJECTED, CERTIFIED. Defaults to DRAFT when omitted.\n\n" +
      "Returns: {appId, name, description, status}\n\n" +
      "The caller must be an authenticated user. The new Collection is created as " +
      "Private (visible only to the creator) and DRAFT status unless overridden. " +
      "Use `collection_update` to change metadata after creation."
  )
  public String collectionCreate(
    @ToolArg(description = "Name of the new Collection (required).") String name,
    @ToolArg(name = "description", description = "Optional free-text description.", required = false) String description,
    @ToolArg(name = "status", description = "Optional lifecycle status (default: DRAFT).", required = false) String status
  ) {
    return support.run("collection_create", () -> {
      contextBridge.bind();
      if (name == null || name.isBlank()) {
        throw McpToolSupport.invalidParams("name is required and must not be blank.");
      }
      CollectionIO io = new CollectionIO();
      io.setName(name.trim());
      io.setDescription(description);
      io.setStatus(status);
      Collection created = collectionService.createCollection(io);
      return support.toJson(toCollectionSummary(created));
    });
  }

  // ── collection_update ──────────────────────────────────────────────────────

  @Tool(
    name = "collection_update",
    description =
      "Update metadata of an existing Collection. Only the fields you supply are changed; " +
      "fields left null/absent are preserved.\n\n" +
      "Required:\n" +
      "  collectionAppId — UUID v7 of the Collection (from `list_collections`).\n\n" +
      "Optional (supply at least one):\n" +
      "  name        — new name.\n" +
      "  description — new description (supply empty string '' to clear it).\n" +
      "  status      — new lifecycle status. Allowed: DRAFT, IN_REVIEW, READY, PUBLISHED, " +
      "ARCHIVED, NCR_OPEN, ON_HOLD, REJECTED, CERTIFIED, CONCESSION_PENDING.\n" +
      "                Some transitions are forbidden (e.g. ARCHIVED → DRAFT). " +
      "The server returns 409 on an invalid transition.\n\n" +
      "Returns: {appId, name, description, status}\n\n" +
      "Auth: the caller must have WRITE permission on the Collection."
  )
  public String collectionUpdate(
    @ToolArg(description = "UUID v7 of the Collection to update (from `list_collections`).") String collectionAppId,
    @ToolArg(name = "name", description = "New name (null = keep existing).", required = false) String name,
    @ToolArg(name = "description", description = "New description (null = keep existing; '' = clear).", required = false) String description,
    @ToolArg(name = "status", description = "New lifecycle status (null = keep existing).", required = false) String status
  ) {
    return support.run("collection_update", () -> {
      contextBridge.bind();
      if (collectionAppId == null || collectionAppId.isBlank()) {
        throw McpToolSupport.invalidParams("collectionAppId is required.");
      }
      Collection existing = collectionDAO.findByAppId(collectionAppId);
      if (existing == null) {
        throw McpToolSupport.invalidParams("No Collection found for appId: " + collectionAppId);
      }
      // Build a merged IO: carry through existing values for unset fields.
      CollectionIO io = new CollectionIO();
      io.setName(name != null ? name.trim() : existing.getName());
      io.setDescription(description != null ? description : existing.getDescription());
      io.setStatus(status != null ? status : existing.getStatus());
      io.setAttributes(existing.getAttributes() != null ? existing.getAttributes() : Map.of());
      io.setLicense(existing.getLicense());
      io.setAccessRights(existing.getAccessRights());
      Collection updated = collectionService.updateCollectionByShepardId(existing.getId(), io);
      return support.toJson(toCollectionSummary(updated));
    });
  }

  // ── data_object_create ────────────────────────────────────────────────────

  @Tool(
    name = "data_object_create",
    description =
      "Create a new DataObject inside an existing Collection.\n\n" +
      "A DataObject is a logical research artefact — one test run, one process step, " +
      "one measurement session. It contains Containers (timeseries, files, structured data) " +
      "and References. Use `list_collections` to discover the parent Collection appId.\n\n" +
      "Required:\n" +
      "  collectionAppId — UUID v7 of the parent Collection.\n" +
      "  name            — human-readable label (e.g. 'TR-004 anomaly investigation').\n\n" +
      "Optional:\n" +
      "  description — free-text description.\n" +
      "  status      — initial lifecycle status (default: DRAFT). Allowed: DRAFT, IN_REVIEW, " +
      "READY, PUBLISHED, ARCHIVED, NCR_OPEN, ON_HOLD, REJECTED, CERTIFIED.\n\n" +
      "Returns: {appId, name, description, status, collectionAppId}\n\n" +
      "Auth: the caller must have WRITE permission on the parent Collection."
  )
  public String dataObjectCreate(
    @ToolArg(description = "UUID v7 of the parent Collection (from `list_collections`).") String collectionAppId,
    @ToolArg(description = "Name for the new DataObject (required).") String name,
    @ToolArg(name = "description", description = "Optional free-text description.", required = false) String description,
    @ToolArg(name = "status", description = "Optional initial lifecycle status (default: DRAFT).", required = false) String status
  ) {
    return support.run("data_object_create", () -> {
      contextBridge.bind();
      if (collectionAppId == null || collectionAppId.isBlank()) {
        throw McpToolSupport.invalidParams("collectionAppId is required.");
      }
      if (name == null || name.isBlank()) {
        throw McpToolSupport.invalidParams("name is required and must not be blank.");
      }
      Collection coll = collectionDAO.findByAppId(collectionAppId);
      if (coll == null) {
        throw McpToolSupport.invalidParams("No Collection found for appId: " + collectionAppId);
      }
      DataObjectIO io = new DataObjectIO();
      io.setName(name.trim());
      io.setDescription(description);
      io.setStatus(status);
      DataObject created = dataObjectService.createDataObject(coll.getId(), io);
      return support.toJson(toDataObjectSummary(created));
    });
  }

  // ── data_object_update ────────────────────────────────────────────────────

  @Tool(
    name = "data_object_update",
    description =
      "Update metadata of an existing DataObject. Only the fields you supply are changed; " +
      "fields left null/absent are preserved.\n\n" +
      "Note: this tool updates name, description, and status only. Predecessor/successor " +
      "relationships, Containers, and References are NOT modified by this tool.\n\n" +
      "Required:\n" +
      "  collectionAppId  — UUID v7 of the parent Collection (from `list_collections`).\n" +
      "  dataObjectAppId  — UUID v7 of the DataObject (from `list_data_objects`).\n\n" +
      "Optional (supply at least one):\n" +
      "  name        — new name.\n" +
      "  description — new description (supply '' to clear).\n" +
      "  status      — new lifecycle status. Allowed: DRAFT, IN_REVIEW, READY, PUBLISHED, " +
      "ARCHIVED, NCR_OPEN, ON_HOLD, REJECTED, CERTIFIED, CONCESSION_PENDING.\n" +
      "                Some transitions are forbidden (e.g. ARCHIVED → DRAFT). " +
      "The server returns 409 on an invalid transition.\n\n" +
      "Returns: {appId, name, description, status, collectionAppId}\n\n" +
      "Auth: the caller must have WRITE permission on the parent Collection."
  )
  public String dataObjectUpdate(
    @ToolArg(description = "UUID v7 of the parent Collection (from `list_collections`).") String collectionAppId,
    @ToolArg(description = "UUID v7 of the DataObject to update (from `list_data_objects`).") String dataObjectAppId,
    @ToolArg(name = "name", description = "New name (null = keep existing).", required = false) String name,
    @ToolArg(name = "description", description = "New description (null = keep existing; '' = clear).", required = false) String description,
    @ToolArg(name = "status", description = "New lifecycle status (null = keep existing).", required = false) String status
  ) {
    return support.run("data_object_update", () -> {
      contextBridge.bind();
      if (collectionAppId == null || collectionAppId.isBlank()) {
        throw McpToolSupport.invalidParams("collectionAppId is required.");
      }
      if (dataObjectAppId == null || dataObjectAppId.isBlank()) {
        throw McpToolSupport.invalidParams("dataObjectAppId is required.");
      }
      Collection coll = collectionDAO.findByAppId(collectionAppId);
      if (coll == null) {
        throw McpToolSupport.invalidParams("No Collection found for appId: " + collectionAppId);
      }
      DataObject existing = dataObjectDAO.findByAppId(dataObjectAppId);
      if (existing == null) {
        throw McpToolSupport.invalidParams("No DataObject found for appId: " + dataObjectAppId);
      }
      // Build a merged IO: carry through relationship and attribute fields so they
      // are not cleared by the full-replace service call.
      DataObjectIO io = new DataObjectIO();
      io.setName(name != null ? name.trim() : existing.getName());
      io.setDescription(description != null ? description : existing.getDescription());
      io.setStatus(status != null ? status : existing.getStatus());
      io.setAttributes(existing.getAttributes() != null ? existing.getAttributes() : Map.of());
      io.setLicense(existing.getLicense());
      io.setAccessRights(existing.getAccessRights());
      io.setParentId(existing.getParent() != null ? existing.getParent().getId() : null);
      // Carry through predecessor appIds (v2 appId-keyed path — preferred over numeric ids).
      if (existing.getPredecessors() != null && !existing.getPredecessors().isEmpty()) {
        io.setPredecessorAppIds(
          existing.getPredecessors().stream()
            .map(DataObject::getAppId)
            .filter(Objects::nonNull)
            .toArray(String[]::new)
        );
      }
      // successorIds: leave null → service skips the "given must match current" check.
      DataObject updated = dataObjectService.updateDataObject(coll.getId(), existing.getId(), io);
      return support.toJson(toDataObjectSummary(updated));
    });
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private static Map<String, Object> toCollectionSummary(Collection c) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("appId", c.getAppId());
    m.put("name", c.getName());
    m.put("description", c.getDescription());
    m.put("status", c.getStatus());
    return m;
  }

  private static Map<String, Object> toDataObjectSummary(DataObject d) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("appId", d.getAppId());
    m.put("name", d.getName());
    m.put("description", d.getDescription());
    m.put("status", d.getStatus());
    Collection coll = d.getCollection();
    m.put("collectionAppId", coll != null ? coll.getAppId() : null);
    return m;
  }
}
