package de.dlr.shepard.v2.file.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.mongoDB.NamedInputStream;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.references.file.entities.FileReference;
import de.dlr.shepard.context.references.file.services.SingletonFileReferenceService;
import de.dlr.shepard.v2.file.io.FileReferenceV2IO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

/**
 * {@code /v2/files/...} REST surface for the FR1b singleton
 * {@link FileReference} (see {@code aidocs/53 §1.8.4}).
 *
 * <p>Routes:
 * <ul>
 *   <li>{@code POST   /v2/files} — upload one file. Multipart body
 *       with a single {@code file} part; requires a
 *       {@code parentDataObjectAppId} query parameter so the new
 *       Reference attaches to a DataObject.</li>
 *   <li>{@code GET    /v2/files/{appId}} — singleton metadata.</li>
 *   <li>{@code GET    /v2/files/{appId}/content} — the byte stream.
 *       Range-request capable ({@code Range: bytes=...}).</li>
 *   <li>{@code PATCH  /v2/files/{appId}} — RFC 7396 merge-patch on
 *       the {@code name} field. Other fields are immutable in FR1b.</li>
 *   <li>{@code DELETE /v2/files/{appId}} — hard-delete the Reference
 *       and its underlying bytes.</li>
 * </ul>
 *
 * <p>Permissions: every endpoint resolves the parent DataObject from
 * the singleton, then asks {@link PermissionsService} (same code
 * path as the upstream API). Returns 401 unauthenticated, 403 on
 * permission denied, 404 on missing singleton.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/files")
@RequestScoped
@Tag(name = "File references (v2 singleton)")
public class FileReferenceV2Rest {

  @Inject
  SingletonFileReferenceService singletonService;

  @Inject
  PermissionsService permissionsService;

  @Inject
  ObjectMapper objectMapper;

  // ─── upload ───────────────────────────────────────────────────────────────

  @POST
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @Operation(
    summary = "Upload one file and create a singleton FileReference attached to a DataObject.",
    description =
      "Accepts a `multipart/form-data` body with a single `file` part and creates a " +
      "`:FileReference` (FR1b singleton shape) in the active storage backend. The " +
      "Reference is immediately linked to the DataObject identified by the required " +
      "`parentDataObjectAppId` query parameter (UUID v7 of the DataObject).\n\n" +
      "Query parameters: `parentDataObjectAppId` (required, UUID v7) — the DataObject " +
      "to attach the new Reference to. `name` (optional, string) — the human-readable " +
      "display name for the Reference; defaults to the uploaded filename when omitted.\n\n" +
      "Form field: `file` (required, binary) — the file bytes.\n\n" +
      "Auth: Write permission on the parent DataObject (inherited from its Collection). " +
      "Permission is checked against the DataObject OGM id resolved from `parentDataObjectAppId` " +
      "before any bytes are stored.\n\n" +
      "Side effects: bytes are stored in the active storage backend " +
      "(GridFS or S3 depending on `shepard.storage.provider`). A `:FileReference` node " +
      "and its relationship to the DataObject are written to Neo4j. " +
      "`ProvenanceCaptureFilter` records a `CREATE` Activity.\n\n" +
      "Next step: `GET /v2/files/{appId}` to read metadata, or " +
      "`GET /v2/files/{appId}/content` to download the bytes."
  )
  @APIResponse(
    responseCode = "201",
    description = "FileReference created; body contains the new entity including its minted appId.",
    content = @Content(schema = @Schema(implementation = FileReferenceV2IO.class))
  )
  @APIResponse(responseCode = "400", description = "Missing required `file` form part, or `parentDataObjectAppId` is absent or blank.")
  @APIResponse(responseCode = "401", description = "Authentication required (no JWT or X-API-KEY).")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No DataObject with that appId.")
  public Response createSingleton(
    @QueryParam("parentDataObjectAppId") String parentDataObjectAppId,
    @QueryParam("name") String name,
    @RestForm("file") FileUpload upload,
    @Context SecurityContext securityContext
  ) {
    String caller = callerOrNull(securityContext);
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();
    if (parentDataObjectAppId == null || parentDataObjectAppId.isBlank()) {
      return Response.status(Response.Status.BAD_REQUEST)
        .entity("parentDataObjectAppId query parameter is required")
        .build();
    }
    if (upload == null || upload.uploadedFile() == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("file part is required").build();
    }

    // Permission check against the parent DataObject — same shape as
    // the bundle Rest. The singleton entity doesn't exist yet (we're
    // about to create it), so we resolve the parent OGM id from the
    // appId via the service's resolver helper.
    Long parentOgmId = singletonService.getDataObjectOgmId(parentDataObjectAppId);
    if (parentOgmId == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    if (!permissionsService.isAccessTypeAllowedForUser(parentOgmId, AccessType.Write, caller)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    String referenceName = (name != null && !name.isBlank()) ? name : upload.fileName();
    File uploaded = upload.uploadedFile().toFile();
    try (InputStream is = new FileInputStream(uploaded)) {
      FileReference created = singletonService.createSingleton(
        parentDataObjectAppId,
        referenceName,
        upload.fileName(),
        is
      );
      return Response.status(Response.Status.CREATED).entity(new FileReferenceV2IO(created)).build();
    } catch (NotFoundException nfe) {
      return Response.status(Response.Status.NOT_FOUND).build();
    } catch (BadRequestException bre) {
      return Response.status(Response.Status.BAD_REQUEST).entity(bre.getMessage()).build();
    } catch (IOException ioe) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ioe.getMessage()).build();
    }
  }

  // ─── metadata ─────────────────────────────────────────────────────────────

  @GET
  @Path("/{appId}")
  @Operation(
    summary = "Get singleton FileReference metadata by appId.",
    description =
      "Returns the `FileReferenceV2IO` metadata record for the FR1b singleton `:FileReference` " +
      "identified by `appId` (UUID v7). The response includes: `appId` (UUID v7), `name` " +
      "(human-readable display name), `dataObjectId` (OGM id of the parent DataObject), " +
      "`type`, `createdAt`, `createdBy`, `updatedAt`, `updatedBy`, `revision`; and an " +
      "embedded `file` object (nullable for degenerate rows) containing `filename` " +
      "(original upload name), `md5` checksum, and `fileSize` (bytes, nullable for files " +
      "uploaded before the FB1a size-capture migration).\n\n" +
      "Auth: Read permission on the parent DataObject (inherited from its Collection). " +
      "The parent DataObject is resolved from the Reference's graph relationship; 404 is " +
      "returned both when the Reference is unknown and when its DataObject link is missing " +
      "(graph inconsistency treated as not-found).\n\n" +
      "Next step: `GET /v2/files/{appId}/content` to download the file bytes, or " +
      "`PATCH /v2/files/{appId}` to rename the Reference."
  )
  @APIResponse(
    responseCode = "200",
    description = "FileReferenceV2IO metadata record for the singleton Reference.",
    content = @Content(schema = @Schema(implementation = FileReferenceV2IO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required (no JWT or X-API-KEY).")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No singleton FileReference with that appId.")
  public Response getSingleton(
    @PathParam("appId") String appId,
    @Context SecurityContext securityContext
  ) {
    FileReference ref = singletonService.getByAppId(appId);
    if (ref == null) return Response.status(Response.Status.NOT_FOUND).build();
    Response gate = checkAccess(ref, AccessType.Read, securityContext);
    if (gate != null) return gate;
    return Response.ok(new FileReferenceV2IO(ref)).build();
  }

  // ─── content ──────────────────────────────────────────────────────────────

  @GET
  @Path("/{appId}/content")
  @Produces({ MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_JSON })
  @Operation(
    summary = "Download the raw bytes of a singleton FileReference.",
    description =
      "Streams the file bytes stored for the FR1b singleton `:FileReference` identified " +
      "by `appId` (UUID v7). The response `Content-Disposition` header is set to " +
      "`attachment; filename=\"<originalName>\"` and `Content-Length` is set to the " +
      "stored byte count so the caller can show a progress bar.\n\n" +
      "Range requests: a single `Range: bytes=START-END` header is honoured (single-range " +
      "only; FR1b does not support multi-range or suffix-range). A valid range returns " +
      "HTTP 206 with `Content-Range` and `Accept-Ranges: bytes` headers. An unsatisfiable " +
      "range (start ≥ total) returns 416 with `Content-Range: bytes */TOTAL`.\n\n" +
      "Auth: Read permission on the parent DataObject (inherited from its Collection). " +
      "The `Accept-Ranges` header is always included in 200 responses so clients can " +
      "probe range support.\n\n" +
      "Note: the underlying bytes live in the active storage backend (GridFS or S3). " +
      "This endpoint proxies them through the shepard JVM; for large-file scenarios " +
      "prefer `POST /v2/collections/{appId}/export-url` (presigned download) where " +
      "supported."
  )
  @APIResponse(
    responseCode = "200",
    description = "Full file bytes as `application/octet-stream`; `Content-Disposition` and `Content-Length` are set.",
    content = @Content(
      mediaType = MediaType.APPLICATION_OCTET_STREAM,
      schema = @Schema(type = SchemaType.STRING, format = "binary")
    )
  )
  @APIResponse(
    responseCode = "206",
    description = "Partial content — single-range `Range: bytes=START-END` was honoured; `Content-Range` header is set."
  )
  @APIResponse(responseCode = "401", description = "Authentication required (no JWT or X-API-KEY).")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No singleton FileReference with that appId, or its backing bytes are missing.")
  @APIResponse(responseCode = "416", description = "Range not satisfiable — start offset is beyond the file size; `Content-Range: bytes */TOTAL` is included.")
  public Response getContent(
    @PathParam("appId") String appId,
    @HeaderParam("Range") String rangeHeader,
    @Context SecurityContext securityContext
  ) {
    FileReference ref = singletonService.getByAppId(appId);
    if (ref == null) return Response.status(Response.Status.NOT_FOUND).build();
    Response gate = checkAccess(ref, AccessType.Read, securityContext);
    if (gate != null) return gate;

    NamedInputStream payload;
    try {
      payload = singletonService.getPayload(appId);
    } catch (NotFoundException nfe) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    long total = payload.getSize();
    String filename = payload.getName();

    // No range header → full body.
    if (rangeHeader == null || rangeHeader.isBlank()) {
      return Response.ok(payload.getInputStream(), MediaType.APPLICATION_OCTET_STREAM)
        .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
        .header("Content-Length", total)
        .header("Accept-Ranges", "bytes")
        .build();
    }

    // Parse "bytes=START-END" (END optional). Multi-range and
    // suffix-range ("bytes=-N") not supported in FR1b.
    long[] range = parseRange(rangeHeader, total);
    if (range == null) {
      return Response.status(Response.Status.REQUESTED_RANGE_NOT_SATISFIABLE)
        .header("Content-Range", "bytes */" + total)
        .build();
    }
    long start = range[0];
    long end = range[1];
    long length = end - start + 1;
    InputStream stream = payload.getInputStream();
    StreamingOutput ranged = (OutputStream out) -> {
      try (InputStream in = stream) {
        long skipped = 0;
        while (skipped < start) {
          long s = in.skip(start - skipped);
          if (s <= 0) break;
          skipped += s;
        }
        byte[] buf = new byte[8192];
        long remaining = length;
        while (remaining > 0) {
          int toRead = (int) Math.min(buf.length, remaining);
          int n = in.read(buf, 0, toRead);
          if (n < 0) break;
          out.write(buf, 0, n);
          remaining -= n;
        }
      }
    };
    return Response.status(Response.Status.PARTIAL_CONTENT)
      .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
      .header("Content-Length", length)
      .header("Content-Range", "bytes " + start + "-" + end + "/" + total)
      .header("Accept-Ranges", "bytes")
      .entity(ranged)
      .type(MediaType.APPLICATION_OCTET_STREAM)
      .build();
  }

  // ─── patch ────────────────────────────────────────────────────────────────

  @PATCH
  @Path("/{appId}")
  @Consumes({ "application/merge-patch+json", MediaType.APPLICATION_JSON })
  @Operation(
    summary = "RFC 7396 merge-patch on a singleton FileReference.",
    description =
      "Applies a partial update to the `:FileReference` identified by `appId` (UUID v7). " +
      "In FR1b the only mutable field via PATCH is `name` (the human-readable display " +
      "name shown in the UI). The embedded `file` object fields (`filename`, `md5`, " +
      "`fileSize`) are immutable after upload — re-upload to replace bytes.\n\n" +
      "Example body: `{\"name\": \"calibration-run-2026\"}`.\n\n" +
      "Setting `name` to `null` or an empty string returns 400. Absent fields are left " +
      "unchanged per RFC 7396 semantics.\n\n" +
      "Content-Type: prefer `application/merge-patch+json`; `application/json` is also " +
      "accepted.\n\n" +
      "Auth: Write permission on the parent DataObject (inherited from its Collection).\n\n" +
      "Side effects: `ProvenanceCaptureFilter` records an `UPDATE` Activity."
  )
  @APIResponse(
    responseCode = "200",
    description = "Updated FileReferenceV2IO with the new name applied.",
    content = @Content(schema = @Schema(implementation = FileReferenceV2IO.class))
  )
  @APIResponse(responseCode = "400", description = "Patch body is not a JSON object, or `name` is null or blank.")
  @APIResponse(responseCode = "401", description = "Authentication required (no JWT or X-API-KEY).")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No singleton FileReference with that appId.")
  public Response patchSingleton(
    @PathParam("appId") String appId,
    @RequestBody(required = true, content = @Content(mediaType = "application/merge-patch+json")) JsonNode body,
    @Context SecurityContext securityContext
  ) {
    if (body == null || !body.isObject()) {
      return Response.status(Response.Status.BAD_REQUEST).entity("PATCH body must be a JSON object").build();
    }
    FileReference ref = singletonService.getByAppId(appId);
    if (ref == null) return Response.status(Response.Status.NOT_FOUND).build();
    Response gate = checkAccess(ref, AccessType.Write, securityContext);
    if (gate != null) return gate;

    try {
      Map<String, Object> patch = jsonNodeToMap(body);
      FileReference updated = singletonService.patchSingleton(appId, patch);
      return Response.ok(new FileReferenceV2IO(updated)).build();
    } catch (BadRequestException bre) {
      return Response.status(Response.Status.BAD_REQUEST).entity(bre.getMessage()).build();
    } catch (NotFoundException nfe) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
  }

  // ─── delete ───────────────────────────────────────────────────────────────

  @DELETE
  @Path("/{appId}")
  @Operation(
    summary = "Hard-delete a singleton FileReference and its stored bytes.",
    description =
      "Permanently removes the `:FileReference` identified by `appId` (UUID v7) from " +
      "Neo4j and deletes its stored bytes from the active storage backend (GridFS or S3). " +
      "This is a hard delete — the bytes are unrecoverable after this call. To remove " +
      "only the graph node without deleting bytes, use the upstream " +
      "`DELETE /shepard/api/...` endpoint (which soft-deletes the Reference).\n\n" +
      "Auth: Write permission on the parent DataObject (inherited from its Collection).\n\n" +
      "Idempotency: if the Reference no longer exists the call returns 404, not 204 — " +
      "the operation is not idempotent in the HTTP sense because a second call after " +
      "deletion will find nothing to return.\n\n" +
      "Side effects: `ProvenanceCaptureFilter` records a `DELETE` Activity. The parent " +
      "DataObject's `referenceIds[]` list no longer includes this Reference's id after " +
      "the call."
  )
  @APIResponse(responseCode = "204", description = "FileReference and its backing bytes permanently deleted.")
  @APIResponse(responseCode = "401", description = "Authentication required (no JWT or X-API-KEY).")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No singleton FileReference with that appId.")
  public Response deleteSingleton(
    @PathParam("appId") String appId,
    @Context SecurityContext securityContext
  ) {
    FileReference ref = singletonService.getByAppId(appId);
    if (ref == null) return Response.status(Response.Status.NOT_FOUND).build();
    Response gate = checkAccess(ref, AccessType.Write, securityContext);
    if (gate != null) return gate;
    try {
      singletonService.deleteSingleton(appId);
      return Response.noContent().build();
    } catch (NotFoundException nfe) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
  }

  // ─── helpers ──────────────────────────────────────────────────────────────

  /**
   * Returns the caller's principal name, or {@code null} when no
   * principal is present (unauthenticated request — short-circuit
   * 401 at the call site).
   */
  private String callerOrNull(SecurityContext securityContext) {
    return securityContext.getUserPrincipal() != null ? securityContext.getUserPrincipal().getName() : null;
  }

  /**
   * Common access gate. Returns {@code null} when access is allowed;
   * otherwise returns the short-circuit Response (401 / 403 / 404).
   * The singleton must already be known to exist by the call site
   * (404 if missing).
   */
  private Response checkAccess(FileReference ref, AccessType accessType, SecurityContext securityContext) {
    String caller = callerOrNull(securityContext);
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();
    if (ref.getDataObject() == null) {
      // Graph inconsistency — treat as 404.
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    // DataObjects have no own :Permissions node — walk up to the parent
    // Collection via the perm-walk helper. Gating on doOgmId directly
    // always 403'd because PermissionsDAO.findByEntityNeo4jId returns
    // null for DOs.
    String doAppId = ref.getDataObject().getAppId();
    if (doAppId == null) {
      // Pre-L2a DataObject with no appId — fall back to the old behaviour
      // (gates on the DO's own perms, which fail closed; the operator can
      // run the L2b backfill to populate appIds and unblock this path).
      long doOgmId = ref.getDataObject().getId();
      if (!permissionsService.isAccessTypeAllowedForUser(doOgmId, accessType, caller)) {
        return Response.status(Response.Status.FORBIDDEN).build();
      }
      return null;
    }
    if (!permissionsService.isAccessAllowedForDataObjectAppId(doAppId, accessType, caller)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    return null;
  }

  /**
   * Parse a single {@code "bytes=START-END"} range header against
   * a known total content length. Returns {@code null} for any
   * unsupported / unsatisfiable shape (multi-range, suffix-range,
   * out-of-bounds start, etc.) — call sites translate that into
   * HTTP 416.
   *
   * <p>The single-range shape covers ~100 % of real-world clients
   * (HTML5 video, Postman, curl). Multi-range / suffix-range are
   * RFC 7233 features that virtually no browser issues; FR1b
   * declines to implement them.
   *
   * @param header raw {@code Range} header value, e.g. {@code "bytes=0-1023"}.
   * @param total total byte length of the resource.
   * @return {@code [start, end]} (inclusive) or {@code null} if
   *   unparseable / unsatisfiable.
   */
  static long[] parseRange(String header, long total) {
    if (header == null) return null;
    String trimmed = header.trim();
    if (!trimmed.startsWith("bytes=")) return null;
    String spec = trimmed.substring("bytes=".length());
    // Multi-range (comma-separated) — refuse.
    if (spec.contains(",")) return null;
    int dash = spec.indexOf('-');
    if (dash < 0) return null;
    String startStr = spec.substring(0, dash).trim();
    String endStr = spec.substring(dash + 1).trim();
    if (startStr.isEmpty()) {
      // Suffix-range "bytes=-N" — refuse (FR1b).
      return null;
    }
    long start;
    long end;
    try {
      start = Long.parseLong(startStr);
      end = endStr.isEmpty() ? total - 1 : Long.parseLong(endStr);
    } catch (NumberFormatException nfe) {
      return null;
    }
    if (start < 0 || start >= total) return null;
    if (end >= total) end = total - 1;
    if (end < start) return null;
    return new long[] { start, end };
  }

  /**
   * Convert a Jackson {@link JsonNode} object into a plain
   * {@code Map<String, Object>} preserving null values (so
   * RFC 7396 explicit-null clears flow through to the patch service).
   * Mirrors {@code FileBundleReferenceRest#jsonNodeToMap}.
   */
  Map<String, Object> jsonNodeToMap(JsonNode node) {
    Map<String, Object> out = new LinkedHashMap<>();
    var fields = node.fields();
    while (fields.hasNext()) {
      var e = fields.next();
      JsonNode v = e.getValue();
      if (v == null || v.isNull()) {
        out.put(e.getKey(), null);
      } else if (v.isTextual()) {
        out.put(e.getKey(), v.asText());
      } else if (v.isInt() || v.isLong()) {
        out.put(e.getKey(), v.asLong());
      } else if (v.isNumber()) {
        out.put(e.getKey(), v.asDouble());
      } else if (v.isBoolean()) {
        out.put(e.getKey(), v.asBoolean());
      } else if (v.isObject()) {
        Map<String, Object> sub = new HashMap<>();
        var inner = v.fields();
        while (inner.hasNext()) {
          var ie = inner.next();
          sub.put(ie.getKey(), ie.getValue() == null || ie.getValue().isNull() ? null : ie.getValue().asText());
        }
        out.put(e.getKey(), sub);
      } else {
        out.put(e.getKey(), v.toString());
      }
    }
    return out;
  }
}
