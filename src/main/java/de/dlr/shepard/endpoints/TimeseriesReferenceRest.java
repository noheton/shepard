package de.dlr.shepard.endpoints;

import java.util.Set;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.influxDB.TimeseriesPayload;
import de.dlr.shepard.neo4Core.io.TimeseriesReferenceIO;
import de.dlr.shepard.util.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

public interface TimeseriesReferenceRest {

	@GET
	@Tag(name = Constants.TIMESERIES_REFERENCE)
	@Operation(description = "Get all timeseries references")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TimeseriesReferenceIO.class))))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getAllTimeseriesReferences(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId);

	@GET
	@Path("/{" + Constants.TIMESERIES_REFERENCE_ID + "}")
	@Tag(name = Constants.TIMESERIES_REFERENCE)
	@Operation(description = "Get timeseries reference")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(schema = @Schema(implementation = TimeseriesReferenceIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getTimeseriesReference(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.TIMESERIES_REFERENCE_ID) long timeseriesId);

	@POST
	@Tag(name = Constants.TIMESERIES_REFERENCE)
	@Operation(description = "Create a new timeseries reference")
	@ApiResponse(description = "created", responseCode = "201", content = @Content(schema = @Schema(implementation = TimeseriesReferenceIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response createTimeseriesReference(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@RequestBody(required = true, content = @Content(schema = @Schema(implementation = TimeseriesReferenceIO.class))) TimeseriesReferenceIO timeseriesReference)
			throws InvalidBodyException;

	@DELETE
	@Path("/{" + Constants.TIMESERIES_REFERENCE_ID + "}")
	@Tag(name = Constants.TIMESERIES_REFERENCE)
	@Operation(description = "Delete timeseries reference")
	@ApiResponse(description = "deleted", responseCode = "204")
	@ApiResponse(description = "not found", responseCode = "404")
	Response deleteTimeseriesReference(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.TIMESERIES_REFERENCE_ID) long timeseriesId);

	@GET
	@Path("/{" + Constants.TIMESERIES_REFERENCE_ID + "}/payload")
	@Tag(name = Constants.TIMESERIES_REFERENCE)
	@Operation(description = "Get timeseries reference payload")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TimeseriesPayload.class))))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getTimeseriesPayload(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.TIMESERIES_REFERENCE_ID) long timeseriesId,
			@QueryParam(Constants.DEVICE) Set<String> deviceFilterTag,
			@QueryParam(Constants.LOCATION) Set<String> locationFilterTag,
			@QueryParam(Constants.SYMBOLICNAME) Set<String> symbolicNameFilterTag);

}
