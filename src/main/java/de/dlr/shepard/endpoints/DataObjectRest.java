package de.dlr.shepard.endpoints;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.neo4Core.io.DataObjectIO;
import de.dlr.shepard.util.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

public interface DataObjectRest {

	@GET
	@Tag(name = Constants.DATAOBJECT)
	@Operation(description = "Get all dataObjects")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = DataObjectIO.class))))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getAllDataObjects(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@QueryParam(Constants.QP_NAME) String name, @QueryParam(Constants.QP_PAGE) Integer page,
			@QueryParam(Constants.QP_SIZE) Integer size, @QueryParam(Constants.QP_PARENT_ID) Long parentId);

	@GET
	@Path("/{" + Constants.DATAOBJECT_ID + "}")
	@Tag(name = Constants.DATAOBJECT)
	@Operation(description = "Get dataObject")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(schema = @Schema(implementation = DataObjectIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getDataObject(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId);

	@POST
	@Tag(name = Constants.DATAOBJECT)
	@Operation(description = "Create a new dataObject")
	@ApiResponse(description = "created", responseCode = "201", content = @Content(schema = @Schema(implementation = DataObjectIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response createDataObject(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@RequestBody(required = true, content = @Content(schema = @Schema(implementation = DataObjectIO.class))) DataObjectIO dataObject)
			throws InvalidBodyException;

	@PUT
	@Path("/{" + Constants.DATAOBJECT_ID + "}")
	@Tag(name = Constants.DATAOBJECT)
	@Operation(description = "Update dataObject")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(schema = @Schema(implementation = DataObjectIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response updateDataObject(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@RequestBody(required = true, content = @Content(schema = @Schema(implementation = DataObjectIO.class))) DataObjectIO dataObject)
			throws InvalidBodyException;

	@DELETE
	@Path("/{" + Constants.DATAOBJECT_ID + "}")
	@Tag(name = Constants.DATAOBJECT)
	@Operation(description = "Delete dataObject")
	@ApiResponse(description = "deleted", responseCode = "204")
	@ApiResponse(description = "not found", responseCode = "404")
	Response deleteDataObject(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId);

}
