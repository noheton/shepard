package de.dlr.shepard.endpoints;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import de.dlr.shepard.neo4Core.io.UserIO;
import de.dlr.shepard.util.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

public interface UserRest {

	@GET
	@Tag(name = Constants.USER)
	@Operation(description = "Get current user")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(schema = @Schema(implementation = UserIO.class)))
	Response getCurrentUser();

	@GET
	@Path("/{" + Constants.USERNAME + "}")
	@Tag(name = Constants.USER)
	@Operation(description = "Get user")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(schema = @Schema(implementation = UserIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getUser(@PathParam(Constants.USERNAME) String username);

}
