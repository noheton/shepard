package de.dlr.shepard.context.semantic.endpoints;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.semantic.io.SemanticAnnotationIO;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
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
  Constants.TIMESERIES_CONTAINERS +
  "/{" +
  Constants.TIMESERIES_CONTAINER_ID +
  "}/" +
  Constants.TIMESERIES +
  "/{" +
  Constants.TIMESERIES_ID +
  "}/" +
  Constants.SEMANTIC_ANNOTATIONS
)
@RequestScoped
public class TimeseriesSemanticAnnotationRest extends SemanticAnnotationRest {

  @Inject
  TimeseriesService timeseriesService;

  @Inject
  TimeseriesContainerService timeseriesContainerService;

  @GET
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(
    operationId = "getAllAnnotationsOfTimeseries",
    description = "Get all semantic annotations of a timeseries."
  )
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = SemanticAnnotationIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID, deprecated = true)
  @Parameter(name = Constants.TIMESERIES_ID)
  public Response getAllAnnotations(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero @Deprecated Long timeseriesContainerId,
    @PathParam(Constants.TIMESERIES_ID) @NotNull @PositiveOrZero Integer timeseriesId
  ) {
    var timeseries = timeseriesService.getTimeseriesById(timeseriesId.longValue());
    return getAllByShepardId(timeseries.getId());
  }

  @GET
  @Path("/{" + Constants.SEMANTIC_ANNOTATION_ID + "}")
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(
    operationId = "getSemanticAnnotationOfTimeseries",
    description = "Get a specific semantic annotation of a timeseries."
  )
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = SemanticAnnotationIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID, deprecated = true)
  @Parameter(name = Constants.TIMESERIES_ID)
  @Parameter(name = Constants.SEMANTIC_ANNOTATION_ID)
  public Response getAnnotationById(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero @Deprecated Long timeseriesContainerId,
    @PathParam(Constants.TIMESERIES_ID) @NotNull @PositiveOrZero Integer timeseriesId,
    @PathParam(Constants.SEMANTIC_ANNOTATION_ID) @NotNull @PositiveOrZero Long annotationId
  ) {
    var timeseries = timeseriesService.getTimeseriesById(timeseriesId.longValue());
    var annotation = timeseries
      .getAnnotations()
      .stream()
      .filter(a -> a.getId().equals(annotationId))
      .findFirst()
      .map(SemanticAnnotationIO::new)
      .orElseThrow(NotFoundException::new);
    return Response.ok(annotation).build();
  }

  @POST
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(operationId = "createAnnotationForTimeseries", description = "Create new annotation for a timeseries.")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = SemanticAnnotationIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID, deprecated = true)
  @Parameter(name = Constants.TIMESERIES_ID)
  @Transactional
  public Response createAnnotation(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero @Deprecated Long timeseriesContainerId,
    @PathParam(Constants.TIMESERIES_ID) @NotNull @PositiveOrZero Integer timeseriesId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = SemanticAnnotationIO.class))
    ) @Valid SemanticAnnotationIO annotation
  ) {
    var timeseries = timeseriesService.getTimeseriesById(timeseriesId.longValue());
    return createByShepardId(timeseries.getId(), annotation);
  }

  @DELETE
  @Path("/{" + Constants.SEMANTIC_ANNOTATION_ID + "}")
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(operationId = "deleteAnnotationOfTimeseries", description = "Delete annotation of timeseries.")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID, deprecated = true)
  @Parameter(name = Constants.TIMESERIES_ID)
  @Parameter(name = Constants.SEMANTIC_ANNOTATION_ID)
  @Transactional
  public Response deleteAnnotation(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) @NotNull @PositiveOrZero @Deprecated Long timeseriesContainerId,
    @PathParam(Constants.TIMESERIES_ID) @NotNull @PositiveOrZero Integer timeseriesId,
    @PathParam(Constants.SEMANTIC_ANNOTATION_ID) @NotNull @PositiveOrZero Long annotationId
  ) {
    var timeseries = timeseriesService.getTimeseriesById(timeseriesId.longValue());
    timeseriesContainerService.assertIsAllowedToEditContainer(timeseries.getContainer().getId());
    var annotation = timeseries
      .getAnnotations()
      .stream()
      .filter(a -> a.getId().equals(annotationId))
      .findFirst()
      .orElseThrow(NotFoundException::new);
    return delete(annotation.getId());
  }
}
