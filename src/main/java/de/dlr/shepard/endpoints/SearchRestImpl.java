package de.dlr.shepard.endpoints;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import de.dlr.shepard.search.ResponseBody;
import de.dlr.shepard.search.SearchBody;
import de.dlr.shepard.search.SearchScope;
import de.dlr.shepard.search.Searcher;
import de.dlr.shepard.security.PermissionsUtil;
import de.dlr.shepard.util.AccessType;
import de.dlr.shepard.util.Constants;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
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
	private PermissionsUtil permissionsUtil = new PermissionsUtil();

	@POST
	@Override
	public Response search(SearchBody body) {
		var principal = securityContext.getUserPrincipal();
		log.info("Received SEARCH REQUEST from user {}", principal.getName());

		Set<Long> collectionIds = Arrays.stream(body.getScopes()).map(SearchScope::getCollectionId)
				.collect(Collectors.toSet());
		if (collectionIds.stream()
				.allMatch(id -> permissionsUtil.isAllowed(id, AccessType.Read, principal.getName()))) {
			ResponseBody ret = searcher.search(body);
			return Response.ok(ret).build();
		}
		return Response.status(Status.FORBIDDEN).build();

	}

}
