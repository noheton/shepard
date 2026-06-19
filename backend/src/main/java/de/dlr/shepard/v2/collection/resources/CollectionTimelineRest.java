package de.dlr.shepard.v2.collection.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.v2.collection.daos.CollectionTimelineDAO;
import de.dlr.shepard.v2.collection.io.CollectionTimelineIO;
import de.dlr.shepard.v2.collection.services.CollectionTimelineBuilder;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.Consumes;
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
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * COLL-TIMELINE-1 — {@code GET /v2/collections/{appId}/timeline}.
 *
 * <p>Returns a swimlane chronograph of the Collection: one row per
 * process-type (derived from the {@code urn:shepard:mffd:process-type}
 * SemanticAnnotation), each row carrying day-binned counts of
 * DataObjects with NCR / REJECTED status counts overlaid. Designed
 * for the Collection-landing "Timeline" tab — the visual answer to
 * "how many tracks per day, when did NCRs cluster, when did we re-test?"
 * at MFFD scale (8k+ DataObjects across 2.6 years).
 *
 * <p>Anchor timestamp is the DataObject's {@code createdAt} (a single
 * Cypher round-trip); time-bounds-derived anchoring is left to a
 * follow-up backlog row when the Collection has timeseries-heavy
 * DataObjects whose createdAt drifts from their actual measurement
 * window.
 *
 * <p>Performance target: ≤ 2 s for an MFFD-scale Collection (≈ 8.2k
 * DataObjects, 5 lanes). The implementation hits Neo4j once for the
 * grouping aggregate plus once for the campaign range — both are
 * pure aggregates, no per-DO fan-out.
 *
 * <p>Cross-references:
 * <ul>
 *   <li>GAP-8 in {@code aidocs/agent-findings/mffd-feature-gaps-2026-06-02.md}</li>
 *   <li>Lane semantics: V100 MFFD_PROCESS_TEMPLATES seed</li>
 *   <li>Status vocabulary (NCR/REJECT): AAA2 status extensions</li>
 * </ul>
 */
@Path("/v2/collections/{collectionAppId}/timeline")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Authenticated
@Tag(name = "Collections — timeline (COLL-TIMELINE-1)")
public class CollectionTimelineRest {

  @Inject
  CollectionTimelineDAO timelineDAO;

  @Inject
  PermissionsService permissionsService;

  @Inject
  EntityIdResolver entityIdResolver;

  @GET
  @Operation(
    summary = "Process-chain timeline (swimlane chronograph) for a Collection.",
    description =
      "Returns a swimlane envelope where each lane is a distinct process-type " +
      "(derived from the `urn:shepard:mffd:process-type` SemanticAnnotation on " +
      "each DataObject) and each bin within a lane counts the DataObjects whose " +
      "`createdAt` falls in that bin's day window. NCR (`NCR_OPEN`, " +
      "`CONCESSION_PENDING`) and `REJECTED` counts are overlaid per-bin so the " +
      "UI can render stacked colour-coded bars.\n\n" +
      "DataObjects without a `urn:shepard:mffd:process-type` annotation collect into a " +
      "synthetic `unclassified` lane — so the Timeline still surfaces activity for " +
      "non-MFFD Collections (LUMEN, home-showcase) without misleadingly hiding it.\n\n" +
      "Adaptive bin size: `?binSizeDays=` accepts 1, 7, 30, 90, or 365 " +
      "(non-ladder values snap upward). When the campaign span divided by the " +
      "requested bin size would produce more than ~730 bins per lane, the " +
      "server auto-coarsens upward through the ladder until it fits. The " +
      "actual bin size used is echoed in the response's `binSizeDays` field.\n\n" +
      "Auth: Read permission on the Collection (inherited by DataObjects).\n\n" +
      "Performance target: ≤ 2 s for an MFFD-scale Collection (≈ 8.2k " +
      "DataObjects, 5 lanes). Cached for 5 minutes via Cache-Control.",
    extensions = @Extension(
      name = "x-agent-hint",
      value = "Process-step swimlane view of a Collection. Use to surface campaign cadence + NCR clusters at scale; pair with the DataObjects list endpoint for drill-down."
    )
  )
  @APIResponse(
    responseCode = "200",
    description =
      "Timeline envelope with lanes + bins. `lanes` is empty when the " +
      "Collection has no non-deleted DataObjects. `binSizeDays` reflects the " +
      "actually-used bin size (may differ from the request when auto-coarsened).",
    content = @Content(schema = @Schema(implementation = CollectionTimelineIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the Collection.")
  @APIResponse(responseCode = "404", description = "No Collection with that appId.")
  public Response timeline(
    @PathParam("collectionAppId") @NotBlank String collectionAppId,
    @Parameter(description = "Bin width in days (1, 7, 30, 90, or 365). Non-ladder values are snapped up. The server auto-coarsens when the campaign span would exceed ~730 bins; the actual bin size used is echoed in the response.") @QueryParam("binSizeDays") @DefaultValue("1") int binSizeDays,
    @Context SecurityContext sc
  ) {
    String caller = sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();

    long ogmId;
    try {
      ogmId = entityIdResolver.resolveLong(collectionAppId);
    } catch (NotFoundException nfe) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    if (!permissionsService.isAccessTypeAllowedForUser(ogmId, AccessType.Read, caller, 0L)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    var aggregate = timelineDAO.aggregate(collectionAppId);
    CollectionTimelineIO body = CollectionTimelineBuilder.build(aggregate, binSizeDays);

    return Response.ok(body)
      .header("Cache-Control", "max-age=300, must-revalidate")
      .build();
  }
}
