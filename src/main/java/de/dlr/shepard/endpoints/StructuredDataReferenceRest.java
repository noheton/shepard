package de.dlr.shepard.endpoints;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

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

public interface StructuredDataReferenceRest {

	@GET
	@Tag(name = Constants.STRUCTUREDDATA_REFERENCE)
	@Operation(description = "Get all structureddata references")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = StructuredDataReferenceIO.class))))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getAllStructuredDataReferences(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId);

	@GET
	@Path("/{" + Constants.STRUCTUREDDATA_REFERENCE_ID + "}")
	@Tag(name = Constants.STRUCTUREDDATA_REFERENCE)
	@Operation(description = "Get structureddata reference")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(schema = @Schema(implementation = StructuredDataReferenceIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getStructuredDataReference(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.STRUCTUREDDATA_REFERENCE_ID) long referenceId);

	@POST
	@Tag(name = Constants.STRUCTUREDDATA_REFERENCE)
	@Operation(description = "Create a new structureddata reference")
	@ApiResponse(description = "created", responseCode = "201", content = @Content(schema = @Schema(implementation = StructuredDataReferenceIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response createStructuredDataReference(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@RequestBody(required = true, content = @Content(schema = @Schema(implementation = StructuredDataReferenceIO.class))) StructuredDataReferenceIO structuredDataReference)
			throws InvalidBodyException;

	@DELETE
	@Path("/{" + Constants.STRUCTUREDDATA_REFERENCE_ID + "}")
	@Tag(name = Constants.STRUCTUREDDATA_REFERENCE)
	@Operation(description = "Delete structureddata reference")
	@ApiResponse(description = "deleted", responseCode = "204")
	@ApiResponse(description = "not found", responseCode = "404")
	Response deleteBasicReference(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.STRUCTUREDDATA_REFERENCE_ID) long structuredDataReferenceId);

	@GET
	@Path("/{" + Constants.STRUCTUREDDATA_REFERENCE_ID + "}/payload")
	@Tag(name = Constants.STRUCTUREDDATA_REFERENCE)
	@Operation(description = "Get structured data payload")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = StructuredDataPayload.class))))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getStructuredDataPayload(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.STRUCTUREDDATA_REFERENCE_ID) long structuredDataId);

}
