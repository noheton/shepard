package de.dlr.shepard.endpoints;

import java.util.ArrayList;
import java.util.UUID;

import de.dlr.shepard.neo4Core.entities.ApiKey;
import de.dlr.shepard.neo4Core.io.ApiKeyIO;
import de.dlr.shepard.neo4Core.io.ApiKeyWithJWTIO;
import de.dlr.shepard.neo4Core.services.ApiKeyService;
import de.dlr.shepard.util.Constants;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
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

	@GET
	@Override
	public Response getAllApiKeys(@PathParam(Constants.USERNAME) String username) {
		log.info("Received GET ALL request with parameters: userID: {} from user {}", username,
				securityContext.getUserPrincipal().getName());

		var apiKeys = apiKeyService.getAllApiKeys(username);
		var result = new ArrayList<ApiKeyIO>(apiKeys.size());

		for (var key : apiKeys) {
			result.add(new ApiKeyIO(key));
		}
		return Response.ok(result).build();
	}

	@GET
	@Path("/{" + Constants.APIKEY_UID + "}")
	@Override
	public Response getApiKey(@PathParam(Constants.USERNAME) String username,
			@PathParam(Constants.APIKEY_UID) String apiKeyUid) {
		log.info("Received GET request with parameters: userID: {} apiKeyUid: {} from user {}", username, apiKeyUid,
				securityContext.getUserPrincipal().getName());
		UUID uid;
		try {
			uid = UUID.fromString(apiKeyUid);
		} catch (IllegalArgumentException e) {
			log.error("Given api key has no valid format: {}", apiKeyUid);
			return Response.status(Status.BAD_REQUEST).build();
		}
		ApiKey apiKey = apiKeyService.getApiKey(uid);
		return Response.ok(new ApiKeyIO(apiKey)).build();
	}

	@POST
	@Override
	public Response createApiKey(@PathParam(Constants.USERNAME) String username, ApiKeyIO apiKey) {
		var principal = securityContext.getUserPrincipal();
		log.info("Received POST request from {} with parameters: username: {} and new entity: name: {} from user {}",
				principal.getName(), username, apiKey.getName(), securityContext.getUserPrincipal().getName());

		if (!principal.getName().equals(username)) {
			return Response.status(Status.FORBIDDEN.getStatusCode(),
					"You are not allowed to create API Keys for user " + username).build();
		}

		ApiKey created = apiKeyService.createApiKey(apiKey, principal.getName(), uriInfo.getBaseUri().toString());
		return Response.ok(new ApiKeyWithJWTIO(created)).status(Status.CREATED).build();
	}

	@DELETE
	@Path("/{" + Constants.APIKEY_UID + "}")
	@Override
	public Response deleteApiKey(@PathParam(Constants.USERNAME) String username,
			@PathParam(Constants.APIKEY_UID) String apiKeyUid) {
		log.info("Received DELETE request with parameters: userID: {} apiKeyID: {} from user {}", username, apiKeyUid,
				securityContext.getUserPrincipal().getName());

		UUID uid;
		try {
			uid = UUID.fromString(apiKeyUid);
		} catch (IllegalArgumentException e) {
			log.error("Given api key has no valid format: {}", apiKeyUid);
			return Response.status(Status.BAD_REQUEST).build();
		}
		return apiKeyService.deleteApiKey(uid) ? Response.status(204).build()
				: Response.status(Status.INTERNAL_SERVER_ERROR).build();
	}
}
