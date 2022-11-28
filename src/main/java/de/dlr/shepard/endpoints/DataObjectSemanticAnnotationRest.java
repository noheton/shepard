package de.dlr.shepard.endpoints;

import de.dlr.shepard.neo4Core.io.SemanticAnnotationIO;
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

public interface DataObjectSemanticAnnotationRest {

	@Operation(description = "Get all semantic annotations")
	@Tag(name = Constants.SEMANTIC_ANNOTATION)
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = SemanticAnnotationIO.class))))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getAllDataObjectAnnotations(long collectionId, long dataObjectId);

	@Operation(description = "Get semantic annotation")
	@Tag(name = Constants.SEMANTIC_ANNOTATION)
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(schema = @Schema(implementation = SemanticAnnotationIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getDataObjectAnnotation(long collectionId, long dataObjectId, long semanticAnnotationId);

	@Operation(description = "Create a new semantic annotation")
	@Tag(name = Constants.SEMANTIC_ANNOTATION)
	@ApiResponse(description = "created", responseCode = "201", content = @Content(schema = @Schema(implementation = SemanticAnnotationIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response createDataObjectAnnotation(long collectionId, long dataObjectId,
			@RequestBody(required = true, content = @Content(schema = @Schema(implementation = SemanticAnnotationIO.class))) @Valid SemanticAnnotationIO semanticAnnotation);

	@Operation(description = "Delete semantic annotation")
	@Tag(name = Constants.SEMANTIC_ANNOTATION)
	@ApiResponse(description = "deleted", responseCode = "204")
	@ApiResponse(description = "not found", responseCode = "404")
	Response deleteDataObjectAnnotation(long collectionId, long dataObjectId, long semanticAnnotationId);
}
