package de.dlr.shepard.v2.references.resources;

import com.fasterxml.jackson.databind.JsonNode;
import de.dlr.shepard.auth.permission.services.PermissionsService;
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
 * V2CONV-A2 — the unified {@code /v2/references} REST surface that converges
 * the homogeneous create / get-one / patch / delete / list-filter operations
 * previously spread across per-kind resources ({@code /v2/uri-references},
 * {@code /v2/timeseries-references}, the JSON portions of {@code /v2/files},
 * and the plugin reference resources).
 *
 * <p>Kind-specific binary / special operations stay at their own paths and
 * are NOT converged here: {@code GET /v2/files/{appId}/content}, video
 * {@code /download}, git {@code /preview} + {@code /check-update}, and the
 * multipart {@code POST /v2/files} upload entry.
 *
 * <h2>Routes</h2>
 * <ul>
 *   <li>{@code POST   /v2/references?kind=…&dataObjectAppId=…} — create a
 *       non-binary reference of {@code kind}. Body is the per-kind create
 *       payload. Binary {@code kind=file} rejects here (use POST /v2/files).</li>
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
@Tag(name = "References (v2 unified)")
public class ReferencesV2Rest {

  @Inject
  ReferencesV2Service referencesService;

  @Inject
  PermissionsService permissionsService;

  // ─── create ────────────────────────────────────────────────────────────

  @POST
  @Operation(
    summary = "Create a non-binary reference of the given kind under a DataObject.",
    description =
      "Creates a reference of `kind` attached to the DataObject identified by " +
      "`dataObjectAppId`. The body is the per-kind create payload (e.g. `{uri, " +
      "relationship}` for kind=uri; `{start, end, timeseriesContainerId, timeseries}` " +
      "for kind=timeseries). Binary kinds are NOT created here — `kind=file` rejects " +
      "with 400 directing the caller to the multipart `POST /v2/files` entry point.\n\n" +
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
    @QueryParam("kind") String kind,
    @QueryParam("dataObjectAppId") String dataObjectAppId,
    JsonNode body,
    @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();
    if (kind == null || kind.isBlank()) {
      return Response.status(Response.Status.BAD_REQUEST).entity("kind query parameter is required").build();
    }
    if (dataObjectAppId == null || dataObjectAppId.isBlank()) {
      return Response.status(Response.Status.BAD_REQUEST).entity("dataObjectAppId query parameter is required").build();
    }
    if (!permissionsService.isAccessAllowedForDataObjectAppId(dataObjectAppId, AccessType.Write, caller)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    Map<String, Object> map = body == null ? Map.of() : JsonNodeMaps.toMap(body);
    try {
      ReferenceV2IO created = referencesService.create(kind, dataObjectAppId, map);
      return Response.status(Response.Status.CREATED).entity(created).build();
    } catch (BadRequestException bre) {
      return Response.status(Response.Status.BAD_REQUEST).entity(bre.getMessage()).build();
    } catch (NotFoundException nfe) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
  }

  // ─── get-one ───────────────────────────────────────────────────────────

  @GET
  @Path("/{appId}")
  @Operation(
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
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();
    var resolved = referencesService.resolveByAppId(appId);
    if (resolved.isEmpty()) return Response.status(Response.Status.NOT_FOUND).build();
    Response gate = gateOnParent(resolved.get().reference(), AccessType.Read, caller);
    if (gate != null) return gate;
    return Response.ok(resolved.get().handler().toIO(resolved.get().reference())).build();
  }

  // ─── patch ─────────────────────────────────────────────────────────────

  @PATCH
  @Path("/{appId}")
  @Consumes({ "application/merge-patch+json", MediaType.APPLICATION_JSON })
  @Operation(
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
      return Response.status(Response.Status.BAD_REQUEST).entity("PATCH body must be a JSON object").build();
    }
    String caller = callerOrNull(sc);
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();
    var resolved = referencesService.resolveByAppId(appId);
    if (resolved.isEmpty()) return Response.status(Response.Status.NOT_FOUND).build();
    Response gate = gateOnParent(resolved.get().reference(), AccessType.Write, caller);
    if (gate != null) return gate;
    try {
      ReferenceV2IO updated = referencesService.patchByAppId(appId, JsonNodeMaps.toMap(body));
      return Response.ok(updated).build();
    } catch (BadRequestException bre) {
      return Response.status(Response.Status.BAD_REQUEST).entity(bre.getMessage()).build();
    } catch (NotFoundException nfe) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
  }

  // ─── delete ────────────────────────────────────────────────────────────

  @DELETE
  @Path("/{appId}")
  @Operation(
    summary = "Delete any reference by appId; dispatched by kind.",
    description = "Deletes the reference at `appId` via the owning kind's deleter.\n\nAuth: Write on the parent DataObject."
  )
  @APIResponse(responseCode = "204", description = "Deleted.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No reference with that appId.")
  public Response delete(@PathParam("appId") String appId, @Context SecurityContext sc) {
    String caller = callerOrNull(sc);
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();
    var resolved = referencesService.resolveByAppId(appId);
    if (resolved.isEmpty()) return Response.status(Response.Status.NOT_FOUND).build();
    Response gate = gateOnParent(resolved.get().reference(), AccessType.Write, caller);
    if (gate != null) return gate;
    try {
      referencesService.deleteByAppId(appId);
      return Response.noContent().build();
    } catch (NotFoundException nfe) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
  }

  // ─── list / filter ───────────────────────────────────────────────────────

  @GET
  @Operation(
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
    @QueryParam("kind") String kind,
    @QueryParam("dataObjectAppId") String dataObjectAppId,
    @QueryParam("fileKind") String fileKind,
    @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();
    if (kind == null || kind.isBlank()) {
      return Response.status(Response.Status.BAD_REQUEST).entity("kind query parameter is required").build();
    }
    if (dataObjectAppId == null || dataObjectAppId.isBlank()) {
      return Response.status(Response.Status.BAD_REQUEST).entity("dataObjectAppId query parameter is required").build();
    }
    if (!permissionsService.isAccessAllowedForDataObjectAppId(dataObjectAppId, AccessType.Read, caller)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    try {
      List<ReferenceV2IO> refs = referencesService.listByDataObject(kind, dataObjectAppId, fileKind);
      return Response.ok(refs).build();
    } catch (BadRequestException bre) {
      return Response.status(Response.Status.BAD_REQUEST).entity(bre.getMessage()).build();
    } catch (NotFoundException nfe) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
  }

  // ─── helpers ───────────────────────────────────────────────────────────

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
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    String doAppId = parent.getAppId();
    if (doAppId == null) {
      // Pre-L2a DataObject without appId — fail closed on its own OGM id.
      if (!permissionsService.isAccessTypeAllowedForUser(parent.getId(), accessType, caller)) {
        return Response.status(Response.Status.FORBIDDEN).build();
      }
      return null;
    }
    if (!permissionsService.isAccessAllowedForDataObjectAppId(doAppId, accessType, caller)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    return null;
  }
}
