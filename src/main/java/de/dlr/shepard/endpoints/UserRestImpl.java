package de.dlr.shepard.endpoints;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import org.apache.http.HttpStatus;

import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.io.UserIO;
import de.dlr.shepard.neo4Core.services.UserService;
import de.dlr.shepard.security.JWTPrincipal;
import de.dlr.shepard.util.Constants;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Path(Constants.USERS)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserRestImpl implements UserRest {

	private UserService userService = new UserService();

	@Context
	private SecurityContext securityContext;

	@GET
	@Override
	public Response getCurrentUser() {
		JWTPrincipal principal = (JWTPrincipal) securityContext.getUserPrincipal();
		log.info("Received GET CURRENT USER request with parameters: username: {}", principal.getUsername());
		User currentUser = userService.getUser(principal.getUsername());
		return currentUser == null ? Response.status(HttpStatus.SC_NOT_FOUND).build()
				: Response.ok(new UserIO(currentUser)).build();

	}

	@GET
	@Path("/{" + Constants.USERNAME + "}")
	@Override
	public Response getUser(@PathParam(Constants.USERNAME) String username) {
		log.info("Received GET request with parameters: userID: {} from user {}", username,
				securityContext.getUserPrincipal().getName());

		User user = userService.getUser(username);
		return user == null ? Response.status(HttpStatus.SC_NOT_FOUND).build() : Response.ok(new UserIO(user)).build();
	}
}
