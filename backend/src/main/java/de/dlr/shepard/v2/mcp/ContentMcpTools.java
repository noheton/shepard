package de.dlr.shepard.v2.mcp;

import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.context.semantic.entities.SemanticRepository;
import de.dlr.shepard.context.semantic.services.SemanticAnnotationService;
import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.data.file.entities.ShepardFile;
import de.dlr.shepard.data.file.services.FileContainerService;
import de.dlr.shepard.data.structureddata.entities.StructuredData;
import de.dlr.shepard.data.structureddata.entities.StructuredDataContainer;
import de.dlr.shepard.data.structureddata.services.StructuredDataContainerService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Phase 1 — content-payload tools for files, structured data, and
 * semantic annotations (aidocs/88).
 *
 * <p>Round out the Phase 1 surface so an agent can enumerate (and later
 * fetch) the non-timeseries payload kinds a DataObject references.
 * Listing-only for now — actual file bytes / structured-data JSON / per-
 * annotation detail are out of scope for Phase 1 (Phase 2 will surface
 * download-by-oid and structured-data record retrieval).
 */
@ApplicationScoped
public class ContentMcpTools {

  @Inject
  FileContainerService fileContainerService;

  @Inject
  StructuredDataContainerService structuredDataContainerService;

  @Inject
  SemanticAnnotationService semanticAnnotationService;

  @Inject
  McpContextBridge contextBridge;

  @Inject
  McpToolSupport support;

  @Tool(
    name = "list_files",
    description =
      "Enumerate every file inside a FileContainer. Get the FileContainer's " +
      "appId from `get_data_object → containers.files[].containerAppId`.\n\n" +
      "Each row carries:\n" +
      "  appId, oid     — handle for the file. {@code oid} is what the legacy\n" +
      "                   download endpoint takes; the appId is the durable\n" +
      "                   fork-native identifier.\n" +
      "  filename, md5  — original name and content hash.\n" +
      "  fileSize       — bytes (may be null on legacy uploads).\n" +
      "  providerId     — backing storage provider id (S3 / MinIO / local).\n" +
      "  createdAt      — upload timestamp.\n\n" +
      "Phase 1 limitation: file content (bytes) is NOT retrievable through MCP. " +
      "To download, point a client at " +
      "`/shepard/api/fileContainers/{numericContainerId}/files/{oid}/file` with the " +
      "same Bearer token. A native download MCP tool is on the Phase 2 roadmap."
  )
  public String listFiles(
    @ToolArg(description = "UUID v7 of the FileContainer (from `get_data_object → containers.files[].containerAppId`). NOT a TimeseriesContainer or StructuredDataContainer appId.") String containerAppId
  ) {
    return support.run("list_files", () -> {
      contextBridge.bind();
      // Type-check before hitting the service so wrong-kind appIds give a
      // clean -32602 instead of a downstream null-deref / 404 ambiguity.
      support.resolveOfType(containerAppId, "FileContainer", "containerAppId");
      FileContainer container = fileContainerService.getContainerByAppId(containerAppId);
      if (container == null) {
        throw McpToolSupport.invalidParams("FileContainer not found: " + containerAppId);
      }

      List<ShepardFile> files = container.getFiles() != null ? container.getFiles() : List.of();
      List<Map<String, Object>> result = new ArrayList<>(files.size());
      for (ShepardFile f : files) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("appId", f.getAppId());
        row.put("oid", f.getOid());
        row.put("filename", f.getFilename());
        row.put("md5", f.getMd5());
        row.put("fileSize", f.getFileSize());
        row.put("providerId", f.getProviderId());
        row.put("createdAt", f.getCreatedAt());
        result.add(row);
      }
      return support.toJson(result);
    });
  }

  @Tool(
    name = "list_structured_data",
    description =
      "Enumerate every StructuredData document inside a StructuredDataContainer. " +
      "Get the container's appId from `get_data_object → containers.structuredData[].containerAppId`.\n\n" +
      "StructuredData is shepard's slot for free-form JSON-shaped payloads — test " +
      "matrices, calibration tables, inspection findings, anything an agent might " +
      "want to read as structured rows rather than a binary file.\n\n" +
      "Each row carries:\n" +
      "  appId, oid    — handle for the document.\n" +
      "  name          — caller-supplied label.\n" +
      "  createdAt     — write timestamp.\n\n" +
      "Phase 1 limitation: the document body (the actual JSON) is NOT returned by " +
      "this tool — only the index. To fetch the body, use the legacy " +
      "`/shepard/api/structuredDataContainers/{numericId}/structuredDatas/{oid}` " +
      "endpoint. A native body-retrieval MCP tool is on the Phase 2 roadmap."
  )
  public String listStructuredData(
    @ToolArg(description = "UUID v7 of the StructuredDataContainer (from `get_data_object → containers.structuredData[].containerAppId`). NOT a TimeseriesContainer or FileContainer appId.") String containerAppId
  ) {
    return support.run("list_structured_data", () -> {
      contextBridge.bind();
      support.resolveOfType(containerAppId, "StructuredDataContainer", "containerAppId");
      StructuredDataContainer container = structuredDataContainerService.getContainerByAppId(containerAppId);
      if (container == null) {
        throw McpToolSupport.invalidParams("StructuredDataContainer not found: " + containerAppId);
      }

      List<StructuredData> records = container.getStructuredDatas() != null ? container.getStructuredDatas() : List.of();
      List<Map<String, Object>> result = new ArrayList<>(records.size());
      for (StructuredData sd : records) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("appId", sd.getAppId());
        row.put("oid", sd.getOid());
        row.put("name", sd.getName());
        row.put("createdAt", sd.getCreatedAt());
        result.add(row);
      }
      return support.toJson(result);
    });
  }

  @Tool(
    name = "list_annotations",
    description =
      "List the semantic annotations attached to a DataObject (or any other " +
      "shepard entity that carries them).\n\n" +
      "Annotations encode controlled-vocabulary metadata: each carries a property " +
      "(e.g. \"propellant\") and a value (e.g. \"LOX/LH2\"), each side optionally " +
      "anchored to an IRI in a known ontology. Quantified annotations also carry " +
      "a numeric value and a unit IRI (e.g. {numericValue: 25, unitIRI: " +
      "\"http://qudt.org/vocab/unit/KN\"}).\n\n" +
      "Use this to read the structured side of a DataObject — the part beyond " +
      "the free-text `attributes` map you got back from `get_data_object`. " +
      "Annotations bridge to FAIR vocabularies (CHAMEO, QUDT, Material OWL, …).\n\n" +
      "Each row:\n" +
      "  appId             — annotation identifier.\n" +
      "  propertyName, propertyIRI       — what is being described.\n" +
      "  valueName, valueIRI             — controlled-vocabulary value (may be null).\n" +
      "  numericValue, unitIRI           — quantitative value + unit (may be null).\n" +
      "  propertyRepository, valueRepository — which ontology each IRI lives in.\n\n" +
      "Returns an empty array if the DataObject has no annotations yet."
  )
  public String listAnnotations(
    @ToolArg(description = "UUID v7 of the DataObject (from `list_data_objects` or `get_data_object → appId`).") String dataObjectAppId
  ) {
    return support.run("list_annotations", () -> {
      contextBridge.bind();
      long ogmId = support.resolveOfType(dataObjectAppId, "DataObject", "dataObjectAppId");

      List<SemanticAnnotation> annotations = semanticAnnotationService.getAllAnnotationsByShepardId(ogmId);
      List<Map<String, Object>> result = new ArrayList<>(annotations.size());
      for (SemanticAnnotation a : annotations) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("appId", a.getAppId());
        row.put("propertyName", a.getPropertyName());
        row.put("propertyIRI", a.getPropertyIRI());
        row.put("valueName", a.getValueName());
        row.put("valueIRI", a.getValueIRI());
        row.put("numericValue", a.getNumericValue());
        row.put("unitIRI", a.getUnitIRI());
        row.put("propertyRepository", repoName(a.getPropertyRepository()));
        row.put("valueRepository", repoName(a.getValueRepository()));
        result.add(row);
      }
      return support.toJson(result);
    });
  }

  private static String repoName(SemanticRepository repo) {
    return repo == null ? null : repo.getName();
  }
}
