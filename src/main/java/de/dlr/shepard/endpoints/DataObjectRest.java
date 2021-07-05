package de.dlr.shepard.endpoints;

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

	@Tag(name = Constants.DATAOBJECT)
	@Operation(description = "Get all dataObjects")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = DataObjectIO.class))))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getAllDataObjects(long collectionId, String name, Integer page, Integer size, Long parentId);

	@Tag(name = Constants.DATAOBJECT)
	@Operation(description = "Get dataObject")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(schema = @Schema(implementation = DataObjectIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getDataObject(long collectionId, long dataObjectId);

	@Tag(name = Constants.DATAOBJECT)
	@Operation(description = "Create a new dataObject")
	@ApiResponse(description = "created", responseCode = "201", content = @Content(schema = @Schema(implementation = DataObjectIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response createDataObject(long collectionId,
			@RequestBody(required = true, content = @Content(schema = @Schema(implementation = DataObjectIO.class))) DataObjectIO dataObject)
			throws InvalidBodyException;

	@Tag(name = Constants.DATAOBJECT)
	@Operation(description = "Update dataObject")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(schema = @Schema(implementation = DataObjectIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response updateDataObject(long collectionId, long dataObjectId,
			@RequestBody(required = true, content = @Content(schema = @Schema(implementation = DataObjectIO.class))) DataObjectIO dataObject)
			throws InvalidBodyException;

	@Tag(name = Constants.DATAOBJECT)
	@Operation(description = "Delete dataObject")
	@ApiResponse(description = "deleted", responseCode = "204")
	@ApiResponse(description = "not found", responseCode = "404")
	Response deleteDataObject(long collectionId, long dataObjectId);

}
