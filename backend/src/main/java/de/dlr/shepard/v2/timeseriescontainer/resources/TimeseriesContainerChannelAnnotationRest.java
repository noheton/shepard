package de.dlr.shepard.v2.timeseriescontainer.resources;

import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.context.semantic.io.SemanticAnnotationIO;
import de.dlr.shepard.context.semantic.services.AnnotatableTimeseriesService;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.v2.timeseriescontainer.io.ChannelAxisAnnotationIO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * TS-AXIS-AUTO — per-channel semantic annotation endpoints.
 *
 * <p>Exposes:
 * <ul>
 *   <li>{@code GET  /v2/timeseries-containers/{containerId}/channels/{channelShepardId}/annotations}
 *       — list all annotations on a channel</li>
 *   <li>{@code POST /v2/timeseries-containers/{containerId}/channels/{channelShepardId}/annotations}
 *       — attach an axis-role annotation to a channel</li>
 * </ul>
 *
 * <p>The POST variant accepts a {@link ChannelAxisAnnotationIO} rather than the full
 * {@link SemanticAnnotationIO}: axis roles are first-class Shepard tokens, not ontology
 * terms, and need no repository lookup or ontology validation.
 */
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path("/v2/timeseries-containers/{containerId}/channels/{channelShepardId}/annotations")
@RequestScoped
@Tag(name = "Timeseries channel annotations (TS-AXIS-AUTO)")
public class TimeseriesContainerChannelAnnotationRest {

  @Inject
  AnnotatableTimeseriesService annotatableTimeseriesService;

  @Inject
  TimeseriesContainerService containerService;

  @GET
  @Operation(
    operationId = "getChannelAnnotations",
    summary = "List annotations on a channel (TS-AXIS-AUTO).",
    description = "Returns all SemanticAnnotations attached to the AnnotatableTimeseries " +
      "bridge node for the given channel. Returns an empty array if the channel has no " +
      "annotations yet."
  )
  @APIResponse(
    responseCode = "200",
    description = "Annotation list (may be empty).",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = SemanticAnnotationIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the container.")
  @APIResponse(responseCode = "404", description = "No TimeseriesContainer with that id.")
  public Response getAnnotations(
    @PathParam("containerId") @NotNull @PositiveOrZero Long containerId,
    @PathParam("channelShepardId") @NotNull UUID channelShepardId
  ) {
    containerService.getContainer(containerId);
    List<SemanticAnnotation> annotations =
        annotatableTimeseriesService.getAnnotationsForChannel(containerId, channelShepardId);
    List<SemanticAnnotationIO> body = annotations.stream()
        .map(SemanticAnnotationIO::new)
        .toList();
    return Response.ok(body).build();
  }

  @POST
  @Operation(
    operationId = "createChannelAxisAnnotation",
    summary = "Annotate a channel with a spatial axis role (TS-AXIS-AUTO).",
    description = "Attaches an axis-role annotation to the channel. The accepted roles are " +
      "x, y, z, rot_a, rot_b, rot_c. If no AnnotatableTimeseries bridge node exists yet " +
      "for this channel, one is created automatically. No ontology validation is performed " +
      "— axis roles are first-class Shepard tokens."
  )
  @APIResponse(
    responseCode = "201",
    description = "Annotation created.",
    content = @Content(schema = @Schema(implementation = SemanticAnnotationIO.class))
  )
  @APIResponse(responseCode = "400", description = "Invalid axis role value.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the container.")
  @APIResponse(responseCode = "404", description = "No container or channel with that id.")
  public Response createAnnotation(
    @PathParam("containerId") @NotNull @PositiveOrZero Long containerId,
    @PathParam("channelShepardId") @NotNull UUID channelShepardId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = ChannelAxisAnnotationIO.class))
    ) @NotNull @Valid ChannelAxisAnnotationIO body
  ) {
    SemanticAnnotation created =
        annotatableTimeseriesService.createAnnotationForChannel(containerId, channelShepardId, body);
    return Response.status(Response.Status.CREATED)
        .entity(new SemanticAnnotationIO(created))
        .build();
  }
}
