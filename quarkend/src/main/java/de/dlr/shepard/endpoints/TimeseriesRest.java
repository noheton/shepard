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
import jakarta.validation.Valid;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.jboss.resteasy.reactive.multipart.FileUpload;

public interface TimeseriesRest {
  @Tag(name = Constants.TIMESERIES)
  @Operation(description = "Get all timeseries containers")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = TimeseriesContainerIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getAllTimeseriesContainers(
    String name,
    Integer page,
    Integer size,
    ContainerAttributes orderAttribute,
    Boolean orderDesc
  );

  @Tag(name = Constants.TIMESERIES)
  @Operation(description = "Get timeseries container")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = TimeseriesContainerIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getTimeseriesContainer(long timeseriesContainerId);

  @Tag(name = Constants.TIMESERIES)
  @Operation(description = "Create a new timeseries container")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = TimeseriesContainerIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response createTimeseriesContainer(
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = TimeseriesContainerIO.class))
    ) @Valid TimeseriesContainerIO timeseriesContainer
  );

  @Tag(name = Constants.TIMESERIES)
  @Operation(description = "Delete timeseries container")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  Response deleteTimeseriesContainer(long timeseriesContainerId);

  @Tag(name = Constants.TIMESERIES)
  @Operation(description = "Upload timeseries to container")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = Timeseries.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response createTimeseries(
    long timeseriesId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = TimeseriesPayload.class))
    ) @Valid TimeseriesPayload payload
  );

  @Tag(name = Constants.TIMESERIES)
  @Operation(description = "Get timeseries available")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = Timeseries.class))
  )
  Response getTimeseriesAvailable(long timeseriesContainerId);

  @Tag(name = Constants.TIMESERIES)
  @Operation(description = "Get timeseries payload")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = TimeseriesPayload.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
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
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(
      mediaType = MediaType.APPLICATION_OCTET_STREAM,
      schema = @Schema(type = SchemaType.STRING, format = "binary")
    )
  )
  @APIResponse(description = "not found", responseCode = "404")
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
  @APIResponse(description = "ok", responseCode = "200")
  @APIResponse(description = "not found", responseCode = "404")
  Response importTimeseries(
    long timeseriesContainerId,
    @Parameter(
      required = true,
      schema = @Schema(type = SchemaType.STRING, format = "binary", description = "Timeseries as CSV")
    ) InputStream fileInputStream,
    @Parameter(hidden = true) FileUpload fileUpload
  ) throws IOException;

  @Tag(name = Constants.TIMESERIES)
  @Operation(description = "Get permissions")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = PermissionsIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getTimeseriesPermissions(long timeseriesContainerId);

  @Tag(name = Constants.TIMESERIES)
  @Operation(description = "Edit permissions")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = PermissionsIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response editTimeseriesPermissions(
    long timeseriesContainerId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = PermissionsIO.class))
    ) @Valid PermissionsIO permissions
  );

  @Tag(name = Constants.TIMESERIES)
  @Operation(description = "Get roles")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = RolesIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getTimeseriesRoles(long timeseriesContainerId);
}
