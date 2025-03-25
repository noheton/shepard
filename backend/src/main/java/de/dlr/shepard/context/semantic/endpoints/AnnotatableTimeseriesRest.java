package de.dlr.shepard.context.semantic.endpoints;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.semantic.io.SemanticAnnotationIO;
import de.dlr.shepard.context.semantic.services.AnnotatableTimeseriesService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.util.stream.Collectors;
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
public class AnnotatableTimeseriesRest {

  private AnnotatableTimeseriesService annotatableTimeseriesService;

  @Inject
  AnnotatableTimeseriesRest(AnnotatableTimeseriesService annotatableTimeseriesService) {
    this.annotatableTimeseriesService = annotatableTimeseriesService;
  }

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
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  @Parameter(name = Constants.TIMESERIES_ID)
  public Response getAllAnnotations(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) long containerId,
    @PathParam(Constants.TIMESERIES_ID) int timeseriesId
  ) {
    var annotations = annotatableTimeseriesService.getAnnotations(containerId, timeseriesId);
    return Response.ok(annotations.stream().map(SemanticAnnotationIO::new).collect(Collectors.toList())).build();
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
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  @Parameter(name = Constants.TIMESERIES_ID)
  @Parameter(name = Constants.SEMANTIC_ANNOTATION_ID)
  public Response getAnnotationById(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) long containerId,
    @PathParam(Constants.TIMESERIES_ID) int timeseriesId,
    @PathParam(Constants.SEMANTIC_ANNOTATION_ID) long annotationId
  ) {
    var annotation = annotatableTimeseriesService.getAnnotationById(containerId, timeseriesId, annotationId);
    return Response.ok(new SemanticAnnotationIO(annotation)).build();
  }

  @POST
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(operationId = "createAnnotationForTimeseries", description = "Create new annotation for a timeseries.")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = SemanticAnnotationIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  @Parameter(name = Constants.TIMESERIES_ID)
  @Transactional
  public Response createAnnotation(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) long containerId,
    @PathParam(Constants.TIMESERIES_ID) int timeseriesId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = SemanticAnnotationIO.class))
    ) @Valid SemanticAnnotationIO annotation
  ) {
    var result = annotatableTimeseriesService.createAnnotation(containerId, timeseriesId, annotation);
    return Response.ok(new SemanticAnnotationIO(result)).status(Status.CREATED).build();
  }

  @DELETE
  @Path("/{" + Constants.SEMANTIC_ANNOTATION_ID + "}")
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(operationId = "deleteAnnotationOfTimeseries", description = "Delete annotation of timeseries.")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  @Parameter(name = Constants.TIMESERIES_ID)
  @Parameter(name = Constants.SEMANTIC_ANNOTATION_ID)
  @Transactional
  public Response deleteAnnotation(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) long containerId,
    @PathParam(Constants.TIMESERIES_ID) int timeseriesId,
    @PathParam(Constants.SEMANTIC_ANNOTATION_ID) long annotationId
  ) {
    annotatableTimeseriesService.deleteAnnotation(timeseriesId, annotationId);
    return Response.status(Status.NO_CONTENT).build();
  }
}
