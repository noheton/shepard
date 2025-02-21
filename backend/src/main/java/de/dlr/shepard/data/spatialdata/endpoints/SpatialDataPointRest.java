package de.dlr.shepard.data.spatialdata.endpoints;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.data.spatialdata.io.SpatialDataParamsIO;
import de.dlr.shepard.data.spatialdata.io.SpatialDataPointIO;
import de.dlr.shepard.data.spatialdata.model.AbstractGeometryFilter;
import de.dlr.shepard.data.spatialdata.services.SpatialDataPointService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(Constants.SPATIAL_DATA_CONTAINER)
@RequestScoped
public class SpatialDataPointRest {

  @Inject
  private SpatialDataPointService postGisService;

  @Inject
  ManagedExecutor executor;

  @GET
  @Path("/{" + Constants.SPATIAL_DATA_CONTAINER_ID + "}/" + Constants.PAYLOAD)
  @Tag(name = Constants.SPATIAL_DATA_CONTAINER)
  @Operation(description = "Get spatial data by container id")
  @Parameter(name = Constants.SPATIAL_DATA_DATABASE_TYPE, required = true)
  @Parameter(name = "metadataFilter", required = false)
  @Parameter(
    name = "geometryFilter",
    required = true,
    examples = {
      @ExampleObject(
        name = "K Nearest Neighbor",
        value = """
        {
          "type": "K_NEAREST_NEIGHBOR",
          "k": 5,
          "x": 10,
          "y": 20,
          "z": 30
        }"""
      ),
      @ExampleObject(
        name = "Bounding Box",
        value = """
        {
          "type": "AXIS_ALIGNED_BOUNDING_BOX",
          "minX": 0,
          "minY": 0,
          "minZ": 0,
          "maxX": 100,
          "maxY": 100,
          "maxZ": 100
        }"""
      ),
      @ExampleObject(
        name = "Bounding Sphere",
        value = """
        {
          "type": "BOUNDING_SPHERE",
          "radius": 50,
          "centerX": 15,
          "centerY": 25,
          "centerZ": 20
        }"""
      ),
    }
  )
  @Parameter(name = "startTime", required = false)
  @Parameter(name = "endTime", required = false)
  @Parameter(name = "limit", required = false)
  @Parameter(name = "offset", required = false)
  @Parameter(name = "skip", required = false)
  @APIResponse(
    description = "OK",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = SpatialDataPointIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  public Response getSpatialDataPoints(
    @PathParam(Constants.SPATIAL_DATA_CONTAINER_ID) long containerId,
    @QueryParam("geometryFilter") String geometryFilterParam,
    @QueryParam("metadataFilter") String metadataFilterParam,
    @QueryParam("startTime") Long startTime,
    @QueryParam("endTime") Long endTime,
    @QueryParam("limit") Integer limit,
    @QueryParam("offset") Integer offset,
    @QueryParam("skip") Integer skip
  ) {
    Optional<AbstractGeometryFilter> geometryFilter = SpatialDataParamParser.parseGeometryFilter(
      Optional.ofNullable(geometryFilterParam)
    );
    Optional<Map<String, Object>> metadata = SpatialDataParamParser.parseMetadata(
      Optional.ofNullable(metadataFilterParam)
    );

    SpatialDataParamsIO spatialDataParams = new SpatialDataParamsIO(
      geometryFilter.orElse(null),
      metadata.orElse(Collections.emptyMap()),
      startTime,
      endTime,
      limit,
      offset,
      skip
    );

    return Response.ok(postGisService.getSpatialDataPointIOs(Math.toIntExact(containerId), spatialDataParams)).build();
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
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = SpatialDataPointIO.class))
    ) @Valid List<SpatialDataPointIO> dataPoints
  ) {
    postGisService.createSpatialDataPoints(containerId, dataPoints);
    return Response.ok().build();
  }

  @DELETE
  @Path("/{" + Constants.SPATIAL_DATA_CONTAINER_ID + "}")
  @Tag(name = Constants.SPATIAL_DATA_CONTAINER)
  @Operation(description = "Deletes spatial data container and related spatial data.")
  @APIResponse(description = "ok", responseCode = "200")
  @Parameter(name = Constants.SPATIAL_DATA_CONTAINER_ID)
  public Response deleteSpatialContainer(@PathParam(Constants.SPATIAL_DATA_CONTAINER_ID) long containerId) {
    postGisService.deleteContainer(containerId);
    return Response.status(Status.OK).build();
  }
}
