package de.dlr.shepard.v2.snapshot.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.snapshot.entities.Snapshot;
import de.dlr.shepard.context.snapshot.io.SnapshotListItemIO;
import de.dlr.shepard.context.snapshot.services.SnapshotService;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import static de.dlr.shepard.v2.common.ProblemResponse.problem;

/**
 * SNAPSHOT-LIST-1-REST — cross-collection paginated snapshot list at
 * {@code /v2/snapshots}.
 *
 * <h2>Why this exists</h2>
 *
 * <p>The pre-existing per-collection list at
 * {@code GET /v2/collections/{collectionAppId}/snapshots} (V2b) forces a
 * picker to first pick a Collection, then list its snapshots. The
 * {@code /snapshots/diff} page wants a one-step snapshot picker across
 * everything the caller can read. SNAPSHOT-LIST-1-REST is that endpoint —
 * the frontend follow-up {@code SNAPSHOT-LIST-1-FE} swaps the helper
 * banner on {@code /snapshots/diff} for a real {@code v-autocomplete}.
 *
 * <h2>Auth + scoping</h2>
 *
 * <p>Reuses the snapshot → parent-Collection permission walk already in
 * {@link SnapshotRest}: each snapshot's reachability is gated on the
 * caller's Read on the parent Collection. The list endpoint post-filters
 * the DAO page to the readable subset — short pages are acceptable; the
 * envelope {@code total} reports the unfiltered total so an operator
 * looking at the count vs. row-count discrepancy can infer "you can't
 * see N of these". This is the same shape SCENEGRAPH-PERMS-1 uses.
 *
 * <h2>Pagination</h2>
 *
 * <p>{@code page} default 0, clamped to {@code >= 0}; {@code pageSize}
 * default 50, clamped into {@code [1, 200]}. {@code collectionAppId}
 * is optional — when supplied, the result is scoped to that Collection;
 * when absent, the result spans every Collection the caller can read.
 *
 * <h2>Path collision</h2>
 *
 * <p>This resource mounts at {@code /v2/snapshots} (no path-template);
 * the existing {@link SnapshotRest} mounts at
 * {@code /v2/snapshots/{appId}}. JAX-RS dispatches by template
 * specificity so there is no collision (template paths win over literal
 * paths only when they actually match — {@code GET /v2/snapshots} routes
 * here, {@code GET /v2/snapshots/abc} routes to {@link SnapshotRest}).
 */
@Path("/v2/snapshots")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Authenticated
@Tag(name = "Snapshots")
public class SnapshotListRest {

  @Inject
  SnapshotService snapshotService;

  @Inject
  PermissionsService permissionsService;

  @Inject
  EntityIdResolver entityIdResolver;

  private static final String PT_UNAUTH = "/problems/snapshots.unauthorized";
  private static final String PT_NOT_FOUND = "/problems/snapshots.not-found";

  @GET
  @Operation(
    operationId = "listSnapshots",
    summary = "List snapshots the caller can read (paginated, optionally scoped to one Collection).",
    description =
      "Returns one page of `:Snapshot` rows the caller has Read on, ordered " +
      "newest first (`snapshotCapturedAtMs DESC`). Optional `collectionAppId` " +
      "scopes the result to a single Collection; when absent, the list spans " +
      "every Collection the caller can read.\n\n" +
      "Response envelope: `{ items[], total, page, pageSize }`. **`total` reports " +
      "the unfiltered count** (every snapshot in scope, not just the ones the " +
      "caller can read) — so a caller looking at `items.length` vs. `total` " +
      "can infer how many they can't see. The page is post-filtered to the " +
      "readable subset, so `items.length` may be lower than `pageSize` even when " +
      "more snapshots exist.\n\n" +
      "Surfaces in `SNAPSHOT-LIST-1-FE` (the `/snapshots/diff` picker follow-up)."
  )
  @APIResponse(
    responseCode = "200",
    description = "Snapshot page (may be empty).",
    content = @Content(schema = @Schema(implementation = PagedResponseIO.class)),
    headers = @Header(name = "X-Total-Count", description = "Total count before paging.", schema = @Schema(implementation = Long.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "404", description = "collectionAppId supplied but does not resolve to an existing Collection.")
  public Response list(
    @Parameter(description =
      "Optional. When supplied, scopes the result to snapshots belonging to the "
      + "Collection with this appId. When absent, all snapshots the caller can "
      + "read are returned regardless of Collection.")
    @QueryParam("collectionAppId") String collectionAppId,
    @Parameter(description = "Zero-based page index (default 0). Negative values are clamped to 0.")
    @QueryParam("page") @DefaultValue("0") @PositiveOrZero int page,
    @Parameter(description =
      "Page size (default 50). Server-side clamp: [1, 200] — values outside this "
      + "range are silently clamped to the nearest boundary.")
    @QueryParam("pageSize") @DefaultValue("50") @Max(200) @Min(1) int pageSize,
    @Context SecurityContext sc
  ) {
    String caller = sc != null && sc.getUserPrincipal() != null
      ? sc.getUserPrincipal().getName() : null;
    if (caller == null) return problem(PT_UNAUTH, "Unauthorized", Response.Status.UNAUTHORIZED, "Authentication required.");

    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(pageSize, 1), 200);

    List<Snapshot> rawPage;
    long total;
    if (collectionAppId != null && !collectionAppId.isBlank()) {
      // Scoped variant: verify the Collection exists first so we 404 cleanly
      // rather than silently returning an empty page.
      try {
        entityIdResolver.resolveLong(collectionAppId);
      } catch (NotFoundException nfe) {
        return problem(PT_NOT_FOUND, "Not Found", Response.Status.NOT_FOUND,
            "No Collection with appId '" + collectionAppId + "'.");
      }
      rawPage = snapshotService.listByCollection(collectionAppId, safePage, safeSize);
      total = snapshotService.countByCollection(collectionAppId);
    } else {
      rawPage = snapshotService.listAll(safePage, safeSize);
      total = snapshotService.countAll();
    }

    // Permission filter: each snapshot's parent Collection must be Read-able.
    List<SnapshotListItemIO> filtered = new ArrayList<>(rawPage.size());
    for (Snapshot snap : rawPage) {
      if (snap.getCollection() == null || snap.getCollection().getAppId() == null) continue;
      long collOgmId;
      try {
        collOgmId = entityIdResolver.resolveLong(snap.getCollection().getAppId());
      } catch (NotFoundException nfe) {
        continue;
      }
      if (permissionsService.isAccessTypeAllowedForUser(collOgmId, AccessType.Read, caller, 0L)) {
        filtered.add(SnapshotListItemIO.from(snap));
      }
    }

    return Response.ok(new PagedResponseIO<>(filtered, total, safePage, safeSize))
        .header("X-Total-Count", total)
        .build();
  }
}
