package de.dlr.shepard.v2.mcp;

import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.v2.dataobject.io.DataObjectDetailV2IO;
import de.dlr.shepard.v2.dataobject.io.DataObjectListItemV2IO;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Phase 1 — Collection and DataObject tools (aidocs/88 §3.1).
 *
 * <p>Tools are served at the native Quarkus MCP endpoint {@code /v2/mcp/sse}.
 * Auth is enforced upstream by {@link McpAuthFilter} (Vert.x
 * {@code @Observes Filters} handler), which validates the Bearer token
 * via {@link de.dlr.shepard.auth.security.JwtTokenAuthService} and
 * populates the request-scoped {@link
 * de.dlr.shepard.auth.security.AuthenticationContext} that the service
 * layer reads for permission checks.
 *
 * <p>Tool methods call the service layer directly — no HTTP hop, no
 * token relay, no separate process.
 */
@ApplicationScoped
public class CollectionMcpTools {

  @Inject
  CollectionService collectionService;

  @Inject
  DataObjectService dataObjectService;

  @Inject
  DataObjectDAO dataObjectDAO;

  @Inject
  McpContextBridge contextBridge;

  @Inject
  McpToolSupport support;

  @Tool(
    name = "list_collections",
    description =
      "Discover Collections the caller can read. A Collection is shepard's top-level " +
      "research-dataset bucket (e.g. one test campaign, one mission phase, one " +
      "manufacturing batch). Start here when the user names a campaign or asks " +
      "\"what data do we have on …\".\n\n" +
      "Returns a JSON array of rows. Each row:\n" +
      "  appId (UUID v7) — pass this to `list_data_objects` to enumerate the " +
      "                    Collection's contents\n" +
      "  name, description (free-text from the Collection owner)\n" +
      "  status (lifecycle: DRAFT|IN_REVIEW|READY|PUBLISHED|ARCHIVED|FAILED|NCR_OPEN|ON_HOLD|REJECTED|CERTIFIED, or null)\n\n" +
      "Filtering: `name` does a case-insensitive substring match. With no " +
      "arguments, returns the first page (50 rows) ordered by the service-layer " +
      "default. Increase `size` (up to 200) before paging when scanning.\n\n" +
      "Empty result is normal — the caller may not have any visible Collections."
  )
  public String listCollections(
    @ToolArg(required = false, description = "Zero-based page index. Default 0.") Integer page,
    @ToolArg(required = false, description = "Page size, capped at 200. Default 50.") Integer size,
    @ToolArg(required = false, description = "Optional case-insensitive substring of the Collection name. Omit to list all.") String name
  ) {
    return support.run("list_collections", () -> {
      contextBridge.bind();
      int safePage = page != null ? Math.max(page, 0) : 0;
      int safeSize = size != null ? Math.min(Math.max(size, 1), 200) : 50;

      var params = new QueryParamHelper().withPageAndSize(safePage, safeSize);
      if (name != null && !name.isBlank()) {
        params = params.withName(name);
      }

      List<Collection> collections = collectionService.getAllCollections(params);
      List<Map<String, Object>> result = new ArrayList<>(collections.size());
      for (Collection c : collections) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("appId", c.getAppId());
        row.put("name", c.getName());
        row.put("description", c.getDescription());
        row.put("status", c.getStatus());
        result.add(row);
      }
      return support.toJson(result);
    });
  }

  @Tool(
    name = "list_data_objects",
    description =
      "Enumerate the DataObjects in a Collection. A DataObject is one unit of " +
      "scientific work — typically one test run, one process step, one inspection. " +
      "DataObjects form a DAG via Predecessor/Successor links (use `get_data_object` " +
      "to follow the chain).\n\n" +
      "Each row has:\n" +
      "  appId (UUID v7) — feed to `get_data_object` for full detail + containers\n" +
      "  name, status\n" +
      "  timeseriesCount, fileCount, structuredDataCount — per-payload-kind counts.\n" +
      "  Non-zero `timeseriesCount` means the DataObject references at least one " +
      "  TimeseriesContainer; drill in via `get_data_object` and then `list_channels`.\n\n" +
      "Returns ONLY DataObjects directly under the given Collection — does not " +
      "recurse into child Collections or follow Predecessor links. To traverse " +
      "lineage call `get_data_object` and follow predecessorSummaries / " +
      "successorSummaries / childSummaries.\n\n" +
      "If the Collection has many DataObjects (LUMEN ≈15, MFFD ≈12 today, " +
      "production campaigns may reach 10⁴+), narrow with `name` first."
  )
  public String listDataObjects(
    @ToolArg(description = "UUID v7 of the parent Collection. Get this from `list_collections`.") String collectionAppId,
    @ToolArg(required = false, description = "Zero-based page index. Default 0.") Integer page,
    @ToolArg(required = false, description = "Page size, capped at 200. Default 50.") Integer size,
    @ToolArg(required = false, description = "Optional case-insensitive substring of the DataObject name. Omit to list all.") String name
  ) {
    return support.run("list_data_objects", () -> {
      contextBridge.bind();
      long collectionOgmId = support.resolveOfType(collectionAppId, "Collection", "collectionAppId");

      int safePage = page != null ? Math.max(page, 0) : 0;
      int safeSize = size != null ? Math.min(Math.max(size, 1), 200) : 50;

      var params = new QueryParamHelper().withPageAndSize(safePage, safeSize);
      if (name != null && !name.isBlank()) {
        params = params.withName(name);
      }

      List<DataObject> dataObjects =
        dataObjectService.getAllDataObjectsByShepardIds(collectionOgmId, params, null);

      List<String> appIds = new ArrayList<>(dataObjects.size());
      for (DataObject d : dataObjects) {
        if (d.getAppId() != null) appIds.add(d.getAppId());
      }
      Map<String, long[]> counts = dataObjectDAO.findRefCountsByAppIds(appIds);

      List<Map<String, Object>> result = new ArrayList<>(dataObjects.size());
      for (DataObject d : dataObjects) {
        long[] c = d.getAppId() != null
          ? counts.getOrDefault(d.getAppId(), new long[] { 0, 0, 0 })
          : new long[] { 0, 0, 0 };
        var item = new DataObjectListItemV2IO(d, c[0], c[1], c[2]);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("appId", item.getAppId());
        row.put("name", item.getName());
        row.put("status", item.getStatus());
        row.put("timeseriesCount", item.getTimeseriesCount());
        row.put("fileCount", item.getFileCount());
        row.put("structuredDataCount", item.getStructuredDataCount());
        result.add(row);
      }
      return support.toJson(result);
    });
  }

  @Tool(
    name = "get_data_object",
    description =
      "Get the full record for one DataObject: identity, free-text fields, " +
      "attributes (key-value annotations), all payload containers it references, " +
      "and direct lineage links (parent / children / predecessors / successors).\n\n" +
      "This is the central navigation tool — most workflows pass through here. " +
      "Call it after `list_data_objects` to pick one specific run / step / sample.\n\n" +
      "Response shape (JSON):\n" +
      "{\n" +
      "  \"appId\": \"…\", \"id\": 123, \"name\": \"TR-004\",\n" +
      "  \"description\": \"…\", \"status\": \"READY\",\n" +
      "  \"attributes\": { \"propellant\": \"LOX/LH2\", \"bench\": \"P8.1\", … },\n" +
      "  \"containers\": {\n" +
      "    \"timeseries\":     [{containerAppId, containerName, containerId, referenceId, referenceAppId}, …],\n" +
      "    \"files\":          [{containerAppId, containerName, containerId, referenceId, referenceAppId}, …],\n" +
      "    \"structuredData\": [{containerAppId, containerName, containerId, referenceId, referenceAppId}, …]\n" +
      "  },\n" +
      "  \"timeseriesReferenceAppIds\":     [\"<UUID v7>\", …] | null,\n" +
      "  \"fileReferenceAppIds\":           [\"<UUID v7>\", …] | null,\n" +
      "  \"structuredDataReferenceAppIds\": [\"<UUID v7>\", …] | null,\n" +
      "  \"predecessorSummaries\": [{appId, id, name, status}, …],\n" +
      "  \"successorSummaries\":   [...],\n" +
      "  \"childSummaries\":       [...],\n" +
      "  \"parentSummary\":        {appId, id, name, status} | null\n" +
      "}\n\n" +
      "IMPORTANT — how to reach timeseries data:\n" +
      "  Use `containers.timeseries[i].containerAppId` when calling `list_channels`.\n" +
      "  `timeseriesReferenceAppIds` are the appIds of the REFERENCE NODES (graph edges),\n" +
      "  NOT container appIds. Passing a referenceAppId to `list_channels` will 404.\n\n" +
      "Common next calls:\n" +
      "  • For timeseries: take `containers.timeseries[].containerAppId` and call " +
      "    `list_channels(containerAppId)`. Then call `get_channel_data` with the " +
      "    5-tuple from `list_channels` to retrieve samples with optional LTTB downsampling.\n" +
      "  • For lineage: follow `predecessorSummaries[].appId` or `successorSummaries[].appId` " +
      "    by recursing into `get_data_object`. predecessor = the DataObject this " +
      "    one was produced from / depends on; successor = a DataObject produced FROM this one.\n" +
      "  • For children: a `childSummaries` entry is a DataObject nested under this " +
      "    one in the Collection hierarchy (e.g. an anomaly-investigation branch under " +
      "    its parent test run)."
  )
  public String getDataObject(
    @ToolArg(description = "UUID v7 of the parent Collection (from `list_collections`).") String collectionAppId,
    @ToolArg(description = "UUID v7 of the DataObject (from `list_data_objects` rows, or from a parent's predecessorSummaries / successorSummaries / childSummaries).") String dataObjectAppId
  ) {
    return support.run("get_data_object", () -> {
      contextBridge.bind();
      long collectionOgmId = support.resolveOfType(collectionAppId, "Collection", "collectionAppId");
      long dataObjectOgmId = support.resolveOfType(dataObjectAppId, "DataObject", "dataObjectAppId");

      // dataObjectService.getDataObject loads at the default OGM depth (1), which
      // leaves each reference's typed container relationship null and produces
      // an empty containers.timeseries[] in the V2 IO. We need depth 2 so the
      // container hop is populated for the agent to find appIds to chain into
      // list_channels / list_files / structured-data tools.
      DataObject deep = dataObjectDAO.findByShepardIdAtDepth(dataObjectOgmId, 2);
      // Still go through the service for the auth + lineage-completion side
      // effects, then graft the deeper references list back on.
      DataObject dataObject = dataObjectService.getDataObject(collectionOgmId, dataObjectOgmId);
      if (deep != null && deep.getReferences() != null) {
        dataObject.setReferences(deep.getReferences());
      }
      DataObjectDetailV2IO detail = new DataObjectDetailV2IO(dataObject);
      return support.toJson(detail);
    });
  }

  // ── MCP-COV-02-1: Collection CRUD ────────────────────────────────────────

  @Tool(
    name = "collection_create",
    description =
      "Create a new Collection owned by the caller. A Collection is shepard's " +
      "top-level research-dataset bucket (one test campaign, one mission phase, " +
      "one manufacturing batch).\n\n" +
      "Returns the created Collection record:\n" +
      "  appId — carry this into `list_data_objects` / `data_object_create`\n" +
      "  id    — internal Neo4j id (use appId in all subsequent tool calls)\n" +
      "  name, description, status\n\n" +
      "Status values: DRAFT (default) | IN_REVIEW | READY | PUBLISHED | ARCHIVED."
  )
  public String collectionCreate(
    @ToolArg(description = "Name for the new Collection (e.g. \"LUMEN Campaign 2024-Q4\").") String name,
    @ToolArg(required = false, description = "Optional free-text description.") String description,
    @ToolArg(required = false, description = "Initial lifecycle status. One of: DRAFT, IN_REVIEW, READY, PUBLISHED, ARCHIVED. Defaults to DRAFT when omitted.") String status
  ) {
    return support.run("collection_create", () -> {
      contextBridge.bind();
      if (name == null || name.isBlank()) {
        throw McpToolSupport.invalidParams("name is required and must not be blank.");
      }
      CollectionIO io = new CollectionIO();
      io.setName(name.trim());
      if (description != null) io.setDescription(description);
      if (status != null && !status.isBlank()) io.setStatus(status);
      Collection created = collectionService.createCollection(io);
      return support.toJson(collectionSummary(created));
    });
  }

  @Tool(
    name = "collection_update",
    description =
      "Update the name, description, or status of an existing Collection.\n\n" +
      "Only the fields you supply are changed — omitted fields keep their current " +
      "values. Returns the updated record (same shape as `collection_create`).\n\n" +
      "Status values: DRAFT | IN_REVIEW | READY | PUBLISHED | ARCHIVED.\n\n" +
      "To update permissions, use the REST API directly " +
      "(`PUT /v2/collections/{appId}/permissions`)."
  )
  public String collectionUpdate(
    @ToolArg(description = "UUID v7 of the Collection to update (from `list_collections`).") String collectionAppId,
    @ToolArg(required = false, description = "New name. Omit to keep the existing name.") String name,
    @ToolArg(required = false, description = "New description. Omit to keep existing. Pass empty string to clear.") String description,
    @ToolArg(required = false, description = "New lifecycle status. One of: DRAFT, IN_REVIEW, READY, PUBLISHED, ARCHIVED. Omit to keep existing.") String status
  ) {
    return support.run("collection_update", () -> {
      contextBridge.bind();
      long ogmId = support.resolveOfType(collectionAppId, "Collection", "collectionAppId");
      Collection existing = collectionService.getCollection(ogmId);

      CollectionIO io = new CollectionIO();
      io.setName((name != null && !name.isBlank()) ? name.trim() : existing.getName());
      io.setDescription(description != null ? description : existing.getDescription());
      io.setStatus((status != null && !status.isBlank()) ? status : existing.getStatus());
      // Preserve all other scalar fields unchanged.
      io.setAttributes(existing.getAttributes() != null ? existing.getAttributes() : new HashMap<>());
      io.setLicense(existing.getLicense());
      io.setAccessRights(existing.getAccessRights());
      io.setEmbargoEndDate(existing.getEmbargoEndDate());
      io.setHeroImageUrl(existing.getHeroImageUrl());
      io.setImportedFrom(existing.getImportedFrom());
      io.setPromptLogMode(existing.getPromptLogMode());

      Collection updated = collectionService.updateCollectionByShepardId(ogmId, io);
      return support.toJson(collectionSummary(updated));
    });
  }

  @Tool(
    name = "collection_delete",
    description =
      "Permanently delete a Collection and ALL its DataObjects, References, and " +
      "contained data. This operation is irreversible.\n\n" +
      "The caller must be the Collection owner or an instance admin. " +
      "Returns {\"deleted\": true, \"appId\": \"…\"} on success."
  )
  public String collectionDelete(
    @ToolArg(description = "UUID v7 of the Collection to delete (from `list_collections`).") String collectionAppId
  ) {
    return support.run("collection_delete", () -> {
      contextBridge.bind();
      long ogmId = support.resolveOfType(collectionAppId, "Collection", "collectionAppId");
      collectionService.deleteCollection(ogmId);
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("deleted", true);
      result.put("appId", collectionAppId);
      return support.toJson(result);
    });
  }

  // ── MCP-COV-02-1: DataObject CRUD ────────────────────────────────────────

  @Tool(
    name = "data_object_create",
    description =
      "Create a new DataObject inside a Collection. A DataObject is one unit of " +
      "scientific work — one test run, one process step, one inspection.\n\n" +
      "Returns the created record:\n" +
      "  appId — use this in `get_data_object`, `data_object_update`, and " +
      "           container-attachment calls\n" +
      "  id, name, description, status\n\n" +
      "Status values: DRAFT (default) | IN_REVIEW | READY | PUBLISHED | ARCHIVED | " +
      "NCR_OPEN | ON_HOLD | REJECTED | CERTIFIED | CONCESSION_PENDING.\n\n" +
      "After creation, attach timeseries, file, or structured-data containers via " +
      "the relevant tools. Set Predecessor/Successor links via the lineage tools."
  )
  public String dataObjectCreate(
    @ToolArg(description = "UUID v7 of the parent Collection (from `list_collections`).") String collectionAppId,
    @ToolArg(description = "Name for the new DataObject (e.g. \"TR-016\", \"AFP Layup Run 5\").") String name,
    @ToolArg(required = false, description = "Optional free-text description.") String description,
    @ToolArg(required = false, description = "Initial lifecycle status. Defaults to DRAFT when omitted.") String status
  ) {
    return support.run("data_object_create", () -> {
      contextBridge.bind();
      if (name == null || name.isBlank()) {
        throw McpToolSupport.invalidParams("name is required and must not be blank.");
      }
      long collectionOgmId = support.resolveOfType(collectionAppId, "Collection", "collectionAppId");
      DataObjectIO io = new DataObjectIO();
      io.setName(name.trim());
      if (description != null) io.setDescription(description);
      if (status != null && !status.isBlank()) io.setStatus(status);
      DataObject created = dataObjectService.createDataObject(collectionOgmId, io);
      return support.toJson(dataObjectSummary(created));
    });
  }

  @Tool(
    name = "data_object_update",
    description =
      "Update the name, description, or status of an existing DataObject.\n\n" +
      "Only the fields you supply are changed — omitted fields keep their current " +
      "values. Predecessor/Successor lineage links are preserved unchanged by this " +
      "tool; use the lineage tools to modify them.\n\n" +
      "Status values: DRAFT | IN_REVIEW | READY | PUBLISHED | ARCHIVED | " +
      "NCR_OPEN | ON_HOLD | REJECTED | CERTIFIED | CONCESSION_PENDING.\n\n" +
      "Returns the updated record (same shape as `data_object_create`)."
  )
  public String dataObjectUpdate(
    @ToolArg(description = "UUID v7 of the parent Collection (from `list_collections`).") String collectionAppId,
    @ToolArg(description = "UUID v7 of the DataObject to update (from `list_data_objects` or `get_data_object`).") String dataObjectAppId,
    @ToolArg(required = false, description = "New name. Omit to keep existing.") String name,
    @ToolArg(required = false, description = "New description. Omit to keep existing. Pass empty string to clear.") String description,
    @ToolArg(required = false, description = "New lifecycle status. Omit to keep existing.") String status
  ) {
    return support.run("data_object_update", () -> {
      contextBridge.bind();
      long collectionOgmId = support.resolveOfType(collectionAppId, "Collection", "collectionAppId");
      long doOgmId = support.resolveOfType(dataObjectAppId, "DataObject", "dataObjectAppId");

      // Fetch existing to preserve predecessor links and all other scalar fields.
      // updateDataObject always deletes then re-adds predecessors from the IO, so
      // passing null predecessorIds would clear them — we must carry them through.
      DataObject existing = dataObjectService.getDataObject(collectionOgmId, doOgmId);

      DataObjectIO io = new DataObjectIO();
      io.setName((name != null && !name.isBlank()) ? name.trim() : existing.getName());
      io.setDescription(description != null ? description : existing.getDescription());
      io.setStatus((status != null && !status.isBlank()) ? status : existing.getStatus());
      io.setAttributes(existing.getAttributes() != null ? existing.getAttributes() : new HashMap<>());
      io.setLicense(existing.getLicense());
      io.setAccessRights(existing.getAccessRights());
      io.setEmbargoEndDate(existing.getEmbargoEndDate());

      // Preserve parent link.
      DataObject parent = existing.getParent();
      if (parent != null) io.setParentId(parent.getShepardId());

      // Preserve predecessor links via their shepardIds so updateDataObject
      // can reconstruct them (it deletes all then re-adds from the IO).
      List<DataObject> preds = existing.getPredecessors();
      if (preds != null && !preds.isEmpty()) {
        long[] predIds = preds.stream()
          .mapToLong(p -> p.getShepardId() != null ? p.getShepardId() : 0L)
          .filter(id -> id > 0)
          .toArray();
        io.setPredecessorIds(predIds);
      } else {
        io.setPredecessorIds(new long[0]);
      }

      DataObject updated = dataObjectService.updateDataObject(collectionOgmId, doOgmId, io);
      return support.toJson(dataObjectSummary(updated));
    });
  }

  @Tool(
    name = "data_object_delete",
    description =
      "Permanently delete a DataObject and all its References. The parent " +
      "Collection is not deleted. This operation is irreversible.\n\n" +
      "Returns {\"deleted\": true, \"appId\": \"…\"} on success."
  )
  public String dataObjectDelete(
    @ToolArg(description = "UUID v7 of the parent Collection (from `list_collections`).") String collectionAppId,
    @ToolArg(description = "UUID v7 of the DataObject to delete.") String dataObjectAppId
  ) {
    return support.run("data_object_delete", () -> {
      contextBridge.bind();
      long collectionOgmId = support.resolveOfType(collectionAppId, "Collection", "collectionAppId");
      long doOgmId = support.resolveOfType(dataObjectAppId, "DataObject", "dataObjectAppId");
      dataObjectService.deleteDataObject(collectionOgmId, doOgmId);
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("deleted", true);
      result.put("appId", dataObjectAppId);
      return support.toJson(result);
    });
  }

  // ── Response helpers ──────────────────────────────────────────────────────

  private static Map<String, Object> collectionSummary(Collection c) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("appId", c.getAppId());
    m.put("id", c.getShepardId());
    m.put("name", c.getName());
    m.put("description", c.getDescription());
    m.put("status", c.getStatus());
    return m;
  }

  private static Map<String, Object> dataObjectSummary(DataObject d) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("appId", d.getAppId());
    m.put("id", d.getShepardId());
    m.put("name", d.getName());
    m.put("description", d.getDescription());
    m.put("status", d.getStatus());
    return m;
  }
}
