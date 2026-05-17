package de.dlr.shepard.v2.snapshot.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.snapshot.entities.Snapshot;
import de.dlr.shepard.context.snapshot.io.SnapshotDataObjectsIO;
import de.dlr.shepard.context.snapshot.services.SnapshotService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * V2c — {@code GET /v2/collections/{collectionAppId}/snapshots/{snapshotAppId}/data-objects}.
 *
 * <p>Returns the list of DataObject {@code appId} strings that were captured in
 * the snapshot identified by {@code snapshotAppId}, filtered to only those
 * {@code entityAppId} values that resolve to a live (non-deleted) {@code :DataObject}
 * node. Collections, References, and soft-deleted entities are excluded.
 *
 * <p>This is the "what DataObjects existed at snapshot time" view. Full
 * DataObject attributes (payload bytes, timeseries data, MongoDB documents)
 * are not included because historical storage for those is not yet implemented
 * — that is the scope of V2d and PV1 slices.
 *
 * <p>Gate order:
 * <ol>
 *   <li>401 when the caller is unauthenticated.</li>
 *   <li>404 when {@code collectionAppId} is not a known Collection.</li>
 *   <li>403 when the caller lacks Read permission on the Collection.</li>
 *   <li>404 when {@code snapshotAppId} is not a known (non-deleted) Snapshot.</li>
 *   <li>409 when the snapshot's root Collection differs from the requested
 *       {@code collectionAppId} (ownership mismatch).</li>
 *   <li>200 with the filtered DataObject list.</li>
 * </ol>
 *
 * <p>Cross-references: {@code aidocs/41} §4.2 + §5; {@code aidocs/16} V2c;
 * API-version policy (CLAUDE.md — all new endpoints under {@code /v2/}).
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/collections/{collectionAppId}/snapshots/{snapshotAppId}/data-objects")
@RequestScoped
@Tag(name = "Snapshots (v2)")
public class SnapshotPinnedReadRest {

  @Inject
  SnapshotService snapshotService;

  @Inject
  PermissionsService permissionsService;

  @Inject
  EntityIdResolver entityIdResolver;

  /**
   * Returns the DataObject {@code appId} list captured in a snapshot.
   *
   * @param collectionAppId the application-level identifier of the root Collection.
   * @param snapshotAppId   the application-level identifier of the snapshot.
   * @param sc              the JAX-RS security context.
   * @return 200 with a {@link SnapshotDataObjectsIO} payload; 401 unauthenticated;
   *         403 forbidden; 404 unknown Collection or Snapshot; 409 when the
   *         snapshot does not belong to the given Collection.
   */
  @GET
  @Operation(
    summary = "List DataObjects captured in a snapshot.",
    description =
      "Returns the appIds of all DataObject nodes that were captured in the given snapshot " +
      "for the specified Collection. Collections, References, and soft-deleted entities are " +
      "excluded. Snapshot metadata (name, capturedAt, totalEntries) is included for caller " +
      "convenience. Requires Read permission on the Collection."
  )
  @APIResponse(
    responseCode = "200",
    description = "Snapshot DataObject list.",
    content = @Content(
      mediaType = MediaType.APPLICATION_JSON,
      schema = @Schema(implementation = SnapshotDataObjectsIO.class)
    )
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the Collection.")
  @APIResponse(responseCode = "404", description = "No Collection or Snapshot with that appId.")
  @APIResponse(responseCode = "409", description = "Snapshot does not belong to the specified Collection.")
  public Response getDataObjects(
    @PathParam("collectionAppId") String collectionAppId,
    @PathParam("snapshotAppId") String snapshotAppId,
    @Context SecurityContext sc
  ) {
    // Gate 1 — authentication
    String caller = sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();

    // Gate 2 — collection exists; Gate 3 — caller has Read permission
    Response gate = checkAccess(collectionAppId, AccessType.Read, sc);
    if (gate != null) return gate;

    // Gate 4 — snapshot exists
    Snapshot snapshot = snapshotService.findByAppId(snapshotAppId);
    if (snapshot == null) return Response.status(Response.Status.NOT_FOUND).build();

    // Gate 5 — snapshot belongs to this collection (ownership check)
    if (
      snapshot.getCollection() == null ||
      !collectionAppId.equals(snapshot.getCollection().getAppId())
    ) {
      return Response.status(Response.Status.CONFLICT).build();
    }

    // Load DataObject appIds from the snapshot entries
    List<String> dataObjectAppIds = snapshotService.listDataObjectAppIds(snapshot);

    return Response.ok(new SnapshotDataObjectsIO(snapshot, dataObjectAppIds)).build();
  }

  // ── private helper ────────────────────────────────────────────────────────

  /**
   * Returns {@code null} when access is allowed; otherwise a short-circuit
   * {@link Response} (401 / 403 / 404) to return immediately.
   *
   * <p>Mirrors {@link CollectionSnapshotRest#checkAccess} exactly — same
   * gate order and error codes.
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
