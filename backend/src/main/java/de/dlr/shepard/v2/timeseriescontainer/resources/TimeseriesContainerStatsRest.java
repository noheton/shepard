package de.dlr.shepard.v2.timeseriescontainer.resources;

import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.neo4j.entities.BasicContainer;
import de.dlr.shepard.v2.containers.services.ContainersV2Service;
import de.dlr.shepard.v2.timeseriescontainer.io.TimeseriesContainerStatsIO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
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
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * TS_STATS1 — storage and ingestion statistics for a TimeseriesContainer.
 *
 * <p>Route: {@code GET /v2/containers/{containerAppId}/stats}
 * (APISIMP-CONT-NS-COLLAPSE-7 — migrated from {@code /v2/timeseries-containers/}).
 * Non-timeseries containers answer 415.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/containers")
@RequestScoped
@Tag(name = "Timeseries container storage stats")
public class TimeseriesContainerStatsRest {

  private static final int BYTES_PER_POINT = 28; // 8 time + 8 double_value + 4 id + 8 overhead
  private static final long WINDOW_NS = 10_000_000_000L; // 10 seconds in nanoseconds

  private static final String PROBLEM_NOT_FOUND = "/problems/containers.not-found";
  private static final String PROBLEM_UNAUTHORIZED = "/problems/containers.unauthorized";
  private static final String PROBLEM_FORBIDDEN = "/problems/containers.forbidden";
  private static final String PROBLEM_UNSUPPORTED = "/problems/containers.stats-unsupported";

  @Inject
  ContainersV2Service containersV2Service;

  @Inject
  PermissionsService permissionsService;

  @PersistenceContext
  EntityManager entityManager;

  @GET
  @Path("/{containerAppId}/stats")
  @Operation(
    operationId = "getTimeseriesContainerStats",
    summary = "Storage and ingestion stats for a timeseries container.",
    description = "Returns point count, channel count, estimated uncompressed size, and recent " +
      "ingest rate. Only supported for `timeseries` kind containers — other kinds answer 415. " +
      "Requires Read permission on the container. The size is an uncompressed estimate " +
      "(pointCount × 28 bytes); compressed chunks on disk are typically 5–10× smaller.\n\n" +
      "(APISIMP-CONT-NS-COLLAPSE-7 — replaces " +
      "`GET /v2/timeseries-containers/{containerAppId}/stats`.)"
  )
  @APIResponse(
    responseCode = "200",
    description = "Stats for the container.",
    content = @Content(schema = @Schema(implementation = TimeseriesContainerStatsIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the container.")
  @APIResponse(responseCode = "404", description = "No container with that appId.")
  @APIResponse(responseCode = "415", description = "Container is not a timeseries container.")
  public Response getStats(
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
      return problem(PROBLEM_UNSUPPORTED, "Stats not supported for this container kind",
        Response.Status.UNSUPPORTED_MEDIA_TYPE,
        "Container kind '" + resolved.get().handler().kind() + "' does not support stats");
    }

    long containerId = container.getId();

    Object[] totals = (Object[]) entityManager.createNativeQuery(
      "SELECT COUNT(dp.timeseries_id) AS point_count, COUNT(DISTINCT dp.timeseries_id) AS channel_count " +
      "FROM timeseries_data_points dp " +
      "JOIN timeseries t ON dp.timeseries_id = t.id " +
      "WHERE t.container_id = :cid"
    ).setParameter("cid", containerId).getSingleResult();

    long pointCount = ((Number) totals[0]).longValue();
    long channelCount = ((Number) totals[1]).longValue();

    long nowNs = System.currentTimeMillis() * 1_000_000L;
    Number recent = (Number) entityManager.createNativeQuery(
      "SELECT COUNT(*) " +
      "FROM timeseries_data_points dp " +
      "JOIN timeseries t ON dp.timeseries_id = t.id " +
      "WHERE t.container_id = :cid AND dp.time > :windowStart"
    )
      .setParameter("cid", containerId)
      .setParameter("windowStart", nowNs - WINDOW_NS)
      .getSingleResult();

    long recentPoints = recent.longValue();
    long ingestRate = recentPoints * BYTES_PER_POINT / 10;

    return Response.ok(new TimeseriesContainerStatsIO(
      pointCount,
      channelCount,
      pointCount * BYTES_PER_POINT,
      recentPoints,
      ingestRate
    )).build();
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
