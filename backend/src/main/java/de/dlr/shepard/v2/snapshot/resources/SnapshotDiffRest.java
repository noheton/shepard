package de.dlr.shepard.v2.snapshot.resources;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.context.snapshot.entities.Snapshot;
import de.dlr.shepard.context.snapshot.io.SnapshotDiffIO;
import de.dlr.shepard.context.snapshot.io.SnapshotDiffIO.DiffEntry;
import de.dlr.shepard.context.snapshot.services.SnapshotService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import static de.dlr.shepard.v2.common.ProblemResponse.problem;

/**
 * V2e — {@code GET /v2/snapshots/{aAppId}/diff/{bAppId}}.
 *
 * <p>Compares the {@link de.dlr.shepard.context.snapshot.entities.SnapshotEntry}
 * sets of two snapshots (A = base, B = head) and returns which entities were
 * added, removed, or changed in revision between them. Entities with identical
 * revisions are counted but not listed (the set can be arbitrarily large).
 *
 * <p>Server-side cap: each of the {@code added}, {@code removed}, and {@code changed}
 * lists is capped at {@code ceil(?maxItems / 3)} entries (default {@code ?maxItems=5000},
 * max {@code 20000}). When any list is capped, {@code truncated=true} is set and the
 * {@code totalAdded}/{@code totalRemoved}/{@code totalChanged} counts reflect the full
 * uncapped diff.
 *
 * <p>Auth: authenticated caller required (401 when unauthenticated). In v1 no
 * additional per-collection permission gate is applied — snapshots are metadata
 * only; a stricter per-collection gate can be added in a future revision when
 * cross-collection diff is in scope.
 *
 * <p>Error conditions:
 * <ul>
 *   <li>401 — unauthenticated caller.</li>
 *   <li>400 — {@code aAppId} equals {@code bAppId} (self-diff is meaningless),
 *       or {@code ?maxItems} is outside [1, 20000].</li>
 *   <li>404 — snapshot A or snapshot B not found or soft-deleted.</li>
 * </ul>
 *
 * <p>All result lists are sorted by {@code entityAppId} ascending for
 * deterministic output.
 *
 * <p>Cross-references: {@code aidocs/41} §7; {@code aidocs/16} V2e,
 * {@code APISIMP-SNAPSHOT-DIFF-UNCAPPED};
 * API-version policy (CLAUDE.md — all new endpoints under {@code /v2/}).
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/snapshots/{aAppId}/diff/{bAppId}")
@RequestScoped
@Tag(name = "Snapshots")
public class SnapshotDiffRest {

  /** Default cap across all three diff lists (added + removed + changed). */
  static final int DEFAULT_MAX_ITEMS = 5000;

  /** Hard upper bound on ?maxItems to protect response budget. */
  static final int HARD_MAX_ITEMS = 20000;

  @Inject
  SnapshotService snapshotService;

  private static final String PT_UNAUTH = "/problems/snapshots.unauthorized";
  private static final String PT_NOT_FOUND = "/problems/snapshots.not-found";

  /**
   * Diff two snapshots.
   *
   * @param aAppId    the application-level identifier of snapshot A (base).
   * @param bAppId    the application-level identifier of snapshot B (head).
   * @param sc        the JAX-RS security context.
   * @param maxItems  server-side cap on the total number of diff items returned across all
   *                  three lists (default 5000, max 20000). Each list is capped at
   *                  {@code ceil(maxItems/3)} entries; {@code truncated} is set to
   *                  {@code true} when any list is capped.
   * @return 200 with a {@link SnapshotDiffIO}; 400 when A and B are the same or
   *         {@code maxItems} is out of range; 401 unauthenticated; 404 when either
   *         snapshot is unknown or deleted.
   */
  @GET
  @Operation(
    operationId = "diff",
    summary = "Diff two snapshots.",
    description =
      "Compares the entity sets of snapshot A (base) and snapshot B (head). " +
      "Returns lists of entityAppIds that were added (in B, not in A), removed " +
      "(in A, not in B), or changed revision (present in both with different revision), " +
      "plus a count of unchanged entities (same revision in both). " +
      "All lists are sorted by entityAppId ascending. " +
      "Each list is capped at ceil(?maxItems/3) entries (default maxItems=5000, max 20000); " +
      "when any list is capped, truncated=true and totalAdded/totalRemoved/totalChanged " +
      "reflect the full uncapped counts. " +
      "Requires an authenticated caller; no per-collection permission gate in v1."
  )
  @APIResponse(
    responseCode = "200",
    description = "Diff result.",
    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SnapshotDiffIO.class))
  )
  @APIResponse(
    responseCode = "400",
    description = "aAppId and bAppId are the same (self-diff is not useful), or ?maxItems is outside [1, 20000].",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemJson.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "404", description = "Snapshot A or snapshot B not found or deleted.")
  public Response diff(
    @PathParam("aAppId") String aAppId,
    @PathParam("bAppId") String bAppId,
    @Context SecurityContext sc,
    @Parameter(description = "Cap on the total number of items returned across added + removed + changed lists. Each list is capped at ceil(maxItems/3). Default 5000, max 20000.")
    @QueryParam("maxItems") @DefaultValue("5000") @Min(1) @Max(HARD_MAX_ITEMS) int maxItems
  ) {
    // Auth gate — must be authenticated
    if (sc.getUserPrincipal() == null) {
      return problem(PT_UNAUTH, "Unauthorized", Response.Status.UNAUTHORIZED, "Authentication required.");
    }

    // Self-diff guard
    if (aAppId.equals(bAppId)) {
      return problem("/problems/snapshots.self-diff", "Bad Request", Response.Status.BAD_REQUEST,
          "aAppId and bAppId are the same; self-diff is not useful.");
    }

    // Resolve both snapshots — 404 if either is missing or soft-deleted
    Snapshot snapshotA = snapshotService.findByAppId(aAppId);
    if (snapshotA == null) {
      return problem(PT_NOT_FOUND, "Not Found", Response.Status.NOT_FOUND, "Snapshot A not found: '" + aAppId + "'.");
    }
    Snapshot snapshotB = snapshotService.findByAppId(bAppId);
    if (snapshotB == null) {
      return problem(PT_NOT_FOUND, "Not Found", Response.Status.NOT_FOUND, "Snapshot B not found: '" + bAppId + "'.");
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

    // Record full totals before capping
    int totalAdded = added.size();
    int totalRemoved = removed.size();
    int totalChanged = changed.size();

    // Apply per-list cap: each list gets ceil(maxItems / 3) entries
    int perListCap = (maxItems + 2) / 3;
    boolean truncated = false;
    if (added.size() > perListCap) {
      added = added.subList(0, perListCap);
      truncated = true;
    }
    if (removed.size() > perListCap) {
      removed = removed.subList(0, perListCap);
      truncated = true;
    }
    if (changed.size() > perListCap) {
      changed = changed.subList(0, perListCap);
      truncated = true;
    }

    SnapshotDiffIO diff = new SnapshotDiffIO(
      snapshotA.getAppId(),
      snapshotB.getAppId(),
      Instant.ofEpochMilli(snapshotA.getSnapshotCapturedAtMs()).toString(),
      Instant.ofEpochMilli(snapshotB.getSnapshotCapturedAtMs()).toString(),
      added,
      removed,
      changed,
      unchangedCount,
      totalAdded,
      totalRemoved,
      totalChanged,
      truncated
    );

    return Response.ok(diff).build();
  }
}
