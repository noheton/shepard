package de.dlr.shepard.endpoints;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import de.dlr.shepard.neo4Core.io.BasicReferenceIO;
import de.dlr.shepard.util.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

public interface BasicReferenceRest {

	@GET
	@Tag(name = Constants.BASIC_REFERENCE)
	@Operation(description = "Get all references")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = BasicReferenceIO.class))))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getAllReferences(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId, @QueryParam(Constants.QP_NAME) String name,
			@QueryParam(Constants.QP_PAGE) Integer page, @QueryParam(Constants.QP_SIZE) Integer size);

	@GET
	@Path("/{" + Constants.BASIC_REFERENCE_ID + "}")
	@Tag(name = Constants.BASIC_REFERENCE)
	@Operation(description = "Get reference")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(schema = @Schema(implementation = BasicReferenceIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getBasicReference(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.BASIC_REFERENCE_ID) long referenceId);

	@DELETE
	@Path("/{" + Constants.BASIC_REFERENCE_ID + "}")
	@Tag(name = Constants.BASIC_REFERENCE)
	@Operation(description = "Delete reference")
	@ApiResponse(description = "deleted", responseCode = "204")
	@ApiResponse(description = "not found", responseCode = "404")
	Response deleteBasicReference(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.BASIC_REFERENCE_ID) long basicReferenceId);

}
