package de.dlr.shepard.v2.timeseriescontainer.resources;

import de.dlr.shepard.context.semantic.endpoints.SemanticAnnotationRest;
import de.dlr.shepard.context.semantic.io.SemanticAnnotationIO;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * SA-CONT — semantic annotations on a TimeseriesContainer itself (not on its
 * Timeseries channels — those are covered by {@code TimeseriesAnnotationRest}).
 *
 * <p>Lives on the {@code /v2/} surface; the upstream API never exposed
 * container-level annotations, so this is purely additive.
 */
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path("/v2/timeseries-containers/{containerId}/annotations")
@RequestScoped
@Tag(name = "Timeseries container annotations (SA-CONT)")
public class TimeseriesContainerSemanticAnnotationRest extends SemanticAnnotationRest {

  @Inject
  TimeseriesContainerService containerService;

  @GET
  @Operation(
    operationId = "getAllTimeseriesContainerAnnotations",
    description = "List semantic annotations attached to this TimeseriesContainer."
  )
  @APIResponse(
    responseCode = "200",
    description = "List of annotations (may be empty).",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = SemanticAnnotationIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the container.")
  @APIResponse(responseCode = "404", description = "No TimeseriesContainer with that id.")
  public Response getAllAnnotations(
    @PathParam("containerId") @NotNull @PositiveOrZero Long containerId
  ) {
    containerService.getContainer(containerId);
    return getAllByShepardId(containerId);
  }

  @POST
  @Operation(
    operationId = "createTimeseriesContainerAnnotation",
    description = "Attach a new semantic annotation to this TimeseriesContainer."
  )
  @APIResponse(
    responseCode = "201",
    description = "Annotation created.",
    content = @Content(schema = @Schema(implementation = SemanticAnnotationIO.class))
  )
  @APIResponse(responseCode = "400", description = "Bad request.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the container.")
  @APIResponse(responseCode = "404", description = "No TimeseriesContainer with that id.")
  public Response createAnnotation(
    @PathParam("containerId") @NotNull @PositiveOrZero Long containerId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = SemanticAnnotationIO.class))
    ) @Valid SemanticAnnotationIO semanticAnnotation
  ) {
    containerService.getContainer(containerId);
    containerService.assertIsAllowedToEditContainer(containerId);
    return createByShepardId(containerId, semanticAnnotation);
  }

  @DELETE
  @Path("{annotationId}")
  @Operation(
    operationId = "deleteTimeseriesContainerAnnotation",
    description = "Remove a semantic annotation from this TimeseriesContainer."
  )
  @APIResponse(responseCode = "204", description = "Annotation deleted.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the container.")
  @APIResponse(responseCode = "404", description = "No container or no such annotation belongs to it.")
  public Response deleteAnnotation(
    @PathParam("containerId") @NotNull @PositiveOrZero Long containerId,
    @PathParam("annotationId") @NotNull @PositiveOrZero Long annotationId
  ) {
    TimeseriesContainer container = containerService.getContainer(containerId);
    containerService.assertIsAllowedToEditContainer(containerId);
    assertSemanticAnnotationBelongsToEntity(container, annotationId);
    return delete(annotationId);
  }
}
