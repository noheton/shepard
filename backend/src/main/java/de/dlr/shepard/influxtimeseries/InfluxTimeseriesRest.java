package de.dlr.shepard.influxtimeseries;

import de.dlr.shepard.exceptions.InvalidRequestException;
import de.dlr.shepard.filters.Subscribable;
import de.dlr.shepard.neo4Core.io.PermissionsIO;
import de.dlr.shepard.neo4Core.io.RolesIO;
import de.dlr.shepard.neo4Core.orderBy.ContainerAttributes;
import de.dlr.shepard.neo4Core.services.PermissionsService;
import de.dlr.shepard.security.PermissionsUtil;
import de.dlr.shepard.timeseries.io.TimeseriesContainerIO;
import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.QueryParamHelper;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
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
public class InfluxTimeseriesRest {

  private InfluxTimeseriesContainerService timeseriesContainerService;
  private PermissionsService permissionsService;

  private PermissionsUtil permissionsUtil;

  @Context
  private SecurityContext securityContext;

  InfluxTimeseriesRest() {}

  @Inject
  public InfluxTimeseriesRest(
    InfluxTimeseriesContainerService timeseriesContainerService,
    PermissionsService permissionsService,
    PermissionsUtil permissionsUtil
  ) {
    this.timeseriesContainerService = timeseriesContainerService;
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
    var containers = timeseriesContainerService.getAllContainers(params, securityContext.getUserPrincipal().getName());
    var result = new ArrayList<TimeseriesContainerIO>(containers.size());
    for (var container : containers) {
      result.add(new TimeseriesContainerIO(container));
    }
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
    var result = timeseriesContainerService.getContainer(timeseriesContainerId);
    return Response.ok(new TimeseriesContainerIO(result)).build();
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
    var result = timeseriesContainerService.createContainer(
      timeseriesContainer,
      securityContext.getUserPrincipal().getName()
    );

    return Response.ok(new TimeseriesContainerIO(result)).status(Status.CREATED).build();
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

    return result ? Response.status(Status.NO_CONTENT).build() : Response.status(Status.INTERNAL_SERVER_ERROR).build();
  }

  @POST
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.PAYLOAD)
  @Subscribable
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Upload timeseries to container")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = InfluxTimeseries.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public Response createTimeseries(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) long timeseriesId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = InfluxTimeseriesPayload.class))
    ) @Valid InfluxTimeseriesPayload payload
  ) {
    var result = timeseriesContainerService.createTimeseries(timeseriesId, payload);
    return result != null
      ? Response.status(Status.CREATED).entity(result).build()
      : Response.status(Status.INTERNAL_SERVER_ERROR).build();
  }

  @GET
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.AVAILABLE)
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Get timeseries available")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = InfluxTimeseries.class))
  )
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public Response getTimeseriesAvailable(@PathParam(Constants.TIMESERIES_CONTAINER_ID) long timeseriesContainerId) {
    return Response.ok(timeseriesContainerService.getTimeseriesAvailable(timeseriesContainerId)).build();
  }

  @GET
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.PAYLOAD)
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Get timeseries payload")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = InfluxTimeseriesPayload.class))
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
    @QueryParam(Constants.FUNCTION) InfluxSingleValuedUnaryFunction function,
    @QueryParam(Constants.GROUP_BY) Long groupBy,
    @QueryParam(Constants.FILLOPTION) InfluxFillOption fillOption
  ) {
    if (measurement == null || location == null || device == null || symbolicName == null || field == null) {
      throw new InvalidRequestException("Some query params are missing");
    }

    var timeseries = new InfluxTimeseries(measurement, device, location, symbolicName, field);
    var result = timeseriesContainerService.getTimeseriesPayload(
      timeseriesContainerId,
      timeseries,
      start,
      end,
      function,
      groupBy,
      fillOption
    );

    return result != null ? Response.ok(result).build() : Response.status(Status.NOT_FOUND).build();
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
    @QueryParam(Constants.FUNCTION) InfluxSingleValuedUnaryFunction function,
    @QueryParam(Constants.GROUP_BY) Long groupBy,
    @QueryParam(Constants.FILLOPTION) InfluxFillOption fillOption
  ) throws IOException {
    if (measurement == null || location == null || device == null || symbolicName == null || field == null) {
      throw new InvalidRequestException("Some query params are missing");
    }

    var timeseries = new InfluxTimeseries(measurement, device, location, symbolicName, field);
    var result = timeseriesContainerService.exportTimeseriesPayload(
      timeseriesContainerId,
      timeseries,
      start,
      end,
      function,
      groupBy,
      fillOption
    );
    return result != null
      ? Response.ok(result, MediaType.APPLICATION_OCTET_STREAM)
        .header("Content-Disposition", "attachment; filename=\"timeseries-export.csv\"")
        .build()
      : Response.status(Status.NOT_FOUND).build();
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
      return Response.status(Status.INTERNAL_SERVER_ERROR).build();
    }

    File file = new File(filePath);
    try (InputStream fileInputStream = new FileInputStream(file)) {
      var result = timeseriesContainerService.importTimeseries(timeseriesContainerId, fileInputStream);

      return result ? Response.ok().build() : Response.status(Status.INTERNAL_SERVER_ERROR).build();
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
  public Response getTimeseriesPermissions(@PathParam(Constants.TIMESERIES_CONTAINER_ID) long timeseriesContainerId) {
    var perms = permissionsService.getPermissionsByNeo4jId(timeseriesContainerId);
    return perms != null ? Response.ok(new PermissionsIO(perms)).build() : Response.status(Status.NOT_FOUND).build();
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
  public Response editTimeseriesPermissions(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) long timeseriesContainerId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = PermissionsIO.class))
    ) @Valid PermissionsIO permissions
  ) {
    var perms = permissionsService.updatePermissionsByNeo4jId(permissions, timeseriesContainerId);
    return perms != null ? Response.ok(new PermissionsIO(perms)).build() : Response.status(Status.NOT_FOUND).build();
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
  public Response getTimeseriesRoles(@PathParam(Constants.TIMESERIES_CONTAINER_ID) long timeseriesContainerId) {
    var roles = permissionsUtil.getRolesByNeo4jId(timeseriesContainerId, securityContext.getUserPrincipal().getName());
    return roles != null ? Response.ok(roles).build() : Response.status(Status.NOT_FOUND).build();
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
