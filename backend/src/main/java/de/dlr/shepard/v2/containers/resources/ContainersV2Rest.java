package de.dlr.shepard.v2.containers.resources;

import com.fasterxml.jackson.databind.JsonNode;
import de.dlr.shepard.auth.permission.io.PermissionsIO;
import de.dlr.shepard.auth.users.services.UserGroupService;
import de.dlr.shepard.auth.permission.model.Roles;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.v2.common.ProblemResponse;
import de.dlr.shepard.common.neo4j.entities.BasicContainer;
import de.dlr.shepard.v2.filecontainer.io.PresignedDownloadUrlIO;
import de.dlr.shepard.v2.filecontainer.io.PresignedUploadRequestIO;
import de.dlr.shepard.v2.filecontainer.io.PresignedUploadUrlIO;
import de.dlr.shepard.v2.filecontainer.io.UploadCommitIO;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.semantic.io.SemanticAnnotationIO;
import de.dlr.shepard.v2.containers.io.ContainerStatsIO;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import de.dlr.shepard.v2.containers.io.ContainerV2IO;
import de.dlr.shepard.v2.containers.services.ContainersV2Service;
import de.dlr.shepard.v2.file.io.PayloadVersionIO;
import de.dlr.shepard.v2.integrity.SafeDeleteConflict;
import de.dlr.shepard.v2.references.util.JsonNodeMaps;
import de.dlr.shepard.data.timeseries.io.TimeseriesWithDataPoints;
import de.dlr.shepard.v2.timeseries.io.TimeseriesAnnotationIO;
import de.dlr.shepard.v2.timeseriescontainer.io.BulkChannelDataRequestIO;
import de.dlr.shepard.v2.timeseriescontainer.io.CopyIngestRequestIO;
import de.dlr.shepard.v2.timeseriescontainer.io.SpatialRolesIO;
import de.dlr.shepard.v2.timeseriescontainer.io.TimeseriesChannelV2IO;
import de.dlr.shepard.v2.timeseriescontainer.io.TimeseriesContainerChartViewIO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import static de.dlr.shepard.v2.common.ProblemResponse.problem;

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
@Tag(name = "Containers")
public class ContainersV2Rest {

  private static final String PROBLEM_TYPE_BAD_REQUEST = "/problems/containers.bad-request";
  private static final String PROBLEM_TYPE_NOT_FOUND = "/problems/containers.not-found";
  private static final String PROBLEM_TYPE_UNAUTHORIZED = "/problems/containers.unauthorized";
  private static final String PROBLEM_TYPE_FORBIDDEN = "/problems/containers.forbidden";
  private static final String PROBLEM_TYPE_UNSUPPORTED = "/problems/containers.unsupported-media-type";

  @Inject
  ContainersV2Service containersService;

  @Inject
  PermissionsService permissionsService;

  @Inject
  UserGroupService userGroupService;

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
  public Response create(
    @Parameter(required = true, description = "Container kind to create: `file` | `timeseries` | `structured-data`. Returns 400 for unknown or uninstalled kinds.")
    @QueryParam("kind") String kind,
    JsonNode body,
    @Context SecurityContext sc) {
    String caller = callerOrNull(sc);
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED, "No valid JWT or API key was provided");
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
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED, "No valid JWT or API key was provided");
    var resolved = containersService.resolveByAppId(appId);
    if (resolved.isEmpty()) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "No container found for appId");
    Response gate = gate(resolved.get().container(), AccessType.Read, caller);
    if (gate != null) return gate;
    return Response.ok(resolved.get().handler().toIO(resolved.get().container())).build();
  }

  // ─── file download (kind-specific single-file payload) ───────────────────

  @GET
  @Path("/{appId}/file")
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  @Operation(
    operationId = "downloadFile",
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
    @HeaderParam("Range") String rangeHeader,
    @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED, "No valid JWT or API key was provided");
    var resolved = containersService.resolveByAppId(appId);
    if (resolved.isEmpty()) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "No container found for appId");
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
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED, "No valid JWT or API key was provided");
    var resolved = containersService.resolveByAppId(appId);
    if (resolved.isEmpty()) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "No container found for appId");
    Response gate = gate(resolved.get().container(), AccessType.Write, caller);
    if (gate != null) return gate;
    try {
      ContainerV2IO updated = containersService.patchByAppId(appId, JsonNodeMaps.toMap(body));
      return Response.ok(updated).build();
    } catch (BadRequestException bre) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Bad request", Response.Status.BAD_REQUEST, bre.getMessage());
    } catch (NotFoundException nfe) {
      return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, nfe.getMessage() != null ? nfe.getMessage() : "Container not found");
    }
  }

  // ─── put (full-replace metadata) ───────────────────────────────────────

  @PUT
  @Path("/{appId}")
  @Operation(
    operationId = "putContainer",
    summary = "P21-V2-METADATA-EDIT: full-replace editable metadata of any container by appId.",
    description =
      "Replaces all mutable metadata fields on the container at `appId`. `name` is " +
      "required; `status` is applied from the body and reset to null when absent " +
      "(unlike PATCH which leaves absent keys unchanged). Kind-specific payload fields " +
      "(e.g. OID, default collection) are read-only and are ignored.\n\nAuth: Write " +
      "on the container."
  )
  @APIResponse(
    responseCode = "200",
    description = "The post-put ContainerV2IO.",
    content = @Content(schema = @Schema(implementation = ContainerV2IO.class))
  )
  @APIResponse(responseCode = "400", description = "Body is not a JSON object, name is missing or blank, or status value is invalid.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write on the container.")
  @APIResponse(responseCode = "404", description = "No container with that appId.")
  public Response put(
    @PathParam("appId") String appId,
    @RequestBody(required = true, content = @Content(mediaType = MediaType.APPLICATION_JSON)) JsonNode body,
    @Context SecurityContext sc
  ) {
    if (body == null || !body.isObject()) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Invalid request body", Response.Status.BAD_REQUEST, "PUT body must be a JSON object");
    }
    String caller = callerOrNull(sc);
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED, "No valid JWT or API key was provided");
    var resolved = containersService.resolveByAppId(appId);
    if (resolved.isEmpty()) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "No container found for appId");
    Response gate = gate(resolved.get().container(), AccessType.Write, caller);
    if (gate != null) return gate;
    try {
      ContainerV2IO updated = containersService.putByAppId(appId, JsonNodeMaps.toMap(body));
      return Response.ok(updated).build();
    } catch (BadRequestException bre) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Bad request", Response.Status.BAD_REQUEST, bre.getMessage());
    } catch (NotFoundException nfe) {
      return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, nfe.getMessage() != null ? nfe.getMessage() : "Container not found");
    }
  }

  // ─── delete ────────────────────────────────────────────────────────────

  @DELETE
  @Path("/{appId}")
  @Operation(
    operationId = "deleteContainer",
    summary = "Delete any container by appId; dispatched by kind.",
    description =
      "Deletes the container at `appId` via the owning kind's deleter. Safe-delete " +
      "by default: returns 409 if the container has active DataObject references. " +
      "Pass `?force=true` to delete regardless (surviving references will be " +
      "orphaned, matching the upstream v1 behaviour).\n\nAuth: Write on the container."
  )
  @APIResponse(responseCode = "204", description = "Deleted.")
  @APIResponse(
    responseCode = "409",
    description = "Container has active DataObject references; retry with ?force=true to force-delete.",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = SafeDeleteConflict.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write on the container.")
  @APIResponse(responseCode = "404", description = "No container with that appId.")
  public Response delete(
    @PathParam("appId") String appId,
    @Parameter(description = "When `true`, deletes the container even if it has active DataObject references (those references are orphaned). Defaults to `false` (safe-delete: 409 if references exist).")
    @QueryParam("force") @DefaultValue("false") boolean force,
    @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED, "No valid JWT or API key was provided");
    var resolved = containersService.resolveByAppId(appId);
    if (resolved.isEmpty()) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "No container found for appId");
    Response gate = gate(resolved.get().container(), AccessType.Write, caller);
    if (gate != null) return gate;
    if (!force) {
      List<String> linkedAppIds = resolved.get().handler().findLinkedDataObjectAppIds(appId)
        .orElse(null);
      if (linkedAppIds != null && !linkedAppIds.isEmpty()) {
        List<String> sample = linkedAppIds.stream()
          .filter(Objects::nonNull)
          .limit(SafeDeleteConflict.SAMPLE_LIMIT)
          .toList();
        return Response.status(Response.Status.CONFLICT)
          .type("application/problem+json")
          .entity(new SafeDeleteConflict(linkedAppIds.size(), sample))
          .build();
      }
    }
    try {
      containersService.deleteByAppId(appId);
      return Response.noContent().build();
    } catch (NotFoundException nfe) {
      return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "Container not found during deletion");
    }
  }

  // ─── list / filter ─────────────────────────────────────────────────────

  @GET
  @Operation(
    operationId = "listContainers",
    summary = "List containers of a kind, optionally filtered by text (q).",
    description =
      "Returns every container of `kind` the caller may read, as ContainerV2IO[]. " +
      "An optional `q` query param narrows by substring (case-sensitive).\n\n" +
      "**Deprecated alias:** `?name=` is accepted for one release cycle and produces a " +
      "`Deprecation: true` response header; migrate callers to `?q=`.\n\n" +
      "Pagination (APISIMP-CONTAINERS-LIST-NO-PAGINATION): supply both `page` (0-based) and `pageSize` " +
      "(1–200) to slice the result. Omitting either returns all containers. " +
      "`X-Total-Count` header carries the total before paging.\n\nAuth: " +
      "authenticated; per-container Read is enforced by the underlying list query."
  )
  @APIResponse(
    responseCode = "200",
    description = "Paged envelope: items + total + page + pageSize. Header X-Total-Count = total count before paging (kept during deprecation window, APISIMP-PAGINATION-ENVELOPE).",
    content = @Content(
      mediaType = MediaType.APPLICATION_JSON,
      schema = @Schema(implementation = PagedResponseIO.class)
    ),
    headers = @Header(
      name = "X-Total-Count",
      description = "Total element count before paging.",
      schema = @Schema(type = SchemaType.INTEGER)
    )
  )
  @APIResponse(responseCode = "400", description = "Missing/unknown kind.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response list(
    @Parameter(required = true, description = "Container kind to list: `file` | `timeseries` | `structured-data`. Returns 400 when absent or unrecognised.")
    @QueryParam("kind") String kind,
    @Parameter(description = "Optional substring filter on container name. Case-sensitive. Omit to return all containers of the given kind.")
    @QueryParam("q") String q,
    @Parameter(description = "Deprecated alias for `q`. Accepted for one release cycle; prefer `q`. Presence adds `Deprecation: true` response header.")
    @Deprecated @QueryParam("name") String nameLegacy,
    @Parameter(description = "Zero-based page index (default 0).")
    @QueryParam("page") @DefaultValue("0") @PositiveOrZero int page,
    @Parameter(description = "Page size, 1–200 (default 50).")
    @QueryParam("pageSize") @DefaultValue("50") @Min(1) @Max(200) int pageSize,
    @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED, "No valid JWT or API key was provided");
    if (kind == null || kind.isBlank()) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Missing query parameter", Response.Status.BAD_REQUEST, "kind query parameter is required");
    }
    try {
      String filter = q != null ? q : nameLegacy;
      int skip = (int) Math.min((long) page * pageSize, Integer.MAX_VALUE);
      int total = containersService.count(kind, filter);
      List<ContainerV2IO> pageItems = containersService.list(kind, filter, skip, pageSize);
      Response.ResponseBuilder rb = Response.ok(new PagedResponseIO<>(pageItems, total, page, pageSize))
          .header("X-Total-Count", total);  // kept during deprecation window (APISIMP-PAGINATION-ENVELOPE)
      if (nameLegacy != null && q == null) rb = rb.header("Deprecation", "true");
      return rb.build();
    } catch (BadRequestException bre) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Bad request", Response.Status.BAD_REQUEST, bre.getMessage());
    }
  }

  // ─── payload versioning ────────────────────────────────────────────────────

  @GET
  @Path("/{appId}/files/{fileName}/versions")
  @Operation(
    operationId = "listVersions",
    summary = "List all payload versions for a named file in a container by appId.",
    description =
      "Returns the complete upload history for the file identified by `fileName` within " +
      "the container at `appId`, ordered by `versionNumber` ascending (oldest first). " +
      "Supported for `file` and `structured-data` kind containers. Other kinds answer 415.\n\n" +
      "Auth: Read on the container."
  )
  @APIResponse(
    responseCode = "200",
    description = "Version list (may be empty).",
    content = @Content(schema = @Schema(implementation = PagedResponseIO.class)),
    headers = @Header(name = "X-Total-Count", description = "Total version count.", schema = @Schema(type = SchemaType.INTEGER))
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
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED, "No valid JWT or API key was provided");
    var resolved = containersService.resolveByAppId(appId);
    if (resolved.isEmpty()) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "No container found for appId");
    Response gate = gate(resolved.get().container(), AccessType.Read, caller);
    if (gate != null) return gate;

    var versionsOpt = resolved.get().handler().listVersions(appId, fileName);
    if (versionsOpt.isEmpty()) {
      return problem("/problems/containers.versioning-unsupported",
          "Container kind does not support payload versioning",
          Response.Status.UNSUPPORTED_MEDIA_TYPE,
          "Container kind '" + resolved.get().handler().kind() + "' does not support payload versioning");
    }
    List<PayloadVersionIO> versionList = versionsOpt.get();
    return Response.ok(new PagedResponseIO<>(versionList, versionList.size(), 0, versionList.size()))
        .header("X-Total-Count", (long) versionList.size())
        .build();
  }

  // ─── stats ───────────────────────────────────────────────────────────────

  @GET
  @Path("/{appId}/stats")
  @Operation(
    operationId = "getContainerStats",
    summary = "Storage and ingestion statistics for a container by appId.",
    description =
      "Returns point count, channel count, estimated uncompressed size, and recent " +
      "ingest rate for the container at `appId`. Supported for `timeseries` kind; " +
      "other kinds answer 415.\n\n" +
      "Auth: Read on the container."
  )
  @APIResponse(
    responseCode = "200",
    description = "Stats for the container.",
    content = @Content(schema = @Schema(implementation = ContainerStatsIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read on the container.")
  @APIResponse(responseCode = "404", description = "No container with that appId.")
  @APIResponse(responseCode = "415", description = "This container kind has no stats concept.")
  public Response getStats(@PathParam("appId") String appId, @Context SecurityContext sc) {
    String caller = callerOrNull(sc);
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED, "No valid JWT or API key was provided");
    var resolved = containersService.resolveByAppId(appId);
    if (resolved.isEmpty()) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "No container found for appId");
    Response gate = gate(resolved.get().container(), AccessType.Read, caller);
    if (gate != null) return gate;

    var statsOpt = resolved.get().handler().getStats(appId);
    if (statsOpt.isEmpty()) {
      return problem("/problems/containers.stats-unsupported",
          "Container kind does not support stats",
          Response.Status.UNSUPPORTED_MEDIA_TYPE,
          "Container kind '" + resolved.get().handler().kind() + "' has no stats concept");
    }
    return Response.ok(statsOpt.get()).build();
  }

  // ─── linked DataObjects ──────────────────────────────────────────────────

  @GET
  @Path("/{appId}/linked-data-objects")
  @Operation(
    operationId = "getContainerLinkedDataObjects",
    summary = "List DataObjects linked to a container by appId.",
    description =
      "Returns the distinct DataObjects whose references point at the container at " +
      "`appId`, as DataObjectIO[]. Supported for `file`, `structured-data` and " +
      "`timeseries` kind containers; other kinds answer 415.\n\n" +
      "Auth: Read on the container."
  )
  @APIResponse(
    responseCode = "200",
    description = "List of linked DataObjects (may be empty).",
    content = @Content(schema = @Schema(implementation = PagedResponseIO.class)),
    headers = @Header(name = "X-Total-Count", description = "Total linked DataObject count.", schema = @Schema(type = SchemaType.INTEGER))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read on the container.")
  @APIResponse(responseCode = "404", description = "No container with that appId.")
  @APIResponse(responseCode = "415", description = "This container kind has no linked-DataObject concept.")
  public Response getLinkedDataObjects(@PathParam("appId") String appId, @Context SecurityContext sc) {
    String caller = callerOrNull(sc);
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED, "No valid JWT or API key was provided");
    var resolved = containersService.resolveByAppId(appId);
    if (resolved.isEmpty()) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "No container found for appId");
    Response gate = gate(resolved.get().container(), AccessType.Read, caller);
    if (gate != null) return gate;

    var linkedOpt = resolved.get().handler().listLinkedDataObjects(appId);
    if (linkedOpt.isEmpty()) {
      return problem("/problems/containers.linked-data-objects-unsupported",
          "Container kind does not support linked DataObjects",
          Response.Status.UNSUPPORTED_MEDIA_TYPE,
          "Container kind '" + resolved.get().handler().kind() + "' has no linked-DataObject concept");
    }
    List<DataObjectIO> linkedList = linkedOpt.get();
    return Response.ok(new PagedResponseIO<>(linkedList, linkedList.size(), 0, linkedList.size()))
        .header("X-Total-Count", (long) linkedList.size())
        .build();
  }

  // ─── channel endpoints (APISIMP-CONT-NS-COLLAPSE-2) ────────────────────────

  @GET
  @Path("/{appId}/channels")
  @Operation(
    operationId = "listContainerChannels",
    summary = "List all channels of a TimeseriesContainer by appId.",
    description =
      "Returns one entry per channel in the container, each carrying its stable single-field " +
      "identity (shepardId) plus the legacy 5-tuple. Non-timeseries container kinds answer 415.\n\n" +
      "Auth: Read on the container."
  )
  @APIResponse(
    responseCode = "200",
    description = "Paged per-channel listing.",
    content = @Content(schema = @Schema(implementation = PagedResponseIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read on the container.")
  @APIResponse(responseCode = "404", description = "No container with that appId.")
  @APIResponse(responseCode = "415", description = "This container kind has no channel concept.")
  public Response listChannels(
    @PathParam("appId") String appId,
    @Parameter(description = "0-based page index. Default `0`. Values past the last page return an empty items list.")
    @QueryParam("page") @DefaultValue("0") @PositiveOrZero int page,
    @Parameter(description = "Number of channels per page. Default `200`. Must be between 1 and 500 (400 on violation).")
    @QueryParam("pageSize") @DefaultValue("200") @Min(1) @Max(500) int pageSize,
    @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED, "No valid JWT or API key was provided");
    var resolved = containersService.resolveByAppId(appId);
    if (resolved.isEmpty()) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "No container found for appId");
    Response gate = gate(resolved.get().container(), AccessType.Read, caller);
    if (gate != null) return gate;

    var result = resolved.get().handler().listChannels(appId, page, pageSize);
    if (result.isEmpty()) {
      return problem(PROBLEM_TYPE_UNSUPPORTED, "No channel concept",
          Response.Status.UNSUPPORTED_MEDIA_TYPE,
          "Container kind '" + resolved.get().handler().kind() + "' has no channel concept");
    }
    return Response.ok(result.get()).build();
  }

  @GET
  @Path("/{appId}/channels/spatial-roles")
  @Operation(
    operationId = "getContainerChannelSpatialRoles",
    summary = "Return per-axis channel assignments for the Trace3D view recipe.",
    description =
      "Scans the container's channels for axis-role annotations and returns one shepardId per " +
      "role. Non-timeseries container kinds answer 415.\n\n" +
      "Auth: Read on the container."
  )
  @APIResponse(
    responseCode = "200",
    description = "Spatial role map.",
    content = @Content(schema = @Schema(implementation = SpatialRolesIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read on the container.")
  @APIResponse(responseCode = "404", description = "No container with that appId.")
  @APIResponse(responseCode = "415", description = "This container kind has no channel concept.")
  public Response getChannelSpatialRoles(
    @PathParam("appId") String appId,
    @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED, "No valid JWT or API key was provided");
    var resolved = containersService.resolveByAppId(appId);
    if (resolved.isEmpty()) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "No container found for appId");
    Response gate = gate(resolved.get().container(), AccessType.Read, caller);
    if (gate != null) return gate;

    var result = resolved.get().handler().getChannelSpatialRoles(appId);
    if (result.isEmpty()) {
      return problem(PROBLEM_TYPE_UNSUPPORTED, "No channel concept",
          Response.Status.UNSUPPORTED_MEDIA_TYPE,
          "Container kind '" + resolved.get().handler().kind() + "' has no channel concept");
    }
    return Response.ok(result.get()).build();
  }

  @GET
  @Path("/{appId}/channels/{shepardId}/data")
  @Operation(
    operationId = "getContainerChannelData",
    summary = "Fetch data points for a channel by shepardId.",
    description =
      "Resolves the single-field shepardId to the legacy 5-tuple internally and returns data " +
      "points for the requested time window. `start` and `end` are nanoseconds since Unix epoch " +
      "(e.g. `Date.now() * 1_000_000` in JS). Accepts optional LTTB downsampling via " +
      "?downsample=lttb&maxPoints=N. Non-timeseries container kinds answer 415.\n\n" +
      "Auth: Read on the container."
  )
  @APIResponse(
    responseCode = "200",
    description = "Data points for the channel.",
    content = @Content(schema = @Schema(implementation = TimeseriesWithDataPoints.class))
  )
  @APIResponse(responseCode = "400", description = "start or end missing / negative, or maxPoints out of range [1–5000].")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read on the container.")
  @APIResponse(responseCode = "404", description = "No container or channel with that id.")
  @APIResponse(responseCode = "415", description = "This container kind has no channel concept.")
  public Response getChannelData(
    @PathParam("appId") String appId,
    @PathParam("shepardId") UUID shepardId,
    @Parameter(description = "Window start, nanoseconds since Unix epoch (e.g. Date.now()*1_000_000 in JS).", required = true)
    @QueryParam("start") @NotNull @PositiveOrZero Long start,
    @Parameter(description = "Window end, nanoseconds since Unix epoch (exclusive). Must be > start.", required = true)
    @QueryParam("end")   @NotNull @PositiveOrZero Long end,
    @Parameter(description = "Downsampling algorithm. Only 'lttb' (Largest Triangle Three Buckets) is supported; any other value disables downsampling.")
    @QueryParam("downsample") String downsample,
    @Parameter(description = "Maximum number of data points to return when downsample=lttb is active (1–5000). Ignored when downsample is absent or unrecognised. Absent or null uses the server default (2000).")
    @QueryParam("maxPoints") @Min(1) @Max(5000) Integer maxPoints,
    @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED, "No valid JWT or API key was provided");
    var resolved = containersService.resolveByAppId(appId);
    if (resolved.isEmpty()) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "No container found for appId");
    Response gate = gate(resolved.get().container(), AccessType.Read, caller);
    if (gate != null) return gate;

    try {
      var result = resolved.get().handler().getChannelData(appId, shepardId, start, end, downsample, maxPoints);
      if (result.isEmpty()) {
        return problem(PROBLEM_TYPE_UNSUPPORTED, "No channel concept",
            Response.Status.UNSUPPORTED_MEDIA_TYPE,
            "Container kind '" + resolved.get().handler().kind() + "' has no channel concept");
      }
      return Response.ok(result.get()).build();
    } catch (NotFoundException nfe) {
      return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND,
          nfe.getMessage() != null ? nfe.getMessage() : "Channel not found");
    }
  }

  @POST
  @Path("/{appId}/channels/data/bulk")
  @Operation(
    operationId = "getContainerBulkChannelData",
    summary = "Fetch raw data for multiple channels in one call.",
    description =
      "Accepts a list of shepardIds (max 200) plus a shared time window and returns raw data " +
      "points — one TimeseriesWithDataPoints entry per resolved channel. Unknown IDs are " +
      "silently skipped. Non-timeseries container kinds answer 415.\n\n" +
      "Auth: Read on the container."
  )
  @APIResponse(
    responseCode = "200",
    description = "Raw data for all resolved channels.",
    content = @Content(schema = @Schema(implementation = PagedResponseIO.class)),
    headers = @Header(name = "X-Total-Count", description = "Number of resolved channel series returned.", schema = @Schema(type = SchemaType.INTEGER))
  )
  @APIResponse(responseCode = "400", description = "Validation error on request body.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read on the container.")
  @APIResponse(responseCode = "404", description = "No container with that appId.")
  @APIResponse(responseCode = "415", description = "This container kind has no channel concept.")
  public Response getBulkChannelData(
    @PathParam("appId") String appId,
    @NotNull @Valid BulkChannelDataRequestIO body,
    @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED, "No valid JWT or API key was provided");
    var resolved = containersService.resolveByAppId(appId);
    if (resolved.isEmpty()) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "No container found for appId");
    Response gate = gate(resolved.get().container(), AccessType.Read, caller);
    if (gate != null) return gate;

    var result = resolved.get().handler().getBulkChannelData(appId, body);
    if (result.isEmpty()) {
      return problem(PROBLEM_TYPE_UNSUPPORTED, "No channel concept",
          Response.Status.UNSUPPORTED_MEDIA_TYPE,
          "Container kind '" + resolved.get().handler().kind() + "' has no channel concept");
    }
    var out = result.get();
    return Response.ok(new PagedResponseIO<>(out, out.size(), 0, out.size()))
        .header("X-Total-Count", (long) out.size())
        .build();
  }

  @POST
  @Path("/{appId}/channels/{shepardId}/data/ingest")
  @Operation(
    operationId = "ingestContainerChannelData",
    summary = "High-throughput COPY ingest for a single channel.",
    description =
      "Uses the PostgreSQL COPY protocol for bulk historical loads. The channel (identified by " +
      "shepardId) must already exist. No ON CONFLICT handling is applied: timestamps must be " +
      "unique within the batch. Non-timeseries container kinds answer 415.\n\n" +
      "Auth: Write on the container."
  )
  @APIResponse(responseCode = "204", description = "Data ingested successfully.")
  @APIResponse(responseCode = "400", description = "Validation error or duplicate timestamp.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write on the container.")
  @APIResponse(responseCode = "404", description = "No container or channel with that id.")
  @APIResponse(responseCode = "415", description = "This container kind has no channel concept.")
  public Response ingestChannelData(
    @PathParam("appId") String appId,
    @PathParam("shepardId") UUID shepardId,
    @NotNull @Valid CopyIngestRequestIO body,
    @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED, "No valid JWT or API key was provided");
    var resolved = containersService.resolveByAppId(appId);
    if (resolved.isEmpty()) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "No container found for appId");
    Response gate = gate(resolved.get().container(), AccessType.Write, caller);
    if (gate != null) return gate;

    try {
      boolean handled = resolved.get().handler().ingestChannelData(appId, shepardId, body);
      if (!handled) {
        return problem(PROBLEM_TYPE_UNSUPPORTED, "No channel concept",
            Response.Status.UNSUPPORTED_MEDIA_TYPE,
            "Container kind '" + resolved.get().handler().kind() + "' has no channel concept");
      }
      return Response.noContent().build();
    } catch (NotFoundException nfe) {
      return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND,
          nfe.getMessage() != null ? nfe.getMessage() : "Channel not found");
    }
  }

  // ─── chart-view ────────────────────────────────────────────────────────

  @GET
  @Path("/{appId}/chart-view")
  @Operation(
    operationId = "getChartView",
    summary = "Read the persisted chart-view configuration for a timeseries container.",
    description =
      "Returns the curated channel-selection list shared by all users viewing this " +
      "timeseries container. An empty list means \"no curated view — show all channels\" " +
      "(the frontend default). Only `timeseries` kind containers support this; other " +
      "kinds answer 415.\n\n" +
      "Auth: Read on the container."
  )
  @APIResponse(
    responseCode = "200",
    description = "Current chart-view (empty selectedChannels when never configured).",
    content = @Content(schema = @Schema(implementation = TimeseriesContainerChartViewIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read on the container.")
  @APIResponse(responseCode = "404", description = "No container with that appId.")
  @APIResponse(responseCode = "415", description = "This container kind has no chart-view.")
  public Response getChartView(@PathParam("appId") String appId, @Context SecurityContext sc) {
    String caller = callerOrNull(sc);
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED, "No valid JWT or API key was provided");
    var resolved = containersService.resolveByAppId(appId);
    if (resolved.isEmpty()) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "No container found for appId");
    Response gate = gate(resolved.get().container(), AccessType.Read, caller);
    if (gate != null) return gate;

    var chartViewOpt = resolved.get().handler().getChartView(appId);
    if (chartViewOpt.isEmpty()) {
      return problem(PROBLEM_TYPE_UNSUPPORTED, "No chart-view", Response.Status.UNSUPPORTED_MEDIA_TYPE,
        "Container kind '" + resolved.get().handler().kind() + "' has no chart-view");
    }
    return Response.ok(chartViewOpt.get()).build();
  }

  @PATCH
  @Path("/{appId}/chart-view")
  @Consumes({ "application/merge-patch+json", MediaType.APPLICATION_JSON })
  @Operation(
    operationId = "patchChartView",
    summary = "RFC 7396 merge-patch the chart-view configuration for a timeseries container.",
    description =
      "Replaces the persisted selectedChannels list. Write permission required. " +
      "Only `timeseries` kind containers support this; other kinds answer 415.\n\n" +
      "Auth: Write on the container."
  )
  @APIResponse(
    responseCode = "200",
    description = "Updated chart-view.",
    content = @Content(schema = @Schema(implementation = TimeseriesContainerChartViewIO.class))
  )
  @APIResponse(responseCode = "400", description = "Missing or invalid body.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write on the container.")
  @APIResponse(responseCode = "404", description = "No container with that appId.")
  @APIResponse(responseCode = "415", description = "This container kind has no chart-view.")
  public Response patchChartView(
    @PathParam("appId") String appId,
    @RequestBody(
      required = true,
      content = @Content(
        mediaType = "application/merge-patch+json",
        schema = @Schema(implementation = TimeseriesContainerChartViewIO.class)
      )
    ) TimeseriesContainerChartViewIO body,
    @Context SecurityContext sc
  ) {
    if (body == null) return problem(PROBLEM_TYPE_BAD_REQUEST, "Missing body", Response.Status.BAD_REQUEST, "PATCH body is required");
    String caller = callerOrNull(sc);
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED, "No valid JWT or API key was provided");
    var resolved = containersService.resolveByAppId(appId);
    if (resolved.isEmpty()) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "No container found for appId");
    Response gate = gate(resolved.get().container(), AccessType.Write, caller);
    if (gate != null) return gate;

    var chartViewOpt = resolved.get().handler().patchChartView(appId, body);
    if (chartViewOpt.isEmpty()) {
      return problem(PROBLEM_TYPE_UNSUPPORTED, "No chart-view", Response.Status.UNSUPPORTED_MEDIA_TYPE,
        "Container kind '" + resolved.get().handler().kind() + "' has no chart-view");
    }
    return Response.ok(chartViewOpt.get()).build();
  }

  // ── APISIMP-CONT-NS-COLLAPSE-4: live-window ────────────────────────────────

  @GET
  @Path("/{appId}/channels/live-window")
  @Operation(
    operationId = "getLiveWindow",
    summary = "Fetch the most recent N seconds of a timeseries channel.",
    description =
      "Returns the last `windowSeconds` of data for a single channel. " +
      "Channel lookup: `shepardId` (preferred) or 5-tuple filter. " +
      "Non-timeseries kinds answer 415.\n\nAuth: Read on the container."
  )
  @APIResponse(responseCode = "200", description = "Window data for the channel.",
    content = @Content(schema = @Schema(
      implementation = de.dlr.shepard.v2.timeseriescontainer.io.LiveWindowResponseIO.class)))
  @APIResponse(responseCode = "400", description = "Channel address is ambiguous.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read on the container.")
  @APIResponse(responseCode = "404", description = "No container with that appId, or no matching channel.")
  @APIResponse(responseCode = "415", description = "This container kind has no live-window concept.")
  public Response getLiveWindow(
      @PathParam("appId") String appId,
      @Parameter(description = "Preferred channel selector: the UUID v7 shepardId of the channel. " +
        "Takes precedence over the 5-tuple (measurement/device/location/symbolicName/field). " +
        "Exactly one of shepardId or a complete 5-tuple must be supplied.")
      @QueryParam("shepardId") UUID shepardId,
      @Parameter(description = "5-tuple selector — measurement component. Required when shepardId is absent.")
      @QueryParam("measurement") String measurement,
      @Parameter(description = "5-tuple selector — device component. Required when shepardId is absent.")
      @QueryParam("device") String device,
      @Parameter(description = "5-tuple selector — location component. Required when shepardId is absent.")
      @QueryParam("location") String location,
      @Parameter(description = "5-tuple selector — symbolicName component. Required when shepardId is absent.")
      @QueryParam("symbolicName") String symbolicName,
      @Parameter(description = "5-tuple selector — field component. Required when shepardId is absent.")
      @QueryParam("field") String field,
      @Parameter(description = "Window size in seconds. Default `300`. Min `1`, max `3600`.")
      @QueryParam("windowSeconds") @DefaultValue("300") @Min(1) @Max(3600) int windowSeconds,
      @Parameter(description = "When `true` (default), the response includes the first point before and " +
        "the first point after the window boundary so charts render continuous lines.")
      @QueryParam("withBoundaryPoints") @DefaultValue("true") boolean withBoundaryPoints,
      @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required",
        Response.Status.UNAUTHORIZED, "No valid JWT or API key was provided");
    var resolved = containersService.resolveByAppId(appId);
    if (resolved.isEmpty()) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found",
        Response.Status.NOT_FOUND, "No container found for appId");
    Response gate = gate(resolved.get().container(), AccessType.Read, caller);
    if (gate != null) return gate;
    var result = resolved.get().handler().getLiveWindow(
        appId, shepardId, measurement, device, location, symbolicName, field,
        windowSeconds, withBoundaryPoints);
    if (result.isEmpty()) return problem(PROBLEM_TYPE_UNSUPPORTED, "No live-window concept",
        Response.Status.UNSUPPORTED_MEDIA_TYPE,
        "Container kind '" + resolved.get().handler().kind() + "' has no live-window concept");
    return result.get();
  }

  // ── APISIMP-CONT-NS-COLLAPSE-4: channel annotations ────────────────────────

  @GET
  @Path("/{appId}/channels/{channelShepardId}/annotations")
  @Operation(
    operationId = "listChannelAnnotations",
    summary = "List semantic annotations on a timeseries channel.",
    description =
      "Pagination: `?page=0&pageSize=200` (default). `pageSize` is capped at 500.\n\n" +
      "Non-timeseries kinds answer 415.\n\nAuth: Read on the container."
  )
  @APIResponse(responseCode = "200",
    description = "Paged list of channel annotations with X-Total-Count header (may be empty).",
    content = @Content(schema = @Schema(implementation = PagedResponseIO.class)),
    headers = @Header(
      name = "X-Total-Count",
      description = "Total element count before paging.",
      schema = @Schema(type = SchemaType.INTEGER)
    ))
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read on the container.")
  @APIResponse(responseCode = "404", description = "No container with that appId.")
  @APIResponse(responseCode = "415", description = "This container kind has no channel-annotation concept.")
  public Response listChannelAnnotations(
      @PathParam("appId") String appId,
      @PathParam("channelShepardId") String channelShepardId,
      @Parameter(description = "Zero-based page index (default 0).",
        schema = @Schema(minimum = "0", defaultValue = "0"))
        @QueryParam("page") @DefaultValue("0") @PositiveOrZero int page,
      @Parameter(description = "Items per page, capped at 500 (default 200).",
        schema = @Schema(minimum = "1", maximum = "500", defaultValue = "200"))
        @QueryParam("pageSize") @DefaultValue("200") @Min(1) @Max(500) int pageSize,
      @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required",
        Response.Status.UNAUTHORIZED, "No valid JWT or API key was provided");
    var resolved = containersService.resolveByAppId(appId);
    if (resolved.isEmpty()) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found",
        Response.Status.NOT_FOUND, "No container found for appId");
    Response gate = gate(resolved.get().container(), AccessType.Read, caller);
    if (gate != null) return gate;
    var result = resolved.get().handler().listChannelAnnotations(appId, channelShepardId, page, pageSize);
    if (result.isEmpty()) return problem(PROBLEM_TYPE_UNSUPPORTED,
        "No channel-annotation concept", Response.Status.UNSUPPORTED_MEDIA_TYPE,
        "Container kind '" + resolved.get().handler().kind() + "' has no channel-annotation concept");
    return result.get();
  }

  @POST
  @Path("/{appId}/channels/{channelShepardId}/annotations")
  @Operation(
    operationId = "createChannelAnnotation",
    summary = "Attach a semantic annotation to a timeseries channel.",
    description =
      "Non-timeseries kinds answer 415.\n\nAuth: Write on the container."
  )
  @APIResponse(responseCode = "201", description = "Annotation created.",
    content = @Content(schema = @Schema(implementation = SemanticAnnotationIO.class)))
  @APIResponse(responseCode = "400", description = "Invalid annotation body.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write on the container.")
  @APIResponse(responseCode = "404", description = "No container with that appId.")
  @APIResponse(responseCode = "415", description = "This container kind has no channel-annotation concept.")
  public Response createChannelAnnotation(
      @PathParam("appId") String appId,
      @PathParam("channelShepardId") String channelShepardId,
      @RequestBody(required = true,
        content = @Content(schema = @Schema(implementation = SemanticAnnotationIO.class)))
      @Valid SemanticAnnotationIO body,
      @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required",
        Response.Status.UNAUTHORIZED, "No valid JWT or API key was provided");
    var resolved = containersService.resolveByAppId(appId);
    if (resolved.isEmpty()) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found",
        Response.Status.NOT_FOUND, "No container found for appId");
    Response gate = gate(resolved.get().container(), AccessType.Write, caller);
    if (gate != null) return gate;
    var result = resolved.get().handler().createChannelAnnotation(appId, channelShepardId, body);
    if (result.isEmpty()) return problem(PROBLEM_TYPE_UNSUPPORTED,
        "No channel-annotation concept", Response.Status.UNSUPPORTED_MEDIA_TYPE,
        "Container kind '" + resolved.get().handler().kind() + "' has no channel-annotation concept");
    return result.get();
  }

  @DELETE
  @Path("/{appId}/channels/{channelShepardId}/annotations/{annotationAppId}")
  @Operation(
    operationId = "deleteChannelAnnotation",
    summary = "Remove a semantic annotation from a timeseries channel.",
    description =
      "Non-timeseries kinds answer 415.\n\nAuth: Write on the container."
  )
  @APIResponse(responseCode = "204", description = "Annotation deleted.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write on the container.")
  @APIResponse(responseCode = "404", description = "No container with that appId or annotation not found.")
  @APIResponse(responseCode = "415", description = "This container kind has no channel-annotation concept.")
  public Response deleteChannelAnnotation(
      @PathParam("appId") String appId,
      @PathParam("channelShepardId") String channelShepardId,
      @PathParam("annotationAppId") @NotNull String annotationAppId,
      @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required",
        Response.Status.UNAUTHORIZED, "No valid JWT or API key was provided");
    var resolved = containersService.resolveByAppId(appId);
    if (resolved.isEmpty()) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found",
        Response.Status.NOT_FOUND, "No container found for appId");
    Response gate = gate(resolved.get().container(), AccessType.Write, caller);
    if (gate != null) return gate;
    var result = resolved.get().handler().deleteChannelAnnotation(
        appId, channelShepardId, annotationAppId);
    if (result.isEmpty()) return problem(PROBLEM_TYPE_UNSUPPORTED,
        "No channel-annotation concept", Response.Status.UNSUPPORTED_MEDIA_TYPE,
        "Container kind '" + resolved.get().handler().kind() + "' has no channel-annotation concept");
    return result.get();
  }

  // ── APISIMP-CONT-NS-COLLAPSE-4 / APISIMP-CONTAINER-TEMPORAL-ANNOTATIONS-UNCAPPED ──

  @GET
  @Path("/{appId}/temporal-annotations")
  @Operation(
    operationId = "listTemporalAnnotations",
    summary = "List all temporal annotations on a container.",
    description =
      "Pagination: `?page=0&pageSize=200` (default). `pageSize` is capped at 500.\n\n" +
      "Non-timeseries kinds answer 415.\n\nAuth: Read on the container."
  )
  @APIResponse(responseCode = "200",
    description = "Paged list of temporal annotations with X-Total-Count header (may be empty).",
    content = @Content(schema = @Schema(implementation = PagedResponseIO.class)),
    headers = @Header(
      name = "X-Total-Count",
      description = "Total element count before paging.",
      schema = @Schema(type = SchemaType.INTEGER)
    ))
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read on the container.")
  @APIResponse(responseCode = "404", description = "No container with that appId.")
  @APIResponse(responseCode = "415", description = "This container kind has no temporal-annotation concept.")
  public Response listTemporalAnnotations(
      @PathParam("appId") String appId,
      @Parameter(description = "Zero-based page index (default 0).",
        schema = @Schema(minimum = "0", defaultValue = "0"))
        @QueryParam("page") @DefaultValue("0") @PositiveOrZero int page,
      @Parameter(description = "Items per page, capped at 500 (default 200).",
        schema = @Schema(minimum = "1", maximum = "500", defaultValue = "200"))
        @QueryParam("pageSize") @DefaultValue("200") @Min(1) @Max(500) int pageSize,
      @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required",
        Response.Status.UNAUTHORIZED, "No valid JWT or API key was provided");
    var resolved = containersService.resolveByAppId(appId);
    if (resolved.isEmpty()) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found",
        Response.Status.NOT_FOUND, "No container found for appId");
    Response gate = gate(resolved.get().container(), AccessType.Read, caller);
    if (gate != null) return gate;
    var result = resolved.get().handler().listTemporalAnnotations(appId, page, pageSize);
    if (result.isEmpty()) return problem(PROBLEM_TYPE_UNSUPPORTED,
        "No temporal-annotation concept", Response.Status.UNSUPPORTED_MEDIA_TYPE,
        "Container kind '" + resolved.get().handler().kind() + "' has no temporal-annotation concept");
    return result.get();
  }

  @POST
  @Path("/{appId}/temporal-annotations")
  @Operation(
    operationId = "createTemporalAnnotation",
    summary = "Create a temporal annotation on a container.",
    description =
      "Non-timeseries kinds answer 415.\n\nAuth: Write on the container."
  )
  @APIResponse(responseCode = "201", description = "Annotation created.",
    content = @Content(schema = @Schema(implementation = TimeseriesAnnotationIO.class)))
  @APIResponse(responseCode = "400", description = "`startNs` is null, or `label` is null or blank.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write on the container.")
  @APIResponse(responseCode = "404", description = "No container with that appId.")
  @APIResponse(responseCode = "415", description = "This container kind has no temporal-annotation concept.")
  public Response createTemporalAnnotation(
      @PathParam("appId") String appId,
      TimeseriesAnnotationIO body,
      @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required",
        Response.Status.UNAUTHORIZED, "No valid JWT or API key was provided");
    var resolved = containersService.resolveByAppId(appId);
    if (resolved.isEmpty()) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found",
        Response.Status.NOT_FOUND, "No container found for appId");
    Response gate = gate(resolved.get().container(), AccessType.Write, caller);
    if (gate != null) return gate;
    var result = resolved.get().handler().createTemporalAnnotation(appId, body);
    if (result.isEmpty()) return problem(PROBLEM_TYPE_UNSUPPORTED,
        "No temporal-annotation concept", Response.Status.UNSUPPORTED_MEDIA_TYPE,
        "Container kind '" + resolved.get().handler().kind() + "' has no temporal-annotation concept");
    return result.get();
  }

  @GET
  @Path("/{appId}/temporal-annotations/{annotationAppId}")
  @Operation(
    operationId = "getTemporalAnnotation",
    summary = "Read a single container temporal annotation by appId.",
    description =
      "Non-timeseries kinds answer 415.\n\nAuth: Read on the container."
  )
  @APIResponse(responseCode = "200", description = "TimeseriesAnnotationIO for the requested annotation.",
    content = @Content(schema = @Schema(implementation = TimeseriesAnnotationIO.class)))
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read on the container.")
  @APIResponse(responseCode = "404", description = "No container or annotation with those appIds.")
  @APIResponse(responseCode = "415", description = "This container kind has no temporal-annotation concept.")
  public Response getTemporalAnnotation(
      @PathParam("appId") String appId,
      @PathParam("annotationAppId") String annotationAppId,
      @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required",
        Response.Status.UNAUTHORIZED, "No valid JWT or API key was provided");
    var resolved = containersService.resolveByAppId(appId);
    if (resolved.isEmpty()) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found",
        Response.Status.NOT_FOUND, "No container found for appId");
    Response gate = gate(resolved.get().container(), AccessType.Read, caller);
    if (gate != null) return gate;
    var result = resolved.get().handler().getTemporalAnnotation(appId, annotationAppId);
    if (result.isEmpty()) return problem(PROBLEM_TYPE_UNSUPPORTED,
        "No temporal-annotation concept", Response.Status.UNSUPPORTED_MEDIA_TYPE,
        "Container kind '" + resolved.get().handler().kind() + "' has no temporal-annotation concept");
    return result.get();
  }

  @PATCH
  @Path("/{appId}/temporal-annotations/{annotationAppId}")
  @Operation(
    operationId = "updateTemporalAnnotation",
    summary = "Update a container temporal annotation (merge-patch).",
    description =
      "Non-timeseries kinds answer 415.\n\nAuth: Write on the container."
  )
  @APIResponse(responseCode = "200", description = "TimeseriesAnnotationIO reflecting the patched state.",
    content = @Content(schema = @Schema(implementation = TimeseriesAnnotationIO.class)))
  @APIResponse(responseCode = "400", description = "`label` is provided but blank.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write on the container.")
  @APIResponse(responseCode = "404", description = "No container or annotation with those appIds.")
  @APIResponse(responseCode = "415", description = "This container kind has no temporal-annotation concept.")
  public Response updateTemporalAnnotation(
      @PathParam("appId") String appId,
      @PathParam("annotationAppId") String annotationAppId,
      TimeseriesAnnotationIO body,
      @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required",
        Response.Status.UNAUTHORIZED, "No valid JWT or API key was provided");
    var resolved = containersService.resolveByAppId(appId);
    if (resolved.isEmpty()) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found",
        Response.Status.NOT_FOUND, "No container found for appId");
    Response gate = gate(resolved.get().container(), AccessType.Write, caller);
    if (gate != null) return gate;
    var result = resolved.get().handler().updateTemporalAnnotation(appId, annotationAppId, body);
    if (result.isEmpty()) return problem(PROBLEM_TYPE_UNSUPPORTED,
        "No temporal-annotation concept", Response.Status.UNSUPPORTED_MEDIA_TYPE,
        "Container kind '" + resolved.get().handler().kind() + "' has no temporal-annotation concept");
    return result.get();
  }

  @DELETE
  @Path("/{appId}/temporal-annotations/{annotationAppId}")
  @Operation(
    operationId = "deleteTemporalAnnotation",
    summary = "Delete a container temporal annotation.",
    description =
      "Non-timeseries kinds answer 415.\n\nAuth: Write on the container."
  )
  @APIResponse(responseCode = "204", description = "Annotation deleted.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write on the container.")
  @APIResponse(responseCode = "404", description = "No container or annotation with those appIds.")
  @APIResponse(responseCode = "415", description = "This container kind has no temporal-annotation concept.")
  public Response deleteTemporalAnnotation(
      @PathParam("appId") String appId,
      @PathParam("annotationAppId") String annotationAppId,
      @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required",
        Response.Status.UNAUTHORIZED, "No valid JWT or API key was provided");
    var resolved = containersService.resolveByAppId(appId);
    if (resolved.isEmpty()) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found",
        Response.Status.NOT_FOUND, "No container found for appId");
    Response gate = gate(resolved.get().container(), AccessType.Write, caller);
    if (gate != null) return gate;
    var result = resolved.get().handler().deleteTemporalAnnotation(appId, annotationAppId);
    if (result.isEmpty()) return problem(PROBLEM_TYPE_UNSUPPORTED,
        "No temporal-annotation concept", Response.Status.UNSUPPORTED_MEDIA_TYPE,
        "Container kind '" + resolved.get().handler().kind() + "' has no temporal-annotation concept");
    return result.get();
  }

  // ─── permissions ───────────────────────────────────────────────────────

  @GET
  @Path("/{appId}/permissions")
  @Operation(
    operationId = "getPermissions",
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
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED, "No valid JWT or API key was provided");
    var resolved = containersService.resolveByAppId(appId);
    if (resolved.isEmpty()) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "No container found for appId");
    Response gate = gate(resolved.get().container(), AccessType.Manage, caller);
    if (gate != null) return gate;
    Long id = resolved.get().container().getId();
    var perms = permissionsService.getPermissionsOfEntityOptional(id);
    if (perms.isEmpty()) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "No permissions found for this container");
    return Response.ok(new PermissionsIO(perms.get())).build();
  }

  @PATCH
  @Path("/{appId}/permissions")
  @Consumes({ "application/merge-patch+json", MediaType.APPLICATION_JSON })
  @Operation(
    operationId = "patchPermissions",
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
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED, "No valid JWT or API key was provided");
    var resolved = containersService.resolveByAppId(appId);
    if (resolved.isEmpty()) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "No container found for appId");
    Response gate = gate(resolved.get().container(), AccessType.Manage, caller);
    if (gate != null) return gate;
    Long id = resolved.get().container().getId();
    var existing = permissionsService.getPermissionsOfEntityOptional(id);
    if (existing.isEmpty()) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "No permissions found for this container");
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
    // APISIMP-CONTAINERS-PERMS-IO-NUMERIC: numeric group IDs are deprecated write input.
    if (patch.containsKey("readerGroupIds") && !patch.containsKey("readerGroupAppIds")) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Deprecated field", Response.Status.BAD_REQUEST,
        "readerGroupIds is deprecated; use readerGroupAppIds (UUID v7) to set reader groups");
    }
    if (patch.containsKey("writerGroupIds") && !patch.containsKey("writerGroupAppIds")) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Deprecated field", Response.Status.BAD_REQUEST,
        "writerGroupIds is deprecated; use writerGroupAppIds (UUID v7) to set writer groups");
    }
    if (patch.containsKey("readerGroupAppIds") && patch.get("readerGroupAppIds") instanceof java.util.List<?> rgl) {
      long[] ids = rgl.stream()
        .map(Object::toString)
        .mapToLong(groupAppId -> userGroupService.getUserGroupByAppId(groupAppId).getId())
        .toArray();
      current.setReaderGroupIds(ids);
    }
    if (patch.containsKey("writerGroupAppIds") && patch.get("writerGroupAppIds") instanceof java.util.List<?> wgl) {
      long[] ids = wgl.stream()
        .map(Object::toString)
        .mapToLong(groupAppId -> userGroupService.getUserGroupByAppId(groupAppId).getId())
        .toArray();
      current.setWriterGroupIds(ids);
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
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED, "No valid JWT or API key was provided");
    var resolved = containersService.resolveByAppId(appId);
    if (resolved.isEmpty()) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "No container found for appId");
    Response gate = gate(resolved.get().container(), AccessType.Read, caller);
    if (gate != null) return gate;
    Long id = resolved.get().container().getId();
    return Response.ok(permissionsService.getUserRolesOnEntity(id, caller)).build();
  }


  // ─── thumbnail ─────────────────────────────────────────────────────────

  @GET
  @Path("/{appId}/payload/{oid}/thumbnail")
  @Produces("image/png")
  @Operation(
    operationId = "getThumbnail",
    summary = "Get a PNG thumbnail for a file payload.",
    description =
      "Returns a PNG thumbnail for the file at `oid` inside the container at `appId`. " +
      "Valid sizes: 64, 200, 400; values in range 64–2048 that are not a standard size are normalised to 400. " +
      "Returns 415 when the container kind does not support thumbnails.\n\nAuth: Read on the container."
  )
  @APIResponse(responseCode = "200", description = "PNG thumbnail.",
    content = @Content(schema = @Schema(type = SchemaType.STRING, format = "binary")))
  @APIResponse(responseCode = "400", description = "Size out of range (< 64 or > 2048).")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read on the container.")
  @APIResponse(responseCode = "404", description = "Container or file not found, or thumbnail unavailable.")
  @APIResponse(responseCode = "415", description = "Container kind does not support thumbnails.")
  @APIResponse(responseCode = "503", description = "Thumbnail generation temporarily unavailable.")
  public Response getThumbnail(
    @PathParam("appId") String appId,
    @PathParam("oid") String oid,
    @Parameter(description = "Thumbnail pixel size. Default 200. Valid standard sizes: 64, 200, 400; values in 64–2048 not in the standard set are normalised to 400.")
    @DefaultValue("200") @Min(64) @Max(2048) @QueryParam("size") int size,
    @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED, "No valid JWT or API key was provided");
    var resolved = containersService.resolveByAppId(appId);
    if (resolved.isEmpty()) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "No container found for appId");
    Response gate = gate(resolved.get().container(), AccessType.Read, caller);
    if (gate != null) return gate;
    return resolved.get().handler().getThumbnail(appId, oid, size)
      .orElse(unsupportedKind("thumbnails"));
  }

  // ─── presigned upload ──────────────────────────────────────────────────

  @POST
  @Path("/{appId}/upload-url")
  @Operation(
    operationId = "getUploadUrl",
    summary = "Obtain a presigned PUT URL to upload a file directly to storage.",
    description =
      "Returns a short-lived PUT URL and the assigned oid. Upload file bytes with a single " +
      "HTTP PUT to uploadUrl, then call POST .../upload-url/commit with the oid. " +
      "Returns 415 when the container kind does not support presigned uploads.\n\nAuth: Write on the container."
  )
  @APIResponse(responseCode = "200", description = "Presigned upload URL + oid.",
    content = @Content(schema = @Schema(implementation = PresignedUploadUrlIO.class)))
  @APIResponse(responseCode = "400", description = "Missing fileName.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write on the container.")
  @APIResponse(responseCode = "404", description = "No container with that appId.")
  @APIResponse(responseCode = "415", description = "Container kind does not support presigned uploads.")
  @APIResponse(responseCode = "503", description = "Active storage provider does not support presigned uploads.")
  public Response getUploadUrl(
    @PathParam("appId") String appId,
    PresignedUploadRequestIO request,
    @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED, "No valid JWT or API key was provided");
    var resolved = containersService.resolveByAppId(appId);
    if (resolved.isEmpty()) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "No container found for appId");
    Response gate = gate(resolved.get().container(), AccessType.Write, caller);
    if (gate != null) return gate;
    return resolved.get().handler().getUploadUrl(appId, request)
      .orElse(unsupportedKind("presigned uploads"));
  }

  @POST
  @Path("/{appId}/upload-url/commit")
  @Operation(
    operationId = "commitUpload",
    summary = "Register a file uploaded via presigned PUT.",
    description =
      "After the PUT upload completes, call this to create the ShepardFile record and attach it " +
      "to the container. Returns 415 when the kind does not support presigned uploads.\n\nAuth: Write on the container."
  )
  @APIResponse(responseCode = "201", description = "File registered.")
  @APIResponse(responseCode = "400", description = "Missing oid or fileName.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write on the container.")
  @APIResponse(responseCode = "404", description = "No container with that appId.")
  @APIResponse(responseCode = "415", description = "Container kind does not support presigned uploads.")
  public Response commitUpload(
    @PathParam("appId") String appId,
    UploadCommitIO commit,
    @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED, "No valid JWT or API key was provided");
    var resolved = containersService.resolveByAppId(appId);
    if (resolved.isEmpty()) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "No container found for appId");
    Response gate = gate(resolved.get().container(), AccessType.Write, caller);
    if (gate != null) return gate;
    return resolved.get().handler().commitUpload(appId, commit)
      .orElse(unsupportedKind("presigned uploads"));
  }

  // ─── presigned download ────────────────────────────────────────────────

  @GET
  @Path("/{appId}/files/{oid}/download-url")
  @Operation(
    operationId = "getDownloadUrl",
    summary = "Obtain a presigned GET URL to download a file directly from storage.",
    description =
      "Returns a short-lived GET URL for the file at `oid`. No auth headers are required on the " +
      "download itself. Returns 415 when the kind does not support presigned downloads.\n\nAuth: Read on the container."
  )
  @APIResponse(responseCode = "200", description = "Presigned download URL.",
    content = @Content(schema = @Schema(implementation = PresignedDownloadUrlIO.class)))
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read on the container.")
  @APIResponse(responseCode = "404", description = "No container or file with that id.")
  @APIResponse(responseCode = "415", description = "Container kind does not support presigned downloads.")
  @APIResponse(responseCode = "503", description = "Active storage provider does not support presigned downloads.")
  public Response getDownloadUrl(
    @PathParam("appId") String appId,
    @PathParam("oid") String oid,
    @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED, "No valid JWT or API key was provided");
    var resolved = containersService.resolveByAppId(appId);
    if (resolved.isEmpty()) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "No container found for appId");
    Response gate = gate(resolved.get().container(), AccessType.Read, caller);
    if (gate != null) return gate;
    return resolved.get().handler().getDownloadUrl(appId, oid)
      .orElse(unsupportedKind("presigned downloads"));
  }

  // ─── helpers ───────────────────────────────────────────────────────────

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
      return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "Container has no id (graph inconsistency)");
    }
    if (!permissionsService.isAccessTypeAllowedForUser(id, accessType, caller)) {
      return problem(PROBLEM_TYPE_FORBIDDEN, "Permission denied", Response.Status.FORBIDDEN, "Caller lacks the required permission on this container");
    }
    return null;
  }

  private Response unsupportedKind(String capability) {
    return problem(PROBLEM_TYPE_UNSUPPORTED, "Unsupported container kind",
        Response.Status.UNSUPPORTED_MEDIA_TYPE, "This container kind does not support " + capability + ".");
  }
}
