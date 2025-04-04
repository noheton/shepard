package de.dlr.shepard.context.references.timeseriesreference.endpoints;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.references.timeseriesreference.services.TimeseriesReferenceService;
import de.dlr.shepard.data.timeseries.io.TimeseriesIO;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
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
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
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
  Constants.TIMESERIES_REFERENCES +
  "/{" +
  Constants.TIMESERIES_REFERENCE_ID +
  "}/metrics"
)
@RequestScoped
public class TimeseriesReferenceMetricsRest {

  private TimeseriesReferenceService timeseriesReferenceService;

  @Context
  private SecurityContext securityContext;

  @Inject
  public TimeseriesReferenceMetricsRest(
    TimeseriesReferenceService timeseriesReferenceService,
    TimeseriesService timeseriesService
  ) {
    this.timeseriesReferenceService = timeseriesReferenceService;
  }

  @GET
  @Tag(name = Constants.TIMESERIES_REFERENCE)
  @Operation(description = "Get timeseries by id.")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = TimeseriesIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.TIMESERIES_REFERENCE_ID)
  @Parameter(name = Constants.TIMESERIES_ID)
  @Parameter(name = Constants.MEASUREMENT)
  @Parameter(name = Constants.DEVICE)
  @Parameter(name = Constants.LOCATION)
  @Parameter(name = Constants.SYMBOLICNAME)
  @Parameter(name = Constants.FIELD)
  @Parameter(name = Constants.VERSION_UID)
  public Response getMetricsOfTimeseriesReference(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) long dataObjectId,
    @PathParam(Constants.TIMESERIES_REFERENCE_ID) long timeseriesReferenceId,
    @PathParam(Constants.TIMESERIES_ID) int timeseriesId,
    @QueryParam(Constants.MEASUREMENT) String measurement,
    @QueryParam(Constants.DEVICE) String device,
    @QueryParam(Constants.LOCATION) String location,
    @QueryParam(Constants.SYMBOLICNAME) String symbolicName,
    @QueryParam(Constants.FIELD) String field,
    @QueryParam(Constants.VERSION_UID) UUID versionUID
  ) {
    var reference = timeseriesReferenceService.getReference(
      collectionId,
      dataObjectId,
      timeseriesReferenceId,
      versionUID
    );

    // Todo: Implementation for metrics will be done with #574
    Log.infof(
      "Measurement: %s, Device: %s, Location: %s, SymbolicName: %s, Field: %s",
      measurement,
      device,
      location,
      symbolicName,
      field
    );

    return Response.ok().build();
  }
}
