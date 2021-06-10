package de.dlr.shepard.endpoints;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.neo4Core.io.URIReferenceIO;
import de.dlr.shepard.util.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

public interface URIReferenceRest {

	@GET
	@Tag(name = Constants.URI_REFERENCE)
	@Operation(description = "Get all uri references")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = URIReferenceIO.class))))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getAllReferences(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId);

	@GET
	@Path("/{" + Constants.URI_REFERENCE_ID + "}")
	@Tag(name = Constants.URI_REFERENCE)
	@Operation(description = "Get uri reference")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(schema = @Schema(implementation = URIReferenceIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getUriReference(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.URI_REFERENCE_ID) long referenceId);

	@POST
	@Tag(name = Constants.URI_REFERENCE)
	@Operation(description = "Create a new uri reference")
	@ApiResponse(description = "created", responseCode = "201", content = @Content(schema = @Schema(implementation = URIReferenceIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response createUriReference(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@RequestBody(required = true, content = @Content(schema = @Schema(implementation = URIReferenceIO.class))) URIReferenceIO timeseriesReference)
			throws InvalidBodyException;

	@DELETE
	@Path("/{" + Constants.URI_REFERENCE_ID + "}")
	@Tag(name = Constants.URI_REFERENCE)
	@Operation(description = "Delete uri reference")
	@ApiResponse(description = "deleted", responseCode = "204")
	@ApiResponse(description = "not found", responseCode = "404")
	Response deleteUriReference(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.URI_REFERENCE_ID) long referenceId);

}
