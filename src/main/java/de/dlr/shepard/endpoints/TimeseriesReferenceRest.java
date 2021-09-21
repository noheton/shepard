package de.dlr.shepard.endpoints;

import java.util.Set;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.influxDB.AggregateFunction;
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
import jakarta.ws.rs.core.Response;

public interface TimeseriesReferenceRest {

	@Tag(name = Constants.TIMESERIES_REFERENCE)
	@Operation(description = "Get all timeseries references")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TimeseriesReferenceIO.class))))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getAllTimeseriesReferences(long collectionId, long dataObjectId);

	@Tag(name = Constants.TIMESERIES_REFERENCE)
	@Operation(description = "Get timeseries reference")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(schema = @Schema(implementation = TimeseriesReferenceIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getTimeseriesReference(long collectionId, long dataObjectId, long timeseriesId);

	@Tag(name = Constants.TIMESERIES_REFERENCE)
	@Operation(description = "Create a new timeseries reference")
	@ApiResponse(description = "created", responseCode = "201", content = @Content(schema = @Schema(implementation = TimeseriesReferenceIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response createTimeseriesReference(long collectionId, long dataObjectId,
			@RequestBody(required = true, content = @Content(schema = @Schema(implementation = TimeseriesReferenceIO.class))) TimeseriesReferenceIO timeseriesReference)
			throws InvalidBodyException;

	@Tag(name = Constants.TIMESERIES_REFERENCE)
	@Operation(description = "Delete timeseries reference")
	@ApiResponse(description = "deleted", responseCode = "204")
	@ApiResponse(description = "not found", responseCode = "404")
	Response deleteTimeseriesReference(long collectionId, long dataObjectId, long timeseriesId);

	@Tag(name = Constants.TIMESERIES_REFERENCE)
	@Operation(description = "Get timeseries reference payload")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TimeseriesPayload.class))))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getTimeseriesPayload(long collectionId, long dataObjectId, long timeseriesId, AggregateFunction function,
			Long groupBy, Set<String> deviceFilterTag, Set<String> locationFilterTag,
			Set<String> symbolicNameFilterTag);

}
