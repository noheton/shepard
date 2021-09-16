package de.dlr.shepard.endpoints;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.neo4Core.io.DataObjectIO;
import de.dlr.shepard.neo4Core.io.DataObjectReferenceIO;
import de.dlr.shepard.util.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.core.Response;

public interface DataObjectReferenceRest {

	@Tag(name = Constants.DATAOBJECT_REFERENCE)
	@Operation(description = "Get all dataObject references")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = DataObjectReferenceIO.class))))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getAllDataObjectReferences(long collectionId, long dataObjectId);

	@Tag(name = Constants.DATAOBJECT_REFERENCE)
	@Operation(description = "Get dataObject reference")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(schema = @Schema(implementation = DataObjectReferenceIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getDataObjectReference(long collectionId, long dataObjectId, long dataObjectReferenceId);

	@Tag(name = Constants.DATAOBJECT_REFERENCE)
	@Operation(description = "Create a new dataObject reference")
	@ApiResponse(description = "created", responseCode = "201", content = @Content(schema = @Schema(implementation = DataObjectReferenceIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response createDataObjectReference(long collectionId, long dataObjectId,
			@RequestBody(required = true, content = @Content(schema = @Schema(implementation = DataObjectReferenceIO.class))) DataObjectReferenceIO dataObjectReference)
			throws InvalidBodyException;

	@Tag(name = Constants.DATAOBJECT_REFERENCE)
	@Operation(description = "Delete dataObject reference")
	@ApiResponse(description = "deleted", responseCode = "204")
	@ApiResponse(description = "not found", responseCode = "404")
	Response deleteDataObjectReference(long collectionId, long dataObjectId, long dataObjectReferenceId);

	@Tag(name = Constants.DATAOBJECT_REFERENCE)
	@Operation(description = "Get dataObject reference payload")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(schema = @Schema(implementation = DataObjectIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getDataObjectReferencePayload(long collectionId, long dataObjectId, long dataObjectReferenceId);
}
