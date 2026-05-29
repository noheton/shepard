package de.dlr.shepard.data.spatialdata.endpoints;

import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.healthz.DatabaseKind;
import de.dlr.shepard.common.healthz.RequiresDatabase;
import de.dlr.shepard.data.spatialdata.daos.SpatialDataContainerDAO;
import de.dlr.shepard.data.spatialdata.io.SpatialDataPointIO;
import de.dlr.shepard.data.spatialdata.io.SpatialDataQueryParams;
import de.dlr.shepard.data.spatialdata.services.SpatialDataContainerService;
import de.dlr.shepard.data.spatialdata.services.SpatialDataPointService;
import io.quarkus.resteasy.reactive.server.EndpointDisabled;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * SPATIAL-V6-004 — v2 trace endpoint for SpatialDataContainer.
 *
 * <p>Exposes {@code GET /v2/spatial-containers/{appId}/trace} — a thin
 * appId-addressed facade over the existing v1 payload endpoint. Callers
 * pass the container's UUID v7 {@code appId} instead of the Neo4j
 * long id; the service resolves to the same underlying spatial data.
 *
 * <p>The endpoint is gated by the same
 * {@code shepard.spatial-data.enabled} feature flag as the v1 surface.
 * Auth: {@code @RolesAllowed("authenticated")} — the same posture as
 * other v2 read endpoints (only registered users can read spatial data).
 *
 * @see SpatialDataPointRest
 */
@EndpointDisabled(name = "shepard.spatial-data.enabled", stringValue = "false")
@RequiresDatabase(DatabaseKind.SPATIAL)
@Path("/v2/spatial-containers")
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
@Tag(name = "Spatial containers v2 (SPATIAL-V6-004)")
public class SpatialDataTraceRest {

  @Inject
  SpatialDataPointService dataPointService;

  @Inject
  SpatialDataContainerService containerService;

  @Inject
  SpatialDataContainerDAO containerDAO;

  /**
   * Returns spatial data points for the container identified by its appId.
   *
   * <p>Supports an optional {@code limit} query parameter to cap the
   * number of points returned (default: 5000 — suitable for the 3D viewer).
   * The response shape is identical to the v1 payload endpoint so the
   * frontend can use the same {@link SpatialDataPointIO} type.
   *
   * @param appId   UUID v7 appId of the SpatialDataContainer
   * @param limit   optional cap on returned points (default 5000)
   * @return 200 with the point list; 404 when the container is not found
   */
  @GET
  @Path("/{appId}/trace")
  @RolesAllowed("authenticated")
  @Operation(
    description = "Get spatial data points (brush trace) for a container, addressed by its UUID v7 appId. " +
      "Returns the same SpatialDataPoint shape as GET /shepard/api/spatial-data-containers/{id}/payload. " +
      "SPATIAL-V6-004."
  )
  @APIResponse(
    description = "OK",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = SpatialDataPointIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found — no SpatialDataContainer with this appId")
  @APIResponse(responseCode = "503", description = "spatial database currently unreachable")
  @Parameter(name = "appId", description = "UUID v7 appId of the SpatialDataContainer", required = true)
  @Parameter(name = "limit", description = "Maximum number of points to return (default 5000)", required = false)
  public Response getTrace(
    @PathParam("appId") @NotBlank String appId,
    @QueryParam("limit") @PositiveOrZero Integer limit
  ) {
    var container = containerDAO.findByAppId(appId);
    if (container == null) {
      throw new InvalidPathException(
        "SpatialDataContainer with appId %s not found".formatted(appId)
      );
    }

    // Ensure the current user has read access to the container via the service.
    containerService.getContainer(container.getId());

    int effectiveLimit = (limit != null) ? limit : 5000;
    SpatialDataQueryParams params = new SpatialDataQueryParams(
      null,
      Collections.emptyMap(),
      Collections.emptyList(),
      null,
      null,
      effectiveLimit,
      null
    );
    List<SpatialDataPointIO> points = dataPointService.getSpatialDataPointIOs(container.getId(), params);
    return Response.ok(points).build();
  }
}
