package de.dlr.shepard.v2.snapshot.resources;

import de.dlr.shepard.context.snapshot.entities.Snapshot;
import de.dlr.shepard.context.snapshot.io.SnapshotDiffIO;
import de.dlr.shepard.context.snapshot.io.SnapshotDiffIO.DiffEntry;
import de.dlr.shepard.context.snapshot.services.SnapshotService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * V2e — {@code GET /v2/snapshots/{aAppId}/diff/{bAppId}}.
 *
 * <p>Compares the {@link de.dlr.shepard.context.snapshot.entities.SnapshotEntry}
 * sets of two snapshots (A = base, B = head) and returns which entities were
 * added, removed, or changed in revision between them. Entities with identical
 * revisions are counted but not listed (the set can be arbitrarily large).
 *
 * <p>Auth: authenticated caller required (401 when unauthenticated). In v1 no
 * additional per-collection permission gate is applied — snapshots are metadata
 * only; a stricter per-collection gate can be added in a future revision when
 * cross-collection diff is in scope.
 *
 * <p>Error conditions:
 * <ul>
 *   <li>401 — unauthenticated caller.</li>
 *   <li>400 — {@code aAppId} equals {@code bAppId} (self-diff is meaningless).</li>
 *   <li>404 — snapshot A or snapshot B not found or soft-deleted.</li>
 * </ul>
 *
 * <p>All result lists are sorted by {@code entityAppId} ascending for
 * deterministic output.
 *
 * <p>Cross-references: {@code aidocs/41} §7; {@code aidocs/16} V2e;
 * API-version policy (CLAUDE.md — all new endpoints under {@code /v2/}).
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/snapshots/{aAppId}/diff/{bAppId}")
@RequestScoped
@Tag(name = "Snapshots (v2)")
public class SnapshotDiffRest {

  @Inject
  SnapshotService snapshotService;

  /**
   * Diff two snapshots.
   *
   * @param aAppId the application-level identifier of snapshot A (base).
   * @param bAppId the application-level identifier of snapshot B (head).
   * @param sc     the JAX-RS security context.
   * @return 200 with a {@link SnapshotDiffIO}; 400 when A and B are the same;
   *         401 unauthenticated; 404 when either snapshot is unknown or deleted.
   */
  @GET
  @Operation(
    summary = "Diff two snapshots.",
    description =
      "Compares the entity sets of snapshot A (base) and snapshot B (head). " +
      "Returns lists of entityAppIds that were added (in B, not in A), removed " +
      "(in A, not in B), or changed revision (present in both with different revision), " +
      "plus a count of unchanged entities (same revision in both). " +
      "All lists are sorted by entityAppId ascending. " +
      "Requires an authenticated caller; no per-collection permission gate in v1."
  )
  @APIResponse(
    responseCode = "200",
    description = "Diff result.",
    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SnapshotDiffIO.class))
  )
  @APIResponse(responseCode = "400", description = "aAppId and bAppId are the same (self-diff is not useful).")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "404", description = "Snapshot A or snapshot B not found or deleted.")
  public Response diff(
    @PathParam("aAppId") String aAppId,
    @PathParam("bAppId") String bAppId,
    @Context SecurityContext sc
  ) {
    // Auth gate — must be authenticated
    if (sc.getUserPrincipal() == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    // Self-diff guard
    if (aAppId.equals(bAppId)) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }

    // Resolve both snapshots — 404 if either is missing or soft-deleted
    Snapshot snapshotA = snapshotService.findByAppId(aAppId);
    if (snapshotA == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    Snapshot snapshotB = snapshotService.findByAppId(bAppId);
    if (snapshotB == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    // Load entry revision maps for both snapshots
    Map<String, Long> mapA = snapshotService.getEntryRevisionMap(snapshotA.getId());
    Map<String, Long> mapB = snapshotService.getEntryRevisionMap(snapshotB.getId());

    // Compute diff categories
    List<String> added = new ArrayList<>();
    List<String> removed = new ArrayList<>();
    List<DiffEntry> changed = new ArrayList<>();
    int unchangedCount = 0;

    // Entities in A — check if they are in B and whether they changed
    for (Map.Entry<String, Long> entryA : mapA.entrySet()) {
      String entityAppId = entryA.getKey();
      long revA = entryA.getValue();
      if (mapB.containsKey(entityAppId)) {
        long revB = mapB.get(entityAppId);
        if (revA == revB) {
          unchangedCount++;
        } else {
          changed.add(new DiffEntry(entityAppId, revA, revB));
        }
      } else {
        removed.add(entityAppId);
      }
    }

    // Entities in B not in A → added
    for (String entityAppId : mapB.keySet()) {
      if (!mapA.containsKey(entityAppId)) {
        added.add(entityAppId);
      }
    }

    // Sort all lists by entityAppId for deterministic output
    added.sort(String::compareTo);
    removed.sort(String::compareTo);
    changed.sort((x, y) -> x.entityAppId().compareTo(y.entityAppId()));

    SnapshotDiffIO diff = new SnapshotDiffIO(
      snapshotA.getAppId(),
      snapshotB.getAppId(),
      snapshotA.getSnapshotCapturedAtMs(),
      snapshotB.getSnapshotCapturedAtMs(),
      added,
      removed,
      changed,
      unchangedCount
    );

    return Response.ok(diff).build();
  }
}
