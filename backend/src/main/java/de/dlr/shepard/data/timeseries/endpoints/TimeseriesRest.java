package de.dlr.shepard.data.timeseries.endpoints;

import de.dlr.shepard.auth.permission.io.PermissionsIO;
import de.dlr.shepard.auth.permission.model.Roles;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.filters.Subscribable;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.data.ContainerAttributes;
import de.dlr.shepard.data.timeseries.io.TimeseriesContainerIO;
import de.dlr.shepard.data.timeseries.io.TimeseriesContainerIOMapper;
import de.dlr.shepard.data.timeseries.io.TimeseriesIO;
import de.dlr.shepard.data.timeseries.io.TimeseriesWithDataPoints;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPointsQueryParams;
import de.dlr.shepard.data.timeseries.model.TimeseriesTuple;
import de.dlr.shepard.data.timeseries.model.enums.AggregateFunction;
import de.dlr.shepard.data.timeseries.model.enums.CsvFormat;
import de.dlr.shepard.data.timeseries.model.enums.FillOption;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.data.timeseries.services.TimeseriesCsvService;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.util.Collections;
import java.util.NoSuchElementException;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(Constants.TIMESERIES_CONTAINERS)
@RequestScoped
public class TimeseriesRest {

  @Inject
  TimeseriesService timeseriesService;

  @Inject
  TimeseriesCsvService timeseriesCsvService;

  @Inject
  TimeseriesContainerService timeseriesContainerService;

  @Inject
  PermissionsService permissionsService;

  @Context
  private SecurityContext securityContext;

  @GET
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Get all timeseries containers")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = TimeseriesContainerIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.QP_NAME)
  @Parameter(name = Constants.QP_PAGE)
  @Parameter(name = Constants.QP_SIZE)
  @Parameter(name = Constants.QP_ORDER_BY_ATTRIBUTE)
  @Parameter(name = Constants.QP_ORDER_DESC)
  public Response getAllTimeseriesContainers(
    @QueryParam(Constants.QP_NAME) String name,
    @QueryParam(Constants.QP_PAGE) @PositiveOrZero Integer page,
    @QueryParam(Constants.QP_SIZE) @PositiveOrZero Integer size,
    @QueryParam(Constants.QP_ORDER_BY_ATTRIBUTE) ContainerAttributes orderBy,
    @QueryParam(Constants.QP_ORDER_DESC) Boolean orderDesc
  ) {
    var params = new QueryParamHelper();
    if (name != null) params = params.withName(name);
    if (page != null && size != null) params = params.withPageAndSize(page, size);
    if (orderBy != null) params = params.withOrderByAttribute(orderBy, orderDesc);
    var containers = timeseriesContainerService.getAllContainers(params);
    var result = TimeseriesContainerIOMapper.map(containers);

    return Response.ok(result).build();
  }

  @GET
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}")
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Get timeseries container")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = TimeseriesContainerIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public Response getTimeseriesContainer(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long timeseriesContainerId
  ) {
    var container = timeseriesContainerService.getContainer(timeseriesContainerId);
    return Response.ok(TimeseriesContainerIOMapper.map(container)).build();
  }

  @POST
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Create a new timeseries container")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = TimeseriesContainerIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Transactional
  public Response createTimeseriesContainer(
    @RequestBody(
      content = @Content(schema = @Schema(implementation = TimeseriesContainerIO.class))
    ) @Valid TimeseriesContainerIO timeseriesContainer
  ) {
    var container = timeseriesContainerService.createContainer(timeseriesContainer);
    return Response.ok(TimeseriesContainerIOMapper.map(container)).status(Status.CREATED).build();
  }

  @DELETE
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}")
  @Subscribable
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Delete timeseries container")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public Response deleteTimeseriesContainer(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long timeseriesContainerId
  ) {
    timeseriesContainerService.deleteContainer(timeseriesContainerId);
    return Response.status(Status.NO_CONTENT).build();
  }

  @POST
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.PAYLOAD)
  @Subscribable
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Upload timeseries to container")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = TimeseriesTuple.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public Response createTimeseries(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long containerId,
    @RequestBody(
      content = @Content(schema = @Schema(implementation = TimeseriesWithDataPoints.class))
    ) @Valid TimeseriesWithDataPoints payload
  ) {
    Timeseries timeseries = timeseriesService.saveDataPoints(containerId, payload.getTimeseries(), payload.getPoints());

    return Response.ok(timeseries.getTimeseriesTuple()).status(Status.CREATED).build();
  }

  @Deprecated(forRemoval = true)
  @GET
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.AVAILABLE)
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(
    description = "Get timeseries available. Deprecated, use /timeseriesContainers/{containerId}/timeseries instead."
  )
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = TimeseriesTuple.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public Response getTimeseriesAvailable(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long timeseriesContainerId
  ) {
    try {
      var timeseriesListWithoutId = timeseriesService
        .getTimeseriesAvailable(timeseriesContainerId)
        .map(Timeseries::getTimeseriesTuple)
        .toList();
      return Response.ok(timeseriesListWithoutId).build();
    } catch (InvalidPathException | InvalidAuthException e) {
      return Response.ok(Collections.emptyList()).build();
    }
  }

  @GET
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.TIMESERIES)
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Get all available timeseries for that container.")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = TimeseriesIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  @Parameter(name = Constants.MEASUREMENT)
  @Parameter(name = Constants.DEVICE)
  @Parameter(name = Constants.LOCATION)
  @Parameter(name = Constants.SYMBOLICNAME)
  @Parameter(name = Constants.FIELD)
  public Response getTimeseriesOfContainer(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long timeseriesContainerId,
    @QueryParam(Constants.MEASUREMENT) String measurement,
    @QueryParam(Constants.DEVICE) String device,
    @QueryParam(Constants.LOCATION) String location,
    @QueryParam(Constants.SYMBOLICNAME) String symbolicName,
    @QueryParam(Constants.FIELD) String field
  ) {
    var timeseriesList = timeseriesService
      .getTimeseriesAvailable(timeseriesContainerId)
      .map(TimeseriesIO::new)
      .filter(
        entity ->
          (measurement == null || measurement.isEmpty() || entity.getMeasurement().equals(measurement)) &&
          (device == null || device.isEmpty() || entity.getDevice().equals(device)) &&
          (location == null || location.isEmpty() || entity.getLocation().equals(location)) &&
          (symbolicName == null || symbolicName.isEmpty() || entity.getSymbolicName().equals(symbolicName)) &&
          (field == null || field.isEmpty() || entity.getField().equals(field))
      )
      .toList();
    return Response.ok(timeseriesList).build();
  }

  @GET
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.TIMESERIES + "/{" + Constants.TIMESERIES_ID + "}")
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Get the 5-tuple describing a timeseries by its id.")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = TimeseriesIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public Response getTimeseriesById(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long containerId,
    @PathParam(Constants.TIMESERIES_ID) @NotNull @PositiveOrZero Long timeseriesId
  ) {
    var timeseries = timeseriesService.getTimeseriesById(timeseriesId);
    return Response.ok(new TimeseriesIO(timeseries)).build();
  }

  @GET
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.PAYLOAD)
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Get timeseries payload")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = TimeseriesWithDataPoints.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  @Parameter(name = Constants.MEASUREMENT, required = true)
  @Parameter(name = Constants.LOCATION, required = true)
  @Parameter(name = Constants.DEVICE, required = true)
  @Parameter(name = Constants.SYMBOLICNAME, required = true)
  @Parameter(name = Constants.FIELD, required = true)
  @Parameter(name = Constants.START, required = true)
  @Parameter(name = Constants.END, required = true)
  @Parameter(name = Constants.FUNCTION)
  @Parameter(name = Constants.GROUP_BY)
  @Parameter(name = Constants.FILLOPTION)
  public Response getTimeseries(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long timeseriesContainerId,
    @QueryParam(Constants.MEASUREMENT) @NotBlank String measurement,
    @QueryParam(Constants.LOCATION) @NotBlank String location,
    @QueryParam(Constants.DEVICE) @NotBlank String device,
    @QueryParam(Constants.SYMBOLICNAME) @NotBlank String symbolicName,
    @QueryParam(Constants.FIELD) @NotBlank String field,
    @QueryParam(Constants.START) @NotNull @PositiveOrZero Long start,
    @QueryParam(Constants.END) @NotNull @PositiveOrZero Long end,
    @QueryParam(Constants.FUNCTION) AggregateFunction function,
    @QueryParam(Constants.GROUP_BY) Long groupBy,
    @QueryParam(Constants.FILLOPTION) FillOption fillOption
  ) throws Exception {
    var timeseries = new TimeseriesTuple(measurement, device, location, symbolicName, field);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
      start,
      end,
      groupBy,
      fillOption,
      function
    );
    var timeseriesData = timeseriesService.getDataPointsByTimeseries(timeseriesContainerId, timeseries, queryParams);
    TimeseriesWithDataPoints timeseriesWithData = new TimeseriesWithDataPoints(timeseries, timeseriesData);
    return Response.ok(timeseriesWithData).build();
  }

  @GET
  @Produces({ MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_JSON })
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.EXPORT)
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Export timeseries payload")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(
      mediaType = MediaType.APPLICATION_OCTET_STREAM,
      schema = @Schema(type = SchemaType.STRING, format = "binary")
    )
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  @Parameter(name = Constants.MEASUREMENT, required = true)
  @Parameter(name = Constants.LOCATION, required = true)
  @Parameter(name = Constants.DEVICE, required = true)
  @Parameter(name = Constants.SYMBOLICNAME, required = true)
  @Parameter(name = Constants.FIELD, required = true)
  @Parameter(name = Constants.START, required = true)
  @Parameter(name = Constants.END, required = true)
  @Parameter(name = Constants.FUNCTION)
  @Parameter(name = Constants.GROUP_BY)
  @Parameter(name = Constants.FILLOPTION)
  @Parameter(name = Constants.CSVFORMAT)
  public Response exportTimeseries(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long timeseriesContainerId,
    @QueryParam(Constants.MEASUREMENT) @NotBlank String measurement,
    @QueryParam(Constants.LOCATION) @NotBlank String location,
    @QueryParam(Constants.DEVICE) @NotBlank String device,
    @QueryParam(Constants.SYMBOLICNAME) @NotBlank String symbolicName,
    @QueryParam(Constants.FIELD) @NotBlank String field,
    @QueryParam(Constants.START) @NotNull @PositiveOrZero Long start,
    @QueryParam(Constants.END) @NotNull @PositiveOrZero Long end,
    @QueryParam(Constants.FUNCTION) AggregateFunction function,
    @QueryParam(Constants.GROUP_BY) Long groupBy,
    @QueryParam(Constants.FILLOPTION) FillOption fillOption,
    @QueryParam(Constants.CSVFORMAT) @DefaultValue(value = "ROW") CsvFormat csvFormat
  ) throws IOException {
    var timeseries = new TimeseriesTuple(measurement, device, location, symbolicName, field);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
      start,
      end,
      groupBy,
      fillOption,
      function
    );
    var inputStream = timeseriesCsvService.exportTimeseriesDataToCsv(
      timeseriesContainerId,
      timeseries,
      queryParams,
      csvFormat
    );

    return Response.ok(inputStream, MediaType.APPLICATION_OCTET_STREAM)
      .header("Content-Disposition", "attachment; filename=\"timeseries-export.csv\"")
      .build();
  }

  @POST
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.IMPORT)
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Import timeseries payload")
  @APIResponse(description = "ok", responseCode = "200")
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Subscribable
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public Response importTimeseries(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long timeseriesContainerId,
    MultipartBodyFileUpload body
  ) throws IOException {
    String filePath = body.fileUpload != null ? body.fileUpload.uploadedFile().toString() : null;

    if (filePath == null) {
      throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
    }

    timeseriesCsvService.importTimeseriesFromCsv(timeseriesContainerId, filePath);
    return Response.ok().build();
  }

  @GET
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.PERMISSIONS)
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Get permissions")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = PermissionsIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public PermissionsIO getTimeseriesPermissions(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long timeseriesContainerId
  ) {
    var permissions = permissionsService.getPermissionsOfEntity(timeseriesContainerId);
    return new PermissionsIO(permissions);
  }

  @PUT
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.PERMISSIONS)
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Edit permissions")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = PermissionsIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public PermissionsIO editTimeseriesPermissions(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long timeseriesContainerId,
    @RequestBody(
      content = @Content(schema = @Schema(implementation = PermissionsIO.class))
    ) @Valid PermissionsIO permissions
  ) {
    var updatedPermissions = permissionsService.updatePermissionsByNeo4jId(permissions, timeseriesContainerId);
    if (updatedPermissions == null) throw new NotFoundException();
    return new PermissionsIO(updatedPermissions);
  }

  @GET
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.ROLES)
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Get roles")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = Roles.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public Roles getTimeseriesRoles(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero Long timeseriesContainerId
  ) {
    var roles = permissionsService.getUserRolesOnEntity(
      timeseriesContainerId,
      securityContext.getUserPrincipal().getName()
    );
    if (roles == null) throw new NotFoundException();
    return roles;
  }

  @Schema(type = SchemaType.STRING, format = "binary", description = "Timeseries as CSV")
  public interface UploadItemSchema {}

  public static class UploadFormSchema {

    @Schema(required = true)
    public UploadItemSchema file;
  }

  @Schema(implementation = UploadFormSchema.class)
  public static class MultipartBodyFileUpload {

    @RestForm(Constants.FILE)
    public FileUpload fileUpload;
  }
}
