package de.dlr.shepard.endpoints;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.neo4Core.io.AbstractDataObjectIO;
import de.dlr.shepard.neo4Core.io.DataObjectReferenceIO;
import de.dlr.shepard.util.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

public interface DataObjectReferenceRest {

	@GET
	@Tag(name = Constants.DATAOBJECT_REFERENCE)
	@Operation(description = "Get all dataObject references")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = DataObjectReferenceIO.class))))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getAllDataObjectReferences(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId);

	@GET
	@Path("/{" + Constants.DATAOBJECT_REFERENCE_ID + "}")
	@Tag(name = Constants.DATAOBJECT_REFERENCE)
	@Operation(description = "Get dataObject reference")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(schema = @Schema(implementation = DataObjectReferenceIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getDataObjectReference(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.DATAOBJECT_REFERENCE_ID) long dataObjectReferenceId);

	@POST
	@Tag(name = Constants.DATAOBJECT_REFERENCE)
	@Operation(description = "Create a new dataObject reference")
	@ApiResponse(description = "created", responseCode = "201", content = @Content(schema = @Schema(implementation = DataObjectReferenceIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response createDataObjectReference(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@RequestBody(required = true, content = @Content(schema = @Schema(implementation = DataObjectReferenceIO.class))) DataObjectReferenceIO dataObjectReference)
			throws InvalidBodyException;

	@DELETE
	@Path("/{" + Constants.DATAOBJECT_REFERENCE_ID + "}")
	@Tag(name = Constants.DATAOBJECT_REFERENCE)
	@Operation(description = "Delete dataObject reference")
	@ApiResponse(description = "deleted", responseCode = "204")
	@ApiResponse(description = "not found", responseCode = "404")
	Response deleteDataObjectReference(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.DATAOBJECT_REFERENCE_ID) long dataObjectReferenceId);

	@GET
	@Path("/{" + Constants.DATAOBJECT_REFERENCE_ID + "}/payload")
	@Tag(name = Constants.DATAOBJECT_REFERENCE)
	@Operation(description = "Get dataObject reference payload")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(schema = @Schema(implementation = AbstractDataObjectIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getDataObjectReferencePayload(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.DATAOBJECT_REFERENCE_ID) long dataObjectReferenceId);
}
