package de.dlr.shepard.endpoints;

import de.dlr.shepard.neo4Core.io.SemanticRepositoryIO;
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

public interface SemanticRepositoryRest {

	@Tag(name = Constants.SEMANTIC_REPOSITORY)
	@Operation(description = "Get all semantic repositories")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = SemanticRepositoryIO.class))))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getAllSemanticRepositories();

	@Tag(name = Constants.SEMANTIC_REPOSITORY)
	@Operation(description = "Get semantic repository")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(schema = @Schema(implementation = SemanticRepositoryIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getSemanticRepository(long semanticRepositoryId);

	@Tag(name = Constants.SEMANTIC_REPOSITORY)
	@Operation(description = "Create a new semantic repository")
	@ApiResponse(description = "created", responseCode = "201", content = @Content(schema = @Schema(implementation = SemanticRepositoryIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response createSemanticRepository(
			@RequestBody(required = true, content = @Content(schema = @Schema(implementation = SemanticRepositoryIO.class))) @Valid SemanticRepositoryIO semanticRepository);

	@Tag(name = Constants.SEMANTIC_REPOSITORY)
	@Operation(description = "Delete semantic repository")
	@ApiResponse(description = "deleted", responseCode = "204")
	@ApiResponse(description = "not found", responseCode = "404")
	Response deleteSemanticRepository(long semanticRepositoryId);

}
