package de.dlr.shepard.endpoints;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import de.dlr.shepard.mongoDB.StructuredData;
import de.dlr.shepard.mongoDB.StructuredDataPayload;
import de.dlr.shepard.neo4Core.io.StructuredDataContainerIO;
import de.dlr.shepard.util.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

public interface StructuredDataRest {

	@GET
	@Tag(name = Constants.STRUCTUREDDATA)
	@Operation(description = "Get all structured data containers")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = StructuredDataContainerIO.class))))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getAllStructuredDataContainer();

	@GET
	@Path("/{" + Constants.STRUCTUREDDATA_CONTAINER_ID + "}")
	@Tag(name = Constants.STRUCTUREDDATA)
	@Operation(description = "Get structured data container")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(schema = @Schema(implementation = StructuredDataContainerIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getStructuredDataContainer(@PathParam(Constants.STRUCTUREDDATA_CONTAINER_ID) long structuredDataId);

	@POST
	@Tag(name = Constants.STRUCTUREDDATA)
	@Operation(description = "Create a new structured data container")
	@ApiResponse(description = "created", responseCode = "201", content = @Content(schema = @Schema(implementation = StructuredDataContainerIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response createStructuredDataContainer(
			@RequestBody(required = true, content = @Content(schema = @Schema(implementation = StructuredDataContainerIO.class))) StructuredDataContainerIO structuredDataContainer);

	@POST
	@Path("/{" + Constants.STRUCTUREDDATA_CONTAINER_ID + "}/payload")
	@Tag(name = Constants.STRUCTUREDDATA)
	@Operation(description = "Upload a new structured data object")
	@ApiResponse(description = "created", responseCode = "201", content = @Content(schema = @Schema(implementation = StructuredData.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response createStructuredData(@PathParam(Constants.STRUCTUREDDATA_CONTAINER_ID) long structuredDataId,
			@RequestBody(required = true, content = @Content(schema = @Schema(implementation = StructuredDataPayload.class))) StructuredDataPayload payload);

	@DELETE
	@Path("/{" + Constants.STRUCTUREDDATA_CONTAINER_ID + "}")
	@Tag(name = Constants.STRUCTUREDDATA)
	@Operation(description = "Delete structured data container")
	@ApiResponse(description = "deleted", responseCode = "204")
	@ApiResponse(description = "not found", responseCode = "404")
	Response deleteStructuredDataContainer(@PathParam(Constants.STRUCTUREDDATA_CONTAINER_ID) long structuredDataId);

}
