package de.dlr.shepard.v2.timeseries.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.references.timeseriesreference.daos.TimeseriesReferenceDAO;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.v2.timeseries.daos.TimeseriesAnnotationDAO;
import de.dlr.shepard.v2.timeseries.io.TimeseriesAnnotationIO;
import de.dlr.shepard.v2.timeseries.model.TimeseriesAnnotation;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * TA1a — CRUD for {@link TimeseriesAnnotation} nodes attached to a
 * {@link TimeseriesReference}.
 *
 * <p>Auth piggybacks on the parent DataObject's permissions: Read to list/get,
 * Write to create/update/delete. 404 when the reference doesn't exist;
 * 403 on permission denied; 401 when unauthenticated.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/timeseries-references/{refAppId}/annotations")
@RequestScoped
@Tag(name = "Timeseries annotations (v2)")
public class TimeseriesAnnotationRest {

  @Inject
  TimeseriesAnnotationDAO annotationDAO;

  @Inject
  TimeseriesReferenceDAO timeseriesReferenceDAO;

  @Inject
  PermissionsService permissionsService;

  // ─── helpers ─────────────────────────────────────────────────────────────

  private TimeseriesReference resolveRef(String refAppId) {
    return timeseriesReferenceDAO.findByAppId(refAppId);
  }

  private Response checkAccess(String refAppId, AccessType type, SecurityContext sc) {
    String caller = sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();
    TimeseriesReference ref = resolveRef(refAppId);
    if (ref == null) return Response.status(Response.Status.NOT_FOUND).build();
    var dataObject = ref.getDataObject();
    if (dataObject == null) return Response.status(Response.Status.NOT_FOUND).build();
    if (!permissionsService.isAccessTypeAllowedForUser(dataObject.getId(), type, caller)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    return null;
  }

  // ─── endpoints ───────────────────────────────────────────────────────────

  @GET
  @Operation(summary = "List all annotations on a TimeseriesReference.")
  @APIResponse(
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = TimeseriesAnnotationIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No TimeseriesReference with that appId.")
  public Response list(@PathParam("refAppId") String refAppId, @Context SecurityContext sc) {
    var gate = checkAccess(refAppId, AccessType.Read, sc);
    if (gate != null) return gate;
    List<TimeseriesAnnotationIO> rows = annotationDAO
      .findByTimeseriesReferenceAppId(refAppId)
      .stream()
      .map(TimeseriesAnnotationIO::new)
      .toList();
    return Response.ok(rows).build();
  }

  @POST
  @Operation(summary = "Create an annotation on a TimeseriesReference.")
  @APIResponse(
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = TimeseriesAnnotationIO.class))
  )
  @APIResponse(responseCode = "400", description = "Missing required fields (startNs, label).")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No TimeseriesReference with that appId.")
  public Response create(
    @PathParam("refAppId") String refAppId,
    TimeseriesAnnotationIO body,
    @Context SecurityContext sc
  ) {
    if (body == null || body.getStartNs() == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("startNs is required").build();
    }
    if (body.getLabel() == null || body.getLabel().isBlank()) {
      return Response.status(Response.Status.BAD_REQUEST).entity("label is required and must be non-blank").build();
    }
    var gate = checkAccess(refAppId, AccessType.Write, sc);
    if (gate != null) return gate;

    TimeseriesAnnotation a = new TimeseriesAnnotation();
    a.setStartNs(body.getStartNs());
    a.setEndNs(body.getEndNs());
    a.setLabel(body.getLabel().strip());
    a.setDescription(body.getDescription());
    a.setAiGenerated(body.isAiGenerated());
    a.setConfidence(body.getConfidence());

    annotationDAO.createOrUpdate(a);
    annotationDAO.linkToReference(refAppId, a.getAppId());

    return Response.status(Response.Status.CREATED).entity(new TimeseriesAnnotationIO(a)).build();
  }

  @GET
  @Path("/{annotationAppId}")
  @Operation(summary = "Read a single annotation by appId.")
  @APIResponse(
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = TimeseriesAnnotationIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No TimeseriesReference or annotation with those appIds.")
  public Response read(
    @PathParam("refAppId") String refAppId,
    @PathParam("annotationAppId") String annotationAppId,
    @Context SecurityContext sc
  ) {
    var gate = checkAccess(refAppId, AccessType.Read, sc);
    if (gate != null) return gate;
    TimeseriesAnnotation a = annotationDAO.findByAppId(annotationAppId);
    if (a == null) return Response.status(Response.Status.NOT_FOUND).build();
    return Response.ok(new TimeseriesAnnotationIO(a)).build();
  }

  @PATCH
  @Path("/{annotationAppId}")
  @Operation(summary = "Update an annotation (merge-patch: only provided non-null fields are changed).")
  @APIResponse(
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = TimeseriesAnnotationIO.class))
  )
  @APIResponse(responseCode = "400", description = "label must be non-blank if provided.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No TimeseriesReference or annotation with those appIds.")
  public Response update(
    @PathParam("refAppId") String refAppId,
    @PathParam("annotationAppId") String annotationAppId,
    TimeseriesAnnotationIO body,
    @Context SecurityContext sc
  ) {
    var gate = checkAccess(refAppId, AccessType.Write, sc);
    if (gate != null) return gate;
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
  @Operation(summary = "Delete an annotation.")
  @APIResponse(responseCode = "204", description = "Annotation deleted.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No TimeseriesReference or annotation with those appIds.")
  public Response delete(
    @PathParam("refAppId") String refAppId,
    @PathParam("annotationAppId") String annotationAppId,
    @Context SecurityContext sc
  ) {
    var gate = checkAccess(refAppId, AccessType.Write, sc);
    if (gate != null) return gate;
    TimeseriesAnnotation a = annotationDAO.findByAppId(annotationAppId);
    if (a == null) return Response.status(Response.Status.NOT_FOUND).build();
    annotationDAO.unlinkAndDelete(refAppId, a);
    return Response.noContent().build();
  }
}
