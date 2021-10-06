package de.dlr.shepard.endpoints;

import de.dlr.shepard.search.ResponseBody;
import de.dlr.shepard.search.SearchBody;
import de.dlr.shepard.search.Searcher;
import de.dlr.shepard.security.JWTPrincipal;
import de.dlr.shepard.util.Constants;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import lombok.extern.log4j.Log4j2;

@Log4j2
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
		JWTPrincipal principal = (JWTPrincipal) securityContext.getUserPrincipal();
		log.info("Received SEARCH REQUEST from user {}", principal.getUsername());
		ResponseBody ret = searcher.search(body);
		return Response.ok(ret).build();
	}

}
