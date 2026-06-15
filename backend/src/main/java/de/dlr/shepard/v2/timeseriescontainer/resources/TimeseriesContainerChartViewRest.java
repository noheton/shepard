package de.dlr.shepard.v2.timeseriescontainer.resources;

import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.neo4j.entities.BasicContainer;
import de.dlr.shepard.v2.containers.services.ContainersV2Service;
import de.dlr.shepard.v2.timeseriescontainer.entities.TimeseriesContainerChartView;
import de.dlr.shepard.v2.timeseriescontainer.io.TimeseriesContainerChartViewIO;
import de.dlr.shepard.v2.timeseriescontainer.services.TimeseriesContainerChartViewService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
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
 * <p>Route: {@code GET / PATCH /v2/containers/{containerAppId}/chart-view}
 * (APISIMP-CONT-NS-COLLAPSE-7 — migrated from
 * {@code /v2/timeseries-containers/{containerAppId}/chart-view}).
 * Non-timeseries containers answer 415.
 *
 * <p>One persisted curated channel list per TimeseriesContainer, shared
 * across all users. Read inherits Read perm on the container; mutation
 * requires Write perm.
 */
@Path("/v2/containers/{containerAppId}/chart-view")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Tag(name = "Timeseries container chart view")
public class TimeseriesContainerChartViewRest {

  private static final String PROBLEM_NOT_FOUND = "/problems/containers.not-found";
  private static final String PROBLEM_UNAUTHORIZED = "/problems/containers.unauthorized";
  private static final String PROBLEM_FORBIDDEN = "/problems/containers.forbidden";
  private static final String PROBLEM_UNSUPPORTED = "/problems/containers.chart-view-unsupported";

  @Inject
  ContainersV2Service containersV2Service;

  @Inject
  PermissionsService permissionsService;

  @Inject
  TimeseriesContainerChartViewService chartViewService;

  @GET
  @Operation(
    operationId = "getChartView",
    summary = "Read the persisted chart-view configuration for this container.",
    description = "Returns the curated channel-selection list shared by all users " +
    "viewing this container. An empty list (or no persisted view yet) means " +
    "\"no curated view — show all channels\" (the frontend default).\n\n" +
    "Each entry in selectedChannels is a 5-tuple key " +
    "`measurement|device|location|symbolicName|field` (pipe-separated).\n\n" +
    "Only supported for `timeseries` kind containers — other kinds answer 415.\n\n" +
    "(APISIMP-CONT-NS-COLLAPSE-7 — replaces " +
    "`GET /v2/timeseries-containers/{containerAppId}/chart-view`.)"
  )
  @APIResponse(
    responseCode = "200",
    description = "Current chart-view (empty when never configured).",
    content = @Content(schema = @Schema(implementation = TimeseriesContainerChartViewIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the container.")
  @APIResponse(responseCode = "404", description = "No container with that containerAppId.")
  @APIResponse(responseCode = "415", description = "Container is not a timeseries container.")
  public Response get(
    @PathParam("containerAppId") String containerAppId,
    @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) {
      return problem(PROBLEM_UNAUTHORIZED, "Authentication required",
        Response.Status.UNAUTHORIZED, "No valid JWT or API key was provided");
    }

    var resolved = containersV2Service.resolveByAppId(containerAppId);
    if (resolved.isEmpty()) {
      return problem(PROBLEM_NOT_FOUND, "Not found",
        Response.Status.NOT_FOUND, "No container found for appId");
    }

    BasicContainer container = resolved.get().container();
    Response gate = gate(container, AccessType.Read, caller);
    if (gate != null) return gate;

    if (!"timeseries".equals(resolved.get().handler().kind())) {
      return problem(PROBLEM_UNSUPPORTED, "Chart-view not supported for this container kind",
        Response.Status.UNSUPPORTED_MEDIA_TYPE,
        "Container kind '" + resolved.get().handler().kind() + "' does not support chart-view");
    }

    long containerId = container.getId();
    TimeseriesContainerChartView view = chartViewService.find(containerId);
    return Response.ok(TimeseriesContainerChartViewIO.from(view)).build();
  }

  @PATCH
  @Operation(
    operationId = "patchChartView",
    summary = "RFC 7396 merge-patch the chart-view configuration.",
    description = "Replaces the persisted selectedChannels list. Mutation requires " +
    "Write permission on the container — anyone who can upload data can also " +
    "reshape what other users see in the Channel Overview chart. The mutation " +
    "is captured in the audit trail (PROV1a).\n\n" +
    "Only supported for `timeseries` kind containers — other kinds answer 415.\n\n" +
    "(APISIMP-CONT-NS-COLLAPSE-7 — replaces " +
    "`PATCH /v2/timeseries-containers/{containerAppId}/chart-view`.)"
  )
  @APIResponse(
    responseCode = "200",
    description = "Updated chart-view.",
    content = @Content(schema = @Schema(implementation = TimeseriesContainerChartViewIO.class))
  )
  @APIResponse(responseCode = "400", description = "Bad request — malformed channel keys.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the container.")
  @APIResponse(responseCode = "404", description = "No container with that containerAppId.")
  @APIResponse(responseCode = "415", description = "Container is not a timeseries container.")
  public Response patch(
    @PathParam("containerAppId") String containerAppId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = TimeseriesContainerChartViewIO.class))
    ) @Valid TimeseriesContainerChartViewIO patch,
    @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) {
      return problem(PROBLEM_UNAUTHORIZED, "Authentication required",
        Response.Status.UNAUTHORIZED, "No valid JWT or API key was provided");
    }

    var resolved = containersV2Service.resolveByAppId(containerAppId);
    if (resolved.isEmpty()) {
      return problem(PROBLEM_NOT_FOUND, "Not found",
        Response.Status.NOT_FOUND, "No container found for appId");
    }

    BasicContainer container = resolved.get().container();
    Response gate = gate(container, AccessType.Write, caller);
    if (gate != null) return gate;

    if (!"timeseries".equals(resolved.get().handler().kind())) {
      return problem(PROBLEM_UNSUPPORTED, "Chart-view not supported for this container kind",
        Response.Status.UNSUPPORTED_MEDIA_TYPE,
        "Container kind '" + resolved.get().handler().kind() + "' does not support chart-view");
    }

    long containerId = container.getId();
    TimeseriesContainerChartView updated = chartViewService.patch(containerId, patch);
    return Response.ok(TimeseriesContainerChartViewIO.from(updated)).build();
  }

  private static String callerOrNull(SecurityContext sc) {
    return sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
  }

  private Response gate(BasicContainer container, AccessType accessType, String caller) {
    Long id = container.getId();
    if (id == null) {
      return problem(PROBLEM_NOT_FOUND, "Not found", Response.Status.NOT_FOUND,
        "Container has no id (graph inconsistency)");
    }
    if (!permissionsService.isAccessTypeAllowedForUser(id, accessType, caller)) {
      return problem(PROBLEM_FORBIDDEN, "Permission denied", Response.Status.FORBIDDEN,
        "Caller lacks the required permission on this container");
    }
    return null;
  }

  private static Response problem(String type, String title, Response.Status status, String detail) {
    return Response.status(status)
      .type("application/problem+json")
      .entity(new ProblemJson(type, title, status.getStatusCode(), detail, null))
      .build();
  }
}
