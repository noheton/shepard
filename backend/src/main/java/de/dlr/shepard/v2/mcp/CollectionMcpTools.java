package de.dlr.shepard.v2.mcp;

import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.v2.dataobject.io.DataObjectDetailV2IO;
import de.dlr.shepard.v2.dataobject.io.DataObjectListItemV2IO;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
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
      "  id (long) — legacy numeric ID (for the upstream /shepard/api/… endpoints)\n" +
      "  name, description (free-text from the Collection owner)\n" +
      "  status (lifecycle: DRAFT|IN_REVIEW|READY|PUBLISHED|ARCHIVED, or null)\n\n" +
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
        row.put("id", c.getId());
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
      "  id (long), name, status\n" +
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
        row.put("id", item.getId());
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
      "    \"timeseries\":     [{containerAppId, containerName, containerId, referenceId}, …],\n" +
      "    \"files\":          [{containerAppId, containerName, containerId, referenceId}, …],\n" +
      "    \"structuredData\": [{containerAppId, containerName, containerId, referenceId}, …]\n" +
      "  },\n" +
      "  \"predecessorSummaries\": [{appId, id, name, status}, …],\n" +
      "  \"successorSummaries\":   [...],\n" +
      "  \"childSummaries\":       [...],\n" +
      "  \"parentSummary\":        {appId, id, name, status} | null\n" +
      "}\n\n" +
      "Common next calls:\n" +
      "  • For timeseries: take a `containers.timeseries[].containerAppId` and call " +
      "    `list_channels(containerAppId)` to enumerate the 5-tuple channel " +
      "    descriptors. (Channel POINTS are not yet exposed in Phase 1 — see " +
      "    the legacy /shepard/api/… endpoints with `containers.timeseries[].containerId`.)\n" +
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
}
