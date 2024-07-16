package de.dlr.shepard.endpoints;

import de.dlr.shepard.influxDB.FillOption;
import de.dlr.shepard.influxDB.SingleValuedUnaryFunction;
import de.dlr.shepard.influxDB.Timeseries;
import de.dlr.shepard.influxDB.TimeseriesPayload;
import de.dlr.shepard.neo4Core.io.PermissionsIO;
import de.dlr.shepard.neo4Core.io.RolesIO;
import de.dlr.shepard.neo4Core.io.TimeseriesContainerIO;
import de.dlr.shepard.neo4Core.orderBy.ContainerAttributes;
import de.dlr.shepard.util.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;

public interface TimeseriesRest {
  @Tag(name = Constants.TIMESERIES)
  @Operation(description = "Get all timeseries containers")
  @ApiResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(array = @ArraySchema(schema = @Schema(implementation = TimeseriesContainerIO.class)))
  )
  @ApiResponse(description = "not found", responseCode = "404")
  Response getAllTimeseriesContainers(
    String name,
    Integer page,
    Integer size,
    ContainerAttributes orderAttribute,
    Boolean orderDesc
  );

  @Tag(name = Constants.TIMESERIES)
  @Operation(description = "Get timeseries container")
  @ApiResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = TimeseriesContainerIO.class))
  )
  @ApiResponse(description = "not found", responseCode = "404")
  Response getTimeseriesContainer(long timeseriesContainerId);

  @Tag(name = Constants.TIMESERIES)
  @Operation(description = "Create a new timeseries container")
  @ApiResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = TimeseriesContainerIO.class))
  )
  @ApiResponse(description = "not found", responseCode = "404")
  Response createTimeseriesContainer(
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = TimeseriesContainerIO.class))
    ) @Valid TimeseriesContainerIO timeseriesContainer
  );

  @Tag(name = Constants.TIMESERIES)
  @Operation(description = "Delete timeseries container")
  @ApiResponse(description = "deleted", responseCode = "204")
  @ApiResponse(description = "not found", responseCode = "404")
  Response deleteTimeseriesContainer(long timeseriesContainerId);

  @Tag(name = Constants.TIMESERIES)
  @Operation(description = "Upload timeseries to container")
  @ApiResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = Timeseries.class))
  )
  @ApiResponse(description = "not found", responseCode = "404")
  Response createTimeseries(
    long timeseriesId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = TimeseriesPayload.class))
    ) @Valid TimeseriesPayload payload
  );

  @Tag(name = Constants.TIMESERIES)
  @Operation(description = "Get timeseries available")
  @ApiResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(array = @ArraySchema(schema = @Schema(implementation = Timeseries.class)))
  )
  Response getTimeseriesAvailable(long timeseriesContainerId);

  @Tag(name = Constants.TIMESERIES)
  @Operation(description = "Get timeseries payload")
  @ApiResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = TimeseriesPayload.class))
  )
  @ApiResponse(description = "not found", responseCode = "404")
  Response getTimeseries(
    long timeseriesContainerId,
    String measurement,
    String location,
    String device,
    String symbolicName,
    String field,
    long start,
    long end,
    SingleValuedUnaryFunction function,
    Long groupBy,
    FillOption fillOption
  );

  @Tag(name = Constants.TIMESERIES)
  @Operation(description = "Export timeseries payload")
  @ApiResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(
      mediaType = MediaType.APPLICATION_OCTET_STREAM,
      schema = @Schema(type = "string", format = "binary")
    )
  )
  @ApiResponse(description = "not found", responseCode = "404")
  Response exportTimeseries(
    long timeseriesContainerId,
    String measurement,
    String location,
    String device,
    String symbolicName,
    String field,
    long start,
    long end,
    SingleValuedUnaryFunction function,
    Long groupBy,
    FillOption fillOption
  ) throws IOException;

  @Tag(name = Constants.TIMESERIES)
  @Operation(description = "Import timeseries payload")
  @ApiResponse(description = "ok", responseCode = "200")
  @ApiResponse(description = "not found", responseCode = "404")
  Response importTimeseries(
    long timeseriesContainerId,
    @Parameter(
      required = true,
      schema = @Schema(type = "string", format = "binary", description = "Timeseries as CSV")
    ) InputStream fileInputStream,
    @Parameter(hidden = true) FormDataContentDisposition fileMetaData
  ) throws IOException;

  @Tag(name = Constants.TIMESERIES)
  @Operation(description = "Get permissions")
  @ApiResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = PermissionsIO.class))
  )
  @ApiResponse(description = "not found", responseCode = "404")
  Response getTimeseriesPermissions(long timeseriesContainerId);

  @Tag(name = Constants.TIMESERIES)
  @Operation(description = "Edit permissions")
  @ApiResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = PermissionsIO.class))
  )
  @ApiResponse(description = "not found", responseCode = "404")
  Response editTimeseriesPermissions(
    long timeseriesContainerId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = PermissionsIO.class))
    ) @Valid PermissionsIO permissions
  );

  @Tag(name = Constants.TIMESERIES)
  @Operation(description = "Get roles")
  @ApiResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = RolesIO.class))
  )
  @ApiResponse(description = "not found", responseCode = "404")
  Response getTimeseriesRoles(long timeseriesContainerId);
}
