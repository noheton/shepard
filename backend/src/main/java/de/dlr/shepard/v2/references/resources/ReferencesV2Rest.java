package de.dlr.shepard.v2.references.resources;

import com.fasterxml.jackson.databind.JsonNode;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.v2.references.io.ReferenceV2IO;
import de.dlr.shepard.v2.references.services.ReferencesV2Service;
import de.dlr.shepard.v2.references.util.JsonNodeMaps;
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
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * V2CONV-A2 — the unified {@code /v2/references} REST surface that converges
 * the homogeneous create / get-one / patch / delete / list-filter operations
 * previously spread across per-kind resources ({@code /v2/uri-references},
 * {@code /v2/timeseries-references}, the JSON portions of {@code /v2/files},
 * and the plugin reference resources).
 *
 * <p>Kind-specific special operations that live at their own paths: git
 * {@code /preview} + {@code /check-update}. The file content GET
 * ({@code GET /v2/files/{appId}/content}) and the now-tombstoned video
 * {@code /download} path are both superseded by the generic
 * {@code GET /v2/references/{appId}/content} endpoint on this class.
 *
 * <h2>Routes</h2>
 * <ul>
 *   <li>{@code POST   /v2/references?kind=…&dataObjectAppId=…} — create a
 *       reference of {@code kind}. For {@code kind=file} this is now the
 *       APISIMP-KIND-DISCRIMINATOR Option C phase-1: creates a metadata-only
 *       node (name required in body; no bytes). Follow up with PUT …/content.</li>
 *   <li>{@code PUT    /v2/references/{appId}/content} — APISIMP-KIND-DISCRIMINATOR
 *       Option C phase-2: upload binary content to a reference node that supports
 *       it ({@code kind=file}, {@code kind=video}). Takes {@code ?filename=} query param.</li>
 *   <li>{@code GET    /v2/references/{appId}/content} — APISIMP-VIDEO-STREAMREF-PATH:
 *       download binary content for {@code kind=video} (range-aware) and
 *       {@code kind=file}. Takes optional {@code Range} header.</li>
 *   <li>{@code GET    /v2/references/{appId}} — the entity self-describes its
 *       kind; returns the unified {@link ReferenceV2IO}.</li>
 *   <li>{@code PATCH  /v2/references/{appId}} — RFC 7396 merge-patch, dispatched
 *       to the owning kind's patcher.</li>
 *   <li>{@code DELETE /v2/references/{appId}} — dispatched to the owning kind's
 *       deleter.</li>
 *   <li>{@code GET    /v2/references?kind=…&dataObjectAppId=…[&fileKind=…]} —
 *       list/filter; returns {@code ReferenceV2IO[]}.</li>
 * </ul>
 *
 * <p>Identifiers are {@code appId} (UUID v7) strings throughout; numeric Neo4j
 * ids never appear on the wire. Permission is gated against the resolved parent
 * DataObject's appId (Read for get/list, Write for create/patch/delete).
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/references")
@RequestScoped
@Tag(name = "References")
public class ReferencesV2Rest {

  private static final String PROBLEM_TYPE_BAD_REQUEST = "/problems/references.bad-request";
  private static final String PROBLEM_TYPE_UNAUTHORIZED = "/problems/references.unauthorized";
  private static final String PROBLEM_TYPE_NOT_FOUND = "/problems/references.not-found";
  private static final String PROBLEM_TYPE_FORBIDDEN = "/problems/references.forbidden";

  @Inject
  ReferencesV2Service referencesService;

  @Inject
  PermissionsService permissionsService;

  // ─── create ────────────────────────────────────────────────────────────

  @POST
  @Operation(
    operationId = "createReference",
    summary = "Create a non-binary reference of the given kind under a DataObject.",
    description =
      "Creates a reference of `kind` attached to the DataObject identified by " +
      "`dataObjectAppId`. The body is the per-kind create payload (e.g. `{uri, " +
      "relationship}` for kind=uri; `{start, end, timeseriesContainerId, timeseries}` " +
      "for kind=timeseries; `{name}` for kind=file — APISIMP-KIND-DISCRIMINATOR " +
      "Option C phase-1: creates a metadata-only node, then call " +
      "`PUT /v2/references/{appId}/content` to upload bytes).\n\n" +
      "Auth: Write on the parent DataObject."
  )
  @APIResponse(
    responseCode = "201",
    description = "Created; body is the unified ReferenceV2IO.",
    content = @Content(schema = @Schema(implementation = ReferenceV2IO.class))
  )
  @APIResponse(responseCode = "400", description = "Unknown/uninstalled kind, binary kind, or invalid body.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No DataObject with that appId.")
  public Response create(
    @Parameter(name = "kind", description = "Reference kind discriminator — required. Accepted values: \"file\", \"uri\", \"timeseries\", \"git\", or any plugin-registered kind. Returns 400 for unknown/uninstalled kinds.", required = true)
    @QueryParam("kind") String kind,
    @Parameter(name = "dataObjectAppId", description = "UUID v7 appId of the parent DataObject to attach the new reference to. Returns 404 if no DataObject exists with this appId.", required = true)
    @QueryParam("dataObjectAppId") String dataObjectAppId,
    JsonNode body,
    @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED, "authentication is required to create a reference");
    if (kind == null || kind.isBlank()) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Missing query parameter", Response.Status.BAD_REQUEST, "kind query parameter is required");
    }
    if (dataObjectAppId == null || dataObjectAppId.isBlank()) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Missing query parameter", Response.Status.BAD_REQUEST, "dataObjectAppId query parameter is required");
    }
    if (!permissionsService.isAccessAllowedForDataObjectAppId(dataObjectAppId, AccessType.Write, caller)) {
      return problem(PROBLEM_TYPE_FORBIDDEN, "Forbidden", Response.Status.FORBIDDEN, "caller lacks Write access on the parent DataObject");
    }
    Map<String, Object> map = body == null ? Map.of() : JsonNodeMaps.toMap(body);
    try {
      ReferenceV2IO created = referencesService.create(kind, dataObjectAppId, map);
      return Response.status(Response.Status.CREATED).entity(created).build();
    } catch (BadRequestException bre) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Bad request", Response.Status.BAD_REQUEST, bre.getMessage());
    } catch (NotFoundException nfe) {
      return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "no DataObject found with appId: " + dataObjectAppId);
    }
  }

  // ─── get-one ───────────────────────────────────────────────────────────

  @GET
  @Path("/{appId}")
  @Operation(
    operationId = "getReference",
    summary = "Get any reference by appId; the entity self-describes its kind.",
    description =
      "Resolves the reference (of any kind) at `appId` and returns the unified " +
      "ReferenceV2IO, including the `kind`, `referenceShape` (singleton/bundle for " +
      "files), and `fileKind` discriminators plus the per-kind `payload`.\n\nAuth: " +
      "Read on the parent DataObject."
  )
  @APIResponse(
    responseCode = "200",
    description = "The unified ReferenceV2IO.",
    content = @Content(schema = @Schema(implementation = ReferenceV2IO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No reference with that appId.")
  public Response get(@PathParam("appId") String appId, @Context SecurityContext sc) {
    String caller = callerOrNull(sc);
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED, "authentication is required to get a reference");
    var resolved = referencesService.resolveByAppId(appId);
    if (resolved.isEmpty()) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "no reference found with appId: " + appId);
    Response gate = gateOnParent(resolved.get().reference(), AccessType.Read, caller);
    if (gate != null) return gate;
    return Response.ok(resolved.get().handler().toIO(resolved.get().reference())).build();
  }

  // ─── patch ─────────────────────────────────────────────────────────────

  @PATCH
  @Path("/{appId}")
  @Consumes({ "application/merge-patch+json", MediaType.APPLICATION_JSON })
  @Operation(
    operationId = "patchReference",
    summary = "RFC 7396 merge-patch any reference by appId; dispatched by kind.",
    description =
      "Applies a merge-patch to the reference at `appId`, dispatched to the owning " +
      "kind's patcher (timeseries → time-alignment fields; uri → name/uri/relationship; " +
      "file → name). Absent keys are left unchanged.\n\nAuth: Write on the parent DataObject."
  )
  @APIResponse(
    responseCode = "200",
    description = "The post-patch ReferenceV2IO.",
    content = @Content(schema = @Schema(implementation = ReferenceV2IO.class))
  )
  @APIResponse(responseCode = "400", description = "Body is not a JSON object, or kind-specific validation failed.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No reference with that appId.")
  public Response patch(
    @PathParam("appId") String appId,
    @RequestBody(required = true, content = @Content(mediaType = "application/merge-patch+json")) JsonNode body,
    @Context SecurityContext sc
  ) {
    if (body == null || !body.isObject()) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Invalid request body", Response.Status.BAD_REQUEST, "PATCH body must be a JSON object");
    }
    String caller = callerOrNull(sc);
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED, "authentication is required to patch a reference");
    var resolved = referencesService.resolveByAppId(appId);
    if (resolved.isEmpty()) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "no reference found with appId: " + appId);
    Response gate = gateOnParent(resolved.get().reference(), AccessType.Write, caller);
    if (gate != null) return gate;
    try {
      ReferenceV2IO updated = referencesService.patchByAppId(appId, JsonNodeMaps.toMap(body));
      return Response.ok(updated).build();
    } catch (BadRequestException bre) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Bad request", Response.Status.BAD_REQUEST, bre.getMessage());
    } catch (NotFoundException nfe) {
      return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "no reference found with appId: " + appId);
    }
  }

  // ─── delete ────────────────────────────────────────────────────────────

  @DELETE
  @Path("/{appId}")
  @Operation(
    operationId = "deleteReference",
    summary = "Delete any reference by appId; dispatched by kind.",
    description = "Deletes the reference at `appId` via the owning kind's deleter.\n\nAuth: Write on the parent DataObject."
  )
  @APIResponse(responseCode = "204", description = "Deleted.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No reference with that appId.")
  public Response delete(@PathParam("appId") String appId, @Context SecurityContext sc) {
    String caller = callerOrNull(sc);
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED, "authentication is required to delete a reference");
    var resolved = referencesService.resolveByAppId(appId);
    if (resolved.isEmpty()) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "no reference found with appId: " + appId);
    Response gate = gateOnParent(resolved.get().reference(), AccessType.Write, caller);
    if (gate != null) return gate;
    try {
      referencesService.deleteByAppId(appId);
      return Response.noContent().build();
    } catch (NotFoundException nfe) {
      return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "no reference found with appId: " + appId);
    }
  }

  // ─── upload-content ────────────────────────────────────────────────────────

  @PUT
  @Path("/{appId}/content")
  @Consumes(MediaType.APPLICATION_OCTET_STREAM)
  @Operation(
    operationId = "uploadReferenceContent",
    summary = "APISIMP-KIND-DISCRIMINATOR Option C phase-2: upload binary content to an existing reference node.",
    description =
      "Attaches binary content to the reference at `appId`. The reference must already " +
      "exist (created via `POST /v2/references?kind=file`). The body is the raw binary " +
      "payload (`application/octet-stream`). The `filename` query parameter provides the " +
      "original filename for MIME/fileKind detection and GridFS storage (required).\n\n" +
      "For references that do not support binary content (`kind=uri`, `kind=timeseries`, " +
      "`kind=git`, etc.) this returns 400.\n\n" +
      "Re-uploading (calling PUT a second time on the same appId) replaces the previous " +
      "content — the old GridFS blob is deleted after the new one is written.\n\n" +
      "Auth: Write on the parent DataObject."
  )
  @APIResponse(
    responseCode = "200",
    description = "Content attached; body is the updated ReferenceV2IO.",
    content = @Content(schema = @Schema(implementation = ReferenceV2IO.class))
  )
  @APIResponse(responseCode = "400", description = "Missing filename, empty body, or kind does not support content upload.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No reference with that appId.")
  public Response uploadContent(
    @PathParam("appId") String appId,
    @Parameter(name = "filename", description = "Original filename including extension (e.g. \"robot.urdf\", \"scan.h5\"). Required — used for MIME-type detection and fileKind classification (e.g. \".urdf\" → fileKind=urdf). Returns 400 when absent.", required = true)
    @QueryParam("filename") String filename,
    @HeaderParam("Content-Length") String contentLengthHeader,
    InputStream body,
    @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED, "authentication is required to upload reference content");
    if (filename == null || filename.isBlank()) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Missing query parameter", Response.Status.BAD_REQUEST, "filename query parameter is required");
    }
    if (body == null) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Missing body", Response.Status.BAD_REQUEST, "binary body is required");
    }
    var resolved = referencesService.resolveByAppId(appId);
    if (resolved.isEmpty()) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "no reference found with appId: " + appId);
    Response gate = gateOnParent(resolved.get().reference(), AccessType.Write, caller);
    if (gate != null) return gate;
    long declaredSize = -1L;
    if (contentLengthHeader != null && !contentLengthHeader.isBlank()) {
      try {
        declaredSize = Long.parseLong(contentLengthHeader.trim());
      } catch (NumberFormatException ignored) {
        // non-numeric Content-Length — skip cap check
      }
    }
    try {
      ReferenceV2IO updated = resolved.get().handler().uploadContent(appId, body, filename, declaredSize);
      return Response.ok(updated).build();
    } catch (UnsupportedOperationException uoe) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Not supported", Response.Status.BAD_REQUEST, uoe.getMessage());
    } catch (BadRequestException bre) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Bad request", Response.Status.BAD_REQUEST, bre.getMessage());
    } catch (NotFoundException nfe) {
      return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "no reference found with appId: " + appId);
    }
  }

  // ─── download-content ─────────────────────────────────────────────────────

  @GET
  @Path("/{appId}/content")
  @Produces({ MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_JSON })
  @Operation(
    operationId = "downloadReferenceContent",
    summary = "APISIMP-VIDEO-STREAMREF-PATH: download binary content for a reference that supports it.",
    description =
      "Streams the binary payload for the reference at `appId`. Only binary reference kinds " +
      "(`kind=video`, `kind=file`) support this endpoint; non-binary kinds return 400.\n\n" +
      "**Range requests:** a single `Range: bytes=START-END` header is honoured for kinds " +
      "whose handler implements range-aware streaming (e.g. `kind=video` returns 206 Partial " +
      "Content + `Content-Range` for browser-native video scrubbing). An unsatisfiable range " +
      "returns 416.\n\n" +
      "**Auth:** Read permission on the parent DataObject. The JWT may be supplied via the " +
      "`?access_token=…` query param fallback (RFC 6750 §2.3) for HTML5 `<video src>` usage."
  )
  @APIResponse(
    responseCode = "200",
    description = "Full binary payload.",
    content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM)
  )
  @APIResponse(
    responseCode = "206",
    description = "Partial content — Range header was honoured."
  )
  @APIResponse(responseCode = "400", description = "Reference kind does not support binary content download.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No reference with that appId, or content not yet uploaded.")
  @APIResponse(responseCode = "416", description = "Range not satisfiable.")
  @APIResponse(responseCode = "503", description = "No active file storage adapter configured.")
  public Response downloadContent(
    @PathParam("appId") String appId,
    @HeaderParam("Range") String rangeHeader,
    @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED, "authentication is required to download reference content");
    var resolved = referencesService.resolveByAppId(appId);
    if (resolved.isEmpty()) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "no reference found with appId: " + appId);
    Response gate = gateOnParent(resolved.get().reference(), AccessType.Read, caller);
    if (gate != null) return gate;
    try {
      return resolved.get().handler().downloadContent(appId, rangeHeader);
    } catch (UnsupportedOperationException uoe) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Not supported", Response.Status.BAD_REQUEST, uoe.getMessage());
    } catch (NotFoundException nfe) {
      return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, nfe.getMessage());
    }
  }

  // ─── list / filter ───────────────────────────────────────────────────────

  @GET
  @Operation(
    operationId = "listReferences",
    summary = "List references of a kind attached to a DataObject, optionally filtered.",
    description =
      "Returns every reference of `kind` attached to `dataObjectAppId` as " +
      "ReferenceV2IO[]. For `kind=file`, an optional `fileKind` query param narrows " +
      "to singletons of that file-kind (e.g. `fileKind=urdf`).\n\nAuth: Read on the " +
      "parent DataObject."
  )
  @APIResponse(
    responseCode = "200",
    description = "List of ReferenceV2IO (may be empty).",
    content = @Content(
      mediaType = MediaType.APPLICATION_JSON,
      schema = @Schema(type = SchemaType.ARRAY, implementation = ReferenceV2IO.class)
    )
  )
  @APIResponse(responseCode = "400", description = "Missing kind/dataObjectAppId or unknown kind.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No DataObject with that appId.")
  public Response list(
    @Parameter(name = "kind", description = "Reference kind discriminator — required. Accepted values: \"file\", \"uri\", \"timeseries\", \"git\", or any plugin-registered kind. Returns 400 for unknown/uninstalled kinds.", required = true)
    @QueryParam("kind") String kind,
    @Parameter(name = "dataObjectAppId", description = "UUID v7 appId of the parent DataObject whose references to list. Returns 404 if no DataObject exists with this appId.", required = true)
    @QueryParam("dataObjectAppId") String dataObjectAppId,
    @Parameter(name = "fileKind", description = "Optional sub-type filter; only meaningful when kind=file. Narrows results to FileReferences whose fileKind matches (e.g. \"urdf\", \"hdf5\"). Ignored for all other kinds.")
    @QueryParam("fileKind") String fileKind,
    @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED, "authentication is required to list references");
    if (kind == null || kind.isBlank()) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Missing query parameter", Response.Status.BAD_REQUEST, "kind query parameter is required");
    }
    if (dataObjectAppId == null || dataObjectAppId.isBlank()) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Missing query parameter", Response.Status.BAD_REQUEST, "dataObjectAppId query parameter is required");
    }
    if (!permissionsService.isAccessAllowedForDataObjectAppId(dataObjectAppId, AccessType.Read, caller)) {
      return problem(PROBLEM_TYPE_FORBIDDEN, "Forbidden", Response.Status.FORBIDDEN, "caller lacks Read access on the parent DataObject");
    }
    try {
      List<ReferenceV2IO> refs = referencesService.listByDataObject(kind, dataObjectAppId, fileKind);
      return Response.ok(refs).build();
    } catch (BadRequestException bre) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Bad request", Response.Status.BAD_REQUEST, bre.getMessage());
    } catch (NotFoundException nfe) {
      return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "no DataObject found with appId: " + dataObjectAppId);
    }
  }

  // ─── helpers ───────────────────────────────────────────────────────────

  private static Response problem(String type, String title, Response.Status status, String detail) {
    ProblemJson body = new ProblemJson(type, title, status.getStatusCode(), detail, null);
    return Response.status(status).type("application/problem+json").entity(body).build();
  }

  private String callerOrNull(SecurityContext sc) {
    return sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
  }

  /**
   * Gate access on the reference's parent DataObject. Returns null when access
   * is allowed; otherwise the short-circuit 403/404 Response.
   */
  private Response gateOnParent(BasicReference ref, AccessType accessType, String caller) {
    DataObject parent = ref.getDataObject();
    if (parent == null) {
      // Graph inconsistency — treat as 404.
      return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "reference parent DataObject is missing (graph inconsistency)");
    }
    String doAppId = parent.getAppId();
    if (doAppId == null) {
      // Pre-L2a DataObject without appId — fail closed on its own OGM id.
      if (!permissionsService.isAccessTypeAllowedForUser(parent.getId(), accessType, caller)) {
        return problem(PROBLEM_TYPE_FORBIDDEN, "Forbidden", Response.Status.FORBIDDEN, "caller lacks required access on the parent DataObject");
      }
      return null;
    }
    if (!permissionsService.isAccessAllowedForDataObjectAppId(doAppId, accessType, caller)) {
      return problem(PROBLEM_TYPE_FORBIDDEN, "Forbidden", Response.Status.FORBIDDEN, "caller lacks required access on the parent DataObject");
    }
    return null;
  }
}
