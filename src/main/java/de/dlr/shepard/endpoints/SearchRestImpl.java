package de.dlr.shepard.endpoints;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import de.dlr.shepard.neo4Core.services.PermissionsService;
import de.dlr.shepard.search.ResponseBody;
import de.dlr.shepard.search.SearchBody;
import de.dlr.shepard.search.SearchScope;
import de.dlr.shepard.search.Searcher;
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
	private PermissionsService permissionsService = new PermissionsService();

	@POST
	@Override
	public Response search(SearchBody body) {
		var principal = securityContext.getUserPrincipal();
		log.info("Received SEARCH REQUEST from user {}", principal.getName());

		Set<Long> collectionIds = Arrays.stream(body.getScopes()).map(SearchScope::getCollectionId)
				.collect(Collectors.toSet());
		boolean allowed = collectionIds.stream().allMatch(id -> allowedToRead(id, principal.getName()));
		if (!allowed)
			return Response.status(Status.FORBIDDEN).build();

		ResponseBody ret = searcher.search(body);
		return Response.ok(ret).build();
	}

	private boolean allowedToRead(Long entityId, String username) {
		var perms = permissionsService.getPermissionsByEntity(entityId);
		if (perms == null)
			return true;
		if (perms.getOwner() != null && username.equals(perms.getOwner().getUsername()))
			return true;
		return perms.getReader().stream().anyMatch(u -> u.getUsername().equals(username));
	}

}
