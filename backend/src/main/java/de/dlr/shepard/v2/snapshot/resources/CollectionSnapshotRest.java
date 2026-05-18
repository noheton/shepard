package de.dlr.shepard.v2.snapshot.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.snapshot.entities.Snapshot;
import de.dlr.shepard.context.snapshot.io.SnapshotIO;
import de.dlr.shepard.context.snapshot.services.SnapshotService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
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
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * V2b — {@code /v2/collections/{collectionAppId}/snapshots}.
 *
 * <p>Two operations:
 * <ul>
 *   <li>{@code POST} — create a snapshot of the Collection's current state
 *       (requires Write permission on the Collection).</li>
 *   <li>{@code GET} — list all snapshots for a Collection
 *       (requires Read permission).</li>
 * </ul>
 *
 * <p>Auth model: piggybacks on the Collection's permission node — the same
 * gate used by all other {@code /v2/collections/{appId}/...} endpoints.
 * 401 unauthenticated; 404 unknown collectionAppId; 403 permission denied.
 *
 * <p>Cross-references: {@code aidocs/41} §5; {@code aidocs/16} V2b;
 * API-version policy (CLAUDE.md — all new endpoints under {@code /v2/}).
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/collections/{collectionAppId}/snapshots")
@RequestScoped
@Tag(name = "Snapshots (v2)")
public class CollectionSnapshotRest {

  @Inject
  SnapshotService snapshotService;

  @Inject
  PermissionsService permissionsService;

  @Inject
  EntityIdResolver entityIdResolver;

  /**
   * Create a snapshot of the Collection's current state.
   *
   * <p>The body carries {@code name} (required) and {@code description}
   * (optional). The server walks the Collection subtree up to 15 hops deep
   * and records the current {@code revision} counter of every
   * {@code :VersionableEntity} found.
   *
   * @param collectionAppId the application-level identifier of the root Collection.
   * @param body            the snapshot creation request (name + description).
   * @param sc              the JAX-RS security context.
   * @return 201 with the newly created {@link SnapshotIO}; 400 when
   *         {@code name} is missing; 401 unauthenticated; 403 forbidden;
   *         404 unknown Collection.
   */
  @POST
  @Operation(
    summary = "Create a snapshot of a Collection's current state.",
    description =
      "Walks the Collection subtree (up to 15 hops) and records the current " +
      "revision of every VersionableEntity. Requires Write permission on the Collection."
  )
  @APIResponse(
    responseCode = "201",
    description = "Snapshot created.",
    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SnapshotIO.class))
  )
  @APIResponse(responseCode = "400", description = "name is required and must be non-blank.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the Collection.")
  @APIResponse(responseCode = "404", description = "No Collection with that appId.")
  public Response create(
    @PathParam("collectionAppId") String collectionAppId,
    SnapshotIO body,
    @Context SecurityContext sc
  ) {
    if (body == null || body.name() == null || body.name().isBlank()) {
      return Response.status(Response.Status.BAD_REQUEST).entity("name is required and must be non-blank").build();
    }

    Response gate = checkAccess(collectionAppId, AccessType.Write, sc);
    if (gate != null) return gate;

    String caller = sc.getUserPrincipal().getName();
    Snapshot snapshot;
    try {
      snapshot = snapshotService.createSnapshot(collectionAppId, body.name(), body.description(), caller);
    } catch (NotFoundException nfe) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    return Response.status(Response.Status.CREATED).entity(new SnapshotIO(snapshot)).build();
  }

  /**
   * List all snapshots of a Collection, newest first.
   *
   * @param collectionAppId the application-level identifier of the root Collection.
   * @param sc              the JAX-RS security context.
   * @return 200 with a (possibly empty) snapshot array; 401 unauthenticated;
   *         403 forbidden; 404 unknown Collection.
   */
  @GET
  @Operation(
    summary = "List snapshots of a Collection.",
    description =
      "Returns one page of non-deleted snapshots for the Collection, " +
      "ordered newest first. Requires Read permission. " +
      "Pagination defaults: page=0, size=50 (max 200). " +
      "Use the upstream-style 'page' and 'size' query params for navigation."
  )
  @APIResponse(
    responseCode = "200",
    description = "Snapshot page (may be empty).",
    content = @Content(
      mediaType = MediaType.APPLICATION_JSON,
      schema = @Schema(type = SchemaType.ARRAY, implementation = SnapshotIO.class)
    )
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the Collection.")
  @APIResponse(responseCode = "404", description = "No Collection with that appId.")
  public Response list(
    @PathParam("collectionAppId") String collectionAppId,
    @QueryParam("page") @DefaultValue("0") int page,
    @QueryParam("size") @DefaultValue("50") int size,
    @Context SecurityContext sc
  ) {
    Response gate = checkAccess(collectionAppId, AccessType.Read, sc);
    if (gate != null) return gate;

    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), 200);

    List<SnapshotIO> rows = snapshotService
      .listByCollection(collectionAppId, safePage, safeSize)
      .stream()
      .map(SnapshotIO::new)
      .toList();

    return Response.ok(rows).build();
  }

  // ── private helper ────────────────────────────────────────────────────────

  /**
   * Returns {@code null} when access is allowed; otherwise a short-circuit
   * {@link Response} (401 / 403 / 404) to return immediately.
   */
  private Response checkAccess(String collectionAppId, AccessType accessType, SecurityContext sc) {
    String caller = sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();

    long ogmId;
    try {
      ogmId = entityIdResolver.resolveLong(collectionAppId);
    } catch (NotFoundException nfe) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    if (!permissionsService.isAccessTypeAllowedForUser(ogmId, accessType, caller, 0L)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    return null;
  }
}
