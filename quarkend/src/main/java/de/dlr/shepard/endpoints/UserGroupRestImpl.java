package de.dlr.shepard.endpoints;

import de.dlr.shepard.neo4Core.entities.UserGroup;
import de.dlr.shepard.neo4Core.io.PermissionsIO;
import de.dlr.shepard.neo4Core.io.UserGroupIO;
import de.dlr.shepard.neo4Core.orderBy.UserGroupAttributes;
import de.dlr.shepard.neo4Core.services.PermissionsService;
import de.dlr.shepard.neo4Core.services.UserGroupService;
import de.dlr.shepard.security.PermissionsUtil;
import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.QueryParamHelper;
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

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path(Constants.USERGROUP)
public class UserGroupRestImpl implements UserGroupRest {

  @Context
  private SecurityContext securityContext;

  private UserGroupService userGroupService = new UserGroupService();
  private PermissionsService permissionsService = new PermissionsService();

  @POST
  @Override
  public Response createUserGroup(@Valid UserGroupIO userGroup) {
    var newUserGroup = userGroupService.createUserGroup(userGroup, securityContext.getUserPrincipal().getName());
    return Response.ok(new UserGroupIO(newUserGroup)).status(Status.CREATED).build();
  }

  @PUT
  @Path("/{" + Constants.USERGROUP_ID + "}")
  @Override
  public Response updateUserGroup(@PathParam(Constants.USERGROUP_ID) Long id, @Valid UserGroupIO userGroup) {
    UserGroup updatedUserGroup = userGroupService.updateUserGroup(
      id,
      userGroup,
      securityContext.getUserPrincipal().getName()
    );
    return Response.ok(new UserGroupIO(updatedUserGroup)).build();
  }

  @DELETE
  @Path("/{" + Constants.USERGROUP_ID + "}")
  @Override
  public Response deleteUserGroup(@PathParam(Constants.USERGROUP_ID) Long id) {
    return userGroupService.deleteUserGroup(id)
      ? Response.status(Status.NO_CONTENT).build()
      : Response.status(Status.INTERNAL_SERVER_ERROR).build();
  }

  @GET
  @Path("/{" + Constants.USERGROUP_ID + "}")
  @Override
  public Response getUserGroup(@PathParam(Constants.USERGROUP_ID) Long usergroupId) {
    UserGroup ret = userGroupService.getUserGroup(usergroupId);
    if (ret == null) return Response.status(Status.NOT_FOUND).build();
    return Response.ok(new UserGroupIO(ret)).build();
  }

  @GET
  @Override
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
  @Override
  public Response getUserGroupPermissions(@PathParam(Constants.USERGROUP_ID) long userGroupId) {
    var perms = permissionsService.getPermissionsByNeo4jId(userGroupId);
    return perms != null ? Response.ok(new PermissionsIO(perms)).build() : Response.status(Status.NOT_FOUND).build();
  }

  @PUT
  @Path("/{" + Constants.USERGROUP_ID + "}/" + Constants.PERMISSIONS)
  @Override
  public Response editUserGroupPermissions(
    @PathParam(Constants.USERGROUP_ID) long userGroupId,
    @Valid PermissionsIO permissions
  ) {
    var perms = permissionsService.updatePermissionsByNeo4jId(permissions, userGroupId);
    return perms != null ? Response.ok(new PermissionsIO(perms)).build() : Response.status(Status.NOT_FOUND).build();
  }

  @GET
  @Path("/{" + Constants.USERGROUP_ID + "}/" + Constants.ROLES)
  @Override
  public Response getUserGroupRoles(@PathParam(Constants.USERGROUP_ID) long userGroupId) {
    var roles = new PermissionsUtil().getRolesByNeo4jId(userGroupId, securityContext.getUserPrincipal().getName());
    return roles != null ? Response.ok(roles).build() : Response.status(Status.NOT_FOUND).build();
  }
}
