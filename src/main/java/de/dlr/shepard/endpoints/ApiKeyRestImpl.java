package de.dlr.shepard.endpoints;

import java.util.ArrayList;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import org.apache.http.HttpStatus;

import de.dlr.shepard.neo4Core.entities.ApiKey;
import de.dlr.shepard.neo4Core.io.ApiKeyIO;
import de.dlr.shepard.neo4Core.io.ApiKeyWithJWTIO;
import de.dlr.shepard.neo4Core.services.ApiKeyService;
import de.dlr.shepard.util.Constants;
import lombok.extern.log4j.Log4j2;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path(Constants.USERS + "/{" + Constants.USERNAME + "}/" + Constants.APIKEYS)
@Log4j2
public class ApiKeyRestImpl implements ApiKeyRest {
	private ApiKeyService apiKeyService = new ApiKeyService();

	@Context
	private UriInfo uriInfo;

	@Context
	private SecurityContext securityContext;

	@Override
	public Response getAllApiKeys(String username) {
		log.info("Received GET ALL request with parameters: userID: {} from user {}", username,
				securityContext.getUserPrincipal().getName());

		var apiKeys = apiKeyService.getAllApiKeys(username);
		var result = new ArrayList<ApiKeyIO>(apiKeys.size());

		for (var key : apiKeys) {
			result.add(new ApiKeyIO(key));
		}
		return Response.ok(result).build();
	}

	@Override
	public Response getApiKey(String username, UUID apiKeyUid) {
		log.info("Received GET request with parameters: userID: {} apiKeyUid: {} from user {}", username, apiKeyUid,
				securityContext.getUserPrincipal().getName());

		ApiKey apiKey = apiKeyService.getApiKey(apiKeyUid);
		return Response.ok(new ApiKeyIO(apiKey)).build();
	}

	@Override
	public Response createApiKey(String username, ApiKeyIO apiKey) {
		var principal = securityContext.getUserPrincipal();
		log.info("Received POST request from {} with parameters: username: {} and new entity: name: {} from user {}",
				principal.getName(), username, apiKey.getName(), securityContext.getUserPrincipal().getName());

		if (!principal.getName().equals(username)) {
			return Response
					.status(HttpStatus.SC_FORBIDDEN, "You are not allowed to create API Keys for user " + username)
					.build();
		}

		ApiKey created = apiKeyService.createApiKey(apiKey, principal.getName(), uriInfo.getBaseUri().toString());
		return Response.ok(new ApiKeyWithJWTIO(created)).status(HttpStatus.SC_CREATED).build();
	}

	@Override
	public Response deleteApiKey(String username, UUID apiKeyUid) {
		log.info("Received DELETE request with parameters: userID: {} apiKeyID: {} from user {}", username, apiKeyUid,
				securityContext.getUserPrincipal().getName());

		return apiKeyService.deleteApiKey(apiKeyUid) ? Response.status(204).build()
				: Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR).build();
	}
}
