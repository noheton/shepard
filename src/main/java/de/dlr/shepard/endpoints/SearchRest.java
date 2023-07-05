package de.dlr.shepard.endpoints;

import de.dlr.shepard.neo4Core.io.UserIO;
import de.dlr.shepard.search.container.ContainerSearchBody;
import de.dlr.shepard.search.container.ContainerSearchResult;
import de.dlr.shepard.search.unified.ResponseBody;
import de.dlr.shepard.search.unified.SearchBody;
import de.dlr.shepard.search.user.UserSearchBody;
import de.dlr.shepard.search.user.UserSearchResult;
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

public interface SearchRest {

	@Tag(name = Constants.SEARCH)
	@Operation(description = "search")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(schema = @Schema(implementation = ResponseBody.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response search(
			@RequestBody(required = true, content = @Content(schema = @Schema(implementation = SearchBody.class))) @Valid SearchBody body);

	@Tag(name = Constants.SEARCH)
	@Deprecated
	@Operation(description = "Search users")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserIO.class))))
	@ApiResponse(description = "not found", responseCode = "404")
	Response searchUsers(String username, String firstName, String lastName, String email);

	@Tag(name = Constants.SEARCH)
	@Operation(description = "Search containers")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(schema = @Schema(implementation = ContainerSearchResult.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response searchContainers(
			@RequestBody(required = true, content = @Content(schema = @Schema(implementation = ContainerSearchBody.class))) @Valid ContainerSearchBody containerSearchBody);

	@Tag(name = Constants.SEARCH)
	@Operation(description = "Search users")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserSearchResult.class))))
	@ApiResponse(description = "not found", responseCode = "404")
	Response searchUsersNew(
			@RequestBody(required = true, content = @Content(schema = @Schema(implementation = UserSearchBody.class))) @Valid UserSearchBody userSearchBody);
}
