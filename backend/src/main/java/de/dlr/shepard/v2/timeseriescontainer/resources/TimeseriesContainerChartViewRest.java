package de.dlr.shepard.v2.timeseriescontainer.resources;

import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.v2.timeseriescontainer.entities.TimeseriesContainerChartView;
import de.dlr.shepard.v2.timeseriescontainer.io.TimeseriesContainerChartViewIO;
import de.dlr.shepard.v2.timeseriescontainer.services.TimeseriesContainerChartViewService;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * TS_CHART_VIEW1 — REST surface for the per-container "Channel Overview"
 * chart selection.
 *
 * <p>One persisted curated channel list per TimeseriesContainer, shared
 * across all users. Read inherits Read perm on the container; mutation
 * requires Write perm — i.e. anyone who can upload data can also
 * reshape what other users see in the overview chart.
 *
 * <p>Per-session "Show all channels" overrides live in the browser (the
 * frontend keeps a session-only toggle that ignores this persisted
 * default). The audit trail (PROV1a) captures every PATCH.
 */
@Path("/v2/timeseries-containers/{containerId}/chart-view")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Authenticated
@Tag(name = "Timeseries container chart view (TS_CHART_VIEW1)")
public class TimeseriesContainerChartViewRest {

  @Inject
  TimeseriesContainerService containerService;

  @Inject
  TimeseriesContainerChartViewService chartViewService;

  @GET
  @Operation(
    summary = "Read the persisted chart-view configuration for this container.",
    description = "Returns the curated channel-selection list shared by all users " +
    "viewing this container. An empty list (or no persisted view yet) means " +
    "\"no curated view — show all channels\" (the frontend default).\n\n" +
    "Each entry in selectedChannels is a 5-tuple key " +
    "`measurement|device|location|symbolicName|field` (pipe-separated)."
  )
  @APIResponse(
    responseCode = "200",
    description = "Current chart-view (empty when never configured).",
    content = @Content(schema = @Schema(implementation = TimeseriesContainerChartViewIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the container.")
  @APIResponse(responseCode = "404", description = "No TimeseriesContainer with that id.")
  public Response get(
    @PathParam("containerId") @NotNull @PositiveOrZero Long containerId
  ) {
    // getContainer enforces the Read-permission check.
    containerService.getContainer(containerId);
    TimeseriesContainerChartView view = chartViewService.find(containerId);
    return Response.ok(TimeseriesContainerChartViewIO.from(view)).build();
  }

  @PATCH
  @Operation(
    summary = "RFC 7396 merge-patch the chart-view configuration.",
    description = "Replaces the persisted selectedChannels list. Mutation requires " +
    "Write permission on the container — anyone who can upload data can also " +
    "reshape what other users see in the Channel Overview chart. The mutation " +
    "is captured in the audit trail (PROV1a)."
  )
  @APIResponse(
    responseCode = "200",
    description = "Updated chart-view.",
    content = @Content(schema = @Schema(implementation = TimeseriesContainerChartViewIO.class))
  )
  @APIResponse(responseCode = "400", description = "Bad request — malformed channel keys.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the container.")
  @APIResponse(responseCode = "404", description = "No TimeseriesContainer with that id.")
  public Response patch(
    @PathParam("containerId") @NotNull @PositiveOrZero Long containerId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = TimeseriesContainerChartViewIO.class))
    ) @Valid TimeseriesContainerChartViewIO patch
  ) {
    // Read first to fail fast on 404 / 403 (Read); then escalate to the
    // Write check via the standard container-service assertion.
    containerService.getContainer(containerId);
    containerService.assertIsAllowedToEditContainer(containerId);
    TimeseriesContainerChartView updated = chartViewService.patch(containerId, patch);
    return Response.ok(TimeseriesContainerChartViewIO.from(updated)).build();
  }
}
