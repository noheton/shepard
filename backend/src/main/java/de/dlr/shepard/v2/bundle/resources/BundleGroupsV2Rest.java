package de.dlr.shepard.v2.bundle.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.references.file.daos.FileBundleReferenceDAO;
import de.dlr.shepard.context.references.file.entities.FileBundleReference;
import de.dlr.shepard.context.references.file.entities.FileGroup;
import de.dlr.shepard.context.references.file.io.CreateFileGroupIO;
import de.dlr.shepard.context.references.file.io.FileGroupIO;
import de.dlr.shepard.context.references.file.services.FileGroupService;
import de.dlr.shepard.data.file.entities.ShepardFile;
import de.dlr.shepard.storage.FileStorageService;
import de.dlr.shepard.v2.bundle.io.PagedFilesIO;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
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
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

/**
 * Groups sub-resource for {@code FileBundleReference} under the unified
 * {@code /v2/references} surface (APISIMP-BUNDLE-REF-KIND-UNIFY slice 1).
 *
 * <p>Canonical paths:
 * <ul>
 *   <li>{@code GET    /v2/references/{bundleAppId}/groups} — list groups.</li>
 *   <li>{@code POST   /v2/references/{bundleAppId}/groups} — create a group.</li>
 *   <li>{@code GET    /v2/references/{bundleAppId}/groups/{groupAppId}} — get a group.</li>
 *   <li>{@code PATCH  /v2/references/{bundleAppId}/groups/{groupAppId}} — RFC 7396 patch.</li>
 *   <li>{@code DELETE /v2/references/{bundleAppId}/groups/{groupAppId}} — delete a group.</li>
 *   <li>{@code GET    /v2/references/{bundleAppId}/groups/{groupAppId}/files} — list files.</li>
 *   <li>{@code POST   /v2/references/{bundleAppId}/groups/{groupAppId}/files} — upload file.</li>
 * </ul>
 *
 * <p>The legacy paths at {@code /v2/bundles/{bundleAppId}/groups} remain active
 * in {@link FileBundleReferenceRest} until the APISIMP-BUNDLE-REF-KIND-UNIFY
 * tombstone slice ships. Auth: same {@link PermissionsService} permission check
 * as the legacy surface — walks from the bundle to its parent DataObject.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/references/{bundleAppId}/groups")
@RequestScoped
@Tag(name = "References")
public class BundleGroupsV2Rest {

  @Inject
  FileBundleReferenceDAO fileBundleReferenceDAO;

  @Inject
  FileGroupService fileGroupService;

  @Inject
  FileStorageService fileStorageService;

  @Inject
  PermissionsService permissionsService;

  @Inject
  ObjectMapper objectMapper;

  private static final String PROBLEM_TYPE_BAD_REQUEST   = "/problems/file-bundle-references.bad-request";
  private static final String PROBLEM_TYPE_UNPROCESSABLE  = "/problems/file-bundle-references.unprocessable-entity";
  private static final String PROBLEM_TYPE_INTERNAL       = "/problems/file-bundle-references.internal-error";
  private static final String PROBLEM_TYPE_UNAUTHORIZED   = "/problems/file-bundle-references.unauthorized";
  private static final String PROBLEM_TYPE_FORBIDDEN      = "/problems/file-bundle-references.forbidden";
  private static final String PROBLEM_TYPE_NOT_FOUND      = "/problems/file-bundle-references.not-found";

  static final int DEFAULT_FILES_PAGE_SIZE = 200;
  static final int MAX_FILES_PAGE_SIZE     = 1000;

  // ─── list groups ─────────────────────────────────────────────────────────

  @GET
  @Operation(
    operationId = "listBundleGroups",
    summary = "List FileGroups under a FileBundleReference, ordered by ascending index.",
    description =
      "Canonical path (APISIMP-BUNDLE-REF-KIND-UNIFY). " +
      "Returns a paged list of `:FileGroup` nodes belonging to the " +
      "`:FileBundleReference` identified by `bundleAppId` (UUID v7). " +
      "Groups are sorted by `index` ascending. " +
      "Pagination: `?page=0&pageSize=50` (pageSize capped at 200 server-side). " +
      "Auth: Read permission on the parent DataObject."
  )
  @APIResponse(
    responseCode = "200",
    description = "Paged envelope of FileGroupIO records ordered by index ascending.",
    content = @Content(schema = @Schema(implementation = PagedResponseIO.class)),
    headers = @Header(
      name = "X-Total-Count",
      description = "Total element count before paging.",
      schema = @Schema(type = SchemaType.INTEGER)
    )
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission.")
  @APIResponse(responseCode = "404", description = "No FileBundleReference with that appId.")
  public Response listGroups(
    @PathParam("bundleAppId") String bundleAppId,
    @QueryParam("page") @DefaultValue("0") @PositiveOrZero int page,
    @QueryParam("pageSize") @DefaultValue("50") @Min(1) @Max(200) int pageSize,
    @Context SecurityContext securityContext
  ) {
    FileBundleReference bundle = fileBundleReferenceDAO.findByAppId(bundleAppId);
    if (bundle == null) return problem(PROBLEM_TYPE_NOT_FOUND, "Bundle not found", Response.Status.NOT_FOUND, "No FileBundleReference with appId '" + bundleAppId + "'.");
    Response gate = checkAccess(bundle, AccessType.Read, securityContext);
    if (gate != null) return gate;

    long total = fileGroupService.countGroups(bundleAppId);
    List<FileGroupIO> items = fileGroupService.listGroups(bundleAppId, page, pageSize)
        .stream().map(FileGroupIO::new).toList();
    return Response.ok(new PagedResponseIO<>(items, total, page, pageSize))
        .header("X-Total-Count", total)
        .build();
  }

  // ─── create group ─────────────────────────────────────────────────────────

  @POST
  @Operation(
    operationId = "createBundleGroup",
    summary = "Create a new FileGroup under a FileBundleReference.",
    description =
      "Canonical path (APISIMP-BUNDLE-REF-KIND-UNIFY). " +
      "Creates a `:FileGroup` and links it to the `:FileBundleReference` identified by `bundleAppId`. " +
      "Body: `name` (required, non-blank), `description`, `attributes`, `startedAt`, `endedAt`, `index`. " +
      "Auth: Write permission on the parent DataObject."
  )
  @APIResponse(
    responseCode = "201",
    description = "FileGroup created.",
    content = @Content(schema = @Schema(implementation = FileGroupIO.class))
  )
  @APIResponse(responseCode = "400", description = "`name` is missing or blank.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission.")
  @APIResponse(responseCode = "404", description = "No FileBundleReference with that appId.")
  public Response createGroup(
    @PathParam("bundleAppId") String bundleAppId,
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = CreateFileGroupIO.class)))
      CreateFileGroupIO body,
    @Context SecurityContext securityContext
  ) {
    if (body == null || body.getName() == null || body.getName().isBlank()) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Missing field", Response.Status.BAD_REQUEST, "name is required and must be non-blank");
    }
    FileBundleReference bundle = fileBundleReferenceDAO.findByAppId(bundleAppId);
    if (bundle == null) return problem(PROBLEM_TYPE_NOT_FOUND, "Bundle not found", Response.Status.NOT_FOUND, "No FileBundleReference with appId '" + bundleAppId + "'.");
    Response gate = checkAccess(bundle, AccessType.Write, securityContext);
    if (gate != null) return gate;

    try {
      FileGroup created = fileGroupService.createGroup(bundleAppId, body);
      return Response.status(Response.Status.CREATED).entity(new FileGroupIO(created)).build();
    } catch (BadRequestException bre) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Bad request", Response.Status.BAD_REQUEST, bre.getMessage());
    } catch (NotFoundException nfe) {
      return problem(PROBLEM_TYPE_NOT_FOUND, "Bundle not found", Response.Status.NOT_FOUND, "No FileBundleReference with appId '" + bundleAppId + "'.");
    }
  }

  // ─── get group ────────────────────────────────────────────────────────────

  @GET
  @Path("/{groupAppId}")
  @Operation(
    operationId = "getBundleGroup",
    summary = "Get a single FileGroup with its files.",
    description =
      "Canonical path (APISIMP-BUNDLE-REF-KIND-UNIFY). " +
      "Returns the `FileGroupIO` for the `:FileGroup` identified by `groupAppId` " +
      "within the bundle identified by `bundleAppId`. " +
      "404 if either is unknown or the group does not belong to the bundle. " +
      "Auth: Read permission on the parent DataObject."
  )
  @APIResponse(
    responseCode = "200",
    description = "FileGroupIO with files embedded.",
    content = @Content(schema = @Schema(implementation = FileGroupIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission.")
  @APIResponse(responseCode = "404", description = "Bundle or group not found, or group not in bundle.")
  public Response getGroup(
    @PathParam("bundleAppId") String bundleAppId,
    @PathParam("groupAppId") String groupAppId,
    @Context SecurityContext securityContext
  ) {
    FileBundleReference bundle = fileBundleReferenceDAO.findByAppId(bundleAppId);
    if (bundle == null) return problem(PROBLEM_TYPE_NOT_FOUND, "Bundle not found", Response.Status.NOT_FOUND, "No FileBundleReference with appId '" + bundleAppId + "'.");
    Response gate = checkAccess(bundle, AccessType.Read, securityContext);
    if (gate != null) return gate;

    FileGroup group = fileGroupService.getByAppId(groupAppId);
    if (group == null) return problem(PROBLEM_TYPE_NOT_FOUND, "Group not found", Response.Status.NOT_FOUND, "No FileGroup with appId '" + groupAppId + "'.");
    String parent = fileGroupService.findBundleAppIdForGroup(groupAppId);
    if (!bundleAppId.equals(parent)) return problem(PROBLEM_TYPE_NOT_FOUND, "Group not in bundle", Response.Status.NOT_FOUND, "FileGroup '" + groupAppId + "' does not belong to bundle '" + bundleAppId + "'.");
    return Response.ok(new FileGroupIO(group)).build();
  }

  // ─── patch group ──────────────────────────────────────────────────────────

  @PATCH
  @Path("/{groupAppId}")
  @Consumes({ "application/merge-patch+json", MediaType.APPLICATION_JSON })
  @Operation(
    operationId = "patchBundleGroup",
    summary = "RFC 7396 merge-patch on a FileGroup.",
    description =
      "Canonical path (APISIMP-BUNDLE-REF-KIND-UNIFY). " +
      "Patchable fields: `name`, `description`, `attributes`, `startedAt`, `endedAt`, `index`. " +
      "Absent fields are left unchanged; explicit JSON `null` clears a nullable field. " +
      "Auth: Write permission on the parent DataObject."
  )
  @APIResponse(
    responseCode = "200",
    description = "FileGroupIO after patch.",
    content = @Content(schema = @Schema(implementation = FileGroupIO.class))
  )
  @APIResponse(responseCode = "400", description = "Body is not a JSON object, or `name` is null or blank.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission.")
  @APIResponse(responseCode = "404", description = "Bundle or group not found, or group not in bundle.")
  public Response patchGroup(
    @PathParam("bundleAppId") String bundleAppId,
    @PathParam("groupAppId") String groupAppId,
    @RequestBody(required = true, content = @Content(mediaType = "application/merge-patch+json")) JsonNode body,
    @Context SecurityContext securityContext
  ) {
    if (body == null || !body.isObject()) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Invalid request body", Response.Status.BAD_REQUEST, "PATCH body must be a JSON object");
    }
    FileBundleReference bundle = fileBundleReferenceDAO.findByAppId(bundleAppId);
    if (bundle == null) return problem(PROBLEM_TYPE_NOT_FOUND, "Bundle not found", Response.Status.NOT_FOUND, "No FileBundleReference with appId '" + bundleAppId + "'.");
    Response gate = checkAccess(bundle, AccessType.Write, securityContext);
    if (gate != null) return gate;

    String parent = fileGroupService.findBundleAppIdForGroup(groupAppId);
    if (!bundleAppId.equals(parent)) return problem(PROBLEM_TYPE_NOT_FOUND, "Group not in bundle", Response.Status.NOT_FOUND, "FileGroup '" + groupAppId + "' does not belong to bundle '" + bundleAppId + "'.");

    try {
      Map<String, Object> patch = jsonNodeToMap(body);
      FileGroup updated = fileGroupService.patchGroup(groupAppId, patch);
      return Response.ok(new FileGroupIO(updated)).build();
    } catch (BadRequestException bre) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Bad request", Response.Status.BAD_REQUEST, bre.getMessage());
    } catch (NotFoundException nfe) {
      return problem(PROBLEM_TYPE_NOT_FOUND, "Group not found", Response.Status.NOT_FOUND, "No FileGroup with appId '" + groupAppId + "'.");
    }
  }

  // ─── delete group ─────────────────────────────────────────────────────────

  @DELETE
  @Path("/{groupAppId}")
  @Operation(
    operationId = "deleteBundleGroup",
    summary = "Delete a FileGroup from a FileBundleReference.",
    description =
      "Canonical path (APISIMP-BUNDLE-REF-KIND-UNIFY). " +
      "Removes the `:FileGroup`. Two guards: (1) refuses if the group has files and " +
      "`?force=true` is not supplied; (2) refuses if it is the last group in the bundle. " +
      "Auth: Write permission on the parent DataObject."
  )
  @APIResponse(responseCode = "204", description = "FileGroup deleted.")
  @APIResponse(responseCode = "400", description = "Group has files (no force) or is the last group.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission.")
  @APIResponse(responseCode = "404", description = "Bundle or group not found, or group not in bundle.")
  public Response deleteGroup(
    @PathParam("bundleAppId") String bundleAppId,
    @PathParam("groupAppId") String groupAppId,
    @Parameter(description = "When true, also permanently deletes all files in the group.")
    @QueryParam("force") boolean force,
    @Context SecurityContext securityContext
  ) {
    FileBundleReference bundle = fileBundleReferenceDAO.findByAppId(bundleAppId);
    if (bundle == null) return problem(PROBLEM_TYPE_NOT_FOUND, "Bundle not found", Response.Status.NOT_FOUND, "No FileBundleReference with appId '" + bundleAppId + "'.");
    Response gate = checkAccess(bundle, AccessType.Write, securityContext);
    if (gate != null) return gate;

    String parent = fileGroupService.findBundleAppIdForGroup(groupAppId);
    if (!bundleAppId.equals(parent)) return problem(PROBLEM_TYPE_NOT_FOUND, "Group not in bundle", Response.Status.NOT_FOUND, "FileGroup '" + groupAppId + "' does not belong to bundle '" + bundleAppId + "'.");

    try {
      fileGroupService.deleteGroup(groupAppId, force);
      return Response.noContent().build();
    } catch (BadRequestException bre) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Bad request", Response.Status.BAD_REQUEST, bre.getMessage());
    } catch (NotFoundException nfe) {
      return problem(PROBLEM_TYPE_NOT_FOUND, "Group not found", Response.Status.NOT_FOUND, "No FileGroup with appId '" + groupAppId + "'.");
    }
  }

  // ─── list files in a group ────────────────────────────────────────────────

  @GET
  @Path("/{groupAppId}/files")
  @Operation(
    operationId = "listBundleGroupFiles",
    summary = "List files in a FileGroup, paginated.",
    description =
      "Canonical path (APISIMP-BUNDLE-REF-KIND-UNIFY). " +
      "Returns the files attached to the `:FileGroup` identified by `groupAppId`, paginated. " +
      "`pageSize` default 200; max 1000. " +
      "Auth: Read permission on the parent DataObject."
  )
  @APIResponse(
    responseCode = "200",
    description = "PagedFilesIO envelope.",
    content = @Content(schema = @Schema(implementation = PagedFilesIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission.")
  @APIResponse(responseCode = "404", description = "Bundle or group not found, or group not in bundle.")
  public Response listGroupFiles(
    @PathParam("bundleAppId") String bundleAppId,
    @PathParam("groupAppId") String groupAppId,
    @Parameter(
      description = "Zero-based page index (default 0).",
      schema = @Schema(minimum = "0", defaultValue = "0")
    )
    @QueryParam("page") @DefaultValue("0") @PositiveOrZero int page,
    @Parameter(
      description = "Page size, 1–1000 (default 200).",
      schema = @Schema(minimum = "1", maximum = "1000", defaultValue = "200")
    )
    @QueryParam("pageSize") @DefaultValue("200") @Min(1) @Max(1000) int pageSize,
    @Context SecurityContext securityContext
  ) {
    FileBundleReference bundle = fileBundleReferenceDAO.findByAppId(bundleAppId);
    if (bundle == null) return problem(PROBLEM_TYPE_NOT_FOUND, "Bundle not found", Response.Status.NOT_FOUND, "No FileBundleReference with appId '" + bundleAppId + "'.");
    Response gate = checkAccess(bundle, AccessType.Read, securityContext);
    if (gate != null) return gate;

    String parent = fileGroupService.findBundleAppIdForGroup(groupAppId);
    if (parent == null) return problem(PROBLEM_TYPE_NOT_FOUND, "Group not found", Response.Status.NOT_FOUND, "No FileGroup with appId '" + groupAppId + "'.");
    if (!bundleAppId.equals(parent)) return problem(PROBLEM_TYPE_NOT_FOUND, "Group not in bundle", Response.Status.NOT_FOUND, "FileGroup '" + groupAppId + "' does not belong to bundle '" + bundleAppId + "'.");

    long total = fileGroupService.countFiles(groupAppId);
    int totalPages = (int) ((total + pageSize - 1) / pageSize);
    int skip = page * pageSize;
    List<ShepardFile> items = skip < total ? fileGroupService.listFiles(groupAppId, skip, pageSize) : List.of();

    return Response.ok(new PagedFilesIO(items, page, pageSize, total, totalPages)).build();
  }

  // ─── upload file into a group ─────────────────────────────────────────────

  @POST
  @Path("/{groupAppId}/files")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @Operation(
    operationId = "uploadFileToBundleGroup",
    summary = "Upload one file into a specific FileGroup.",
    description =
      "Canonical path (APISIMP-BUNDLE-REF-KIND-UNIFY). " +
      "Accepts a `multipart/form-data` body with a `file` part. Stores the bytes in the " +
      "`FileContainer` backing the bundle and links the resulting `ShepardFile` to the group. " +
      "Auth: Write permission on the parent DataObject."
  )
  @APIResponse(
    responseCode = "201",
    description = "ShepardFile stored and linked to the group.",
    content = @Content(schema = @Schema(implementation = ShepardFile.class))
  )
  @APIResponse(responseCode = "400", description = "`file` part missing, or bundle has no FileContainer.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission.")
  @APIResponse(responseCode = "404", description = "Bundle or group not found, or group not in bundle.")
  public Response uploadFileIntoGroup(
    @PathParam("bundleAppId") String bundleAppId,
    @PathParam("groupAppId") String groupAppId,
    @RestForm("file") FileUpload upload,
    @Context SecurityContext securityContext
  ) {
    FileBundleReference bundle = fileBundleReferenceDAO.findByAppId(bundleAppId);
    if (bundle == null) return problem(PROBLEM_TYPE_NOT_FOUND, "Bundle not found", Response.Status.NOT_FOUND, "No FileBundleReference with appId '" + bundleAppId + "'.");
    Response gate = checkAccess(bundle, AccessType.Write, securityContext);
    if (gate != null) return gate;

    String parent = fileGroupService.findBundleAppIdForGroup(groupAppId);
    if (!bundleAppId.equals(parent)) return problem(PROBLEM_TYPE_NOT_FOUND, "Group not in bundle", Response.Status.NOT_FOUND, "FileGroup '" + groupAppId + "' does not belong to bundle '" + bundleAppId + "'.");

    if (upload == null || upload.uploadedFile() == null) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Missing upload part", Response.Status.BAD_REQUEST, "file part is required");
    }
    if (bundle.getFileContainer() == null || bundle.getFileContainer().getMongoId() == null) {
      return problem(PROBLEM_TYPE_UNPROCESSABLE, "Bundle not writable", Response.Status.BAD_REQUEST, "Bundle has no underlying FileContainer; cannot accept uploads.");
    }

    File file = upload.uploadedFile().toFile();
    long declaredSize = file.length();
    try (InputStream is = new FileInputStream(file)) {
      ShepardFile saved = fileStorageService.storeFile(bundle.getFileContainer().getMongoId(), upload.fileName(), is, declaredSize);
      fileGroupService.attachFile(groupAppId, saved);
      return Response.status(Response.Status.CREATED).entity(saved).build();
    } catch (IOException ioe) {
      return problem(PROBLEM_TYPE_INTERNAL, "Upload failed", Response.Status.INTERNAL_SERVER_ERROR, ioe.getMessage());
    }
  }

  // ─── helpers ──────────────────────────────────────────────────────────────

  private Response checkAccess(FileBundleReference bundle, AccessType accessType, SecurityContext securityContext) {
    String caller = securityContext.getUserPrincipal() != null ? securityContext.getUserPrincipal().getName() : null;
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED, "No valid JWT or API key was provided.");
    if (bundle.getDataObject() == null) {
      return problem(PROBLEM_TYPE_NOT_FOUND, "Bundle has no parent DataObject", Response.Status.NOT_FOUND, "No DataObject is associated with this bundle (graph inconsistency).");
    }
    String doAppId = bundle.getDataObject().getAppId();
    if (doAppId == null) {
      long dataObjectOgmId = bundle.getDataObject().getId();
      if (!permissionsService.isAccessTypeAllowedForUser(dataObjectOgmId, accessType, caller)) {
        return problem(PROBLEM_TYPE_FORBIDDEN, "Forbidden", Response.Status.FORBIDDEN,
            "Caller does not have " + accessType + " access to the parent DataObject.");
      }
      return null;
    }
    if (!permissionsService.isAccessAllowedForDataObjectAppId(doAppId, accessType, caller)) {
      return problem(PROBLEM_TYPE_FORBIDDEN, "Forbidden", Response.Status.FORBIDDEN,
          "Caller does not have " + accessType + " access to the parent DataObject.");
    }
    return null;
  }

  private static Response problem(String type, String title, Response.Status status, String detail) {
    ProblemJson body = new ProblemJson(type, title, status.getStatusCode(), detail, null);
    return Response.status(status).type("application/problem+json").entity(body).build();
  }

  private Map<String, Object> jsonNodeToMap(JsonNode node) {
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
