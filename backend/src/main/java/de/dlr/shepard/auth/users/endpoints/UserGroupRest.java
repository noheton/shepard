package de.dlr.shepard.auth.users.endpoints;

import de.dlr.shepard.auth.permission.io.PermissionsIO;
import de.dlr.shepard.auth.permission.model.Roles;
import de.dlr.shepard.auth.users.entities.UserGroup;
import de.dlr.shepard.auth.users.io.UserGroupIO;
import de.dlr.shepard.auth.users.services.UserGroupService;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.QueryParamHelper;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
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

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path(Constants.SHEPARD_API + "/" + Constants.USERGROUPS)
@RequestScoped
public class UserGroupRest {

  @Inject
  UserGroupService userGroupService;

  @POST
  @Tag(name = Constants.USERGROUP)
  @Operation(description = "Create a new usergroup")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = UserGroupIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  public Response createUserGroup(
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = UserGroupIO.class))
    ) @Valid UserGroupIO userGroup
  ) {
    var newUserGroup = userGroupService.createUserGroup(userGroup);
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
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.USERGROUP_ID)
  public Response updateUserGroup(
    @PathParam(Constants.USERGROUP_ID) @NotNull @PositiveOrZero Long id,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = UserGroupIO.class))
    ) @Valid UserGroupIO userGroup
  ) {
    UserGroup updatedUserGroup = userGroupService.updateUserGroup(id, userGroup);
    return Response.ok(new UserGroupIO(updatedUserGroup)).build();
  }

  @DELETE
  @Path("/{" + Constants.USERGROUP_ID + "}")
  @Tag(name = Constants.USERGROUP)
  @Operation(description = "Delete usergroup")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.USERGROUP_ID)
  public Response deleteUserGroup(@PathParam(Constants.USERGROUP_ID) @NotNull @PositiveOrZero Long id) {
    userGroupService.deleteUserGroup(id);
    return Response.status(Status.NO_CONTENT).build();
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
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.USERGROUP_ID)
  public Response getUserGroup(@PathParam(Constants.USERGROUP_ID) @NotNull @PositiveOrZero Long id) {
    UserGroup ret = userGroupService.getUserGroup(id);
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
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @Parameter(name = Constants.QP_PAGE)
  @Parameter(name = Constants.QP_SIZE)
  @Parameter(name = Constants.QP_ORDER_BY_ATTRIBUTE)
  @Parameter(name = Constants.QP_ORDER_DESC)
  public Response getAllUserGroups(
    @QueryParam(Constants.QP_PAGE) @PositiveOrZero Integer page,
    @QueryParam(Constants.QP_SIZE) @PositiveOrZero Integer size,
    @QueryParam(Constants.QP_ORDER_BY_ATTRIBUTE) UserGroupAttributes orderBy,
    @QueryParam(Constants.QP_ORDER_DESC) Boolean orderDesc
  ) {
    var params = new QueryParamHelper();
    if (page != null && size != null) params = params.withPageAndSize(page, size);
    if (orderBy != null) params = params.withOrderByAttribute(orderBy, orderDesc);
    List<UserGroup> allUserGroups = userGroupService.getAllUserGroups(params);
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
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.USERGROUP_ID)
  public Response getUserGroupPermissions(
    @PathParam(Constants.USERGROUP_ID) @NotNull @PositiveOrZero Long userGroupId
  ) {
    var perms = userGroupService.getUserGroupPermissions(userGroupId);
    return Response.ok(new PermissionsIO(perms)).build();
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
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.USERGROUP_ID)
  public Response editUserGroupPermissions(
    @PathParam(Constants.USERGROUP_ID) @NotNull @PositiveOrZero Long userGroupId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = PermissionsIO.class))
    ) @Valid PermissionsIO permissions
  ) {
    var perms = userGroupService.updateUserGroupPermissions(permissions, userGroupId);
    return Response.ok(new PermissionsIO(perms)).build();
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
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.USERGROUP_ID)
  public Response getUserGroupRoles(@PathParam(Constants.USERGROUP_ID) @NotNull @PositiveOrZero Long userGroupId) {
    var roles = userGroupService.getUserGroupRoles(userGroupId);
    return Response.ok(roles).build();
  }
}
