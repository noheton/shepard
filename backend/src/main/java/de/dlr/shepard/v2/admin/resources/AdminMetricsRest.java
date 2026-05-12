package de.dlr.shepard.v2.admin.resources;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.v2.admin.io.AdminMetricsSummaryIO;
import de.dlr.shepard.v2.admin.services.AdminMetricsSummaryService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * {@code GET /v2/admin/metrics-summary} — the in-process readout that
 * powers the admin pane's "Instance health" strip
 * ({@code aidocs/51 §9.5}, A3b1).
 *
 * <p>Returns a single rolled-up payload of JVM memory, uptime, HTTP
 * request totals, and permissions-cache hit ratio. The full Grafana
 * dashboard (`monitoring` compose profile, per `aidocs/16` PERF1)
 * remains the canonical operator UI; this endpoint is the
 * link-and-summary variant for the in-app admin pane.
 *
 * <p>Gated on {@code instance-admin} per {@code aidocs/51}.
 */
@Produces(MediaType.APPLICATION_JSON)
@Path("/v2/admin/metrics-summary")
@RequestScoped
@Tag(name = "Admin metrics (v2)")
public class AdminMetricsRest {

  @Inject
  AdminMetricsSummaryService service;

  @GET
  @Operation(
    summary = "Instance-health summary for the admin pane.",
    description = "Rolls up JVM heap / uptime / HTTP-request total + mean / permissions-cache " +
    "hit ratio into one payload, read from the in-process Micrometer registry. " +
    "Operator's browser never talks to Prometheus."
  )
  @APIResponse(
    responseCode = "200",
    description = "Current snapshot.",
    content = @Content(schema = @Schema(implementation = AdminMetricsSummaryIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  @RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
  public Response get() {
    return Response.ok(service.snapshot()).build();
  }
}
