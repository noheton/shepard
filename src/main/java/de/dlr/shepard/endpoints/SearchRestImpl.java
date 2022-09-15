package de.dlr.shepard.endpoints;

import de.dlr.shepard.search.ResponseBody;
import de.dlr.shepard.search.SearchBody;
import de.dlr.shepard.search.Searcher;
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

	@POST
	@Override
	public Response search(SearchBody body) {
		log.info("Search for {} with query: {}", body.getSearchParams().getQueryType(),
				body.getSearchParams().getQuery());
		ResponseBody ret = searcher.search(body, securityContext.getUserPrincipal().getName());
		return Response.ok(ret).build();

	}

}
