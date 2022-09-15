package de.dlr.shepard.endpoints;

import de.dlr.shepard.neo4Core.io.URIReferenceIO;
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

public interface URIReferenceRest {

	@Tag(name = Constants.URI_REFERENCE)
	@Operation(description = "Get all uri references")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = URIReferenceIO.class))))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getAllUriReferences(long collectionId, long dataObjectId);

	@Tag(name = Constants.URI_REFERENCE)
	@Operation(description = "Get uri reference")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(schema = @Schema(implementation = URIReferenceIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getUriReference(long collectionId, long dataObjectId, long referenceId);

	@Tag(name = Constants.URI_REFERENCE)
	@Operation(description = "Create a new uri reference")
	@ApiResponse(description = "created", responseCode = "201", content = @Content(schema = @Schema(implementation = URIReferenceIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response createUriReference(long collectionId, long dataObjectId,
			@RequestBody(required = true, content = @Content(schema = @Schema(implementation = URIReferenceIO.class))) @Valid URIReferenceIO timeseriesReference);

	@Tag(name = Constants.URI_REFERENCE)
	@Operation(description = "Delete uri reference")
	@ApiResponse(description = "deleted", responseCode = "204")
	@ApiResponse(description = "not found", responseCode = "404")
	Response deleteUriReference(long collectionId, long dataObjectId, long referenceId);

}
