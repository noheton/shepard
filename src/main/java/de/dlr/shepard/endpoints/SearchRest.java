package de.dlr.shepard.endpoints;

import de.dlr.shepard.exceptions.ShepardParserException;
import de.dlr.shepard.search.ResponseBody;
import de.dlr.shepard.search.SearchBody;
import de.dlr.shepard.util.Constants;
import io.swagger.v3.oas.annotations.Operation;
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
			@RequestBody(required = true, content = @Content(schema = @Schema(implementation = SearchBody.class))) @Valid SearchBody body)
			throws ShepardParserException;

}
