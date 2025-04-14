package de.dlr.shepard.context.references.timeseriesreference.endpoints;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.references.timeseriesreference.io.MetricsIO;
import de.dlr.shepard.context.references.timeseriesreference.services.TimeseriesReferenceMetricsService;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.enums.AggregateFunction;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(
  Constants.COLLECTIONS +
  "/{" +
  Constants.COLLECTION_ID +
  "}/" +
  Constants.DATA_OBJECTS +
  "/{" +
  Constants.DATA_OBJECT_ID +
  "}/" +
  Constants.TIMESERIES_REFERENCES
)
@RequestScoped
public class TimeseriesReferenceMetricsRest {

  private TimeseriesReferenceMetricsService timeseriesReferenceMetricsService;

  @Context
  private SecurityContext securityContext;

  @Inject
  public TimeseriesReferenceMetricsRest(TimeseriesReferenceMetricsService timeseriesReferenceMetricsService) {
    this.timeseriesReferenceMetricsService = timeseriesReferenceMetricsService;
  }

  @GET
  @Path("/{" + Constants.TIMESERIES_REFERENCE_ID + "}" + "/" + Constants.METRICS)
  @Tag(name = Constants.TIMESERIES_REFERENCE)
  @Operation(description = "Get timeseries reference metrics by reference id.")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = MetricsIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.TIMESERIES_REFERENCE_ID)
  @Parameter(name = Constants.MEASUREMENT, required = true)
  @Parameter(name = Constants.DEVICE, required = true)
  @Parameter(name = Constants.LOCATION, required = true)
  @Parameter(name = Constants.SYMBOLICNAME, required = true)
  @Parameter(name = Constants.FIELD, required = true)
  @Parameter(name = Constants.VERSION_UID)
  public Response getMetricsOfTimeseriesReference(
    @PathParam(Constants.COLLECTION_ID) @NotNull @PositiveOrZero long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) @NotNull @PositiveOrZero long dataObjectId,
    @PathParam(Constants.TIMESERIES_REFERENCE_ID) @NotNull @PositiveOrZero long timeseriesReferenceId,
    @QueryParam(Constants.MEASUREMENT) String measurement,
    @QueryParam(Constants.DEVICE) String device,
    @QueryParam(Constants.LOCATION) String location,
    @QueryParam(Constants.SYMBOLICNAME) String symbolicName,
    @QueryParam(Constants.FIELD) String field,
    @QueryParam(Constants.VERSION_UID) UUID versionUID
  ) {
    Timeseries timeseries = new Timeseries(measurement, device, location, symbolicName, field);

    List<MetricsIO> result = timeseriesReferenceMetricsService.getTimeseriesReferenceMetrics(
      collectionId,
      dataObjectId,
      timeseriesReferenceId,
      versionUID,
      timeseries
    );

    return Response.ok(result).build();
  }

  @GET
  @Path("/{" + Constants.TIMESERIES_REFERENCE_ID + "}" + "/" + Constants.METRIC)
  @Tag(name = Constants.TIMESERIES_REFERENCE)
  @Operation(description = "Get timeseries reference metric by id.")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = MetricsIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.TIMESERIES_REFERENCE_ID)
  @Parameter(name = Constants.MEASUREMENT, required = true)
  @Parameter(name = Constants.DEVICE, required = true)
  @Parameter(name = Constants.LOCATION, required = true)
  @Parameter(name = Constants.SYMBOLICNAME, required = true)
  @Parameter(name = Constants.FIELD, required = true)
  @Parameter(name = Constants.FUNCTION, required = true)
  @Parameter(name = Constants.VERSION_UID)
  public Response getMetricOfTimeseriesReference(
    @PathParam(Constants.COLLECTION_ID) @NotNull @PositiveOrZero long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) @NotNull @PositiveOrZero long dataObjectId,
    @PathParam(Constants.TIMESERIES_REFERENCE_ID) @NotNull @PositiveOrZero long timeseriesReferenceId,
    @QueryParam(Constants.MEASUREMENT) String measurement,
    @QueryParam(Constants.DEVICE) String device,
    @QueryParam(Constants.LOCATION) String location,
    @QueryParam(Constants.SYMBOLICNAME) String symbolicName,
    @QueryParam(Constants.FIELD) String field,
    @QueryParam(Constants.FUNCTION) AggregateFunction function,
    @QueryParam(Constants.VERSION_UID) UUID versionUID
  ) {
    Timeseries timeseries = new Timeseries(measurement, device, location, symbolicName, field);

    MetricsIO result = timeseriesReferenceMetricsService
      .getTimeseriesReferenceMetrics(
        collectionId,
        dataObjectId,
        timeseriesReferenceId,
        versionUID,
        timeseries,
        List.of(function)
      )
      .get(0);

    return Response.ok(result).build();
  }
}
