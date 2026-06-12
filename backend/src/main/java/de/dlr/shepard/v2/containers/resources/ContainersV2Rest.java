package de.dlr.shepard.v2.containers.resources;

import com.fasterxml.jackson.databind.JsonNode;
import de.dlr.shepard.auth.permission.io.PermissionsIO;
import de.dlr.shepard.auth.permission.model.Roles;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.neo4j.entities.BasicContainer;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.v2.containers.io.ContainerV2IO;
import de.dlr.shepard.v2.containers.services.ContainersV2Service;
import de.dlr.shepard.v2.file.io.PayloadVersionIO;
import de.dlr.shepard.v2.references.util.JsonNodeMaps;
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
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * V2CONV-A3 — the unified {@code /v2/containers} REST surface that converges
 * the homogeneous create / get-one / patch / delete / list operations for every
 * container kind. The direct sibling of {@code /v2/references} (V2CONV-A2).
 *
 * <p>Before A3 there was no {@code /v2/} container CRUD at all — the only
 * container create/get/delete/list lived on the frozen, byte-compatible v1
 * surface ({@code /shepard/api/fileContainers}, {@code …/timeseriesContainers},
 * {@code …/structuredDataContainers}) keyed by numeric id. A3 adds the first
 * {@code /v2/} container CRUD, keyed by {@code appId}, dispatched by a
 * {@link de.dlr.shepard.v2.containers.spi.ContainerKindHandler} SPI. The v1
 * surface is untouched (frozen for third-party upstream clients).
 *
 * <p>Kind-specific operations stay at their own paths and are NOT converged
 * here: timeseries data / chart-view / anomaly endpoints, the file-container
 * payload / content / presigned-url endpoints, the hdf browse surface, etc.
 *
 * <h2>Routes</h2>
 * <ul>
 *   <li>{@code POST   /v2/containers?kind=…} — create a container of {@code kind}.
 *       Body is the per-kind create payload (today {@code {name}} for every core
 *       kind).</li>
 *   <li>{@code GET    /v2/containers/{appId}} — the entity self-describes its kind;
 *       returns the unified {@link ContainerV2IO}.</li>
 *   <li>{@code PATCH  /v2/containers/{appId}} — RFC 7396 merge-patch ({@code name},
 *       {@code status}), dispatched to the owning kind's patcher.</li>
 *   <li>{@code DELETE /v2/containers/{appId}} — dispatched to the owning kind's deleter.</li>
 *   <li>{@code GET    /v2/containers?kind=…[&name=…]} — list/filter; returns
 *       {@code ContainerV2IO[]}.</li>
 * </ul>
 *
 * <p>Identifiers are {@code appId} (UUID v7) strings throughout; numeric Neo4j
 * ids never appear on the wire. Create/list require authentication; get is gated
 * Read and patch/delete Write on the resolved container's own id.
 *
 * <p>Plugin kinds: {@code ?kind=hdf} returns 400 ("uninstalled kind") until the
 * hdf5 plugin ships its own {@code ContainerKindHandler} — tracked as
 * {@code PLUGIN-CONTAINER-HANDLER-HDF} in {@code aidocs/16}.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/containers")
@RequestScoped
@Tag(name = "Containers (v2 unified)")
public class ContainersV2Rest {

  private static final String PROBLEM_TYPE_BAD_REQUEST = "/problems/containers.bad-request";
  private static final String PROBLEM_TYPE_UNSUPPORTED = "/problems/containers.unsupported-media-type";

  @Inject
  ContainersV2Service containersService;

  @Inject
  PermissionsService permissionsService;

  // ─── create ────────────────────────────────────────────────────────────

  @POST
  @Operation(
    operationId = "createContainer",
    summary = "Create a container of the given kind.",
    description =
      "Creates a container of `kind` (file | timeseries | structured-data). The " +
      "body is the per-kind create payload — today `{name}` for every core kind. " +
      "Plugin kinds (e.g. hdf) reject with 400 until their module ships a " +
      "ContainerKindHandler.\n\nAuth: any authenticated user (containers are " +
      "top-level; the creator becomes owner)."
  )
  @APIResponse(
    responseCode = "201",
    description = "Created; body is the unified ContainerV2IO.",
    content = @Content(schema = @Schema(implementation = ContainerV2IO.class))
  )
  @APIResponse(responseCode = "400", description = "Unknown/uninstalled kind or invalid body.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response create(@QueryParam("kind") String kind, JsonNode body, @Context SecurityContext sc) {
    String caller = callerOrNull(sc);
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();
    if (kind == null || kind.isBlank()) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Missing query parameter", Response.Status.BAD_REQUEST, "kind query parameter is required");
    }
    Map<String, Object> map = body == null ? Map.of() : JsonNodeMaps.toMap(body);
    try {
      ContainerV2IO created = containersService.create(kind, map);
      return Response.status(Response.Status.CREATED).entity(created).build();
    } catch (BadRequestException bre) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Bad request", Response.Status.BAD_REQUEST, bre.getMessage());
    }
  }

  // ─── get-one ───────────────────────────────────────────────────────────

  @GET
  @Path("/{appId}")
  @Operation(
    operationId = "getContainer",
    summary = "Get any container by appId; the entity self-describes its kind.",
    description =
      "Resolves the container (of any kind) at `appId` and returns the unified " +
      "ContainerV2IO, including the `kind` discriminator and the per-kind " +
      "read-only `payload` (e.g. `oid`).\n\nAuth: Read on the container."
  )
  @APIResponse(
    responseCode = "200",
    description = "The unified ContainerV2IO.",
    content = @Content(schema = @Schema(implementation = ContainerV2IO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read on the container.")
  @APIResponse(responseCode = "404", description = "No container with that appId.")
  public Response get(@PathParam("appId") String appId, @Context SecurityContext sc) {
    String caller = callerOrNull(sc);
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();
    var resolved = containersService.resolveByAppId(appId);
    if (resolved.isEmpty()) return Response.status(Response.Status.NOT_FOUND).build();
    Response gate = gate(resolved.get().container(), AccessType.Read, caller);
    if (gate != null) return gate;
    return Response.ok(resolved.get().handler().toIO(resolved.get().container())).build();
  }

  // ─── file download (kind-specific single-file payload) ───────────────────

  @GET
  @Path("/{appId}/file")
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  @Operation(
    summary = "Download the single-file payload of a container by appId.",
    description =
      "Streams the raw single-file payload for the container at `appId`, when its " +
      "kind exposes one (today: `hdf` → the raw HDF5 from HSDS). `Range` requests " +
      "are forwarded to the underlying store; a 206 Partial Content + `Content-Range` " +
      "is relayed verbatim. Kinds without a single-file payload (timeseries, " +
      "structured-data) answer 415.\n\nAuth: Read on the container."
  )
  @APIResponse(responseCode = "200", description = "The full file payload.")
  @APIResponse(responseCode = "206", description = "Partial content (Range honoured by the store).")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read on the container.")
  @APIResponse(responseCode = "404", description = "No container with that appId.")
  @APIResponse(responseCode = "415", description = "This container kind has no single-file payload.")
  public Response downloadFile(
    @PathParam("appId") String appId,
    @jakarta.ws.rs.HeaderParam("Range") String rangeHeader,
    @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();
    var resolved = containersService.resolveByAppId(appId);
    if (resolved.isEmpty()) return Response.status(Response.Status.NOT_FOUND).build();
    Response gate = gate(resolved.get().container(), AccessType.Read, caller);
    if (gate != null) return gate;

    var downloadOpt = resolved.get().handler().downloadFile(appId, rangeHeader);
    if (downloadOpt.isEmpty()) {
      return problem(PROBLEM_TYPE_UNSUPPORTED, "No single-file payload", Response.Status.UNSUPPORTED_MEDIA_TYPE,
          "Container kind '" + resolved.get().handler().kind() + "' has no single-file payload");
    }
    var download = downloadOpt.get();

    String mediaType = download.mediaType() == null || download.mediaType().isBlank()
      ? MediaType.APPLICATION_OCTET_STREAM
      : download.mediaType();

    jakarta.ws.rs.core.StreamingOutput streaming = out -> {
      try (download) {
        download.body().transferTo(out);
      } catch (Exception e) {
        throw new java.io.IOException("Failed streaming container file for appId " + appId, e);
      }
    };

    Response.ResponseBuilder builder = Response.status(download.status())
      .entity(streaming)
      .type(mediaType)
      .header("Content-Disposition", buildContentDisposition(download.fileName()));

    if (download.contentLength() >= 0) {
      builder.header("Content-Length", download.contentLength());
    }
    if (download.contentRange() != null) {
      builder.header("Content-Range", download.contentRange());
    }
    builder.header("Accept-Ranges", download.acceptRanges() != null ? download.acceptRanges() : "bytes");
    return builder.build();
  }

  /**
   * Build a safe {@code Content-Disposition: attachment} header value. Uses RFC
   * 5987 {@code filename*=UTF-8''<percent-encoded>} so a Unicode container name
   * transports safely, with an ASCII {@code filename=} fallback for old clients.
   */
  private static String buildContentDisposition(String name) {
    String safeName = (name == null || name.isBlank()) ? "container" : name;
    String asciiName = safeName.replaceAll("[^\\x20-\\x7E]", "_").replace('"', '_').replace('/', '_');
    String encoded = java.net.URLEncoder
      .encode(safeName, java.nio.charset.StandardCharsets.UTF_8)
      .replace("+", "%20");
    return "attachment; filename=\"" + asciiName + "\"; filename*=UTF-8''" + encoded;
  }

  // ─── patch ─────────────────────────────────────────────────────────────

  @PATCH
  @Path("/{appId}")
  @Consumes({ "application/merge-patch+json", MediaType.APPLICATION_JSON })
  @Operation(
    operationId = "patchContainer",
    summary = "RFC 7396 merge-patch any container by appId; dispatched by kind.",
    description =
      "Applies a merge-patch to the container at `appId` (`name`, `status`). " +
      "Absent keys are left unchanged.\n\nAuth: Write on the container."
  )
  @APIResponse(
    responseCode = "200",
    description = "The post-patch ContainerV2IO.",
    content = @Content(schema = @Schema(implementation = ContainerV2IO.class))
  )
  @APIResponse(responseCode = "400", description = "Body is not a JSON object, or field validation failed.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write on the container.")
  @APIResponse(responseCode = "404", description = "No container with that appId.")
  public Response patch(
    @PathParam("appId") String appId,
    @RequestBody(required = true, content = @Content(mediaType = "application/merge-patch+json")) JsonNode body,
    @Context SecurityContext sc
  ) {
    if (body == null || !body.isObject()) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Invalid request body", Response.Status.BAD_REQUEST, "PATCH body must be a JSON object");
    }
    String caller = callerOrNull(sc);
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();
    var resolved = containersService.resolveByAppId(appId);
    if (resolved.isEmpty()) return Response.status(Response.Status.NOT_FOUND).build();
    Response gate = gate(resolved.get().container(), AccessType.Write, caller);
    if (gate != null) return gate;
    try {
      ContainerV2IO updated = containersService.patchByAppId(appId, JsonNodeMaps.toMap(body));
      return Response.ok(updated).build();
    } catch (BadRequestException bre) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Bad request", Response.Status.BAD_REQUEST, bre.getMessage());
    } catch (NotFoundException nfe) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
  }

  // ─── delete ────────────────────────────────────────────────────────────

  @DELETE
  @Path("/{appId}")
  @Operation(
    operationId = "deleteContainer",
    summary = "Delete any container by appId; dispatched by kind.",
    description = "Deletes the container at `appId` via the owning kind's deleter.\n\nAuth: Write on the container."
  )
  @APIResponse(responseCode = "204", description = "Deleted.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write on the container.")
  @APIResponse(responseCode = "404", description = "No container with that appId.")
  public Response delete(@PathParam("appId") String appId, @Context SecurityContext sc) {
    String caller = callerOrNull(sc);
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();
    var resolved = containersService.resolveByAppId(appId);
    if (resolved.isEmpty()) return Response.status(Response.Status.NOT_FOUND).build();
    Response gate = gate(resolved.get().container(), AccessType.Write, caller);
    if (gate != null) return gate;
    try {
      containersService.deleteByAppId(appId);
      return Response.noContent().build();
    } catch (NotFoundException nfe) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
  }

  // ─── list / filter ─────────────────────────────────────────────────────

  @GET
  @Operation(
    operationId = "listContainers",
    summary = "List containers of a kind, optionally filtered by name.",
    description =
      "Returns every container of `kind` the caller may read, as ContainerV2IO[]. " +
      "An optional `name` query param narrows by substring.\n\nAuth: " +
      "authenticated; per-container Read is enforced by the underlying list query."
  )
  @APIResponse(
    responseCode = "200",
    description = "List of ContainerV2IO (may be empty).",
    content = @Content(
      mediaType = MediaType.APPLICATION_JSON,
      schema = @Schema(type = SchemaType.ARRAY, implementation = ContainerV2IO.class)
    )
  )
  @APIResponse(responseCode = "400", description = "Missing/unknown kind.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response list(
    @QueryParam("kind") String kind,
    @QueryParam("name") String name,
    @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();
    if (kind == null || kind.isBlank()) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Missing query parameter", Response.Status.BAD_REQUEST, "kind query parameter is required");
    }
    try {
      List<ContainerV2IO> containers = containersService.list(kind, name);
      return Response.ok(containers).build();
    } catch (BadRequestException bre) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Bad request", Response.Status.BAD_REQUEST, bre.getMessage());
    }
  }

  // ─── payload versioning ────────────────────────────────────────────────────

  @GET
  @Path("/{appId}/files/{fileName}/versions")
  @Operation(
    summary = "List all payload versions for a named file in a container by appId.",
    description =
      "Returns the complete upload history for the file identified by `fileName` within " +
      "the container at `appId`, ordered by `versionNumber` ascending (oldest first). " +
      "Supported for `file` and `structured-data` kind containers. Other kinds answer 415.\n\n" +
      "Auth: Read on the container. (APISIMP-PV-UNIFY — replaces per-kind " +
      "`/v2/file-containers/{appId}/files/{name}/versions` and " +
      "`/v2/structured-data-containers/{appId}/files/{name}/versions`.)"
  )
  @APIResponse(
    responseCode = "200",
    description = "Version list (may be empty).",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = PayloadVersionIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read on the container.")
  @APIResponse(responseCode = "404", description = "No container with that appId.")
  @APIResponse(responseCode = "415", description = "This container kind does not support file-payload versioning.")
  public Response listVersions(
    @PathParam("appId") String appId,
    @PathParam("fileName") String fileName,
    @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();
    var resolved = containersService.resolveByAppId(appId);
    if (resolved.isEmpty()) return Response.status(Response.Status.NOT_FOUND).build();
    Response gate = gate(resolved.get().container(), AccessType.Read, caller);
    if (gate != null) return gate;

    var versionsOpt = resolved.get().handler().listVersions(appId, fileName);
    if (versionsOpt.isEmpty()) {
      return Response.status(Response.Status.UNSUPPORTED_MEDIA_TYPE)
        .entity("Container kind '" + resolved.get().handler().kind() + "' does not support payload versioning")
        .build();
    }
    return Response.ok(versionsOpt.get()).build();
  }

  // ─── permissions ───────────────────────────────────────────────────────

  @GET
  @Path("/{appId}/permissions")
  @Operation(
    summary = "Get permissions for a container by appId.",
    description =
      "Returns the current permissions for the container at `appId`.\n\n" +
      "Auth: Manage on the container (CONTAINER-PERMS-V2)."
  )
  @APIResponse(
    responseCode = "200",
    description = "Current permissions.",
    content = @Content(schema = @Schema(implementation = PermissionsIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Manage on the container.")
  @APIResponse(responseCode = "404", description = "No container with that appId.")
  public Response getPermissions(@PathParam("appId") String appId, @Context SecurityContext sc) {
    String caller = callerOrNull(sc);
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();
    var resolved = containersService.resolveByAppId(appId);
    if (resolved.isEmpty()) return Response.status(Response.Status.NOT_FOUND).build();
    Response gate = gate(resolved.get().container(), AccessType.Manage, caller);
    if (gate != null) return gate;
    Long id = resolved.get().container().getId();
    var perms = permissionsService.getPermissionsOfEntityOptional(id);
    if (perms.isEmpty()) return Response.status(Response.Status.NOT_FOUND).build();
    return Response.ok(new PermissionsIO(perms.get())).build();
  }

  @PATCH
  @Path("/{appId}/permissions")
  @Consumes({ "application/merge-patch+json", MediaType.APPLICATION_JSON })
  @Operation(
    summary = "Merge-patch permissions for a container by appId.",
    description =
      "RFC 7396 merge-patch the permissions for the container at `appId`. " +
      "Only fields present in the body are applied; absent fields are left unchanged.\n\n" +
      "Auth: Manage on the container (CONTAINER-PERMS-V2)."
  )
  @APIResponse(
    responseCode = "200",
    description = "Post-patch permissions.",
    content = @Content(schema = @Schema(implementation = PermissionsIO.class))
  )
  @APIResponse(responseCode = "400", description = "Body is not a JSON object.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Manage on the container.")
  @APIResponse(responseCode = "404", description = "No container with that appId.")
  public Response patchPermissions(
    @PathParam("appId") String appId,
    @RequestBody(required = true, content = @Content(mediaType = "application/merge-patch+json")) JsonNode body,
    @Context SecurityContext sc
  ) {
    if (body == null || !body.isObject()) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Invalid request body", Response.Status.BAD_REQUEST, "PATCH body must be a JSON object");
    }
    String caller = callerOrNull(sc);
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();
    var resolved = containersService.resolveByAppId(appId);
    if (resolved.isEmpty()) return Response.status(Response.Status.NOT_FOUND).build();
    Response gate = gate(resolved.get().container(), AccessType.Manage, caller);
    if (gate != null) return gate;
    Long id = resolved.get().container().getId();
    var existing = permissionsService.getPermissionsOfEntityOptional(id);
    if (existing.isEmpty()) return Response.status(Response.Status.NOT_FOUND).build();
    // Merge-patch: apply only the fields present in the body onto the current PermissionsIO
    PermissionsIO current = new PermissionsIO(existing.get());
    Map<String, Object> patch = JsonNodeMaps.toMap(body);
    if (patch.containsKey("permissionType")) {
      var pt = patch.get("permissionType");
      if (pt instanceof String ptStr) {
        current.setPermissionType(de.dlr.shepard.common.util.PermissionType.valueOf(ptStr));
      }
    }
    if (patch.containsKey("owner") && patch.get("owner") instanceof String ownerStr) {
      current.setOwner(ownerStr);
    }
    if (patch.containsKey("reader") && patch.get("reader") instanceof java.util.List<?> readerList) {
      current.setReader(readerList.stream().map(Object::toString).toArray(String[]::new));
    }
    if (patch.containsKey("writer") && patch.get("writer") instanceof java.util.List<?> writerList) {
      current.setWriter(writerList.stream().map(Object::toString).toArray(String[]::new));
    }
    if (patch.containsKey("manager") && patch.get("manager") instanceof java.util.List<?> managerList) {
      current.setManager(managerList.stream().map(Object::toString).toArray(String[]::new));
    }
    if (patch.containsKey("readerGroupIds") && patch.get("readerGroupIds") instanceof java.util.List<?> rgl) {
      current.setReaderGroupIds(rgl.stream().mapToLong(v -> Long.parseLong(v.toString())).toArray());
    }
    if (patch.containsKey("writerGroupIds") && patch.get("writerGroupIds") instanceof java.util.List<?> wgl) {
      current.setWriterGroupIds(wgl.stream().mapToLong(v -> Long.parseLong(v.toString())).toArray());
    }
    var updated = permissionsService.updatePermissionsByNeo4jId(current, id);
    return Response.ok(new PermissionsIO(updated)).build();
  }

  // ─── roles ─────────────────────────────────────────────────────────────

  @GET
  @Path("/{appId}/roles")
  @Operation(
    operationId = "getContainerRoles",
    summary = "Get the calling user's roles on a container by appId.",
    description =
      "Returns the calling user's roles (owner / manager / writer / reader) on " +
      "the container at `appId`. This is the v2 equivalent of the per-kind v1 " +
      "`GET /shepard/api/{kind}Containers/{id}/roles` endpoints, converged onto " +
      "the unified container surface (V2-SWEEP-003).\n\n" +
      "Auth: Read on the container."
  )
  @APIResponse(
    responseCode = "200",
    description = "Roles of the calling user on this container.",
    content = @Content(schema = @Schema(implementation = Roles.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read on the container.")
  @APIResponse(responseCode = "404", description = "No container with that appId.")
  public Response getRoles(@PathParam("appId") String appId, @Context SecurityContext sc) {
    String caller = callerOrNull(sc);
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();
    var resolved = containersService.resolveByAppId(appId);
    if (resolved.isEmpty()) return Response.status(Response.Status.NOT_FOUND).build();
    Response gate = gate(resolved.get().container(), AccessType.Read, caller);
    if (gate != null) return gate;
    Long id = resolved.get().container().getId();
    return Response.ok(permissionsService.getUserRolesOnEntity(id, caller)).build();
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
   * Gate access on the container's own numeric id. Returns null when access is
   * allowed; otherwise the short-circuit 403/404 Response.
   */
  private Response gate(BasicContainer container, AccessType accessType, String caller) {
    Long id = container.getId();
    if (id == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    if (!permissionsService.isAccessTypeAllowedForUser(id, accessType, caller)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    return null;
  }
}
