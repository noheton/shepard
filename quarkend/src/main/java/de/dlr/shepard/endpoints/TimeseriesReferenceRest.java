package de.dlr.shepard.endpoints;

import de.dlr.shepard.influxDB.FillOption;
import de.dlr.shepard.influxDB.SingleValuedUnaryFunction;
import de.dlr.shepard.influxDB.TimeseriesPayload;
import de.dlr.shepard.neo4Core.io.TimeseriesReferenceIO;
import de.dlr.shepard.util.Constants;
import jakarta.validation.Valid;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.util.Set;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

public interface TimeseriesReferenceRest {
  @Tag(name = Constants.TIMESERIES_REFERENCE)
  @Operation(description = "Get all timeseries references")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = TimeseriesReferenceIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getAllTimeseriesReferences(long collectionId, long dataObjectId);

  @Tag(name = Constants.TIMESERIES_REFERENCE)
  @Operation(description = "Get timeseries reference")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = TimeseriesReferenceIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getTimeseriesReference(long collectionId, long dataObjectId, long timeseriesReferenceId);

  @Tag(name = Constants.TIMESERIES_REFERENCE)
  @Operation(description = "Create a new timeseries reference")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = TimeseriesReferenceIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response createTimeseriesReference(
    long collectionId,
    long dataObjectId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = TimeseriesReferenceIO.class))
    ) @Valid TimeseriesReferenceIO timeseriesReference
  );

  @Tag(name = Constants.TIMESERIES_REFERENCE)
  @Operation(description = "Delete timeseries reference")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  Response deleteTimeseriesReference(long collectionId, long dataObjectId, long timeseriesReferenceId);

  @Tag(name = Constants.TIMESERIES_REFERENCE)
  @Operation(description = "Get timeseries reference payload")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = TimeseriesPayload.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getTimeseriesPayload(
    long collectionId,
    long dataObjectId,
    long timeseriesReferenceId,
    SingleValuedUnaryFunction function,
    Long groupBy,
    FillOption fillOption,
    Set<String> deviceFilterTag,
    Set<String> locationFilterTag,
    Set<String> symbolicNameFilterTag
  );

  @Tag(name = Constants.TIMESERIES_REFERENCE)
  @Operation(description = "Export timeseries reference payload")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(
      mediaType = MediaType.APPLICATION_OCTET_STREAM,
      schema = @Schema(type = SchemaType.STRING, format = "binary")
    )
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response exportTimeseriesPayload(
    long collectionId,
    long dataObjectId,
    long timeseriesReferenceId,
    SingleValuedUnaryFunction function,
    Long groupBy,
    FillOption fillOption,
    Set<String> deviceFilterTag,
    Set<String> locationFilterTag,
    Set<String> symbolicNameFilterTag
  ) throws IOException;
}
