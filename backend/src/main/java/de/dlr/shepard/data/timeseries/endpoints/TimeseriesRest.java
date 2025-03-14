package de.dlr.shepard.data.timeseries.endpoints;

import de.dlr.shepard.auth.permission.io.PermissionsIO;
import de.dlr.shepard.auth.permission.model.Roles;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.exceptions.InvalidRequestException;
import de.dlr.shepard.common.filters.Subscribable;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.data.ContainerAttributes;
import de.dlr.shepard.data.timeseries.io.TimeseriesContainerIO;
import de.dlr.shepard.data.timeseries.io.TimeseriesContainerIOMapper;
import de.dlr.shepard.data.timeseries.io.TimeseriesIO;
import de.dlr.shepard.data.timeseries.io.TimeseriesWithDataPoints;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPointsQueryParams;
import de.dlr.shepard.data.timeseries.model.TimeseriesEntity;
import de.dlr.shepard.data.timeseries.model.enums.AggregateFunction;
import de.dlr.shepard.data.timeseries.model.enums.FillOption;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.data.timeseries.services.TimeseriesCsvService;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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

  private TimeseriesService timeseriesService;
  private TimeseriesCsvService timeseriesCsvService;
  private TimeseriesContainerService timeseriesContainerService;
  private PermissionsService permissionsService;

  @Context
  private SecurityContext securityContext;

  TimeseriesRest() {}

  @Inject
  public TimeseriesRest(
    TimeseriesService timeseriesService,
    TimeseriesCsvService timeseriesCsvService,
    TimeseriesContainerService timeseriesContainerService,
    SecurityContext securityContext,
    PermissionsService permissionsService
  ) {
    this.timeseriesService = timeseriesService;
    this.timeseriesCsvService = timeseriesCsvService;
    this.timeseriesContainerService = timeseriesContainerService;
    this.securityContext = securityContext;
    this.permissionsService = permissionsService;
  }

  @GET
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Get all timeseries containers")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = TimeseriesContainerIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.QP_NAME)
  @Parameter(name = Constants.QP_PAGE)
  @Parameter(name = Constants.QP_SIZE)
  @Parameter(name = Constants.QP_ORDER_BY_ATTRIBUTE)
  @Parameter(name = Constants.QP_ORDER_DESC)
  public Response getAllTimeseriesContainers(
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
    var containers = timeseriesContainerService.getContainers(params, securityContext.getUserPrincipal().getName());
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
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public Response getTimeseriesContainer(@PathParam(Constants.TIMESERIES_CONTAINER_ID) long timeseriesContainerId) {
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
  @APIResponse(description = "not found", responseCode = "404")
  @Transactional
  public Response createTimeseriesContainer(
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = TimeseriesContainerIO.class))
    ) @Valid TimeseriesContainerIO timeseriesContainer
  ) {
    var container = timeseriesContainerService.createContainer(
      timeseriesContainer.getName(),
      securityContext.getUserPrincipal().getName()
    );

    return Response.ok(TimeseriesContainerIOMapper.map(container)).status(Status.CREATED).build();
  }

  @DELETE
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}")
  @Subscribable
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Delete timeseries container")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public Response deleteTimeseriesContainer(@PathParam(Constants.TIMESERIES_CONTAINER_ID) long timeseriesContainerId) {
    timeseriesContainerService.deleteContainer(timeseriesContainerId, securityContext.getUserPrincipal().getName());

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
    content = @Content(schema = @Schema(implementation = Timeseries.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @APIResponse(description = "bad request", responseCode = "400")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public Response createTimeseries(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) long containerId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = TimeseriesWithDataPoints.class))
    ) @Valid TimeseriesWithDataPoints payload
  ) {
    Optional<TimeseriesContainer> containerOptional = this.timeseriesContainerService.getContainerOptional(containerId);

    if (containerOptional.isEmpty()) {
      throw new InvalidBodyException("Timeseries container with id %s is null or deleted.", containerId);
    }

    TimeseriesEntity timeseriesEntity = timeseriesService.saveDataPoints(
      containerOptional.get().getId(),
      payload.getTimeseries(),
      payload.getPoints()
    );

    return Response.ok(new Timeseries(timeseriesEntity)).status(Status.CREATED).build();
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
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = Timeseries.class))
  )
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public Response getTimeseriesAvailable(@PathParam(Constants.TIMESERIES_CONTAINER_ID) long timeseriesContainerId) {
    Optional<TimeseriesContainer> containerOptional =
      this.timeseriesContainerService.getContainerOptional(timeseriesContainerId);

    if (containerOptional.isEmpty()) {
      return Response.ok(Collections.emptyList()).build();
    }

    List<TimeseriesEntity> timeseriesEntityList = timeseriesService.getTimeseriesAvailable(timeseriesContainerId);

    List<Timeseries> timeseriesListWithoutId = timeseriesEntityList
      .stream()
      .map(entity -> new Timeseries(entity))
      .toList();

    return Response.ok(timeseriesListWithoutId).build();
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
  @APIResponse(responseCode = "404", description = "Not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public Response getTimeseriesOfContainer(@PathParam(Constants.TIMESERIES_CONTAINER_ID) long timeseriesContainerId) {
    Optional<TimeseriesContainer> containerOptional =
      this.timeseriesContainerService.getContainerOptional(timeseriesContainerId);

    if (containerOptional.isEmpty()) {
      return Response.status(404, "Timeseries container not found").build();
    }

    var timeseriesEntityList = timeseriesService.getTimeseriesAvailable(timeseriesContainerId);
    var timeseriesList = timeseriesEntityList.stream().map(entity -> new TimeseriesIO(entity)).toList();

    return Response.ok(timeseriesList).build();
  }

  @GET
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.TIMESERIES + "/{" + Constants.TIMESERIES_ID + "}")
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Get timeseries by id.")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = TimeseriesIO.class))
  )
  @APIResponse(responseCode = "404", description = "Not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public Response getTimeseriesById(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) long timeseriesContainerId,
    @PathParam(Constants.TIMESERIES_ID) int timeseriesId
  ) {
    Optional<TimeseriesContainer> containerOptional =
      this.timeseriesContainerService.getContainerOptional(timeseriesContainerId);

    if (containerOptional.isEmpty()) {
      return Response.status(404, "Timeseries container not found").build();
    }

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
  @APIResponse(description = "not found", responseCode = "404")
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
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) long timeseriesContainerId,
    @QueryParam(Constants.MEASUREMENT) String measurement,
    @QueryParam(Constants.LOCATION) String location,
    @QueryParam(Constants.DEVICE) String device,
    @QueryParam(Constants.SYMBOLICNAME) String symbolicName,
    @QueryParam(Constants.FIELD) String field,
    @QueryParam(Constants.START) long start,
    @QueryParam(Constants.END) long end,
    @QueryParam(Constants.FUNCTION) AggregateFunction function,
    @QueryParam(Constants.GROUP_BY) Long groupBy,
    @QueryParam(Constants.FILLOPTION) FillOption fillOption
  ) throws Exception {
    if (measurement == null || location == null || device == null || symbolicName == null || field == null) {
      throw new InvalidRequestException(
        "Some query params are missing. Make sure that 'measurement', 'location', 'device', 'symbolicName' and 'field' are set."
      );
    }
    var timeseries = new Timeseries(measurement, device, location, symbolicName, field);

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
  @APIResponse(description = "not found", responseCode = "404")
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
  public Response exportTimeseries(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) long timeseriesContainerId,
    @QueryParam(Constants.MEASUREMENT) String measurement,
    @QueryParam(Constants.LOCATION) String location,
    @QueryParam(Constants.DEVICE) String device,
    @QueryParam(Constants.SYMBOLICNAME) String symbolicName,
    @QueryParam(Constants.FIELD) String field,
    @QueryParam(Constants.START) long start,
    @QueryParam(Constants.END) long end,
    @QueryParam(Constants.FUNCTION) AggregateFunction function,
    @QueryParam(Constants.GROUP_BY) Long groupBy,
    @QueryParam(Constants.FILLOPTION) FillOption fillOption
  ) throws IOException {
    if (measurement == null || location == null || device == null || symbolicName == null || field == null) {
      throw new InvalidRequestException("Some query params are missing");
    }

    Optional<TimeseriesContainer> containerOptional =
      this.timeseriesContainerService.getContainerOptional(timeseriesContainerId);

    if (containerOptional.isEmpty()) {
      throw new InvalidBodyException("Timeseries container with id %s is null or deleted.", timeseriesContainerId);
    }

    var timeseries = new Timeseries(measurement, device, location, symbolicName, field);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
      start,
      end,
      groupBy,
      fillOption,
      function
    );
    var inputStream = timeseriesCsvService.exportTimeseriesDataToCsv(
      containerOptional.get().getId(),
      timeseries,
      queryParams
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
  @APIResponse(description = "not found", responseCode = "404")
  @Subscribable
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public Response importTimeseries(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) long timeseriesContainerId,
    MultipartBodyFileUpload body
  ) throws IOException {
    String filePath = body.fileUpload != null ? body.fileUpload.uploadedFile().toString() : null;

    if (filePath == null) {
      throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
    }

    Optional<TimeseriesContainer> containerOptional =
      this.timeseriesContainerService.getContainerOptional(timeseriesContainerId);

    if (containerOptional.isEmpty()) {
      throw new InvalidBodyException("Timeseries container with id %s is null or deleted.", timeseriesContainerId);
    }

    timeseriesCsvService.importTimeseriesFromCsv(containerOptional.get(), filePath);
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
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public PermissionsIO getTimeseriesPermissions(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) long timeseriesContainerId
  ) {
    var permissions = permissionsService.getPermissionsOfEntity(timeseriesContainerId);
    if (permissions == null) throw new NotFoundException();
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
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public PermissionsIO editTimeseriesPermissions(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) long timeseriesContainerId,
    @RequestBody(
      required = true,
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
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public Roles getTimeseriesRoles(@PathParam(Constants.TIMESERIES_CONTAINER_ID) long timeseriesContainerId) {
    var roles = permissionsService.getUserRolesOnEntity(
      timeseriesContainerId,
      securityContext.getUserPrincipal().getName()
    );
    if (roles == null) throw new NotFoundException();
    return roles;
  }

  @Schema(type = SchemaType.STRING, format = "binary", description = "Timeseries as CSV")
  public interface UploadItemSchema {}

  public class UploadFormSchema {

    @Schema(required = true)
    public UploadItemSchema file;
  }

  @Schema(implementation = UploadFormSchema.class)
  public static class MultipartBodyFileUpload {

    @RestForm(Constants.FILE)
    public FileUpload fileUpload;
  }
}
