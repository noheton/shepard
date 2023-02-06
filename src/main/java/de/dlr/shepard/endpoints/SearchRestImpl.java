package de.dlr.shepard.endpoints;

import java.util.ArrayList;
import java.util.List;

import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.io.UserIO;
import de.dlr.shepard.neo4Core.services.UserService;
import de.dlr.shepard.search.ContainerSearchBody;
import de.dlr.shepard.search.ContainerSearchResult;
import de.dlr.shepard.search.ContainerSearcher;
import de.dlr.shepard.search.ResponseBody;
import de.dlr.shepard.search.SearchBody;
import de.dlr.shepard.search.Searcher;
import de.dlr.shepard.util.Constants;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Path(Constants.SEARCH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SearchRestImpl implements SearchRest {

	@Context
	private SecurityContext securityContext;
	private Searcher searcher = new Searcher();
	private UserService userService = new UserService();
	private ContainerSearcher containerSearcher = new ContainerSearcher();

	@POST
	@Override
	public Response search(SearchBody body) {
		log.info("Search for {} with query: {}", body.getSearchParams().getQueryType(),
				body.getSearchParams().getQuery());
		ResponseBody ret = searcher.search(body, securityContext.getUserPrincipal().getName());
		return Response.ok(ret).build();
	}

	@GET
	@Path("/" + Constants.USERS)
	@Override
	public Response searchUsers(@QueryParam(Constants.USERNAME) String username,
			@QueryParam(Constants.FIRSTNAME) String firstName, @QueryParam(Constants.LASTNAME) String lastName,
			@QueryParam(Constants.EMAIL) String email) {
		if (username == null && firstName == null && lastName == null && email == null)
			throw new BadRequestException("At least one of the arguments should not be null");
		List<User> users = userService.searchUsers(username, firstName, lastName, email);
		ArrayList<UserIO> result = new ArrayList<UserIO>(users.size());
		for (User user : users)
			result.add(new UserIO(user));
		return Response.ok(result).build();
	}

	@POST
	@Path("/" + Constants.CONTAINERS)
	@Override
	public Response searchContainers(ContainerSearchBody containerSearchBody) {
		log.info("Search for containers of type {} with query: {}",
				containerSearchBody.getSearchParams().getQueryType(), containerSearchBody.getSearchParams().getQuery());
		ContainerSearchResult ret = containerSearcher.search(containerSearchBody,
				securityContext.getUserPrincipal().getName());
		return Response.ok(ret).build();
	}

}
