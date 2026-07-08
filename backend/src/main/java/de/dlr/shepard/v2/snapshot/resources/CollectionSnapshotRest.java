package de.dlr.shepard.v2.snapshot.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.snapshot.entities.Snapshot;
import de.dlr.shepard.context.snapshot.io.SnapshotIO;
import de.dlr.shepard.context.snapshot.services.SnapshotService;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
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
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
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
@Tag(name = "Snapshots")
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
    operationId = "createCollectionSnapshot",
    summary = "Create a Snapshot of a Collection's current state (V2b).",
    description =
      "Creates a point-in-time `:Snapshot` of the Collection identified by " +
      "`collectionAppId`. The server walks the Collection subtree up to 15 " +
      "hops deep and records the current `revision` counter of every " +
      "`:VersionableEntity` (DataObject, Reference, …) it reaches. The " +
      "resulting Snapshot is immutable — future writes to the underlying " +
      "entities don't affect what the Snapshot reports.\n\n" +
      "Body fields:\n" +
      "  - `name` (required, non-blank) — operator-chosen label, e.g. " +
      "`\"v1.0\"`, `\"pre-publication\"`, `\"after-fix\"`.\n" +
      "  - `description` (optional) — free-form, CommonMark/GFM.\n\n" +
      "Example body: `{\"name\": \"v1.0\", \"description\": \"Initial " +
      "publication-ready state.\"}`.\n\n" +
      "Auth: Write on the Collection (snapshotting is a state-recording " +
      "mutation on the snapshot subgraph, even though it doesn't modify the " +
      "snapshotted entities themselves).\n\n" +
      "Side effects: a `:Snapshot` node plus one `:SnapshotEntry` per " +
      "reachable VersionableEntity. ProvenanceCaptureFilter records a " +
      "`CREATE` Activity addressable at `GET /v2/provenance/entity/{appId}`.\n\n" +
      "Next step: `GET /v2/collections/{collectionAppId}/snapshots` to list, " +
      "`GET /v2/snapshots/{snapshotAppId}` for metadata, or " +
      "`GET /v2/snapshots/{snapshotAppId}/manifest` for the per-entity entries."
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
      return problem("/problems/snapshots.bad-request", "Bad Request", Response.Status.BAD_REQUEST,
          "name is required and must be non-blank");
    }

    Response gate = checkAccess(collectionAppId, AccessType.Write, sc);
    if (gate != null) return gate;

    String caller = sc.getUserPrincipal().getName();
    Snapshot snapshot;
    try {
      snapshot = snapshotService.createSnapshot(collectionAppId, body.name(), body.description(), caller);
    } catch (NotFoundException nfe) {
      return problem("/problems/snapshots.not-found", "Not Found", Response.Status.NOT_FOUND,
          "No Collection with appId '" + collectionAppId + "'.");
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
    operationId = "listCollectionSnapshots",
    summary = "List Snapshots of a Collection, newest first (V2b).",
    description =
      "Returns one page of non-deleted `:Snapshot` entities for the " +
      "Collection identified by `collectionAppId`, ordered by " +
      "`snapshotCapturedAtMs` DESC (newest first).\n\n" +
      "Each `SnapshotIO` carries: `appId`, `name`, `description`, " +
      "`snapshotCapturedAtMs` (millis-epoch), `snapshotCreatedByUsername`, " +
      "and `entryCount` (number of VersionableEntities recorded in the " +
      "manifest).\n\n" +
      "Pagination: omit `page` / `size` to get the first 50; supply both " +
      "to paginate. `size` capped at 200 server-side.\n\n" +
      "Auth: Read on the Collection.\n\n" +
      "Next step: `GET /v2/snapshots/{snapshotAppId}/manifest` for the " +
      "per-entity entries of a specific Snapshot, or " +
      "`GET /v2/snapshots/{a}/diff/{b}` to compare two Snapshots."
  )
  @APIResponse(
    responseCode = "200",
    description = "Snapshot page (may be empty). Envelope: { items[], total, page, pageSize }.",
    content = @Content(
      mediaType = MediaType.APPLICATION_JSON,
      schema = @Schema(implementation = PagedResponseIO.class)
    )
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the Collection.")
  @APIResponse(responseCode = "404", description = "No Collection with that appId.")
  public Response list(
    @PathParam("collectionAppId") String collectionAppId,
    @Parameter(description = "Zero-based page index (default 0). Negative values are clamped to 0.")
    @QueryParam("page") @DefaultValue("0") @PositiveOrZero int page,
    @Parameter(description = "Page size (default 50). Server-side cap: 200. Values below 1 are clamped to 1.")
    @QueryParam("pageSize") @DefaultValue("50") @Max(200) @Min(1) int pageSize,
    @Context SecurityContext sc
  ) {
    Response gate = checkAccess(collectionAppId, AccessType.Read, sc);
    if (gate != null) return gate;

    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(pageSize, 1), 200);

    List<SnapshotIO> rows = snapshotService
      .listByCollection(collectionAppId, safePage, safeSize)
      .stream()
      .map(SnapshotIO::new)
      .toList();

    long total = snapshotService.countByCollection(collectionAppId);

    return Response.ok(new PagedResponseIO<>(rows, total, safePage, safeSize))
        .header("X-Total-Count", total)
        .build();
  }

  // ── private helpers ───────────────────────────────────────────────────────

  private static Response problem(String type, String title, Response.Status status, String detail) {
    ProblemJson body = new ProblemJson(type, title, status.getStatusCode(), detail, null);
    return Response.status(status).type("application/problem+json").entity(body).build();
  }

  /**
   * Returns {@code null} when access is allowed; otherwise a short-circuit
   * {@link Response} (401 / 403 / 404) to return immediately.
   */
  private Response checkAccess(String collectionAppId, AccessType accessType, SecurityContext sc) {
    String caller = sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
    if (caller == null) return problem("/problems/snapshots.unauthorized", "Unauthorized",
        Response.Status.UNAUTHORIZED, "Authentication required.");

    long ogmId;
    try {
      ogmId = entityIdResolver.resolveLong(collectionAppId);
    } catch (NotFoundException nfe) {
      return problem("/problems/snapshots.not-found", "Not Found", Response.Status.NOT_FOUND,
          "No Collection with appId '" + collectionAppId + "'.");
    }

    if (!permissionsService.isAccessTypeAllowedForUser(ogmId, accessType, caller, 0L)) {
      return problem("/problems/snapshots.forbidden", "Forbidden", Response.Status.FORBIDDEN,
          "Caller lacks permission on the Collection.");
    }
    return null;
  }
}
