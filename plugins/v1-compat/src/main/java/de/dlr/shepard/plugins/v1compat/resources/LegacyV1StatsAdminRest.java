package de.dlr.shepard.plugins.v1compat.resources;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.plugins.v1compat.io.LegacyV1StatsIO;
import de.dlr.shepard.plugins.v1compat.services.LegacyV1StatsService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * V1COMPAT.0 — admin REST surface for the in-memory v1 hit-stats
 * counters (Phase 1, per {@code aidocs/platform/103a §2 row 4}).
 *
 * <p>One endpoint: {@code GET /v2/admin/legacy/v1/stats}. Returns a
 * snapshot of the counters maintained by
 * {@link LegacyV1StatsService}: total hits this process, per-
 * endpoint breakdown (top N), per-principal breakdown (top N),
 * first + most-recent hit timestamps. Process restart resets the
 * counters — that's the documented behaviour (the design defers
 * durable per-day / per-week counters to Phase 2).
 *
 * <p>The {@code topN} query parameter caps both breakdown lists;
 * default 50, max 1000 (clamped server-side to avoid pathologically
 * large responses).
 */
@Path("/v2/admin/legacy/v1/stats")
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
@Tag(name = "Admin")
public class LegacyV1StatsAdminRest {

  static final int MAX_TOP_N = 1000;

  @Inject
  LegacyV1StatsService stats;

  @GET
  @Operation(
    summary = "Read the in-memory v1 hit-stats snapshot.",
    description = "Returns total hits, per-endpoint breakdown, per-principal breakdown, " +
    "and first/most-recent hit timestamps. Process restart resets the counters. The " +
    "topN query parameter caps both breakdown lists (default 50, max 1000)."
  )
  @APIResponse(
    responseCode = "200",
    description = "Current v1-hit stats snapshot.",
    content = @Content(schema = @Schema(implementation = LegacyV1StatsIO.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response getStats(@QueryParam("topN") Integer topN) {
    int effective = topN == null ? LegacyV1StatsService.DEFAULT_TOP_N : Math.min(Math.max(1, topN), MAX_TOP_N);
    return Response.ok(stats.snapshot(effective)).build();
  }
}
