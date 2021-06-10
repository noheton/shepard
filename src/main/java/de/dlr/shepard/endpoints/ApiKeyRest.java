package de.dlr.shepard.endpoints;

import java.util.UUID;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import de.dlr.shepard.neo4Core.io.ApiKeyIO;
import de.dlr.shepard.neo4Core.io.ApiKeyWithJWTIO;
import de.dlr.shepard.util.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

public interface ApiKeyRest {

	@GET
	@Tag(name = Constants.APIKEY)
	@Operation(description = "Get all api keys")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ApiKeyIO.class))))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getAllApiKeys(@PathParam(Constants.USERNAME) String username);

	@GET
	@Path("/{" + Constants.APIKEY_UID + "}")
	@Tag(name = Constants.APIKEY)
	@Operation(description = "Get api key")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(schema = @Schema(implementation = ApiKeyIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getApiKey(@PathParam(Constants.USERNAME) String username, @PathParam(Constants.APIKEY_UID) UUID apiKeyUid);

	@POST
	@Tag(name = Constants.APIKEY)
	@Operation(description = "Create a new api key")
	@ApiResponse(description = "created", responseCode = "201", content = @Content(schema = @Schema(implementation = ApiKeyWithJWTIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response createApiKey(@PathParam(Constants.USERNAME) String username,
			@RequestBody(required = true, content = @Content(schema = @Schema(implementation = ApiKeyIO.class))) ApiKeyIO apiKey);

	@DELETE
	@Path("/{" + Constants.APIKEY_UID + "}")
	@Tag(name = Constants.APIKEY)
	@Operation(description = "Delete api key")
	@ApiResponse(description = "deleted", responseCode = "204")
	@ApiResponse(description = "not found", responseCode = "404")
	Response deleteApiKey(@PathParam(Constants.USERNAME) String username,
			@PathParam(Constants.APIKEY_UID) UUID apiKeyUid);
}
