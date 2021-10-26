package de.dlr.shepard.endpoints;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.mongoDB.StructuredData;
import de.dlr.shepard.mongoDB.StructuredDataPayload;
import de.dlr.shepard.neo4Core.io.StructuredDataContainerIO;
import de.dlr.shepard.neo4Core.orderBy.ContainerAttributes;
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

public interface StructuredDataRest {

	@Tag(name = Constants.STRUCTUREDDATA)
	@Operation(description = "Get all structured data containers")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = StructuredDataContainerIO.class))))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getAllStructuredDataContainers(String name, Integer page, Integer size, ContainerAttributes orderAttribute,
			Boolean orderDesc);

	@Tag(name = Constants.STRUCTUREDDATA)
	@Operation(description = "Get structured data container")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(schema = @Schema(implementation = StructuredDataContainerIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getStructuredDataContainer(long structuredDataId);

	@Tag(name = Constants.STRUCTUREDDATA)
	@Operation(description = "Delete structured data container")
	@ApiResponse(description = "deleted", responseCode = "204")
	@ApiResponse(description = "not found", responseCode = "404")
	Response deleteStructuredDataContainer(long structuredDataId);

	@Tag(name = Constants.STRUCTUREDDATA)
	@Operation(description = "Create a new structured data container")
	@ApiResponse(description = "created", responseCode = "201", content = @Content(schema = @Schema(implementation = StructuredDataContainerIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response createStructuredDataContainer(
			@RequestBody(required = true, content = @Content(schema = @Schema(implementation = StructuredDataContainerIO.class))) @Valid StructuredDataContainerIO structuredDataContainer);

	@Tag(name = Constants.STRUCTUREDDATA)
	@Operation(description = "Upload a new structured data object")
	@ApiResponse(description = "created", responseCode = "201", content = @Content(schema = @Schema(implementation = StructuredData.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response createStructuredData(long structuredDataId,
			@RequestBody(required = true, content = @Content(schema = @Schema(implementation = StructuredDataPayload.class))) @Valid StructuredDataPayload payload)
			throws InvalidBodyException;

	@Tag(name = Constants.STRUCTUREDDATA)
	@Operation(description = "Get structured data objects")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = StructuredData.class))))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getAllStructuredDatas(long structuredDataId);

	@Tag(name = Constants.STRUCTUREDDATA)
	@Operation(description = "Download structured data")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(schema = @Schema(implementation = StructuredDataPayload.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getStructuredData(long structuredDataId, String oid);

	@Tag(name = Constants.STRUCTUREDDATA)
	@Operation(description = "Delete structured data")
	@ApiResponse(description = "ok", responseCode = "204")
	@ApiResponse(description = "not found", responseCode = "404")
	Response deleteStructuredData(long structuredDataId, String oid);

}
