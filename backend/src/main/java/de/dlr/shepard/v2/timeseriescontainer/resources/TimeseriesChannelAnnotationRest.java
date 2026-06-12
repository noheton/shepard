package de.dlr.shepard.v2.timeseriescontainer.resources;

import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.context.semantic.io.SemanticAnnotationIO;
import de.dlr.shepard.context.semantic.services.AnnotatableTimeseriesService;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * TS-SEMANTIC-REST — semantic annotations on individual timeseries channels,
 * addressed by the channel's UUID v7 {@code channelShepardId} (the Postgres
 * {@code shepard_id} column set during channel creation by TS-SEMANTIC-01).
 *
 * <p>Path: {@code /v2/timeseries-containers/{containerAppId}/channels/{channelShepardId}/annotations}
 *
 * <p>Only channels that have been through the TS-SEMANTIC-01 dual-write path have
 * a backing {@code AnnotatableTimeseries} node. Channels created before that
 * service shipped will return empty annotation lists (GET) and 404 on POST.
 */
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path("/v2/timeseries-containers/{containerAppId}/channels/{channelShepardId}/annotations")
@RequestScoped
@Tag(name = "Timeseries channel annotations")
public class TimeseriesChannelAnnotationRest {

  @Inject
  AnnotatableTimeseriesService service;

  @Inject
  TimeseriesContainerService containerService;

  @GET
  @Operation(
    operationId = "listChannelAnnotations",
    description = "List semantic annotations attached to a specific timeseries channel identified by its UUID v7 channelShepardId."
  )
  @APIResponse(
    responseCode = "200",
    description = "List of annotations (may be empty).",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = SemanticAnnotationIO.class))
  )
  @APIResponse(responseCode = "400", description = "channelShepardId is blank.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "404", description = "No TimeseriesContainer with that containerAppId.")
  public Response listAnnotations(
    @PathParam("containerAppId") String containerAppId,
    @PathParam("channelShepardId") String channelShepardId
  ) {
    long containerId = containerService.getContainerByAppId(containerAppId).getId();
    List<SemanticAnnotation> annotations =
      service.getAnnotationsByChannelShepardId(containerId, channelShepardId);
    List<SemanticAnnotationIO> result = annotations.stream()
      .map(SemanticAnnotationIO::new)
      .collect(Collectors.toList());
    return Response.ok(result).build();
  }

  @POST
  @Transactional
  @Operation(
    operationId = "createChannelAnnotation",
    description = "Attach a new semantic annotation to a timeseries channel identified by its UUID v7 channelShepardId. " +
      "Requires Write permission on the container. The channel must have been created via the normal " +
      "timeseries upload path (TS-SEMANTIC-01 dual-write) — channels created before that service shipped " +
      "will return 404."
  )
  @APIResponse(
    responseCode = "201",
    description = "Annotation created.",
    content = @Content(schema = @Schema(implementation = SemanticAnnotationIO.class))
  )
  @APIResponse(responseCode = "400", description = "Bad request — blank channelShepardId or invalid annotation body.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the container.")
  @APIResponse(responseCode = "404", description = "No TimeseriesContainer with that containerAppId, or no AnnotatableTimeseries node for that channelShepardId.")
  public Response createAnnotation(
    @PathParam("containerAppId") String containerAppId,
    @PathParam("channelShepardId") String channelShepardId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = SemanticAnnotationIO.class))
    ) @Valid SemanticAnnotationIO annotationIO
  ) {
    long containerId = containerService.getContainerByAppId(containerAppId).getId();
    SemanticAnnotation created =
      service.createAnnotationForChannel(containerId, channelShepardId, annotationIO);
    return Response.status(Response.Status.CREATED)
      .entity(new SemanticAnnotationIO(created))
      .build();
  }

  @DELETE
  @Path("{annotationAppId}")
  @Transactional
  @Operation(
    operationId = "deleteChannelAnnotation",
    description = "Remove a semantic annotation from a timeseries channel by its UUID v7 appId. Requires Write permission on the container."
  )
  @APIResponse(responseCode = "204", description = "Annotation deleted.")
  @APIResponse(responseCode = "400", description = "channelShepardId is blank.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the container.")
  @APIResponse(responseCode = "404", description = "No TimeseriesContainer with that containerAppId, or annotation not found.")
  public Response deleteAnnotation(
    @PathParam("containerAppId") String containerAppId,
    @PathParam("channelShepardId") String channelShepardId,
    @PathParam("annotationAppId") @NotNull String annotationAppId
  ) {
    long containerId = containerService.getContainerByAppId(containerAppId).getId();
    service.deleteAnnotationForChannel(containerId, channelShepardId, annotationAppId);
    return Response.noContent().build();
  }
}
