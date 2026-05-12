package de.dlr.shepard.v2.bundle.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.references.file.daos.FileBundleReferenceDAO;
import de.dlr.shepard.context.references.file.daos.FileGroupDAO;
import de.dlr.shepard.context.references.file.entities.FileBundleReference;
import de.dlr.shepard.context.references.file.entities.FileGroup;
import de.dlr.shepard.context.references.file.io.CreateFileGroupIO;
import de.dlr.shepard.context.references.file.io.FileGroupIO;
import de.dlr.shepard.context.references.file.services.FileGroupService;
import de.dlr.shepard.data.file.entities.ShepardFile;
import de.dlr.shepard.data.file.services.FileService;
import de.dlr.shepard.v2.bundle.io.FileBundleReferenceIO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
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
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

/**
 * {@code /v2/bundles/...} REST surface for FR1a per
 * {@code aidocs/53 §1.6}.
 *
 * <p>Routes:
 * <ul>
 *   <li>{@code GET    /v2/bundles/{appId}} — bundle metadata + groups.</li>
 *   <li>{@code GET    /v2/bundles/{appId}/groups} — list groups.</li>
 *   <li>{@code POST   /v2/bundles/{appId}/groups} — create a new group.</li>
 *   <li>{@code GET    /v2/bundles/{appId}/groups/{groupAppId}} — group + files.</li>
 *   <li>{@code PATCH  /v2/bundles/{appId}/groups/{groupAppId}} — RFC 7396 merge-patch.</li>
 *   <li>{@code DELETE /v2/bundles/{appId}/groups/{groupAppId}} — delete; refuses
 *       if the group has files (unless {@code ?force=true}) or if it's the last
 *       remaining group.</li>
 *   <li>{@code POST   /v2/bundles/{appId}/groups/{groupAppId}/files} — upload one
 *       file into the named group; reuses the existing FileContainer Mongo path.</li>
 * </ul>
 *
 * <p>Permissions: every endpoint resolves the parent DataObject's OGM
 * id via the bundle, then asks {@link PermissionsService} (the same
 * code path as the upstream API). 401 unauthenticated, 403 on
 * permission denied, 404 on missing bundle / group.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/bundles")
@RequestScoped
@Tag(name = "File bundles (v2)")
public class FileBundleReferenceRest {

  @Inject
  FileBundleReferenceDAO fileBundleReferenceDAO;

  @Inject
  FileGroupDAO fileGroupDAO;

  @Inject
  FileGroupService fileGroupService;

  @Inject
  FileService fileService;

  @Inject
  PermissionsService permissionsService;

  @Inject
  EntityIdResolver entityIdResolver;

  @Inject
  ObjectMapper objectMapper;

  // ─── bundle ───────────────────────────────────────────────────────────────

  @GET
  @Path("/{bundleAppId}")
  @Operation(summary = "Get a FileBundleReference (with its groups) by appId.")
  @APIResponse(
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = FileBundleReferenceIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No FileBundleReference with that appId.")
  public Response getBundle(
    @PathParam("bundleAppId") String bundleAppId,
    @Context SecurityContext securityContext
  ) {
    FileBundleReference bundle = fileBundleReferenceDAO.findByAppId(bundleAppId);
    if (bundle == null) return Response.status(Response.Status.NOT_FOUND).build();
    Response gate = checkAccess(bundle, AccessType.Read, securityContext);
    if (gate != null) return gate;

    // Re-load with the groups populated (the appId match returns the
    // node + 1-hop neighbourhood already, but re-fetch the groups via
    // the DAO so we get them ordered by index).
    var groups = fileGroupDAO.findByBundleAppId(bundleAppId);
    bundle.setGroups(groups);
    return Response.ok(new FileBundleReferenceIO(bundle)).build();
  }

  // ─── groups ───────────────────────────────────────────────────────────────

  @GET
  @Path("/{bundleAppId}/groups")
  @Operation(summary = "List FileGroups under a bundle, ordered by ascending index.")
  @APIResponse(
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = FileGroupIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No FileBundleReference with that appId.")
  public Response listGroups(
    @PathParam("bundleAppId") String bundleAppId,
    @Context SecurityContext securityContext
  ) {
    FileBundleReference bundle = fileBundleReferenceDAO.findByAppId(bundleAppId);
    if (bundle == null) return Response.status(Response.Status.NOT_FOUND).build();
    Response gate = checkAccess(bundle, AccessType.Read, securityContext);
    if (gate != null) return gate;

    List<FileGroupIO> rows = fileGroupService.listGroups(bundleAppId).stream().map(FileGroupIO::new).toList();
    return Response.ok(rows).build();
  }

  @POST
  @Path("/{bundleAppId}/groups")
  @Operation(summary = "Create a new FileGroup under a bundle.")
  @APIResponse(
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = FileGroupIO.class))
  )
  @APIResponse(responseCode = "400", description = "name missing or blank.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No FileBundleReference with that appId.")
  public Response createGroup(
    @PathParam("bundleAppId") String bundleAppId,
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = CreateFileGroupIO.class)))
      CreateFileGroupIO body,
    @Context SecurityContext securityContext
  ) {
    if (body == null || body.getName() == null || body.getName().isBlank()) {
      return Response.status(Response.Status.BAD_REQUEST).entity("name is required and must be non-blank").build();
    }
    FileBundleReference bundle = fileBundleReferenceDAO.findByAppId(bundleAppId);
    if (bundle == null) return Response.status(Response.Status.NOT_FOUND).build();
    Response gate = checkAccess(bundle, AccessType.Write, securityContext);
    if (gate != null) return gate;

    try {
      FileGroup created = fileGroupService.createGroup(bundleAppId, body);
      return Response.status(Response.Status.CREATED).entity(new FileGroupIO(created)).build();
    } catch (BadRequestException bre) {
      return Response.status(Response.Status.BAD_REQUEST).entity(bre.getMessage()).build();
    } catch (NotFoundException nfe) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
  }

  @GET
  @Path("/{bundleAppId}/groups/{groupAppId}")
  @Operation(summary = "Get a single FileGroup with its files.")
  @APIResponse(
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = FileGroupIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No FileBundleReference / FileGroup with those appIds.")
  public Response getGroup(
    @PathParam("bundleAppId") String bundleAppId,
    @PathParam("groupAppId") String groupAppId,
    @Context SecurityContext securityContext
  ) {
    FileBundleReference bundle = fileBundleReferenceDAO.findByAppId(bundleAppId);
    if (bundle == null) return Response.status(Response.Status.NOT_FOUND).build();
    Response gate = checkAccess(bundle, AccessType.Read, securityContext);
    if (gate != null) return gate;

    FileGroup group = fileGroupService.getByAppId(groupAppId);
    if (group == null) return Response.status(Response.Status.NOT_FOUND).build();
    String parent = fileGroupService.findBundleAppIdForGroup(groupAppId);
    if (!bundleAppId.equals(parent)) return Response.status(Response.Status.NOT_FOUND).build();
    return Response.ok(new FileGroupIO(group)).build();
  }

  @PATCH
  @Path("/{bundleAppId}/groups/{groupAppId}")
  @Consumes({ "application/merge-patch+json", MediaType.APPLICATION_JSON })
  @Operation(
    summary = "RFC 7396 merge-patch on a FileGroup.",
    description = "Patchable fields: name, description, attributes, startedAt, endedAt, index. " +
    "Fields absent from the body are preserved; explicit null clears the field (except name)."
  )
  @APIResponse(
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = FileGroupIO.class))
  )
  @APIResponse(responseCode = "400", description = "Patch body invalid (e.g. name null/blank).")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No FileBundleReference / FileGroup with those appIds.")
  public Response patchGroup(
    @PathParam("bundleAppId") String bundleAppId,
    @PathParam("groupAppId") String groupAppId,
    @RequestBody(required = true, content = @Content(mediaType = "application/merge-patch+json")) JsonNode body,
    @Context SecurityContext securityContext
  ) {
    if (body == null || !body.isObject()) {
      return Response.status(Response.Status.BAD_REQUEST).entity("PATCH body must be a JSON object").build();
    }
    FileBundleReference bundle = fileBundleReferenceDAO.findByAppId(bundleAppId);
    if (bundle == null) return Response.status(Response.Status.NOT_FOUND).build();
    Response gate = checkAccess(bundle, AccessType.Write, securityContext);
    if (gate != null) return gate;

    String parent = fileGroupService.findBundleAppIdForGroup(groupAppId);
    if (!bundleAppId.equals(parent)) return Response.status(Response.Status.NOT_FOUND).build();

    try {
      Map<String, Object> patch = jsonNodeToMap(body);
      FileGroup updated = fileGroupService.patchGroup(groupAppId, patch);
      return Response.ok(new FileGroupIO(updated)).build();
    } catch (BadRequestException bre) {
      return Response.status(Response.Status.BAD_REQUEST).entity(bre.getMessage()).build();
    } catch (NotFoundException nfe) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
  }

  @DELETE
  @Path("/{bundleAppId}/groups/{groupAppId}")
  @Operation(
    summary = "Delete a FileGroup.",
    description = "Refuses with 400 if the group has files (pass ?force=true to delete the group AND its files). " +
    "Refuses with 400 if the group is the last remaining group of its bundle (would orphan all files)."
  )
  @APIResponse(responseCode = "204", description = "Deleted.")
  @APIResponse(responseCode = "400", description = "Has files (pass ?force=true) OR is the last group.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No FileBundleReference / FileGroup with those appIds.")
  public Response deleteGroup(
    @PathParam("bundleAppId") String bundleAppId,
    @PathParam("groupAppId") String groupAppId,
    @QueryParam("force") boolean force,
    @Context SecurityContext securityContext
  ) {
    FileBundleReference bundle = fileBundleReferenceDAO.findByAppId(bundleAppId);
    if (bundle == null) return Response.status(Response.Status.NOT_FOUND).build();
    Response gate = checkAccess(bundle, AccessType.Write, securityContext);
    if (gate != null) return gate;

    String parent = fileGroupService.findBundleAppIdForGroup(groupAppId);
    if (!bundleAppId.equals(parent)) return Response.status(Response.Status.NOT_FOUND).build();

    try {
      fileGroupService.deleteGroup(groupAppId, force);
      return Response.noContent().build();
    } catch (BadRequestException bre) {
      return Response.status(Response.Status.BAD_REQUEST).entity(bre.getMessage()).build();
    } catch (NotFoundException nfe) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
  }

  // ─── file upload into a group ─────────────────────────────────────────────

  @POST
  @Path("/{bundleAppId}/groups/{groupAppId}/files")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @Operation(summary = "Upload one file into a specific FileGroup.")
  @APIResponse(
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = ShepardFile.class))
  )
  @APIResponse(responseCode = "400", description = "Missing file part / bundle has no FileContainer.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No FileBundleReference / FileGroup with those appIds.")
  public Response uploadFileIntoGroup(
    @PathParam("bundleAppId") String bundleAppId,
    @PathParam("groupAppId") String groupAppId,
    @RestForm("file") FileUpload upload,
    @Context SecurityContext securityContext
  ) {
    FileBundleReference bundle = fileBundleReferenceDAO.findByAppId(bundleAppId);
    if (bundle == null) return Response.status(Response.Status.NOT_FOUND).build();
    Response gate = checkAccess(bundle, AccessType.Write, securityContext);
    if (gate != null) return gate;

    String parent = fileGroupService.findBundleAppIdForGroup(groupAppId);
    if (!bundleAppId.equals(parent)) return Response.status(Response.Status.NOT_FOUND).build();

    if (upload == null || upload.uploadedFile() == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("file part is required").build();
    }
    if (bundle.getFileContainer() == null || bundle.getFileContainer().getMongoId() == null) {
      return Response.status(Response.Status.BAD_REQUEST)
        .entity("Bundle has no underlying FileContainer; cannot accept uploads.")
        .build();
    }

    File file = upload.uploadedFile().toFile();
    try (InputStream is = new FileInputStream(file)) {
      ShepardFile saved = fileService.createFile(bundle.getFileContainer().getMongoId(), upload.fileName(), is);
      fileGroupService.attachFile(groupAppId, saved);
      return Response.status(Response.Status.CREATED).entity(saved).build();
    } catch (IOException ioe) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ioe.getMessage()).build();
    }
  }

  // ─── helpers ──────────────────────────────────────────────────────────────

  /**
   * Returns null when access is allowed, otherwise a short-circuit
   * Response (401 / 403) to abort with. The bundle is required; this
   * method assumes the caller has already 404'd if it didn't exist.
   */
  private Response checkAccess(FileBundleReference bundle, AccessType accessType, SecurityContext securityContext) {
    String caller = securityContext.getUserPrincipal() != null ? securityContext.getUserPrincipal().getName() : null;
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();
    if (bundle.getDataObject() == null) {
      // Inconsistent graph — treat as 404 (caller can't tell whether
      // the bundle exists for this DataObject anyway).
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    long dataObjectOgmId = bundle.getDataObject().getId();
    if (!permissionsService.isAccessTypeAllowedForUser(dataObjectOgmId, accessType, caller)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    return null;
  }

  /**
   * Convert a Jackson {@link JsonNode} object into a plain
   * {@code Map<String, Object>} preserving null values (so
   * RFC 7396 explicit-null clears flow through to the patch service).
   */
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
