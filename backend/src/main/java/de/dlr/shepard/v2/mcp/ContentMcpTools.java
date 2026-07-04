package de.dlr.shepard.v2.mcp;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.common.mongoDB.NamedInputStream;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.references.file.entities.FileReference;
import de.dlr.shepard.context.references.file.services.SingletonFileReferenceService;
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
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
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

  /**
   * Hard cap on {@code file_upload} base64 payload size (decoded bytes).
   * 10 MiB — anything larger should use the multipart REST endpoint
   * {@code POST /v2/files} directly; JSON-RPC over MCP is not the right
   * transport for large payloads.
   */
  static final int FILE_UPLOAD_MAX_BYTES = 10 * 1024 * 1024;

  @Inject
  FileContainerService fileContainerService;

  @Inject
  StructuredDataContainerService structuredDataContainerService;

  @Inject
  SemanticAnnotationService semanticAnnotationService;

  @Inject
  SingletonFileReferenceService singletonFileReferenceService;

  @Inject
  PermissionsService permissionsService;

  @Inject
  AuthenticationContext authenticationContext;

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
      "For singleton FR1b FileReferences (one-file-per-reference shape — the default " +
      "for new uploads per CLAUDE.md), use `file_upload` to create and `file_content` " +
      "to download the bytes — both bypass the FileContainer envelope entirely. The " +
      "tool here lists files inside the legacy bundle shape (`FileContainer` →\n" +
      "`FileBundleReference`)."
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
      "Pagination: default page=0, pageSize=50 (max 200). Call again with page=1 " +
      "if the returned array has exactly `pageSize` rows (more pages likely exist).\n\n" +
      "Each row:\n" +
      "  appId             — annotation identifier.\n" +
      "  propertyName, propertyIRI       — what is being described.\n" +
      "  valueName, valueIRI             — controlled-vocabulary value (may be null).\n" +
      "  numericValue, unitIRI           — quantitative value + unit (may be null).\n" +
      "  propertyRepository, valueRepository — which ontology each IRI lives in.\n" +
      "  subjectKind       — (v6) kind of the annotated entity (e.g. 'DataObject').\n" +
      "  subjectAppId      — (v6) appId of the annotated entity.\n" +
      "  vocabularyId      — (v6) appId of the controlling Vocabulary (may be null).\n" +
      "  sourceMode        — (v6) 'human' | 'ai' | 'collaborative' (may be null).\n" +
      "  confidence        — (v6) AI confidence in [0.0, 1.0] (may be null).\n\n" +
      "Returns an empty array if the DataObject has no annotations yet."
  )
  public String listAnnotations(
    @ToolArg(description = "UUID v7 of the DataObject (from `list_data_objects` or `get_data_object → appId`).") String dataObjectAppId,
    @ToolArg(required = false, description = "Zero-based page index. Default 0.") Integer page,
    @ToolArg(required = false, description = "Page size, capped at 200. Default 50.") Integer pageSize
  ) {
    return support.run("list_annotations", () -> {
      contextBridge.bind();
      long ogmId = support.resolveOfType(dataObjectAppId, "DataObject", "dataObjectAppId");

      int safePage = page != null ? Math.max(page, 0) : 0;
      int safeSize = pageSize != null ? Math.min(Math.max(pageSize, 1), 200) : 50;

      List<SemanticAnnotation> annotations = semanticAnnotationService.getAllAnnotationsByShepardId(ogmId);
      int total = annotations.size();
      int from = Math.min(safePage * safeSize, total);
      int to = Math.min(from + safeSize, total);
      List<SemanticAnnotation> page1 = annotations.subList(from, to);

      List<Map<String, Object>> result = new ArrayList<>(page1.size());
      for (SemanticAnnotation a : page1) {
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
        // SEMA-V6-006 — v6 fields added to list_annotations response
        row.put("subjectKind", a.getSubjectKind());
        row.put("subjectAppId", a.getSubjectAppId());
        row.put("vocabularyId", a.getVocabularyId());
        row.put("sourceMode", a.getSourceMode());
        row.put("confidence", a.getConfidence());
        result.add(row);
      }
      return support.toJson(result);
    });
  }

  private static String repoName(SemanticRepository repo) {
    return repo == null ? null : repo.getName();
  }

  // ─── file_upload (MCP-COV-04) ───────────────────────────────────────────────

  @Tool(
    name = "file_upload",
    description =
      "Upload a small file and attach it to a DataObject as a singleton FR1b " +
      "FileReference (per the CLAUDE.md `singleton FileReference for one-file uploads` " +
      "rule). Returns the new FileReference appId; that appId resolves directly to the " +
      "bytes via `file_content`.\n\n" +
      "Bytes travel as a **base64-encoded string** inside the JSON-RPC envelope. The " +
      "decoded payload is capped at " + FILE_UPLOAD_MAX_BYTES + " bytes (~10 MiB); " +
      "anything larger must use the two-step REST API directly: " +
      "`POST /v2/references?kind=file&dataObjectAppId=...` with JSON body `{\"name\":\"...\"}` " +
      "to create the metadata node, then " +
      "`PUT /v2/references/{appId}/content?filename=...` with `application/octet-stream` body — " +
      "JSON-RPC is not a streaming transport.\n\n" +
      "Parameters:\n" +
      "  parentDataObjectAppId — UUID v7 of the DataObject the new Reference attaches to.\n" +
      "  name                  — human-readable display name for the Reference.\n" +
      "  filename              — original filename (preserved in the embedded ShepardFile;\n" +
      "                          used as `name` when `name` is omitted).\n" +
      "  contentBase64         — base64-encoded file bytes (required).\n" +
      "  mimeType              — informational; not currently stored (annotation hook).\n\n" +
      "Permission: Write on the parent DataObject (inherited from its Collection).\n\n" +
      "Response: {appId, name, dataObjectAppId, filename, fileSize, md5}."
  )
  public String fileUpload(
    @ToolArg(description = "UUID v7 of the DataObject to attach the new singleton FileReference to.") String parentDataObjectAppId,
    @ToolArg(required = false, description = "Display name for the Reference (defaults to filename when omitted).") String name,
    @ToolArg(description = "Original filename of the upload (used as display name when name is omitted).") String filename,
    @ToolArg(description = "File bytes as a base64-encoded string. Hard cap " + FILE_UPLOAD_MAX_BYTES + " bytes (decoded).") String contentBase64,
    @ToolArg(required = false, description = "Informational MIME type; not yet stored. Use a semantic annotation on the returned appId to record it durably.") String mimeType
  ) {
    return support.run("file_upload", () -> {
      contextBridge.bind();

      if (parentDataObjectAppId == null || parentDataObjectAppId.isBlank()) {
        throw McpToolSupport.invalidParams("parentDataObjectAppId is required.");
      }
      if (filename == null || filename.isBlank()) {
        throw McpToolSupport.invalidParams("filename is required (used as display name when name is omitted).");
      }
      if (contentBase64 == null || contentBase64.isBlank()) {
        throw McpToolSupport.invalidParams("contentBase64 is required (base64-encoded file bytes).");
      }

      byte[] decoded;
      try {
        decoded = Base64.getDecoder().decode(contentBase64.trim());
      } catch (IllegalArgumentException iae) {
        throw McpToolSupport.invalidParams("contentBase64 is not valid base64: " + iae.getMessage());
      }
      if (decoded.length == 0) {
        throw McpToolSupport.invalidParams("contentBase64 decoded to zero bytes.");
      }
      if (decoded.length > FILE_UPLOAD_MAX_BYTES) {
        throw McpToolSupport.invalidParams(
          "Decoded payload is " + decoded.length + " bytes; max " + FILE_UPLOAD_MAX_BYTES +
          " for MCP. For larger uploads use the two-step REST API: " +
          "POST /v2/references?kind=file&dataObjectAppId=... (JSON body {name}) " +
          "then PUT /v2/references/{appId}/content?filename=... (octet-stream body)."
        );
      }

      String caller = authenticationContext.getCurrentUserName();
      if (caller == null || caller.isBlank()) {
        throw new NotAuthorizedException("Authentication required for file_upload.");
      }

      Long parentOgmId = singletonFileReferenceService.getDataObjectOgmId(parentDataObjectAppId);
      if (parentOgmId == null) {
        throw McpToolSupport.invalidParams("No DataObject found for parentDataObjectAppId=" + parentDataObjectAppId);
      }
      if (!permissionsService.isAccessTypeAllowedForUser(parentOgmId, AccessType.Write, caller)) {
        throw new ForbiddenException(
          "Caller " + caller + " lacks Write permission on DataObject " + parentDataObjectAppId
        );
      }

      String referenceName = (name != null && !name.isBlank()) ? name : filename;
      FileReference created;
      try (InputStream is = new ByteArrayInputStream(decoded)) {
        created = singletonFileReferenceService.createSingleton(
          parentDataObjectAppId,
          referenceName,
          filename,
          is,
          (long) decoded.length
        );
      } catch (IOException ioe) {
        // ByteArrayInputStream.close() doesn't throw, but the compiler doesn't know that.
        throw new RuntimeException("Failed to close in-memory upload stream", ioe);
      } catch (NotFoundException nfe) {
        throw McpToolSupport.invalidParams("Parent DataObject vanished mid-upload: " + parentDataObjectAppId);
      } catch (BadRequestException bre) {
        throw McpToolSupport.invalidParams(bre.getMessage() == null ? "Upload rejected" : bre.getMessage());
      }

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("appId", created.getAppId());
      result.put("name", created.getName());
      result.put("dataObjectAppId", parentDataObjectAppId);
      ShepardFile f = created.getFile();
      if (f != null) {
        result.put("filename", f.getFilename());
        result.put("fileSize", f.getFileSize());
        result.put("md5", f.getMd5());
      } else {
        // Degenerate row — service contract says this shouldn't happen on the
        // happy path, but we project an explicit null so the agent can detect it.
        result.put("filename", null);
        result.put("fileSize", null);
        result.put("md5", null);
      }
      if (mimeType != null && !mimeType.isBlank()) {
        // Echo the caller-supplied mimeType so an agent can confirm the value;
        // it isn't yet stored on the Reference (see tool description).
        result.put("mimeTypeHint", mimeType);
      }
      return support.toJson(result);
    });
  }

  // ─── file_content (MCP-COV-04) ──────────────────────────────────────────────

  @Tool(
    name = "file_content",
    description =
      "Download the bytes of a singleton FR1b FileReference by appId. Returns the bytes " +
      "as a base64-encoded string inside the JSON-RPC envelope.\n\n" +
      "Hard cap " + FILE_UPLOAD_MAX_BYTES + " bytes (decoded). When the stored file " +
      "exceeds the cap, the tool returns an error pointing the caller at the multipart " +
      "REST endpoint `GET /v2/files/{appId}/content` (which supports Range requests).\n\n" +
      "Permission: Read on the parent DataObject (inherited from its Collection).\n\n" +
      "Response: {appId, filename, fileSize, contentBase64}."
  )
  public String fileContent(
    @ToolArg(description = "UUID v7 of the singleton FileReference (returned by file_upload, or `get_data_object → fileReferenceAppIds[]` for FR1b shapes).") String fileReferenceAppId
  ) {
    return support.run("file_content", () -> {
      contextBridge.bind();

      if (fileReferenceAppId == null || fileReferenceAppId.isBlank()) {
        throw McpToolSupport.invalidParams("fileReferenceAppId is required.");
      }

      FileReference ref = singletonFileReferenceService.getByAppId(fileReferenceAppId);
      if (ref == null) {
        throw McpToolSupport.invalidParams("No singleton FileReference found for appId=" + fileReferenceAppId);
      }
      if (ref.isDeleted()) {
        throw McpToolSupport.invalidParams("FileReference is soft-deleted: " + fileReferenceAppId);
      }

      String caller = authenticationContext.getCurrentUserName();
      if (caller == null || caller.isBlank()) {
        throw new NotAuthorizedException("Authentication required for file_content.");
      }
      // Check Read against the parent DataObject (same pattern as
      // FileReferenceV2Rest.checkAccess on the REST side).
      Long parentOgmId = ref.getDataObject() == null ? null : ref.getDataObject().getId();
      if (parentOgmId == null) {
        throw McpToolSupport.invalidParams(
          "FileReference " + fileReferenceAppId + " has no parent DataObject link (graph inconsistency)."
        );
      }
      if (!permissionsService.isAccessTypeAllowedForUser(parentOgmId, AccessType.Read, caller)) {
        throw new ForbiddenException(
          "Caller " + caller + " lacks Read permission on the parent DataObject of " + fileReferenceAppId
        );
      }

      NamedInputStream payload;
      try {
        payload = singletonFileReferenceService.getPayload(fileReferenceAppId);
      } catch (NotFoundException nfe) {
        throw McpToolSupport.invalidParams("Underlying bytes missing for " + fileReferenceAppId);
      }

      long size = payload.getSize() == null ? -1 : payload.getSize();
      if (size > FILE_UPLOAD_MAX_BYTES) {
        throw McpToolSupport.invalidParams(
          "Stored file is " + size + " bytes; max " + FILE_UPLOAD_MAX_BYTES + " for MCP. " +
          "Use the REST endpoint GET /v2/files/" + fileReferenceAppId + "/content " +
          "(supports Range requests) instead."
        );
      }

      byte[] bytes;
      try (InputStream in = payload.getInputStream()) {
        bytes = in.readNBytes(FILE_UPLOAD_MAX_BYTES + 1);
      } catch (IOException ioe) {
        throw new RuntimeException("Failed to read file content for " + fileReferenceAppId, ioe);
      }
      if (bytes.length > FILE_UPLOAD_MAX_BYTES) {
        // Defence in depth: the stored-size check above is the canonical guard,
        // but in degenerate rows fileSize may be null. This branch catches the
        // null-size + actually-big case.
        throw McpToolSupport.invalidParams(
          "Stored file exceeds " + FILE_UPLOAD_MAX_BYTES + " bytes. " +
          "Use the REST endpoint GET /v2/files/" + fileReferenceAppId + "/content instead."
        );
      }

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("appId", fileReferenceAppId);
      result.put("filename", payload.getName());
      result.put("fileSize", bytes.length);
      result.put("contentBase64", Base64.getEncoder().encodeToString(bytes));
      return support.toJson(result);
    });
  }
}
