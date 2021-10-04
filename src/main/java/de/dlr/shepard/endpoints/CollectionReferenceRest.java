package de.dlr.shepard.endpoints;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.neo4Core.io.CollectionIO;
import de.dlr.shepard.neo4Core.io.CollectionReferenceIO;
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

public interface CollectionReferenceRest {

	@Tag(name = Constants.COLLECTION_REFERENCE)
	@Operation(description = "Get all collection references")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = CollectionReferenceIO.class))))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getAllCollectionReferences(long collectionId, long dataObjectId);

	@Tag(name = Constants.COLLECTION_REFERENCE)
	@Operation(description = "Get collection reference")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(schema = @Schema(implementation = CollectionReferenceIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getCollectionReference(long collectionId, long dataObjectId, long collectionReferenceId);

	@Tag(name = Constants.COLLECTION_REFERENCE)
	@Operation(description = "Create a new collection reference")
	@ApiResponse(description = "created", responseCode = "201", content = @Content(schema = @Schema(implementation = CollectionReferenceIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response createCollectionReference(long collectionId, long dataObjectId,
			@RequestBody(required = true, content = @Content(schema = @Schema(implementation = CollectionReferenceIO.class))) @Valid CollectionReferenceIO collectionReference)
			throws InvalidBodyException;

	@Tag(name = Constants.COLLECTION_REFERENCE)
	@Operation(description = "Delete collection reference")
	@ApiResponse(description = "deleted", responseCode = "204")
	@ApiResponse(description = "not found", responseCode = "404")
	Response deleteCollectionReference(long collectionId, long dataObjectId, long collectionReferenceId);

	@Tag(name = Constants.COLLECTION_REFERENCE)
	@Operation(description = "Get collection reference payload")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(schema = @Schema(implementation = CollectionIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getCollectionReferencePayload(long collectionId, long dataObjectId, long collectionReferenceId);
}
