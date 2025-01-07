package de.dlr.shepard.context.references.timeseriesreference.endpoints;

import de.dlr.shepard.common.filters.Subscribable;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.references.timeseriesreference.io.TimeseriesReferenceIO;
import de.dlr.shepard.context.references.timeseriesreference.services.TimeseriesReferenceService;
import de.dlr.shepard.data.timeseries.io.TimeseriesWithDataPoints;
import de.dlr.shepard.data.timeseries.model.enums.AggregateFunction;
import de.dlr.shepard.data.timeseries.model.enums.FillOption;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
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
public class TimeseriesReferenceRest {

  private TimeseriesReferenceService timeseriesReferenceService;

  @Context
  private SecurityContext securityContext;

  TimeseriesReferenceRest() {}

  @Inject
  public TimeseriesReferenceRest(TimeseriesReferenceService timeseriesReferenceService) {
    this.timeseriesReferenceService = timeseriesReferenceService;
  }

  @GET
  @Tag(name = Constants.TIMESERIES_REFERENCE)
  @Operation(description = "Get all timeseries references")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = TimeseriesReferenceIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  public Response getAllTimeseriesReferences(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) long dataObjectId
  ) {
    var references = timeseriesReferenceService.getAllReferencesByDataObjectShepardId(dataObjectId);
    var result = new ArrayList<TimeseriesReferenceIO>(references.size());
    for (var reference : references) {
      result.add(new TimeseriesReferenceIO(reference));
    }

    return Response.ok(result).build();
  }

  @GET
  @Path("/{" + Constants.TIMESERIES_REFERENCE_ID + "}")
  @Tag(name = Constants.TIMESERIES_REFERENCE)
  @Operation(description = "Get timeseries reference")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = TimeseriesReferenceIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.TIMESERIES_REFERENCE_ID)
  public Response getTimeseriesReference(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) long dataObjectId,
    @PathParam(Constants.TIMESERIES_REFERENCE_ID) long timeseriesId
  ) {
    var result = timeseriesReferenceService.getReferenceByShepardId(timeseriesId);

    return Response.ok(new TimeseriesReferenceIO(result)).build();
  }

  @POST
  @Subscribable
  @Tag(name = Constants.TIMESERIES_REFERENCE)
  @Operation(description = "Create a new timeseries reference")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = TimeseriesReferenceIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  public Response createTimeseriesReference(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) long dataObjectId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = TimeseriesReferenceIO.class))
    ) @Valid TimeseriesReferenceIO timeseriesReference
  ) {
    var result = timeseriesReferenceService.createReferenceByShepardId(
      dataObjectId,
      timeseriesReference,
      securityContext.getUserPrincipal().getName()
    );

    return Response.ok(new TimeseriesReferenceIO(result)).status(Status.CREATED).build();
  }

  @DELETE
  @Path("/{" + Constants.TIMESERIES_REFERENCE_ID + "}")
  @Subscribable
  @Tag(name = Constants.TIMESERIES_REFERENCE)
  @Operation(description = "Delete timeseries reference")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.TIMESERIES_REFERENCE_ID)
  public Response deleteTimeseriesReference(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) long dataObjectId,
    @PathParam(Constants.TIMESERIES_REFERENCE_ID) long timeseriesId
  ) {
    var result = timeseriesReferenceService.deleteReferenceByShepardId(
      timeseriesId,
      securityContext.getUserPrincipal().getName()
    );

    return result ? Response.status(Status.NO_CONTENT).build() : Response.status(Status.INTERNAL_SERVER_ERROR).build();
  }

  @GET
  @Path("/{" + Constants.TIMESERIES_REFERENCE_ID + "}/" + Constants.PAYLOAD)
  @Tag(name = Constants.TIMESERIES_REFERENCE)
  @Operation(description = "Get timeseries reference payload")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = TimeseriesWithDataPoints.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.TIMESERIES_REFERENCE_ID)
  @Parameter(name = Constants.FUNCTION)
  @Parameter(name = Constants.GROUP_BY)
  @Parameter(name = Constants.FILLOPTION)
  @Parameter(name = Constants.DEVICE)
  @Parameter(name = Constants.LOCATION)
  @Parameter(name = Constants.SYMBOLICNAME)
  public Response getTimeseriesPayload(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) long dataObjectId,
    @PathParam(Constants.TIMESERIES_REFERENCE_ID) long timeseriesReferenceId,
    @QueryParam(Constants.FUNCTION) AggregateFunction function,
    @QueryParam(Constants.GROUP_BY) Long groupBy,
    @QueryParam(Constants.FILLOPTION) FillOption fillOption,
    @QueryParam(Constants.DEVICE) Set<String> deviceFilterTag,
    @QueryParam(Constants.LOCATION) Set<String> locationFilterTag,
    @QueryParam(Constants.SYMBOLICNAME) Set<String> symbolicNameFilterTag
  ) {
    List<TimeseriesWithDataPoints> timeseriesWithDataPointsList =
      timeseriesReferenceService.getReferencedTimeseriesWithDataPointsList(
        timeseriesReferenceId,
        function,
        groupBy,
        fillOption,
        deviceFilterTag,
        locationFilterTag,
        symbolicNameFilterTag,
        securityContext.getUserPrincipal().getName()
      );
    return Response.ok(timeseriesWithDataPointsList).build();
  }

  @GET
  @Path("/{" + Constants.TIMESERIES_REFERENCE_ID + "}/" + Constants.EXPORT)
  @Produces({ MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_JSON })
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
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.TIMESERIES_REFERENCE_ID)
  @Parameter(name = Constants.FUNCTION)
  @Parameter(name = Constants.GROUP_BY)
  @Parameter(name = Constants.FILLOPTION)
  @Parameter(name = Constants.DEVICE)
  @Parameter(name = Constants.LOCATION)
  @Parameter(name = Constants.SYMBOLICNAME)
  public Response exportTimeseriesPayload(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) long dataObjectId,
    @PathParam(Constants.TIMESERIES_REFERENCE_ID) long timeseriesReferenceId,
    @QueryParam(Constants.FUNCTION) AggregateFunction function,
    @QueryParam(Constants.GROUP_BY) Long groupBy,
    @QueryParam(Constants.FILLOPTION) FillOption fillOption,
    @QueryParam(Constants.DEVICE) Set<String> deviceFilterTag,
    @QueryParam(Constants.LOCATION) Set<String> locationFilterTag,
    @QueryParam(Constants.SYMBOLICNAME) Set<String> symbolicNameFilterTag
  ) throws IOException {
    var stream = timeseriesReferenceService.exportReferencedTimeseriesByShepardId(
      timeseriesReferenceId,
      function,
      groupBy,
      fillOption,
      deviceFilterTag,
      locationFilterTag,
      symbolicNameFilterTag,
      securityContext.getUserPrincipal().getName()
    );
    if (stream == null) return Response.status(Status.NOT_FOUND).build();
    return Response.ok(stream, MediaType.APPLICATION_OCTET_STREAM).build();
  }
}
