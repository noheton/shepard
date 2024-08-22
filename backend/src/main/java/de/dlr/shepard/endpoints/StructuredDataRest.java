package de.dlr.shepard.endpoints;

import de.dlr.shepard.filters.Subscribable;
import de.dlr.shepard.mongoDB.StructuredData;
import de.dlr.shepard.mongoDB.StructuredDataPayload;
import de.dlr.shepard.neo4Core.io.PermissionsIO;
import de.dlr.shepard.neo4Core.io.RolesIO;
import de.dlr.shepard.neo4Core.io.StructuredDataContainerIO;
import de.dlr.shepard.neo4Core.orderBy.ContainerAttributes;
import de.dlr.shepard.neo4Core.services.PermissionsService;
import de.dlr.shepard.neo4Core.services.StructuredDataContainerService;
import de.dlr.shepard.security.PermissionsUtil;
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
import java.util.ArrayList;
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
@Path(Constants.STRUCTUREDDATAS)
@RequestScoped
public class StructuredDataRest {

  private StructuredDataContainerService structuredDataContainerService;
  private PermissionsService permissionsService;

  private PermissionsUtil permissionsUtil;

  @Context
  private SecurityContext securityContext;

  StructuredDataRest() {}

  @Inject
  public StructuredDataRest(
    StructuredDataContainerService structuredDataContainerService,
    PermissionsService permissionsService,
    PermissionsUtil permissionsUtil
  ) {
    this.structuredDataContainerService = structuredDataContainerService;
    this.permissionsService = permissionsService;
    this.permissionsUtil = permissionsUtil;
  }

  @GET
  @Tag(name = Constants.STRUCTUREDDATA)
  @Operation(description = "Get all structured data containers")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = StructuredDataContainerIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.QP_NAME)
  @Parameter(name = Constants.QP_PAGE)
  @Parameter(name = Constants.QP_SIZE)
  @Parameter(name = Constants.QP_ORDER_BY_ATTRIBUTE)
  @Parameter(name = Constants.QP_ORDER_DESC)
  public Response getAllStructuredDataContainers(
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
    var containers = structuredDataContainerService.getAllContainers(
      params,
      securityContext.getUserPrincipal().getName()
    );
    var result = new ArrayList<StructuredDataContainerIO>(containers.size());
    for (var container : containers) {
      result.add(new StructuredDataContainerIO(container));
    }
    return Response.ok(result).build();
  }

  @GET
  @Path("/{" + Constants.STRUCTUREDDATA_CONTAINER_ID + "}")
  @Tag(name = Constants.STRUCTUREDDATA)
  @Operation(description = "Get structured data container")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = StructuredDataContainerIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.STRUCTUREDDATA_CONTAINER_ID)
  public Response getStructuredDataContainer(@PathParam(Constants.STRUCTUREDDATA_CONTAINER_ID) long structuredDataId) {
    var result = structuredDataContainerService.getContainer(structuredDataId);
    return Response.ok(new StructuredDataContainerIO(result)).build();
  }

  @DELETE
  @Path("/{" + Constants.STRUCTUREDDATA_CONTAINER_ID + "}")
  @Subscribable
  @Tag(name = Constants.STRUCTUREDDATA)
  @Operation(description = "Delete structured data container")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.STRUCTUREDDATA_CONTAINER_ID)
  public Response deleteStructuredDataContainer(
    @PathParam(Constants.STRUCTUREDDATA_CONTAINER_ID) long structuredDataId
  ) {
    var result = structuredDataContainerService.deleteContainer(
      structuredDataId,
      securityContext.getUserPrincipal().getName()
    );
    return result ? Response.status(Status.NO_CONTENT).build() : Response.status(Status.INTERNAL_SERVER_ERROR).build();
  }

  @POST
  @Tag(name = Constants.STRUCTUREDDATA)
  @Operation(description = "Create a new structured data container")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = StructuredDataContainerIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  public Response createStructuredDataContainer(
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = StructuredDataContainerIO.class))
    ) @Valid StructuredDataContainerIO structuredDataContainer
  ) {
    var result = structuredDataContainerService.createContainer(
      structuredDataContainer,
      securityContext.getUserPrincipal().getName()
    );
    return Response.ok(new StructuredDataContainerIO(result)).status(Status.CREATED).build();
  }

  @POST
  @Path("/{" + Constants.STRUCTUREDDATA_CONTAINER_ID + "}/payload")
  @Subscribable
  @Tag(name = Constants.STRUCTUREDDATA)
  @Operation(description = "Upload a new structured data object")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = StructuredData.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.STRUCTUREDDATA_CONTAINER_ID)
  public Response createStructuredData(
    @PathParam(Constants.STRUCTUREDDATA_CONTAINER_ID) long structuredDataId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = StructuredDataPayload.class))
    ) @Valid StructuredDataPayload payload
  ) {
    var result = structuredDataContainerService.createStructuredData(structuredDataId, payload);
    return result != null
      ? Response.status(Status.CREATED).entity(result).build()
      : Response.status(Status.INTERNAL_SERVER_ERROR).build();
  }

  @GET
  @Path("/{" + Constants.STRUCTUREDDATA_CONTAINER_ID + "}/payload")
  @Tag(name = Constants.STRUCTUREDDATA)
  @Operation(description = "Get structured data objects")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = StructuredData.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.STRUCTUREDDATA_CONTAINER_ID)
  public Response getAllStructuredDatas(@PathParam(Constants.STRUCTUREDDATA_CONTAINER_ID) long structuredDataId) {
    var result = structuredDataContainerService.getContainer(structuredDataId).getStructuredDatas();
    return Response.ok(result).build();
  }

  @GET
  @Path("/{" + Constants.STRUCTUREDDATA_CONTAINER_ID + "}/payload/{" + Constants.OID + "}")
  @Tag(name = Constants.STRUCTUREDDATA)
  @Operation(description = "Download structured data")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = StructuredDataPayload.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.STRUCTUREDDATA_CONTAINER_ID)
  @Parameter(name = Constants.OID)
  public Response getStructuredData(
    @PathParam(Constants.STRUCTUREDDATA_CONTAINER_ID) long structuredDataId,
    @PathParam(Constants.OID) String oid
  ) {
    var result = structuredDataContainerService.getStructuredData(structuredDataId, oid);
    return result != null ? Response.ok(result).build() : Response.status(Status.NOT_FOUND).build();
  }

  @DELETE
  @Path("/{" + Constants.STRUCTUREDDATA_CONTAINER_ID + "}/payload/{" + Constants.OID + "}")
  @Subscribable
  @Tag(name = Constants.STRUCTUREDDATA)
  @Operation(description = "Delete structured data")
  @APIResponse(description = "ok", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.STRUCTUREDDATA_CONTAINER_ID)
  @Parameter(name = Constants.OID)
  public Response deleteStructuredData(
    @PathParam(Constants.STRUCTUREDDATA_CONTAINER_ID) long structuredDataId,
    @PathParam(Constants.OID) String oid
  ) {
    var result = structuredDataContainerService.deleteStructuredData(structuredDataId, oid);
    return result ? Response.status(Status.NO_CONTENT).build() : Response.status(Status.INTERNAL_SERVER_ERROR).build();
  }

  @GET
  @Path("/{" + Constants.STRUCTUREDDATA_CONTAINER_ID + "}/" + Constants.PERMISSIONS)
  @Tag(name = Constants.STRUCTUREDDATA)
  @Operation(description = "Get permissions")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = PermissionsIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.STRUCTUREDDATA_CONTAINER_ID)
  public Response getStructuredDataPermissions(
    @PathParam(Constants.STRUCTUREDDATA_CONTAINER_ID) long structuredDataId
  ) {
    var perms = permissionsService.getPermissionsByNeo4jId(structuredDataId);
    return perms != null ? Response.ok(new PermissionsIO(perms)).build() : Response.status(Status.NOT_FOUND).build();
  }

  @PUT
  @Path("/{" + Constants.STRUCTUREDDATA_CONTAINER_ID + "}/" + Constants.PERMISSIONS)
  @Tag(name = Constants.STRUCTUREDDATA)
  @Operation(description = "Edit permissions")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = PermissionsIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.STRUCTUREDDATA_CONTAINER_ID)
  public Response editStructuredDataPermissions(
    @PathParam(Constants.STRUCTUREDDATA_CONTAINER_ID) long structuredDataId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = PermissionsIO.class))
    ) @Valid PermissionsIO permissions
  ) {
    var perms = permissionsService.updatePermissionsByNeo4jId(permissions, structuredDataId);
    return perms != null ? Response.ok(new PermissionsIO(perms)).build() : Response.status(Status.NOT_FOUND).build();
  }

  @GET
  @Path("/{" + Constants.STRUCTUREDDATA_CONTAINER_ID + "}/" + Constants.ROLES)
  @Tag(name = Constants.STRUCTUREDDATA)
  @Operation(description = "Get roles")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = RolesIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.STRUCTUREDDATA_CONTAINER_ID)
  public Response getStructuredDataRoles(@PathParam(Constants.STRUCTUREDDATA_CONTAINER_ID) long structuredDataId) {
    var roles = permissionsUtil.getRolesByNeo4jId(structuredDataId, securityContext.getUserPrincipal().getName());
    return roles != null ? Response.ok(roles).build() : Response.status(Status.NOT_FOUND).build();
  }
}
