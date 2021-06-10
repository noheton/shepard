package de.dlr.shepard.endpoints;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import de.dlr.shepard.influxDB.Timeseries;
import de.dlr.shepard.influxDB.TimeseriesPayload;
import de.dlr.shepard.neo4Core.io.TimeseriesContainerIO;
import de.dlr.shepard.util.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

public interface TimeseriesRest {

	@GET
	@Tag(name = Constants.TIMESERIES)
	@Operation(description = "Get all timeseries containers")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TimeseriesContainerIO.class))))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getAllTimeseriesContainer();

	@GET
	@Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}")
	@Tag(name = Constants.TIMESERIES)
	@Operation(description = "Get timeseries container")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(schema = @Schema(implementation = TimeseriesContainerIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getTimeseriesContainer(@PathParam(Constants.TIMESERIES_CONTAINER_ID) long timeseriesId);

	@POST
	@Tag(name = Constants.TIMESERIES)
	@Operation(description = "Create a new timeseries container")
	@ApiResponse(description = "created", responseCode = "201", content = @Content(schema = @Schema(implementation = TimeseriesContainerIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response createTimeseriesContainer(
			@RequestBody(required = true, content = @Content(schema = @Schema(implementation = TimeseriesContainerIO.class))) TimeseriesContainerIO timeseriesContainer);

	@DELETE
	@Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}")
	@Tag(name = Constants.TIMESERIES)
	@Operation(description = "Delete timeseries container")
	@ApiResponse(description = "deleted", responseCode = "204")
	@ApiResponse(description = "not found", responseCode = "404")
	Response deleteTimeseriesContainer(@PathParam(Constants.TIMESERIES_CONTAINER_ID) long timeseriesId);

	@POST
	@Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/payload")
	@Tag(name = Constants.TIMESERIES)
	@Operation(description = "Upload timeseries to container")
	@ApiResponse(description = "created", responseCode = "201", content = @Content(schema = @Schema(implementation = Timeseries.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response createTimeseries(@PathParam(Constants.TIMESERIES_CONTAINER_ID) long timeseriesId,
			@RequestBody(required = true, content = @Content(schema = @Schema(implementation = TimeseriesPayload.class))) TimeseriesPayload payload);

}
