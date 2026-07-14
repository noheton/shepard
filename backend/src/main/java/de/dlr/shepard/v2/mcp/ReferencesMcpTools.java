package de.dlr.shepard.v2.mcp;

import de.dlr.shepard.v2.references.io.ReferenceV2IO;
import de.dlr.shepard.v2.references.services.ReferencesV2Service;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Reference CRUD tools — aidocs/16 MCP-COV-02-2-REF-CRUD.
 *
 * <p>Five tools covering list / get / create / update / delete for references
 * attached to a DataObject. Dispatches through {@link ReferencesV2Service}
 * (V2CONV-A2), which routes by {@code kind} to the appropriate
 * {@link de.dlr.shepard.v2.references.spi.ReferenceKindHandler}.
 *
 * <p>Binary upload ({@code PUT /v2/references/{appId}/content}) is out of
 * scope — MCP cannot stream binary payloads; use the REST endpoint directly
 * after creating a file reference metadata node via {@code reference_create}.
 * Binary file operations are covered by {@link ContentMcpTools}.
 */
@ApplicationScoped
public class ReferencesMcpTools {

  @Inject
  ReferencesV2Service referencesService;

  @Inject
  McpContextBridge contextBridge;

  @Inject
  McpToolSupport support;

  @Tool(
    name = "list_references",
    description =
      "List all references of a specific kind attached to a DataObject. A Reference\n" +
      "is a typed edge connecting a DataObject to a payload container or external\n" +
      "resource.\n\n" +
      "Kind tokens:\n" +
      "  timeseries — time-windowed link to a TimescaleDB channel set\n" +
      "  file       — singleton FileReference (FR1b) carrying one uploaded file\n" +
      "  uri        — link to an external URI with an optional relationship label\n" +
      "  collection — link to another Collection\n" +
      "  dataobject — link to another DataObject\n" +
      "  structured — link to a structured-data container\n\n" +
      "Returns a JSON array. Each element is a ReferenceV2IO with:\n" +
      "  appId (UUID v7) — use in `get_reference`, `reference_update`, `reference_delete`\n" +
      "  name, kind, type, dataObjectAppId\n" +
      "  payload — kind-specific fields:\n" +
      "    timeseries → {start, end, timeseriesContainerAppId, timeseriesContainerId,\n" +
      "                  timeReference, wallClockOffset, wallClockOffsetSource, qualityScore}\n" +
      "    file       → {file: {name, size, mimeType, sha256, oid}}\n" +
      "    uri        → {uri, relationship}\n\n" +
      "Empty array means the DataObject has no references of that kind."
  )
  public String listReferences(
    @ToolArg(description = "UUID v7 of the parent DataObject. Get from `list_data_objects` or `get_data_object`.") String dataObjectAppId,
    @ToolArg(description = "Kind of references to list: timeseries | file | uri | collection | dataobject | structured.") String kind
  ) {
    return support.run("list_references", () -> {
      contextBridge.bind();
      if (dataObjectAppId == null || dataObjectAppId.isBlank()) {
        throw McpToolSupport.invalidParams("dataObjectAppId is required (UUID v7 appId).");
      }
      if (kind == null || kind.isBlank()) {
        throw McpToolSupport.invalidParams(
          "kind is required: timeseries | file | uri | collection | dataobject | structured."
        );
      }
      List<ReferenceV2IO> refs = referencesService.listByDataObject(kind.toLowerCase(java.util.Locale.ROOT), dataObjectAppId, null);
      return support.toJson(refs);
    });
  }

  @Tool(
    name = "get_reference",
    description =
      "Get the full record for one Reference by its appId. Kind-agnostic — works\n" +
      "for all reference kinds (timeseries, file, uri, collection, …).\n\n" +
      "Returns a ReferenceV2IO with:\n" +
      "  appId, name, kind, type, dataObjectAppId\n" +
      "  payload — kind-specific field map (see `list_references` for payload keys per kind)\n" +
      "  referenceShape — 'singleton' or 'bundle' for file references; null otherwise\n" +
      "  fileKind — e.g. 'urdf', 'pdf', 'krl' for singleton file references\n\n" +
      "Obtain a referenceAppId from:\n" +
      "  • `get_data_object` → containers.timeseries[].referenceAppId\n" +
      "  • `get_data_object` → containers.files[].referenceAppId\n" +
      "  • `list_references` response rows"
  )
  public String getReference(
    @ToolArg(description = "UUID v7 of the Reference. Get from `get_data_object → containers.*[].referenceAppId` or `list_references`.") String referenceAppId
  ) {
    return support.run("get_reference", () -> {
      contextBridge.bind();
      if (referenceAppId == null || referenceAppId.isBlank()) {
        throw McpToolSupport.invalidParams("referenceAppId is required (UUID v7 appId).");
      }
      ReferenceV2IO ref = referencesService.getByAppId(referenceAppId);
      return support.toJson(ref);
    });
  }

  @Tool(
    name = "reference_create",
    description =
      "Create a new Reference of a given kind and attach it to a DataObject.\n" +
      "Dispatches to the correct handler based on `kind`. Supply only the fields\n" +
      "relevant to the chosen kind — unrecognised fields are ignored.\n\n" +
      "Kind-specific required/optional fields:\n\n" +
      "  kind=timeseries:\n" +
      "    timeseriesContainerAppId (required) — UUID v7 of the TimeseriesContainer;\n" +
      "      get from `get_data_object → containers.timeseries[].containerAppId`\n" +
      "    start (required) — time window start as ISO 8601 UTC string,\n" +
      "      e.g. \"2024-06-01T08:00:00Z\" or \"2024-06-01T08:00:00.123456789Z\"\n" +
      "    end   (required) — time window end as ISO 8601 UTC string\n" +
      "    name  (optional) — human-readable label\n\n" +
      "  kind=file (metadata step 1; upload content separately via REST PUT /v2/references/{appId}/content):\n" +
      "    name (required) — filename or label, e.g. 'robot.urdf', 'test-report.pdf'\n\n" +
      "  kind=uri:\n" +
      "    uri          (required) — target URI string\n" +
      "    name         (optional) — label for this link\n" +
      "    relationship (optional) — predicate, e.g. 'isDocumentedBy', 'wasDerivedFrom'\n\n" +
      "Returns the created ReferenceV2IO including the new appId."
  )
  public String referenceCreate(
    @ToolArg(description = "UUID v7 of the parent DataObject.") String dataObjectAppId,
    @ToolArg(description = "Reference kind: timeseries | file | uri | collection | dataobject | structured.") String kind,
    @ToolArg(required = false, description = "Name / label for the reference.") String name,
    @ToolArg(required = false, description = "Start of the time window as ISO 8601 UTC string (timeseries only), e.g. \"2024-06-01T08:00:00Z\".") String start,
    @ToolArg(required = false, description = "End of the time window as ISO 8601 UTC string (timeseries only).") String end,
    @ToolArg(required = false, description = "UUID v7 of the TimeseriesContainer (timeseries only). Get from `get_data_object → containers.timeseries[].containerAppId`.") String timeseriesContainerAppId,
    @ToolArg(required = false, description = "Target URI (uri kind only).") String uri,
    @ToolArg(required = false, description = "Relationship predicate (uri kind only, e.g. 'isDocumentedBy').") String relationship
  ) {
    return support.run("reference_create", () -> {
      contextBridge.bind();
      if (dataObjectAppId == null || dataObjectAppId.isBlank()) {
        throw McpToolSupport.invalidParams("dataObjectAppId is required.");
      }
      if (kind == null || kind.isBlank()) {
        throw McpToolSupport.invalidParams(
          "kind is required: timeseries | file | uri | collection | dataobject | structured."
        );
      }
      Map<String, Object> body = new LinkedHashMap<>();
      if (name != null && !name.isBlank()) body.put("name", name);
      if (start != null && !start.isBlank()) body.put("start", isoToNanos(start));
      if (end != null && !end.isBlank()) body.put("end", isoToNanos(end));
      if (timeseriesContainerAppId != null && !timeseriesContainerAppId.isBlank()) {
        body.put("timeseriesContainerAppId", timeseriesContainerAppId);
      }
      if (uri != null && !uri.isBlank()) body.put("uri", uri);
      if (relationship != null && !relationship.isBlank()) body.put("relationship", relationship);

      ReferenceV2IO created = referencesService.create(kind.toLowerCase(java.util.Locale.ROOT), dataObjectAppId, body);
      return support.toJson(created);
    });
  }

  @Tool(
    name = "reference_update",
    description =
      "Apply a merge-patch to a Reference. Kind-agnostic — works for any reference kind.\n" +
      "Only fields present in the call are updated; absent fields are preserved.\n\n" +
      "Common mutable fields (any kind):\n" +
      "  name (string)\n\n" +
      "Timeseries-only mutable fields:\n" +
      "  start (ISO 8601 UTC string), end (ISO 8601 UTC string)\n" +
      "  timeReference (string), wallClockOffset (long), wallClockOffsetSource (string)\n\n" +
      "URI-only mutable fields:\n" +
      "  uri (string), relationship (string)\n\n" +
      "File references: only name is mutable via this tool; replace the binary content\n" +
      "via REST PUT /v2/references/{appId}/content.\n\n" +
      "Returns the full updated ReferenceV2IO."
  )
  public String referenceUpdate(
    @ToolArg(description = "UUID v7 of the Reference to update. Get from `list_references` or `get_data_object`.") String referenceAppId,
    @ToolArg(required = false, description = "New name / label. Omit to keep existing.") String name,
    @ToolArg(required = false, description = "New start of time window as ISO 8601 UTC string (timeseries only), e.g. \"2024-06-01T08:00:00Z\". Omit to keep existing.") String start,
    @ToolArg(required = false, description = "New end of time window as ISO 8601 UTC string (timeseries only). Omit to keep existing.") String end,
    @ToolArg(required = false, description = "New URI string (uri kind only). Omit to keep existing.") String uri,
    @ToolArg(required = false, description = "New relationship predicate (uri kind only). Omit to keep existing.") String relationship
  ) {
    return support.run("reference_update", () -> {
      contextBridge.bind();
      if (referenceAppId == null || referenceAppId.isBlank()) {
        throw McpToolSupport.invalidParams("referenceAppId is required.");
      }
      Map<String, Object> patch = new LinkedHashMap<>();
      if (name != null && !name.isBlank()) patch.put("name", name);
      if (start != null && !start.isBlank()) patch.put("start", isoToNanos(start));
      if (end != null && !end.isBlank()) patch.put("end", isoToNanos(end));
      if (uri != null && !uri.isBlank()) patch.put("uri", uri);
      if (relationship != null && !relationship.isBlank()) patch.put("relationship", relationship);
      if (patch.isEmpty()) {
        throw McpToolSupport.invalidParams(
          "At least one field (name, start, end, uri, relationship) must be provided."
        );
      }
      ReferenceV2IO updated = referencesService.patchByAppId(referenceAppId, patch);
      return support.toJson(updated);
    });
  }

  @Tool(
    name = "reference_delete",
    description =
      "Delete a Reference by its appId. Kind-agnostic — works for any reference kind.\n" +
      "For file references, this also deletes the stored binary content from object\n" +
      "storage. This operation is irreversible.\n\n" +
      "After deletion the reference no longer appears in\n" +
      "`get_data_object → containers.*[]` or `list_references`.\n\n" +
      "Returns a JSON confirmation object: {\"deleted\": true, \"referenceAppId\": \"...\"}"
  )
  public String referenceDelete(
    @ToolArg(description = "UUID v7 of the Reference to delete. Get from `list_references` or `get_data_object`.") String referenceAppId
  ) {
    return support.run("reference_delete", () -> {
      contextBridge.bind();
      if (referenceAppId == null || referenceAppId.isBlank()) {
        throw McpToolSupport.invalidParams("referenceAppId is required.");
      }
      referencesService.deleteByAppId(referenceAppId);
      Map<String, Object> confirmation = new LinkedHashMap<>();
      confirmation.put("deleted", true);
      confirmation.put("referenceAppId", referenceAppId);
      return support.toJson(confirmation);
    });
  }

  /** Converts an ISO 8601 UTC string to nanoseconds since the Unix epoch. */
  private static long isoToNanos(String iso) {
    try {
      Instant instant = Instant.parse(iso.trim());
      return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
    } catch (DateTimeParseException e) {
      throw McpToolSupport.invalidParams(
        "Invalid ISO 8601 timestamp: \"" + iso + "\". " +
        "Examples: \"2024-06-01T08:00:00Z\" or \"2024-06-01T08:00:00.123456789Z\"."
      );
    }
  }
}
