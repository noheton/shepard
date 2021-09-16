package de.dlr.shepard.endpoints;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.mongoDB.StructuredDataPayload;
import de.dlr.shepard.neo4Core.io.StructuredDataReferenceIO;
import de.dlr.shepard.util.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.core.Response;

public interface StructuredDataReferenceRest {

	@Tag(name = Constants.STRUCTUREDDATA_REFERENCE)
	@Operation(description = "Get all structureddata references")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = StructuredDataReferenceIO.class))))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getAllStructuredDataReferences(long collectionId, long dataObjectId);

	@Tag(name = Constants.STRUCTUREDDATA_REFERENCE)
	@Operation(description = "Get structureddata reference")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(schema = @Schema(implementation = StructuredDataReferenceIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getStructuredDataReference(long collectionId, long dataObjectId, long referenceId);

	@Tag(name = Constants.STRUCTUREDDATA_REFERENCE)
	@Operation(description = "Create a new structureddata reference")
	@ApiResponse(description = "created", responseCode = "201", content = @Content(schema = @Schema(implementation = StructuredDataReferenceIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response createStructuredDataReference(long collectionId, long dataObjectId,
			@RequestBody(required = true, content = @Content(schema = @Schema(implementation = StructuredDataReferenceIO.class))) StructuredDataReferenceIO structuredDataReference)
			throws InvalidBodyException;

	@Tag(name = Constants.STRUCTUREDDATA_REFERENCE)
	@Operation(description = "Delete structureddata reference")
	@ApiResponse(description = "deleted", responseCode = "204")
	@ApiResponse(description = "not found", responseCode = "404")
	Response deleteStructuredDataReference(long collectionId, long dataObjectId, long structuredDataReferenceId);

	@Tag(name = Constants.STRUCTUREDDATA_REFERENCE)
	@Operation(description = "Get structured data payload")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = StructuredDataPayload.class))))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getStructuredDataPayload(long collectionId, long dataObjectId, long structuredDataId);

	@Tag(name = Constants.STRUCTUREDDATA_REFERENCE)
	@Operation(description = "Get a specific structured data payload")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(schema = @Schema(implementation = StructuredDataPayload.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getSpecificStructuredDataPayload(long collectionId, long dataObjectId, long structuredDataId, String oid);

}
