package de.dlr.shepard.timeseries.endpoints;

import com.arjuna.ats.jta.exceptions.NotImplementedException;
import de.dlr.shepard.exceptions.InvalidRequestException;
import de.dlr.shepard.filters.Subscribable;
import de.dlr.shepard.influxDB.FillOption;
import de.dlr.shepard.influxDB.SingleValuedUnaryFunction;
import de.dlr.shepard.neo4Core.io.PermissionsIO;
import de.dlr.shepard.neo4Core.io.RolesIO;
import de.dlr.shepard.neo4Core.io.TimeseriesContainerIO;
import de.dlr.shepard.neo4Core.orderBy.ContainerAttributes;
import de.dlr.shepard.neo4Core.services.PermissionsService;
import de.dlr.shepard.security.PermissionsUtil;
import de.dlr.shepard.timeseries.io.ExperimentalTimeseriesPayloadIO;
import de.dlr.shepard.timeseries.io.TimeseriesContainerIOMapper;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseries;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseriesEntity;
import de.dlr.shepard.timeseries.services.ExperimentalTimeseriesContainerService;
import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.QueryParamHelper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
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
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
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
@Path("experimental-" + Constants.TIMESERIES_CONTAINERS)
@RequestScoped
public class ExperimentalTimeseriesRest {

  private ExperimentalTimeseriesContainerService timeseriesContainerService;
  private PermissionsService permissionsService;
  private PermissionsUtil permissionsUtil;

  @Context
  private SecurityContext securityContext;

  ExperimentalTimeseriesRest() {}

  @Inject
  public ExperimentalTimeseriesRest(
    ExperimentalTimeseriesContainerService timeseriesContainerService,
    SecurityContext securityContext,
    PermissionsService permissionsService,
    PermissionsUtil permissionsUtil
  ) {
    this.timeseriesContainerService = timeseriesContainerService;
    this.securityContext = securityContext;
    this.permissionsService = permissionsService;
    this.permissionsUtil = permissionsUtil;
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
  public List<TimeseriesContainerIO> getAllTimeseriesesContainers(
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
    var containers = timeseriesContainerService.getAllContainers(params, securityContext.getUserPrincipal().getName());
    var result = TimeseriesContainerIOMapper.map(containers);

    return result;
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
  public TimeseriesContainerIO getTimeseriesContainer(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) long timeseriesContainerId
  ) {
    var container = timeseriesContainerService.getContainer(timeseriesContainerId);
    return TimeseriesContainerIOMapper.map(container);
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

    // Todo: Created should return url to get the newly created object --> breaking change
    //return Response.created(URI.create(String.format("/experimental-timeseriesContainers/%s", container.getId()))).build();
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
    var result = timeseriesContainerService.deleteContainer(
      timeseriesContainerId,
      securityContext.getUserPrincipal().getName()
    );

    if (result == false) throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);

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
    content = @Content(schema = @Schema(implementation = ExperimentalTimeseriesPayloadIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public Response createTimeseries(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) long containerId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = ExperimentalTimeseriesPayloadIO.class))
    ) @Valid ExperimentalTimeseriesPayloadIO payload
  ) {
    try {
      var timeseries = timeseriesContainerService.addPayload(containerId, payload.getTimeseries(), payload.getPoints());

      // Todo: created should return url of newly created resource
      return Response.ok(timeseries).build();
    } catch (Exception ex) {
      Log.error(ex);
      throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
    }
  }

  @GET
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.AVAILABLE)
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Get timeseries available")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = ExperimentalTimeseriesEntity.class))
  )
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public List<ExperimentalTimeseriesEntity> getTimeseriesAvailable(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) long timeseriesContainerId
  ) {
    var timeserieses = timeseriesContainerService.getTimeseriesAvailable(timeseriesContainerId);
    return timeserieses;
  }

  @GET
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.PAYLOAD)
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Get timeseries payload")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = ExperimentalTimeseriesPayloadIO.class))
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
  public ExperimentalTimeseriesPayloadIO getTimeseries(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) long timeseriesContainerId,
    @QueryParam(Constants.MEASUREMENT) String measurement,
    @QueryParam(Constants.LOCATION) String location,
    @QueryParam(Constants.DEVICE) String device,
    @QueryParam(Constants.SYMBOLICNAME) String symbolicName,
    @QueryParam(Constants.FIELD) String field,
    @QueryParam(Constants.START) long start,
    @QueryParam(Constants.END) long end,
    @QueryParam(Constants.FUNCTION) SingleValuedUnaryFunction function,
    @QueryParam(Constants.GROUP_BY) Long groupBy,
    @QueryParam(Constants.FILLOPTION) FillOption fillOption // Todo: how to replace filloption?
  ) throws Exception {
    if (measurement == null || location == null || device == null || symbolicName == null || field == null) {
      throw new InvalidRequestException("Some query params are missing");
    }

    throw new NotImplementedException("not implemented");
    // var timeseriesInputParams = new ExperimentalTimeseries(measurement, device, location, symbolicName, field);

    // var payload = timeseriesContainerService.getDataPoints(
    //   timeseriesContainerId,
    //   timeseriesInputParams,
    //   start,
    //   end,
    //   //function,
    //   groupBy
    //   //fillOption
    // );

    // if (payload == null) throw new NotFoundException();

    // var dataPointsIo = payload
    //   .stream()
    //   .map(point -> {
    //     // Todo: get correct value
    //     var io = new ExperimentalTimeseriesPayloadDataPointIO(
    //       LocalDateTimeHelper.fromLocalDateTime(point.getTime()),
    //       point.getDoubleValue()
    //     );
    //     return io;
    //   })
    //   .toList();

    // return new ExperimentalTimeseriesPayloadIO(timeseriesInputParams, dataPointsIo);
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
    @QueryParam(Constants.FUNCTION) SingleValuedUnaryFunction function,
    @QueryParam(Constants.GROUP_BY) Long groupBy,
    @QueryParam(Constants.FILLOPTION) FillOption fillOption
  ) throws IOException {
    if (measurement == null || location == null || device == null || symbolicName == null || field == null) {
      throw new InvalidRequestException("Some query params are missing");
    }

    var timeseries = new ExperimentalTimeseries(measurement, device, location, symbolicName, field);
    var inputStream = timeseriesContainerService.exportTimeseriesPayload(
      timeseriesContainerId,
      timeseries,
      start,
      end,
      function,
      groupBy,
      fillOption
    );

    if (inputStream == null) throw new NotFoundException();

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
  public void importTimeseries(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) long timeseriesContainerId,
    MultipartBodyFileUpload body
  ) throws IOException {
    String filePath = body.fileUpload != null ? body.fileUpload.uploadedFile().toString() : null;

    if (filePath == null) {
      throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
    }

    File file = new File(filePath);
    try (InputStream fileInputStream = new FileInputStream(file)) {
      var result = timeseriesContainerService.importTimeseries(timeseriesContainerId, fileInputStream);

      if (result == false) throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
    }
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
    var permissions = permissionsService.getPermissionsByNeo4jId(timeseriesContainerId);
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
    content = @Content(schema = @Schema(implementation = RolesIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public RolesIO getTimeseriesRoles(@PathParam(Constants.TIMESERIES_CONTAINER_ID) long timeseriesContainerId) {
    var roles = permissionsUtil.getRolesByNeo4jId(timeseriesContainerId, securityContext.getUserPrincipal().getName());
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
