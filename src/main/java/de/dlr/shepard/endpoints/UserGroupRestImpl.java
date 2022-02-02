package de.dlr.shepard.endpoints;

import java.util.ArrayList;
import java.util.List;

import de.dlr.shepard.neo4Core.entities.UserGroup;
import de.dlr.shepard.neo4Core.io.UserGroupIO;
import de.dlr.shepard.neo4Core.services.UserGroupService;
import de.dlr.shepard.util.Constants;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;
import lombok.extern.slf4j.Slf4j;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path(Constants.USERGROUP)
@Slf4j
public class UserGroupRestImpl implements UserGroupRest {

	@Context
	private SecurityContext securityContext;
	private UserGroupService userGroupService = new UserGroupService();

	@POST
	@Override
	public Response createUserGroup(@Valid UserGroupIO userGroup) {
		var principal = securityContext.getUserPrincipal();
		log.info("Received POST request from {} with parameters: userGroupname: {} from user {}", principal.getName(),
				userGroup.getName(), securityContext.getUserPrincipal().getName());
		var newUserGroup = userGroupService.createUserGroup(userGroup, securityContext.getUserPrincipal().getName());
		return Response.ok(new UserGroupIO(newUserGroup)).status(Status.CREATED).build();
	}

	@PUT
	@Path("/{" + Constants.USERGROUPID + "}")
	@Override
	public Response updateUserGroup(@PathParam(Constants.USERGROUPID) Long id, @Valid UserGroupIO userGroup) {
		log.info("Received PUT request from user {} with id {}", securityContext.getUserPrincipal().getName(), id);
		UserGroup updatedUserGroup = userGroupService.updateUserGroup(id, userGroup,
				securityContext.getUserPrincipal().getName());
		return Response.ok(new UserGroupIO(updatedUserGroup)).build();
	}

	@DELETE
	@Path("/{" + Constants.USERGROUPID + "}")
	@Override
	public Response deleteUserGroup(@PathParam(Constants.USERGROUPID) Long id) {
		log.info("Received DELETE request with parameters: id: {} from user {}", id,
				securityContext.getUserPrincipal().getName());
		return userGroupService.deleteUserGroup(id) ? Response.status(204).build()
				: Response.status(Status.INTERNAL_SERVER_ERROR).build();
	}

	@GET
	@Path("/{" + Constants.USERGROUPID + "}")
	@Override
	public Response getUserGroup(@PathParam(Constants.USERGROUPID) Long usergroupId) {
		log.info("Received GET request from user {} with id {}", securityContext.getUserPrincipal().getName(),
				usergroupId);
		UserGroup ret = userGroupService.getUserGroup(usergroupId);
		if (ret == null)
			return Response.status(404).build();
		return Response.ok(new UserGroupIO(ret)).build();
	}

	@GET
	@Override
	public Response getAllUserGroups() {
		log.info("Received GET ALL request from user {}", securityContext.getUserPrincipal().getName());
		List<UserGroup> allUserGroups = userGroupService.getAllUserGroups(securityContext.getUserPrincipal().getName());
		var result = new ArrayList<UserGroupIO>(allUserGroups.size());
		for (UserGroup userGroup : allUserGroups) {
			result.add(new UserGroupIO(userGroup));
		}
		return Response.ok(result).build();
	}

}
