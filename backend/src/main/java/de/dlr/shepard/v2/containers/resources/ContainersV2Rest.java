package de.dlr.shepard.v2.containers.resources;

import com.fasterxml.jackson.databind.JsonNode;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.neo4j.entities.BasicContainer;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.v2.containers.io.ContainerV2IO;
import de.dlr.shepard.v2.containers.services.ContainersV2Service;
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

  @Inject
  ContainersV2Service containersService;

  @Inject
  PermissionsService permissionsService;

  // ─── create ────────────────────────────────────────────────────────────

  @POST
  @Operation(
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
      return Response.status(Response.Status.BAD_REQUEST).entity("kind query parameter is required").build();
    }
    Map<String, Object> map = body == null ? Map.of() : JsonNodeMaps.toMap(body);
    try {
      ContainerV2IO created = containersService.create(kind, map);
      return Response.status(Response.Status.CREATED).entity(created).build();
    } catch (BadRequestException bre) {
      return Response.status(Response.Status.BAD_REQUEST).entity(bre.getMessage()).build();
    }
  }

  // ─── get-one ───────────────────────────────────────────────────────────

  @GET
  @Path("/{appId}")
  @Operation(
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

  // ─── patch ─────────────────────────────────────────────────────────────

  @PATCH
  @Path("/{appId}")
  @Consumes({ "application/merge-patch+json", MediaType.APPLICATION_JSON })
  @Operation(
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
      return Response.status(Response.Status.BAD_REQUEST).entity("PATCH body must be a JSON object").build();
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
      return Response.status(Response.Status.BAD_REQUEST).entity(bre.getMessage()).build();
    } catch (NotFoundException nfe) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
  }

  // ─── delete ────────────────────────────────────────────────────────────

  @DELETE
  @Path("/{appId}")
  @Operation(
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
      return Response.status(Response.Status.BAD_REQUEST).entity("kind query parameter is required").build();
    }
    try {
      List<ContainerV2IO> containers = containersService.list(kind, name);
      return Response.ok(containers).build();
    } catch (BadRequestException bre) {
      return Response.status(Response.Status.BAD_REQUEST).entity(bre.getMessage()).build();
    }
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
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    if (!permissionsService.isAccessTypeAllowedForUser(id, accessType, caller)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    return null;
  }
}
