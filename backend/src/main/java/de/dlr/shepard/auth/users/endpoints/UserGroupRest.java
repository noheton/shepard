package de.dlr.shepard.auth.users.endpoints;

import de.dlr.shepard.auth.permission.io.PermissionsIO;
import de.dlr.shepard.auth.permission.model.Roles;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.users.entities.UserGroup;
import de.dlr.shepard.auth.users.io.UserGroupIO;
import de.dlr.shepard.auth.users.services.UserGroupService;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.QueryParamHelper;
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
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path(Constants.USERGROUPS)
@RequestScoped
public class UserGroupRest {

  @Context
  private SecurityContext securityContext;

  private UserGroupService userGroupService;

  private PermissionsService permissionsService;

  UserGroupRest() {}

  @Inject
  public UserGroupRest(UserGroupService userGroupService, PermissionsService permissionsService) {
    this.userGroupService = userGroupService;
    this.permissionsService = permissionsService;
  }

  @POST
  @Tag(name = Constants.USERGROUP)
  @Operation(description = "Create a new usergroup")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = UserGroupIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  public Response createUserGroup(
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = UserGroupIO.class))
    ) @Valid UserGroupIO userGroup
  ) {
    var newUserGroup = userGroupService.createUserGroup(userGroup, securityContext.getUserPrincipal().getName());
    return Response.ok(new UserGroupIO(newUserGroup)).status(Status.CREATED).build();
  }

  @PUT
  @Path("/{" + Constants.USERGROUP_ID + "}")
  @Tag(name = Constants.USERGROUP)
  @Operation(description = "Update usergroup")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = UserGroupIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.USERGROUP_ID)
  public Response updateUserGroup(
    @PathParam(Constants.USERGROUP_ID) Long id,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = UserGroupIO.class))
    ) @Valid UserGroupIO userGroup
  ) {
    UserGroup updatedUserGroup = userGroupService.updateUserGroup(
      id,
      userGroup,
      securityContext.getUserPrincipal().getName()
    );
    return Response.ok(new UserGroupIO(updatedUserGroup)).build();
  }

  @DELETE
  @Path("/{" + Constants.USERGROUP_ID + "}")
  @Tag(name = Constants.USERGROUP)
  @Operation(description = "Delete usergroup")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.USERGROUP_ID)
  public Response deleteUserGroup(@PathParam(Constants.USERGROUP_ID) Long id) {
    return userGroupService.deleteUserGroup(id)
      ? Response.status(Status.NO_CONTENT).build()
      : Response.status(Status.INTERNAL_SERVER_ERROR).build();
  }

  @GET
  @Path("/{" + Constants.USERGROUP_ID + "}")
  @Tag(name = Constants.USERGROUP)
  @Operation(description = "Get usergroup")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = UserGroupIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.USERGROUP_ID)
  public Response getUserGroup(@PathParam(Constants.USERGROUP_ID) Long id) {
    UserGroup ret = userGroupService.getUserGroup(id);
    if (ret == null) return Response.status(Status.NOT_FOUND).build();
    return Response.ok(new UserGroupIO(ret)).build();
  }

  @GET
  @Tag(name = Constants.USERGROUP)
  @Operation(description = "Get all usergroups")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = UserGroupIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.QP_PAGE)
  @Parameter(name = Constants.QP_SIZE)
  @Parameter(name = Constants.QP_ORDER_BY_ATTRIBUTE)
  @Parameter(name = Constants.QP_ORDER_DESC)
  public Response getAllUserGroups(
    @QueryParam(Constants.QP_PAGE) Integer page,
    @QueryParam(Constants.QP_SIZE) Integer size,
    @QueryParam(Constants.QP_ORDER_BY_ATTRIBUTE) UserGroupAttributes orderBy,
    @QueryParam(Constants.QP_ORDER_DESC) Boolean orderDesc
  ) {
    var params = new QueryParamHelper();
    if (page != null && size != null) params = params.withPageAndSize(page, size);
    if (orderBy != null) params = params.withOrderByAttribute(orderBy, orderDesc);
    List<UserGroup> allUserGroups = userGroupService.getAllUserGroups(
      params,
      securityContext.getUserPrincipal().getName()
    );
    var result = new ArrayList<UserGroupIO>(allUserGroups.size());
    for (UserGroup userGroup : allUserGroups) {
      result.add(new UserGroupIO(userGroup));
    }
    return Response.ok(result).build();
  }

  @GET
  @Path("/{" + Constants.USERGROUP_ID + "}/" + Constants.PERMISSIONS)
  @Tag(name = Constants.USERGROUP)
  @Operation(description = "Get permissions")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = PermissionsIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.USERGROUP_ID)
  public Response getUserGroupPermissions(@PathParam(Constants.USERGROUP_ID) long userGroupId) {
    var perms = permissionsService.getPermissionsOfEntity(userGroupId);
    return perms != null ? Response.ok(new PermissionsIO(perms)).build() : Response.status(Status.NOT_FOUND).build();
  }

  @PUT
  @Path("/{" + Constants.USERGROUP_ID + "}/" + Constants.PERMISSIONS)
  @Tag(name = Constants.USERGROUP)
  @Operation(description = "Edit permissions")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = PermissionsIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.USERGROUP_ID)
  public Response editUserGroupPermissions(
    @PathParam(Constants.USERGROUP_ID) long userGroupId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = PermissionsIO.class))
    ) @Valid PermissionsIO permissions
  ) {
    var perms = permissionsService.updatePermissionsByNeo4jId(permissions, userGroupId);
    return perms != null ? Response.ok(new PermissionsIO(perms)).build() : Response.status(Status.NOT_FOUND).build();
  }

  @GET
  @Path("/{" + Constants.USERGROUP_ID + "}/" + Constants.ROLES)
  @Tag(name = Constants.USERGROUP)
  @Operation(description = "Get roles")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = Roles.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.USERGROUP_ID)
  public Response getUserGroupRoles(@PathParam(Constants.USERGROUP_ID) long userGroupId) {
    var roles = permissionsService.getUserRolesOnEntity(userGroupId, securityContext.getUserPrincipal().getName());
    return roles != null ? Response.ok(roles).build() : Response.status(Status.NOT_FOUND).build();
  }
}
