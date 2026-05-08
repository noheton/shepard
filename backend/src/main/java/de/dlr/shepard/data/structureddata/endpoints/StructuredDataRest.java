package de.dlr.shepard.data.structureddata.endpoints;

import de.dlr.shepard.auth.permission.io.PermissionsIO;
import de.dlr.shepard.auth.permission.model.Roles;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.filters.Subscribable;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.data.ContainerAttributes;
import de.dlr.shepard.data.structureddata.entities.StructuredData;
import de.dlr.shepard.data.structureddata.entities.StructuredDataContainer;
import de.dlr.shepard.data.structureddata.entities.StructuredDataPayload;
import de.dlr.shepard.data.structureddata.io.StructuredDataContainerIO;
import de.dlr.shepard.data.structureddata.services.StructuredDataContainerService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.util.ArrayList;
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
@Path(Constants.SHEPARD_API + "/" + Constants.STRUCTURED_DATA_CONTAINERS)
@RequestScoped
public class StructuredDataRest {

  @Inject
  StructuredDataContainerService structuredDataContainerService;

  @Inject
  PermissionsService permissionsService;

  @Inject
  UserService userService;

  @GET
  @Tag(name = Constants.STRUCTURED_DATA_CONTAINER)
  @Operation(description = "Get all structured data containers")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = StructuredDataContainerIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.QP_NAME)
  @Parameter(name = Constants.QP_PAGE)
  @Parameter(name = Constants.QP_SIZE)
  @Parameter(name = Constants.QP_ORDER_BY_ATTRIBUTE)
  @Parameter(name = Constants.QP_ORDER_DESC)
  public Response getAllStructuredDataContainers(
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

    List<StructuredDataContainer> containers = structuredDataContainerService.getAllContainers(params);
    var result = new ArrayList<StructuredDataContainerIO>(containers.size());
    for (var container : containers) {
      result.add(new StructuredDataContainerIO(container));
    }
    return Response.ok(result).build();
  }

  @GET
  @Path("/{" + Constants.STRUCTURED_DATA_CONTAINER_ID + "}")
  @Tag(name = Constants.STRUCTURED_DATA_CONTAINER)
  @Operation(description = "Get structured data container")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = StructuredDataContainerIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.STRUCTURED_DATA_CONTAINER_ID)
  public Response getStructuredDataContainer(
    @PathParam(Constants.STRUCTURED_DATA_CONTAINER_ID) @NotNull @PositiveOrZero Long structuredDataId
  ) {
    var result = structuredDataContainerService.getContainer(structuredDataId);
    return Response.ok(new StructuredDataContainerIO(result)).build();
  }

  @DELETE
  @Path("/{" + Constants.STRUCTURED_DATA_CONTAINER_ID + "}")
  @Subscribable
  @Tag(name = Constants.STRUCTURED_DATA_CONTAINER)
  @Operation(description = "Delete structured data container")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.STRUCTURED_DATA_CONTAINER_ID)
  public Response deleteStructuredDataContainer(
    @PathParam(Constants.STRUCTURED_DATA_CONTAINER_ID) @NotNull @PositiveOrZero Long structuredDataId
  ) {
    structuredDataContainerService.deleteContainer(structuredDataId);
    return Response.status(Status.NO_CONTENT).build();
  }

  @POST
  @Tag(name = Constants.STRUCTURED_DATA_CONTAINER)
  @Operation(description = "Create a new structured data container")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = StructuredDataContainerIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  public Response createStructuredDataContainer(
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = StructuredDataContainerIO.class))
    ) @Valid StructuredDataContainerIO structuredDataContainer
  ) {
    var result = structuredDataContainerService.createContainer(structuredDataContainer);
    return Response.ok(new StructuredDataContainerIO(result)).status(Status.CREATED).build();
  }

  @POST
  @Path("/{" + Constants.STRUCTURED_DATA_CONTAINER_ID + "}/payload")
  @Subscribable
  @Tag(name = Constants.STRUCTURED_DATA_CONTAINER)
  @Operation(description = "Upload a new structured data object")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = StructuredData.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.STRUCTURED_DATA_CONTAINER_ID)
  public Response createStructuredData(
    @PathParam(Constants.STRUCTURED_DATA_CONTAINER_ID) @NotNull @PositiveOrZero Long structuredDataId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = StructuredDataPayload.class))
    ) @Valid StructuredDataPayload payload
  ) {
    var result = structuredDataContainerService.createStructuredData(structuredDataId, payload);
    return Response.status(Status.CREATED).entity(result).build();
  }

  @GET
  @Path("/{" + Constants.STRUCTURED_DATA_CONTAINER_ID + "}/payload")
  @Tag(name = Constants.STRUCTURED_DATA_CONTAINER)
  @Operation(description = "Get structured data objects")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = StructuredData.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.STRUCTURED_DATA_CONTAINER_ID)
  public Response getAllStructuredDatas(
    @PathParam(Constants.STRUCTURED_DATA_CONTAINER_ID) @NotNull @PositiveOrZero Long structuredDataId
  ) {
    var result = structuredDataContainerService.getContainer(structuredDataId).getStructuredDatas();
    return Response.ok(result).build();
  }

  @GET
  @Path("/{" + Constants.STRUCTURED_DATA_CONTAINER_ID + "}/payload/{" + Constants.OID + "}")
  @Tag(name = Constants.STRUCTURED_DATA_CONTAINER)
  @Operation(description = "Download structured data")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = StructuredDataPayload.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.STRUCTURED_DATA_CONTAINER_ID)
  @Parameter(name = Constants.OID)
  public Response getStructuredData(
    @PathParam(Constants.STRUCTURED_DATA_CONTAINER_ID) @NotNull @PositiveOrZero Long structuredDataId,
    @PathParam(Constants.OID) @NotBlank String oid
  ) {
    var result = structuredDataContainerService.getStructuredData(structuredDataId, oid);
    return Response.ok(result).build();
  }

  @DELETE
  @Path("/{" + Constants.STRUCTURED_DATA_CONTAINER_ID + "}/payload/{" + Constants.OID + "}")
  @Subscribable
  @Tag(name = Constants.STRUCTURED_DATA_CONTAINER)
  @Operation(description = "Delete structured data")
  @APIResponse(description = "ok", responseCode = "204")
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.STRUCTURED_DATA_CONTAINER_ID)
  @Parameter(name = Constants.OID)
  public Response deleteStructuredData(
    @PathParam(Constants.STRUCTURED_DATA_CONTAINER_ID) @NotNull @PositiveOrZero Long structuredDataId,
    @PathParam(Constants.OID) @NotBlank String oid
  ) {
    structuredDataContainerService.deleteStructuredData(structuredDataId, oid);
    return Response.status(Status.NO_CONTENT).build();
  }

  @GET
  @Path("/{" + Constants.STRUCTURED_DATA_CONTAINER_ID + "}/" + Constants.PERMISSIONS)
  @Tag(name = Constants.STRUCTURED_DATA_CONTAINER)
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
  @Parameter(name = Constants.STRUCTURED_DATA_CONTAINER_ID)
  public Response getStructuredDataPermissions(
    @PathParam(Constants.STRUCTURED_DATA_CONTAINER_ID) @NotNull @PositiveOrZero Long structuredDataId
  ) {
    var perms = structuredDataContainerService.getContainerPermissions(structuredDataId);
    return Response.ok(new PermissionsIO(perms)).build();
  }

  @PUT
  @Path("/{" + Constants.STRUCTURED_DATA_CONTAINER_ID + "}/" + Constants.PERMISSIONS)
  @Tag(name = Constants.STRUCTURED_DATA_CONTAINER)
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
  @Parameter(name = Constants.STRUCTURED_DATA_CONTAINER_ID)
  public Response editStructuredDataPermissions(
    @PathParam(Constants.STRUCTURED_DATA_CONTAINER_ID) @NotNull @PositiveOrZero Long structuredDataId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = PermissionsIO.class))
    ) @Valid PermissionsIO permissions
  ) {
    var perms = structuredDataContainerService.updateContainerPermissions(permissions, structuredDataId);
    return Response.ok(new PermissionsIO(perms)).build();
  }

  @GET
  @Path("/{" + Constants.STRUCTURED_DATA_CONTAINER_ID + "}/" + Constants.ROLES)
  @Tag(name = Constants.STRUCTURED_DATA_CONTAINER)
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
  @Parameter(name = Constants.STRUCTURED_DATA_CONTAINER_ID)
  public Response getStructuredDataRoles(
    @PathParam(Constants.STRUCTURED_DATA_CONTAINER_ID) @NotNull @PositiveOrZero Long structuredDataId
  ) {
    var roles = structuredDataContainerService.getContainerRoles(structuredDataId);
    return Response.ok(roles).build();
  }
}
