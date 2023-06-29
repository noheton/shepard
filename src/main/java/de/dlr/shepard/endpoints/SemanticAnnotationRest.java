package de.dlr.shepard.endpoints;

import de.dlr.shepard.neo4Core.io.SemanticAnnotationIO;
import de.dlr.shepard.util.Constants;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.ws.rs.core.Response;

public interface SemanticAnnotationRest {

	@Tag(name = Constants.SEMANTIC_ANNOTATION)
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = SemanticAnnotationIO.class))))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getAllAnnotations(long entityId);

	@Tag(name = Constants.SEMANTIC_ANNOTATION)
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(schema = @Schema(implementation = SemanticAnnotationIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getAnnotation(long entityId, long semanticAnnotationId);

	@Tag(name = Constants.SEMANTIC_ANNOTATION)
	@ApiResponse(description = "created", responseCode = "201", content = @Content(schema = @Schema(implementation = SemanticAnnotationIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response createAnnotation(long entityId,
			@RequestBody(required = true, content = @Content(schema = @Schema(implementation = SemanticAnnotationIO.class))) @Valid SemanticAnnotationIO semanticAnnotation);

	@Tag(name = Constants.SEMANTIC_ANNOTATION)
	@ApiResponse(description = "deleted", responseCode = "204")
	@ApiResponse(description = "not found", responseCode = "404")
	Response deleteAnnotation(long entityId, long semanticAnnotationId);
}
