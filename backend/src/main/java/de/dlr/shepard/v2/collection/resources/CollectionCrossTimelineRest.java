package de.dlr.shepard.v2.collection.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.v2.collection.daos.CollectionTimelineDAO;
import de.dlr.shepard.v2.collection.io.CollectionTimelineIO;
import de.dlr.shepard.v2.collection.services.CollectionTimelineBuilder;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
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
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * COLL-TIMELINE-CROSS-1 — {@code GET /v2/collections/timeline}.
 *
 * <p>Cross-collection timeline overlay: merges DataObjects from two or more
 * Collections into a single swimlane envelope (same shape as the single-Collection
 * {@code GET /v2/collections/{collectionAppId}/timeline} — the UI renders them
 * identically). Designed for programme-level comparisons: e.g. "MFFD Q1 shell +
 * Q2 shell on the same axis" or "LUMEN campaign across test blocks."
 *
 * <p>The {@code ?collections=} parameter accepts the appId of each Collection.
 * Repeat the parameter for each collection (JAX-RS multi-value convention):
 * {@code ?collections=id1&collections=id2&collections=id3}. A comma-separated
 * single value is also accepted for client convenience.
 *
 * <p>Auth: caller must have Read on every listed Collection. The first
 * inaccessible or missing Collection short-circuits with 403 / 404.
 *
 * <p>Static path segment {@code /v2/collections/timeline} takes JAX-RS routing
 * precedence over the per-collection parameterised path
 * {@code /v2/collections/{collectionAppId}/timeline} — no routing ambiguity.
 *
 * <p>Cross-references:
 * <ul>
 *   <li>Single-collection baseline: {@link CollectionTimelineRest} (COLL-TIMELINE-1)</li>
 *   <li>Backlog row: COLL-TIMELINE-CROSS-1</li>
 *   <li>Response shape: {@link CollectionTimelineIO} (same as single-collection)</li>
 * </ul>
 */
@Path("/v2/collections/timeline")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Authenticated
@Tag(name = "Collections")
public class CollectionCrossTimelineRest {

  private static final String PT_UNAUTHORIZED = "/problems/collection-cross-timeline.unauthorized";
  private static final String PT_NOT_FOUND    = "/problems/collection-cross-timeline.not-found";
  private static final String PT_FORBIDDEN    = "/problems/collection-cross-timeline.forbidden";
  private static final String PT_BAD_REQUEST  = "/problems/collection-cross-timeline.bad-request";

  @Inject
  CollectionTimelineDAO timelineDAO;

  @Inject
  PermissionsService permissionsService;

  @Inject
  EntityIdResolver entityIdResolver;

  @GET
  @Operation(
    summary = "Cross-collection timeline overlay.",
    description =
      "Merges DataObjects from two or more Collections into a single swimlane " +
      "envelope. The response shape is identical to the single-collection " +
      "`GET /v2/collections/{appId}/timeline` endpoint — the UI renders them " +
      "the same way.\n\n" +
      "Pass each Collection's appId via `?collections=id` (repeat for each " +
      "Collection, or use a single comma-separated value for convenience).\n\n" +
      "Auth: caller must have Read on every listed Collection. The first " +
      "missing or inaccessible Collection short-circuits with 404 or 403.\n\n" +
      "Use case: programme-level comparisons — e.g. MFFD Q1 + Q2 shells on the " +
      "same axis, or LUMEN campaign across test blocks.",
    extensions = @Extension(
      name = "x-agent-hint",
      value = "Cross-collection swimlane overlay. Supply multiple collections= params to merge their DataObjects into one timeline view."
    )
  )
  @APIResponse(
    responseCode = "200",
    description = "Merged timeline envelope (same shape as the single-collection endpoint).",
    content = @Content(schema = @Schema(implementation = CollectionTimelineIO.class))
  )
  @APIResponse(responseCode = "400", description = "No valid collection appIds supplied.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read on at least one listed Collection.")
  @APIResponse(responseCode = "404", description = "At least one listed Collection was not found.")
  public Response crossTimeline(
    @Parameter(
      description =
        "Collection appId(s) to merge. Repeat the parameter for each collection " +
        "(`?collections=id1&collections=id2`). A single comma-separated value is " +
        "also accepted (`?collections=id1,id2`). Must contain at least one valid appId."
    )
    @QueryParam("collections") List<String> collectionsRaw,
    @Parameter(
      description =
        "Histogram bin width in days. Accepted ladder values: 1, 7, 30, 90, 365. " +
        "Non-ladder values snap upward to the next ladder step. When the merged " +
        "campaign span would produce more than ~730 bins per lane, the server " +
        "auto-coarsens upward through the ladder until it fits. " +
        "The actual bin size used is echoed in the response's binSizeDays field."
    )
    @QueryParam("binSizeDays") @DefaultValue("1") int binSizeDays,
    @Context SecurityContext sc
  ) {
    String caller = sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
    if (caller == null) {
      return problem(PT_UNAUTHORIZED, "Authentication required",
        Response.Status.UNAUTHORIZED, "caller identity unknown");
    }

    List<String> collectionAppIds = parseIds(collectionsRaw);
    if (collectionAppIds.isEmpty()) {
      return problem(PT_BAD_REQUEST, "No collections specified",
        Response.Status.BAD_REQUEST,
        "Provide at least one Collection appId via ?collections=<appId>");
    }

    for (String appId : collectionAppIds) {
      long ogmId;
      try {
        ogmId = entityIdResolver.resolveLong(appId);
      } catch (NotFoundException nfe) {
        return problem(PT_NOT_FOUND, "Collection not found",
          Response.Status.NOT_FOUND, "no Collection with appId '" + appId + "'");
      }
      if (!permissionsService.isAccessTypeAllowedForUser(ogmId, AccessType.Read, caller, 0L)) {
        return problem(PT_FORBIDDEN, "Read access required",
          Response.Status.FORBIDDEN, "caller lacks Read on Collection '" + appId + "'");
      }
    }

    var aggregate = timelineDAO.aggregateMulti(collectionAppIds);
    CollectionTimelineIO body = CollectionTimelineBuilder.build(aggregate, binSizeDays);

    return Response.ok(body)
      .header("Cache-Control", "max-age=300, must-revalidate")
      .build();
  }

  // ── helpers ─────────────────────────────────────────────────────────────

  /**
   * Normalises the multi-value {@code ?collections=} parameter.
   * Each element may itself be a comma-separated list (client convenience).
   */
  static List<String> parseIds(List<String> raw) {
    if (raw == null) return List.of();
    List<String> out = new ArrayList<>();
    for (String val : raw) {
      if (val == null) continue;
      for (String part : val.split(",")) {
        String trimmed = part.strip();
        if (!trimmed.isBlank()) out.add(trimmed);
      }
    }
    return out;
  }

  private static Response problem(String type, String title, Response.Status status, String detail) {
    return Response.status(status).type("application/problem+json")
      .entity(new ProblemJson(type, title, status.getStatusCode(), detail, null)).build();
  }
}
