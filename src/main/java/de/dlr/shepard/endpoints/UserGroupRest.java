package de.dlr.shepard.endpoints;

import de.dlr.shepard.neo4Core.io.UserGroupIO;
import de.dlr.shepard.util.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.ws.rs.core.Response;

public interface UserGroupRest {

	@Tag(name = Constants.USERGROUP)
	@Operation(description = "Get all usergroups")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserGroupIO.class))))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getAllUserGroups();

	@Tag(name = Constants.USERGROUP)
	@Operation(description = "Get usergroup")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(schema = @Schema(implementation = UserGroupIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getUserGroup(Long id);

	@Tag(name = Constants.USERGROUP)
	@Operation(description = "Create a new usergroup")
	@ApiResponse(description = "created", responseCode = "201", content = @Content(schema = @Schema(implementation = UserGroupIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response createUserGroup(
			@RequestBody(required = true, content = @Content(schema = @Schema(implementation = UserGroupIO.class))) @Valid UserGroupIO userGroup);

	@Tag(name = Constants.USERGROUP)
	@Operation(description = "Update usergroup")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(schema = @Schema(implementation = UserGroupIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response updateUserGroup(Long id,
			@RequestBody(required = true, content = @Content(schema = @Schema(implementation = UserGroupIO.class))) @Valid UserGroupIO userGroup);

	@Tag(name = Constants.USERGROUP)
	@Operation(description = "Delete usergroup")
	@ApiResponse(description = "deleted", responseCode = "204")
	@ApiResponse(description = "not found", responseCode = "404")
	Response deleteUserGroup(Long id);

}
