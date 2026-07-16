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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
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
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import static de.dlr.shepard.v2.common.ProblemResponse.problem;

/**
 * V2b — {@code /v2/snapshots/{appId}}.
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
@Path("/v2/snapshots/{appId}")
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

  /**
   * Read snapshot metadata.
   *
   * @param appId the application-level identifier of the snapshot.
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
  public Response read(@PathParam("appId") String appId, @Context SecurityContext sc) {
    // Auth gate
    String caller = sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
    if (caller == null) return problem(PT_UNAUTH, "Unauthorized", Response.Status.UNAUTHORIZED, "Authentication required.");

    Snapshot snapshot = snapshotService.findByAppId(appId);
    if (snapshot == null) return problem(PT_NOT_FOUND, "Not Found", Response.Status.NOT_FOUND, "No Snapshot with appId '" + appId + "'.");

    Response gate = checkCollectionAccess(snapshot, AccessType.Read, caller);
    if (gate != null) return gate;

    return Response.ok(new SnapshotIO(snapshot)).build();
  }

  /**
   * Read the snapshot manifest with real pagination.
   *
   * @param appId the application-level identifier of the snapshot.
   * @param page          zero-based page index (default 0).
   * @param pageSize      entries per page, 1–200 (default 200).
   * @param sc            the JAX-RS security context.
   * @return 200 with a paged {@code {items, total, page, pageSize}} envelope;
   *         401 unauthenticated; 403 forbidden; 404 unknown or deleted snapshot.
   */
  @GET
  @Path("/manifest")
  @Operation(
    operationId = "manifest",
    summary = "Read the snapshot manifest (paginated).",
    description =
      "Returns (entityAppId, revision) pairs captured at snapshot time, " +
      "ordered by entityAppId ascending for deterministic diff tooling. " +
      "Use ?page= and ?pageSize= (default 200, max 200) to paginate large " +
      "snapshots. Requires Read permission on the root Collection."
  )
  @APIResponse(
    responseCode = "200",
    description = "Paginated snapshot manifest.",
    content = @Content(
      mediaType = MediaType.APPLICATION_JSON,
      schema = @Schema(implementation = PagedResponseIO.class)
    ),
    headers = @Header(name = "X-Total-Count", description = "Total count before paging.", schema = @Schema(implementation = Long.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the root Collection.")
  @APIResponse(responseCode = "404", description = "No Snapshot with that appId.")
  public Response manifest(
      @PathParam("appId") String appId,
      @Parameter(description = "Zero-based page index (default 0). Negative values are rejected by bean validation.")
      @QueryParam("page") @DefaultValue("0") @PositiveOrZero int page,
      @Parameter(description = "Page size (default 200). Server-side cap: 200 — values above 200 are rejected by bean validation.")
      @QueryParam("pageSize") @DefaultValue("200") @Min(1) @Max(200) int pageSize,
      @Context SecurityContext sc) {
    String caller = sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
    if (caller == null) return problem(PT_UNAUTH, "Unauthorized", Response.Status.UNAUTHORIZED, "Authentication required.");

    Snapshot snapshot = snapshotService.findByAppId(appId);
    if (snapshot == null) return problem(PT_NOT_FOUND, "Not Found", Response.Status.NOT_FOUND, "No Snapshot with appId '" + appId + "'.");

    Response gate = checkCollectionAccess(snapshot, AccessType.Read, caller);
    if (gate != null) return gate;

    long totalLong = snapshotService.countEntries(snapshot.getId());
    int total = (int) Math.min(totalLong, Integer.MAX_VALUE);
    int skip = (int) Math.min((long) page * pageSize, (long) total);
    List<SnapshotEntryIO> pageEntries = snapshotService
      .findEntriesPage(snapshot.getId(), skip, pageSize)
      .stream()
      .map(SnapshotEntryIO::new)
      .toList();

    return Response.ok(new PagedResponseIO<>(pageEntries, total, page, pageSize))
        .header("X-Total-Count", (long) total)
        .build();
  }

  /**
   * Soft-delete a snapshot. The associated SnapshotEntry rows are also
   * soft-deleted. The underlying entity data (VersionableEntity nodes,
   * payloads) is unaffected.
   *
   * @param appId the application-level identifier of the snapshot.
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
  public Response delete(@PathParam("appId") String appId, @Context SecurityContext sc) {
    String caller = sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
    if (caller == null) return problem(PT_UNAUTH, "Unauthorized", Response.Status.UNAUTHORIZED, "Authentication required.");

    Snapshot snapshot = snapshotService.findByAppId(appId);
    if (snapshot == null) return problem(PT_NOT_FOUND, "Not Found", Response.Status.NOT_FOUND, "No Snapshot with appId '" + appId + "'.");

    Response gate = checkCollectionAccess(snapshot, AccessType.Write, caller);
    if (gate != null) return gate;

    snapshotService.deleteSnapshot(appId);
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
