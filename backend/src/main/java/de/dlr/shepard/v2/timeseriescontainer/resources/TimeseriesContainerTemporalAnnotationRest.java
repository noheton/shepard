package de.dlr.shepard.v2.timeseriescontainer.resources;

import de.dlr.shepard.common.identifier.AppIdGenerator;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.v2.timeseries.daos.TimeseriesAnnotationDAO;
import de.dlr.shepard.v2.timeseries.io.TimeseriesAnnotationIO;
import de.dlr.shepard.v2.timeseries.model.TimeseriesAnnotation;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * TS-ANNOT-B — container-scoped temporal annotations.
 *
 * <p>Container-level annotations span the entire data space of a
 * {@code TimeseriesContainer} rather than a single channel reference.
 * They use the same {@link TimeseriesAnnotation} entity / {@link TimeseriesAnnotationIO}
 * shape as the reference-scoped endpoints; the parent here is a
 * {@code TimeseriesContainer} linked via {@code HAS_TEMPORAL_ANNOTATION}.
 *
 * <p>Routes:
 * <ul>
 *   <li>{@code GET    /v2/timeseries-containers/{containerId}/temporal-annotations}</li>
 *   <li>{@code POST   /v2/timeseries-containers/{containerId}/temporal-annotations}</li>
 *   <li>{@code GET    /v2/timeseries-containers/{containerId}/temporal-annotations/{annotationAppId}}</li>
 *   <li>{@code PATCH  /v2/timeseries-containers/{containerId}/temporal-annotations/{annotationAppId}}</li>
 *   <li>{@code DELETE /v2/timeseries-containers/{containerId}/temporal-annotations/{annotationAppId}}</li>
 * </ul>
 *
 * <p>Auth: same pattern as other container endpoints —
 * {@link TimeseriesContainerService#getContainer(long)} enforces Read;
 * {@link TimeseriesContainerService#assertIsAllowedToEditContainer(long)} enforces Write.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/timeseries-containers/{containerId}/temporal-annotations")
@RequestScoped
@Tag(name = "Timeseries container temporal annotations (TS-ANNOT-B)")
public class TimeseriesContainerTemporalAnnotationRest {

  @Inject
  TimeseriesAnnotationDAO annotationDAO;

  @Inject
  TimeseriesContainerService containerService;

  // ── endpoints ────────────────────────────────────────────────────────────

  @GET
  @Operation(
    summary = "List all temporal annotations on a TimeseriesContainer.",
    description =
      "Returns all `:TimeseriesAnnotation` nodes attached to the `:TimeseriesContainer` " +
      "identified by `containerId` via the `HAS_TEMPORAL_ANNOTATION` relationship. " +
      "Container-level annotations are independent of individual channel references — " +
      "they mark time ranges of interest on the entire container (e.g. an event window, " +
      "a calibration period, or an anomaly span detected by the AI pipeline).\n\n" +
      "Each `TimeseriesAnnotationIO` record includes `appId`, `startNs`, `endNs` " +
      "(nullable for point annotations), `label`, `description`, `aiGenerated`, and " +
      "`confidence`. List is unordered; sort client-side by `startNs` for timeline display.\n\n" +
      "Auth: Read permission on the container."
  )
  @APIResponse(
    responseCode = "200",
    description = "JSON array of TimeseriesAnnotationIO records; may be empty.",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = TimeseriesAnnotationIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the container.")
  @APIResponse(responseCode = "404", description = "No TimeseriesContainer with that id.")
  public Response list(@PathParam("containerId") @NotNull @PositiveOrZero Long containerId) {
    containerService.getContainer(containerId);
    List<TimeseriesAnnotationIO> rows = annotationDAO
      .findByContainerId(containerId)
      .stream()
      .map(TimeseriesAnnotationIO::new)
      .toList();
    return Response.ok(rows).build();
  }

  @POST
  @Operation(
    summary = "Create a temporal annotation on a TimeseriesContainer.",
    description =
      "Creates a `:TimeseriesAnnotation` node and links it to the `:TimeseriesContainer` " +
      "identified by `containerId` via `HAS_TEMPORAL_ANNOTATION`. The server mints `appId` " +
      "(UUID v7).\n\n" +
      "Body: `startNs` (long, required — nanoseconds since Unix epoch), `endNs` (long, optional — " +
      "omit for a point annotation), `label` (string, required, non-blank), `description` " +
      "(string, optional), `aiGenerated` (boolean, default `false`), `confidence` (float 0–1, optional).\n\n" +
      "Auth: Write permission on the container."
  )
  @APIResponse(
    responseCode = "201",
    description = "Annotation created; body contains the new TimeseriesAnnotationIO.",
    content = @Content(schema = @Schema(implementation = TimeseriesAnnotationIO.class))
  )
  @APIResponse(responseCode = "400", description = "`startNs` is null, or `label` is null or blank.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the container.")
  @APIResponse(responseCode = "404", description = "No TimeseriesContainer with that id.")
  public Response create(
    @PathParam("containerId") @NotNull @PositiveOrZero Long containerId,
    TimeseriesAnnotationIO body
  ) {
    if (body == null || body.getStartNs() == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("startNs is required").build();
    }
    if (body.getLabel() == null || body.getLabel().isBlank()) {
      return Response.status(Response.Status.BAD_REQUEST).entity("label is required and must be non-blank").build();
    }
    containerService.getContainer(containerId);
    containerService.assertIsAllowedToEditContainer(containerId);

    TimeseriesAnnotation a = new TimeseriesAnnotation();
    a.setAppId(AppIdGenerator.next());
    a.setStartNs(body.getStartNs());
    a.setEndNs(body.getEndNs());
    a.setLabel(body.getLabel().strip());
    a.setDescription(body.getDescription());
    a.setAiGenerated(body.isAiGenerated());
    a.setConfidence(body.getConfidence());

    annotationDAO.createOrUpdate(a);
    annotationDAO.linkToContainer(containerId, a.getAppId());

    return Response.status(Response.Status.CREATED).entity(new TimeseriesAnnotationIO(a)).build();
  }

  @GET
  @Path("/{annotationAppId}")
  @Operation(
    summary = "Read a single container temporal annotation by appId.",
    description =
      "Returns the `TimeseriesAnnotationIO` record for the annotation identified by " +
      "`annotationAppId` within the container `containerId`. " +
      "Auth: Read permission on the container."
  )
  @APIResponse(
    responseCode = "200",
    description = "TimeseriesAnnotationIO for the requested annotation.",
    content = @Content(schema = @Schema(implementation = TimeseriesAnnotationIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the container.")
  @APIResponse(responseCode = "404", description = "No container with that id, or no annotation with that appId.")
  public Response read(
    @PathParam("containerId") @NotNull @PositiveOrZero Long containerId,
    @PathParam("annotationAppId") String annotationAppId
  ) {
    containerService.getContainer(containerId);
    TimeseriesAnnotation a = annotationDAO.findByAppId(annotationAppId);
    if (a == null) return Response.status(Response.Status.NOT_FOUND).build();
    return Response.ok(new TimeseriesAnnotationIO(a)).build();
  }

  @PATCH
  @Path("/{annotationAppId}")
  @Operation(
    summary = "Update a container temporal annotation (merge-patch).",
    description =
      "Applies a partial update: only non-null fields in the body are changed. " +
      "Patchable: `startNs`, `endNs` (set to null to convert range → point), `label` " +
      "(non-blank if provided), `description`, `confidence`. `aiGenerated` is immutable.\n\n" +
      "Auth: Write permission on the container."
  )
  @APIResponse(
    responseCode = "200",
    description = "TimeseriesAnnotationIO reflecting the patched state.",
    content = @Content(schema = @Schema(implementation = TimeseriesAnnotationIO.class))
  )
  @APIResponse(responseCode = "400", description = "`label` is provided but blank.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the container.")
  @APIResponse(responseCode = "404", description = "No container with that id, or no annotation with that appId.")
  public Response update(
    @PathParam("containerId") @NotNull @PositiveOrZero Long containerId,
    @PathParam("annotationAppId") String annotationAppId,
    TimeseriesAnnotationIO body
  ) {
    containerService.getContainer(containerId);
    containerService.assertIsAllowedToEditContainer(containerId);
    TimeseriesAnnotation a = annotationDAO.findByAppId(annotationAppId);
    if (a == null) return Response.status(Response.Status.NOT_FOUND).build();

    if (body.getStartNs() != null) a.setStartNs(body.getStartNs());
    if (body.getEndNs() != null) a.setEndNs(body.getEndNs());
    if (body.getLabel() != null) {
      if (body.getLabel().isBlank()) {
        return Response.status(Response.Status.BAD_REQUEST).entity("label must be non-blank").build();
      }
      a.setLabel(body.getLabel().strip());
    }
    if (body.getDescription() != null) a.setDescription(body.getDescription());
    if (body.getConfidence() != null) a.setConfidence(body.getConfidence());

    annotationDAO.createOrUpdate(a);
    return Response.ok(new TimeseriesAnnotationIO(a)).build();
  }

  @DELETE
  @Path("/{annotationAppId}")
  @Operation(
    summary = "Delete a container temporal annotation.",
    description =
      "Removes the `:TimeseriesAnnotation` node and its `HAS_TEMPORAL_ANNOTATION` " +
      "relationship to the container. Returns 404 if already gone.\n\n" +
      "Auth: Write permission on the container."
  )
  @APIResponse(responseCode = "204", description = "Annotation deleted.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the container.")
  @APIResponse(responseCode = "404", description = "No container with that id, or no annotation with that appId.")
  public Response delete(
    @PathParam("containerId") @NotNull @PositiveOrZero Long containerId,
    @PathParam("annotationAppId") String annotationAppId
  ) {
    containerService.getContainer(containerId);
    containerService.assertIsAllowedToEditContainer(containerId);
    TimeseriesAnnotation a = annotationDAO.findByAppId(annotationAppId);
    if (a == null) return Response.status(Response.Status.NOT_FOUND).build();
    annotationDAO.unlinkAndDeleteFromContainer(containerId, a);
    return Response.noContent().build();
  }
}
