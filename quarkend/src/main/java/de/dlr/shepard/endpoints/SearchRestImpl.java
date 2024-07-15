package de.dlr.shepard.endpoints;

import de.dlr.shepard.search.container.ContainerSearchBody;
import de.dlr.shepard.search.container.ContainerSearchResult;
import de.dlr.shepard.search.container.ContainerSearcher;
import de.dlr.shepard.search.unified.ResponseBody;
import de.dlr.shepard.search.unified.SearchBody;
import de.dlr.shepard.search.unified.Searcher;
import de.dlr.shepard.search.user.UserSearchBody;
import de.dlr.shepard.search.user.UserSearchResult;
import de.dlr.shepard.search.user.UserSearcher;
import de.dlr.shepard.util.Constants;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
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
	private UserSearcher userSearcher = new UserSearcher();
	private ContainerSearcher containerSearcher = new ContainerSearcher();

	@POST
	@Override
	public Response search(SearchBody body) {
		log.info("Search for {} with query: {}", body.getSearchParams().getQueryType(),
				body.getSearchParams().getQuery());
		ResponseBody ret = searcher.search(body, securityContext.getUserPrincipal().getName());
		return Response.ok(ret).build();
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

	@POST
	@Path("/" + Constants.USERS)
	@Override
	public Response searchUsers(UserSearchBody userSearchBody) {
		log.info("Search for users with query: {}", userSearchBody.getSearchParams().getQuery());
		UserSearchResult ret = userSearcher.search(userSearchBody);
		return Response.ok(ret).build();
	}

}
