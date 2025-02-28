package de.dlr.shepard.data.spatialdata.endpoints;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.data.ContainerAttributes;
import de.dlr.shepard.data.spatialdata.io.SpatialDataContainerIO;
import de.dlr.shepard.data.spatialdata.io.SpatialDataPointIO;
import de.dlr.shepard.data.spatialdata.io.SpatialDataQueryParams;
import de.dlr.shepard.data.spatialdata.model.geometryFilter.AbstractGeometryFilter;
import de.dlr.shepard.data.spatialdata.services.SpatialDataContainerService;
import de.dlr.shepard.data.spatialdata.services.SpatialDataPointService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path(Constants.SPATIAL_DATA_CONTAINERS)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
public class SpatialDataPointRest {

  private SpatialDataPointService dataPointService;
  private SpatialDataContainerService containerService;

  @Context
  private SecurityContext securityContext;

  SpatialDataPointRest() {}

  @Inject
  SpatialDataPointRest(SpatialDataPointService dataPointService, SpatialDataContainerService containerService) {
    this.dataPointService = dataPointService;
    this.containerService = containerService;
  }

  //#region Container functions
  @GET
  @Tag(name = Constants.SPATIAL_DATA_CONTAINER)
  @Operation(description = "Get spatial data containers.")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = SpatialDataContainerIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.QP_NAME)
  @Parameter(name = Constants.QP_PAGE)
  @Parameter(name = Constants.QP_SIZE)
  @Parameter(name = Constants.QP_ORDER_BY_ATTRIBUTE)
  @Parameter(name = Constants.QP_ORDER_DESC)
  public Response getSpatialDataContainers(
    @QueryParam(Constants.QP_NAME) String name,
    @QueryParam(Constants.QP_PAGE) Integer page,
    @QueryParam(Constants.QP_SIZE) Integer size,
    @QueryParam(Constants.QP_ORDER_BY_ATTRIBUTE) ContainerAttributes orderBy,
    @QueryParam(Constants.QP_ORDER_DESC) Boolean orderDesc
  ) {
    var params = new QueryParamHelper();
    if (name != null) params = params.withName(name);
    if (page != null && size != null) params = params.withPageAndSize(page, size);
    if (orderBy != null) params = params.withOrderByAttribute(orderBy, orderDesc);

    var containers = containerService.getContainers(params, securityContext.getUserPrincipal().getName());
    var result = SpatialDataContainerIO.fromEntities(containers);
    return Response.ok(result).build();
  }

  @GET
  @Path("/{" + Constants.SPATIAL_DATA_CONTAINER_ID + "}")
  @Tag(name = Constants.SPATIAL_DATA_CONTAINER)
  @Operation(description = "Get spatial data container.")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = SpatialDataContainerIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.SPATIAL_DATA_CONTAINER_ID)
  public Response getSpatialDataContainer(@PathParam(Constants.SPATIAL_DATA_CONTAINER_ID) long containerId) {
    var container = containerService.getContainer(containerId);
    return Response.ok(SpatialDataContainerIO.fromEntity(container)).build();
  }

  @POST
  @Tag(name = Constants.SPATIAL_DATA_CONTAINER)
  @Operation(description = "Create a new spatial data container.")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = SpatialDataContainerIO.class))
  )
  @Transactional
  public Response createSpatialDataContainer(
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = SpatialDataContainerIO.class))
    ) @Valid SpatialDataContainerIO containerIo
  ) {
    var container = containerService.createContainer(
      containerIo.getName(),
      securityContext.getUserPrincipal().getName()
    );

    return Response.ok(SpatialDataContainerIO.fromEntity(container)).status(Status.CREATED).build();
  }

  @DELETE
  @Path("/{" + Constants.SPATIAL_DATA_CONTAINER_ID + "}")
  @Tag(name = Constants.SPATIAL_DATA_CONTAINER)
  @Operation(description = "Deletes spatial data container and related spatial data.")
  @APIResponse(description = "ok", responseCode = "200")
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.SPATIAL_DATA_CONTAINER_ID)
  public Response deleteSpatialDataContainer(@PathParam(Constants.SPATIAL_DATA_CONTAINER_ID) long containerId) {
    containerService.deleteContainer(containerId, securityContext.getUserPrincipal().getName());
    return Response.status(Status.OK).build();
  }

  //#endregion

  @GET
  @Path("/{" + Constants.SPATIAL_DATA_CONTAINER_ID + "}/" + Constants.PAYLOAD)
  @Tag(name = Constants.SPATIAL_DATA_CONTAINER)
  @Operation(description = "Get spatial data by container id")
  @Parameter(name = Constants.SPATIAL_DATA_DATABASE_TYPE, required = true)
  @Parameter(
    name = "metadataFilter",
    required = false,
    description = """
    This filter should be a stringified list of JSON object for exact match in metadata. example : ```{
          "track":1,
          "layer":4,
          "key":{
            "subKey": "some data"
          }
        }```"""
  )
  @Parameter(
    name = "measurementsFilter",
    required = false,
    description = """
    This filter should be a stringified list of JSON FilterConditions. \n
    FilterCondition has this structure: {'key':<KEY>, 'operator': <OPERATOR>, 'value': <VALUE>}. \n
    The key is a comma separated path keynames string. \n
    The operator is one of ['EQUALS', 'GREATER_THAN'. 'LESS_THAN']. \n
    The value needs to be a number. \n
    example : ```[{ "key": "temperature,val", "operator": "EQUALS", "value": 20 }]```"""
  )
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
  @Parameter(name = "startTime", required = false, description = "Start timestamp in nanoseconds, exclusive")
  @Parameter(name = "endTime", required = false, description = "End timestamp in nanoseconds, exclusive")
  @Parameter(name = "limit", required = false)
  @Parameter(name = "offset", required = false)
  @Parameter(
    name = "skip",
    required = false,
    description = "Returns every nth data point from the container. We use the modulo operator on the point ids, therefore an even distribution cannot be guaranteed."
  )
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
    @QueryParam("measurementsFilter") String measurementsFilterParam,
    @QueryParam("startTime") Long startTime,
    @QueryParam("endTime") Long endTime,
    @QueryParam("limit") Integer limit,
    @QueryParam("offset") Integer offset,
    @QueryParam("skip") Integer skip
  ) {
    AbstractGeometryFilter geometryFilter = SpatialDataParamParser.parseGeometryFilter(
      Optional.ofNullable(geometryFilterParam)
    );
    Optional<Map<String, Object>> metadata = SpatialDataParamParser.parseMetadata(
      Optional.ofNullable(metadataFilterParam)
    );

    var measurementsFilter = SpatialDataParamParser.parseMeasurementsFilter(
      Optional.ofNullable(measurementsFilterParam)
    );

    SpatialDataQueryParams spatialDataParams = new SpatialDataQueryParams(
      geometryFilter,
      metadata.orElse(Collections.emptyMap()),
      measurementsFilter.orElse(Collections.emptyList()),
      startTime,
      endTime,
      limit,
      offset,
      skip
    );

    return Response.ok(dataPointService.getSpatialDataPointIOs(containerId, spatialDataParams)).build();
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
    dataPointService.createSpatialDataPoints(containerId, dataPoints);
    return Response.ok().build();
  }
}
