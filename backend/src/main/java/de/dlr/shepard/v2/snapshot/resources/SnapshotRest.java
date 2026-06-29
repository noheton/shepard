package de.dlr.shepard.v2.snapshot.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.snapshot.entities.Snapshot;
import de.dlr.shepard.context.snapshot.io.SnapshotEntryIO;
import de.dlr.shepard.context.snapshot.io.SnapshotIO;
import de.dlr.shepard.context.snapshot.services.SnapshotService;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
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
 * V2b — {@code /v2/snapshots/{snapshotAppId}}.
 *
 * <p>Three operations on an existing snapshot:
 * <ul>
 *   <li>{@code GET /v2/snapshots/{appId}} — metadata (name, description,
 *       creation time, entry count). Requires Read on the root Collection.</li>
 *   <li>{@code GET /v2/snapshots/{appId}/manifest} — full manifest
 *       ({@code [(entityAppId, revision)]}). Same gate.</li>
 *   <li>{@code DELETE /v2/snapshots/{appId}} — soft-delete. Requires
 *       Write on the root Collection.</li>
 * </ul>
 *
 * <p>Permission is checked against the root Collection that the snapshot is
 * attached to (via the {@code SNAPSHOT_OF} relationship). This mirrors the
 * pattern used for DataObject-scoped resources.
 *
 * <p>Cross-references: {@code aidocs/41} §5; {@code aidocs/16} V2b;
 * API-version policy (CLAUDE.md — all new endpoints under {@code /v2/}).
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/snapshots/{snapshotAppId}")
@RequestScoped
@Tag(name = "Snapshots")
public class SnapshotRest {

  @Inject
  SnapshotService snapshotService;

  @Inject
  PermissionsService permissionsService;

  @Inject
  EntityIdResolver entityIdResolver;

  private static final String PT_UNAUTH = "/problems/snapshots.unauthorized";
  private static final String PT_FORBIDDEN = "/problems/snapshots.forbidden";
  private static final String PT_NOT_FOUND = "/problems/snapshots.not-found";

  private static Response problem(String type, String title, Response.Status status, String detail) {
    ProblemJson body = new ProblemJson(type, title, status.getStatusCode(), detail, null);
    return Response.status(status).type("application/problem+json").entity(body).build();
  }

  /**
   * Read snapshot metadata.
   *
   * @param snapshotAppId the application-level identifier of the snapshot.
   * @param sc            the JAX-RS security context.
   * @return 200 with the snapshot metadata; 401 unauthenticated; 403 forbidden;
   *         404 unknown or deleted snapshot.
   */
  @GET
  @Operation(
    operationId = "getSnapshot",
    summary = "Read snapshot metadata.",
    description = "Returns name, description, snapshotCapturedAt, snapshotCreatedByUsername, collectionAppId, and entryCount. Requires Read permission on the root Collection."
  )
  @APIResponse(
    responseCode = "200",
    description = "Snapshot metadata.",
    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SnapshotIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the root Collection.")
  @APIResponse(responseCode = "404", description = "No Snapshot with that appId.")
  public Response read(@PathParam("snapshotAppId") String snapshotAppId, @Context SecurityContext sc) {
    // Auth gate
    String caller = sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
    if (caller == null) return problem(PT_UNAUTH, "Unauthorized", Response.Status.UNAUTHORIZED, "Authentication required.");

    Snapshot snapshot = snapshotService.findByAppId(snapshotAppId);
    if (snapshot == null) return problem(PT_NOT_FOUND, "Not Found", Response.Status.NOT_FOUND, "No Snapshot with appId '" + snapshotAppId + "'.");

    Response gate = checkCollectionAccess(snapshot, AccessType.Read, caller);
    if (gate != null) return gate;

    return Response.ok(new SnapshotIO(snapshot)).build();
  }

  /**
   * Read the full snapshot manifest.
   *
   * @param snapshotAppId the application-level identifier of the snapshot.
   * @param sc            the JAX-RS security context.
   * @return 200 with an array of {@code {entityAppId, revision}} pairs;
   *         401 unauthenticated; 403 forbidden; 404 unknown or deleted snapshot.
   */
  @GET
  @Path("/manifest")
  @Operation(
    summary = "Read the full snapshot manifest.",
    description =
      "Returns every (entityAppId, revision) pair captured at snapshot time. " +
      "Ordered by entityAppId ascending for deterministic diff tooling. " +
      "Requires Read permission on the root Collection."
  )
  @APIResponse(
    responseCode = "200",
    description = "Snapshot manifest.",
    content = @Content(
      mediaType = MediaType.APPLICATION_JSON,
      schema = @Schema(implementation = PagedResponseIO.class)
    )
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the root Collection.")
  @APIResponse(responseCode = "404", description = "No Snapshot with that appId.")
  public Response manifest(@PathParam("snapshotAppId") String snapshotAppId, @Context SecurityContext sc) {
    String caller = sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
    if (caller == null) return problem(PT_UNAUTH, "Unauthorized", Response.Status.UNAUTHORIZED, "Authentication required.");

    Snapshot snapshot = snapshotService.findByAppId(snapshotAppId);
    if (snapshot == null) return problem(PT_NOT_FOUND, "Not Found", Response.Status.NOT_FOUND, "No Snapshot with appId '" + snapshotAppId + "'.");

    Response gate = checkCollectionAccess(snapshot, AccessType.Read, caller);
    if (gate != null) return gate;

    List<SnapshotEntryIO> entries = snapshotService
      .findEntries(snapshot.getId())
      .stream()
      .map(SnapshotEntryIO::new)
      .toList();

    return Response.ok(new PagedResponseIO<>(entries, entries.size(), 0, entries.size())).build();
  }

  /**
   * Soft-delete a snapshot. The associated SnapshotEntry rows are also
   * soft-deleted. The underlying entity data (VersionableEntity nodes,
   * payloads) is unaffected.
   *
   * @param snapshotAppId the application-level identifier of the snapshot.
   * @param sc            the JAX-RS security context.
   * @return 204 on success; 401 unauthenticated; 403 forbidden; 404 unknown
   *         or already-deleted snapshot.
   */
  @DELETE
  @Operation(
    operationId = "deleteSnapshot",
    summary = "Delete a snapshot.",
    description =
      "Soft-deletes the snapshot and all its SnapshotEntry rows. " +
      "The underlying entity data is unaffected. Requires Write permission on the root Collection."
  )
  @APIResponse(responseCode = "204", description = "Snapshot deleted.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the root Collection.")
  @APIResponse(responseCode = "404", description = "No Snapshot with that appId.")
  public Response delete(@PathParam("snapshotAppId") String snapshotAppId, @Context SecurityContext sc) {
    String caller = sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
    if (caller == null) return problem(PT_UNAUTH, "Unauthorized", Response.Status.UNAUTHORIZED, "Authentication required.");

    Snapshot snapshot = snapshotService.findByAppId(snapshotAppId);
    if (snapshot == null) return problem(PT_NOT_FOUND, "Not Found", Response.Status.NOT_FOUND, "No Snapshot with appId '" + snapshotAppId + "'.");

    Response gate = checkCollectionAccess(snapshot, AccessType.Write, caller);
    if (gate != null) return gate;

    snapshotService.deleteSnapshot(snapshotAppId);
    return Response.noContent().build();
  }

  // ── private helpers ───────────────────────────────────────────────────────

  /**
   * Checks the caller's access to the root Collection that the snapshot is
   * attached to. Returns {@code null} when access is allowed, or a
   * short-circuit {@link Response} (403 / 404) to return immediately.
   *
   * <p>The 404 case (collection not attached) should not arise for well-formed
   * snapshots, but is handled defensively.
   */
  private Response checkCollectionAccess(Snapshot snapshot, AccessType accessType, String caller) {
    if (snapshot.getCollection() == null) {
      return problem(PT_NOT_FOUND, "Not Found", Response.Status.NOT_FOUND, "Snapshot has no associated Collection.");
    }
    long collectionOgmId;
    try {
      collectionOgmId = entityIdResolver.resolveLong(snapshot.getCollection().getAppId());
    } catch (NotFoundException nfe) {
      return problem(PT_NOT_FOUND, "Not Found", Response.Status.NOT_FOUND, "Root Collection for this Snapshot not found.");
    }
    if (!permissionsService.isAccessTypeAllowedForUser(collectionOgmId, accessType, caller, 0L)) {
      return problem(PT_FORBIDDEN, "Forbidden", Response.Status.FORBIDDEN, "Caller lacks permission on the root Collection.");
    }
    return null;
  }
}
