package de.dlr.shepard.endpoints;

import de.dlr.shepard.neo4Core.io.UserIO;
import de.dlr.shepard.util.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.core.Response;

public interface UserRest {

	@Tag(name = Constants.USER)
	@Operation(description = "Get current user")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(schema = @Schema(implementation = UserIO.class)))
	Response getCurrentUser();

	@Tag(name = Constants.USER)
	@Operation(description = "Get user")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(schema = @Schema(implementation = UserIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getUser(String username);

}
