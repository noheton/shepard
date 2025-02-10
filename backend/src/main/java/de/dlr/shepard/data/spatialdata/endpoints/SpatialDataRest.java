package de.dlr.shepard.data.spatialdata.endpoints;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.data.spatialdata.io.SpatialDataParamsIO;
import de.dlr.shepard.data.spatialdata.io.SpatialDataPointIO;
import de.dlr.shepard.data.spatialdata.model.DatabaseType;
import de.dlr.shepard.data.spatialdata.services.SpatialDataService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(Constants.SPATIAL_DATA_CONTAINER)
@RequestScoped
public class SpatialDataRest {

  private SpatialDataService spatialDataService;

  @Inject
  SpatialDataRest(SpatialDataService spatialDataService) {
    this.spatialDataService = spatialDataService;
  }

  @POST
  @Path("/{" + Constants.SPATIAL_DATA_CONTAINER_ID + "}/" + Constants.PAYLOAD)
  @Tag(name = Constants.SPATIAL_DATA_CONTAINER)
  @Operation(description = "Get spatial data by container id")
  @Parameter(name = Constants.SPATIAL_DATA_DATABASE_TYPE, required = true)
  @APIResponse(
    description = "OK",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = SpatialDataPointIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  public Response getSpatialDataPoints(
    @PathParam(Constants.SPATIAL_DATA_CONTAINER_ID) long containerId,
    @QueryParam(Constants.SPATIAL_DATA_DATABASE_TYPE) DatabaseType databaseType,
    @RequestBody(
      required = false,
      content = @Content(schema = @Schema(implementation = SpatialDataParamsIO.class))
    ) @Valid SpatialDataParamsIO spatialDataParams
  ) {
    spatialDataService.getSpatialDataPoints(containerId, spatialDataParams, databaseType);
    return Response.ok().build();
  }

  @PATCH
  @Path("/{" + Constants.SPATIAL_DATA_CONTAINER_ID + "}/" + Constants.PAYLOAD)
  @Tag(name = Constants.SPATIAL_DATA_CONTAINER)
  @Operation(description = "Adding data points to spatial data container")
  @Parameter(name = Constants.SPATIAL_DATA_DATABASE_TYPE, required = true)
  @APIResponse(
    description = "OK",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = SpatialDataPointIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  public Response createSpatialDataPoints(
    @PathParam(Constants.SPATIAL_DATA_CONTAINER_ID) long containerId,
    @QueryParam(Constants.SPATIAL_DATA_DATABASE_TYPE) DatabaseType databaseType,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = SpatialDataPointIO.class))
    ) @Valid List<SpatialDataPointIO> dataPoints
  ) {
    spatialDataService.createSpatialDataPoints(containerId, dataPoints, databaseType);
    return Response.ok().build();
  }
}
