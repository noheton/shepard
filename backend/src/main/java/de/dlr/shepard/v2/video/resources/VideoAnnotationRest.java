package de.dlr.shepard.v2.video.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.references.videostreamreference.daos.VideoStreamReferenceDAO;
import de.dlr.shepard.context.references.videostreamreference.model.VideoStreamReference;
import de.dlr.shepard.v2.video.daos.VideoAnnotationDAO;
import de.dlr.shepard.v2.video.io.VideoAnnotationIO;
import de.dlr.shepard.v2.video.model.VideoAnnotation;
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
 * VID1b-annotation — CRUD for {@link VideoAnnotation} nodes attached to a
 * {@link VideoStreamReference}.
 *
 * <p>Auth: resolves the parent {@link VideoStreamReference} and verifies it
 * belongs to the DataObject named in the URL, then delegates to
 * {@link PermissionsService} on the parent DataObject's OGM id —
 * same pattern as {@link VideoStreamReferenceV2Rest#checkParentAndAccess}.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/data-objects/{dataObjectAppId}/video-stream-references/{refAppId}/annotations")
@RequestScoped
@Tag(name = "Video annotations (v2)")
public class VideoAnnotationRest {

  @Inject
  VideoAnnotationDAO annotationDAO;

  @Inject
  VideoStreamReferenceDAO videoStreamReferenceDAO;

  @Inject
  PermissionsService permissionsService;

  // ─── helpers ─────────────────────────────────────────────────────────────

  private String callerOrNull(SecurityContext sc) {
    return sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
  }

  /**
   * Resolve the reference, verify it belongs to {@code dataObjectAppId}, and
   * check that {@code caller} has the requested access level.
   *
   * @return {@code null} when access is granted; a short-circuit {@link Response} otherwise.
   */
  private Response checkParentAndAccess(
    String refAppId,
    String dataObjectAppId,
    AccessType accessType,
    String caller
  ) {
    VideoStreamReference ref = videoStreamReferenceDAO.findByAppId(refAppId);
    if (ref == null) return Response.status(Response.Status.NOT_FOUND).build();
    if (ref.getDataObject() == null) return Response.status(Response.Status.NOT_FOUND).build();

    String refParentAppId = ref.getDataObject().getAppId();
    if (refParentAppId != null && !refParentAppId.equals(dataObjectAppId)) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    long doOgmId = ref.getDataObject().getId();
    if (!permissionsService.isAccessTypeAllowedForUser(doOgmId, accessType, caller, 0L)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    return null;
  }

  // ─── endpoints ───────────────────────────────────────────────────────────

  @GET
  @Operation(
    summary = "List all annotations on a VideoStreamReference (VID1b-annotation).",
    description =
      "Returns all :VideoAnnotation nodes attached to the :VideoStreamReference " +
      "identified by refAppId. Each VideoAnnotationIO record includes appId, " +
      "startSeconds (start of the annotated segment in seconds from the start of the video), " +
      "endSeconds (end of the segment, nullable for point annotations), label, " +
      "description, aiGenerated, and confidence.\n\n" +
      "Auth: Read permission on the parent DataObject."
  )
  @APIResponse(
    responseCode = "200",
    description = "JSON array of VideoAnnotationIO records; may be empty.",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = VideoAnnotationIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No VideoStreamReference with that appId, or DataObject mismatch.")
  public Response list(
    @PathParam("dataObjectAppId") String dataObjectAppId,
    @PathParam("refAppId") String refAppId,
    @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();

    Response gate = checkParentAndAccess(refAppId, dataObjectAppId, AccessType.Read, caller);
    if (gate != null) return gate;

    List<VideoAnnotationIO> rows = annotationDAO
      .findByVideoReferenceAppId(refAppId)
      .stream()
      .map(VideoAnnotationIO::new)
      .toList();
    return Response.ok(rows).build();
  }

  @POST
  @Operation(
    summary = "Create an annotation on a VideoStreamReference (VID1b-annotation).",
    description =
      "Creates a :VideoAnnotation node and links it to the :VideoStreamReference " +
      "identified by refAppId. The server mints appId (UUID v7) for the new annotation.\n\n" +
      "Required fields: startSeconds (double, seconds from start of video), label (non-blank string).\n" +
      "Optional fields: endSeconds (null for point annotation), description, aiGenerated (default false), " +
      "confidence ([0.0, 1.0]).\n\n" +
      "Auth: Write permission on the parent DataObject."
  )
  @APIResponse(
    responseCode = "201",
    description = "Annotation created; body contains the new VideoAnnotationIO with its minted appId.",
    content = @Content(schema = @Schema(implementation = VideoAnnotationIO.class))
  )
  @APIResponse(responseCode = "400", description = "startSeconds is null, or label is null or blank.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No VideoStreamReference with that appId, or DataObject mismatch.")
  public Response create(
    @PathParam("dataObjectAppId") String dataObjectAppId,
    @PathParam("refAppId") String refAppId,
    VideoAnnotationIO body,
    @Context SecurityContext sc
  ) {
    if (body == null || body.getStartSeconds() == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("startSeconds is required").build();
    }
    if (body.getLabel() == null || body.getLabel().isBlank()) {
      return Response.status(Response.Status.BAD_REQUEST).entity("label is required and must be non-blank").build();
    }

    String caller = callerOrNull(sc);
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();

    Response gate = checkParentAndAccess(refAppId, dataObjectAppId, AccessType.Write, caller);
    if (gate != null) return gate;

    VideoAnnotation a = new VideoAnnotation();
    a.setStartSeconds(body.getStartSeconds());
    a.setEndSeconds(body.getEndSeconds());
    a.setLabel(body.getLabel().strip());
    a.setDescription(body.getDescription());
    a.setAiGenerated(body.isAiGenerated());
    a.setConfidence(body.getConfidence());

    annotationDAO.createOrUpdate(a);
    annotationDAO.linkToReference(refAppId, a.getAppId());

    return Response.status(Response.Status.CREATED).entity(new VideoAnnotationIO(a)).build();
  }

  @GET
  @Path("/{annotationAppId}")
  @Operation(
    summary = "Read a single VideoAnnotation by appId (VID1b-annotation).",
    description =
      "Returns the VideoAnnotationIO for the :VideoAnnotation identified by annotationAppId " +
      "within the :VideoStreamReference identified by refAppId.\n\n" +
      "Auth: Read permission on the parent DataObject."
  )
  @APIResponse(
    responseCode = "200",
    description = "VideoAnnotationIO for the requested annotation.",
    content = @Content(schema = @Schema(implementation = VideoAnnotationIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No VideoStreamReference with refAppId, or no annotation with annotationAppId.")
  public Response read(
    @PathParam("dataObjectAppId") String dataObjectAppId,
    @PathParam("refAppId") String refAppId,
    @PathParam("annotationAppId") String annotationAppId,
    @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();

    Response gate = checkParentAndAccess(refAppId, dataObjectAppId, AccessType.Read, caller);
    if (gate != null) return gate;

    VideoAnnotation a = annotationDAO.findByAppId(annotationAppId);
    if (a == null) return Response.status(Response.Status.NOT_FOUND).build();
    return Response.ok(new VideoAnnotationIO(a)).build();
  }

  @PATCH
  @Path("/{annotationAppId}")
  @Operation(
    summary = "Update a VideoAnnotation (merge-patch) (VID1b-annotation).",
    description =
      "Applies a partial update to the :VideoAnnotation identified by annotationAppId. " +
      "Only fields present and non-null in the request body are applied.\n\n" +
      "Patchable fields: startSeconds, endSeconds, label (non-blank if provided), description, confidence. " +
      "aiGenerated cannot be updated via PATCH — it is set at creation time only.\n\n" +
      "Auth: Write permission on the parent DataObject."
  )
  @APIResponse(
    responseCode = "200",
    description = "VideoAnnotationIO reflecting the state after the patch was applied.",
    content = @Content(schema = @Schema(implementation = VideoAnnotationIO.class))
  )
  @APIResponse(responseCode = "400", description = "label is provided but is blank or whitespace-only.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No VideoStreamReference with refAppId, or no annotation with annotationAppId.")
  public Response update(
    @PathParam("dataObjectAppId") String dataObjectAppId,
    @PathParam("refAppId") String refAppId,
    @PathParam("annotationAppId") String annotationAppId,
    VideoAnnotationIO body,
    @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();

    Response gate = checkParentAndAccess(refAppId, dataObjectAppId, AccessType.Write, caller);
    if (gate != null) return gate;

    VideoAnnotation a = annotationDAO.findByAppId(annotationAppId);
    if (a == null) return Response.status(Response.Status.NOT_FOUND).build();

    if (body.getStartSeconds() != null) a.setStartSeconds(body.getStartSeconds());
    if (body.getEndSeconds() != null) a.setEndSeconds(body.getEndSeconds());
    if (body.getLabel() != null) {
      if (body.getLabel().isBlank()) {
        return Response.status(Response.Status.BAD_REQUEST).entity("label must be non-blank").build();
      }
      a.setLabel(body.getLabel().strip());
    }
    if (body.getDescription() != null) a.setDescription(body.getDescription());
    if (body.getConfidence() != null) a.setConfidence(body.getConfidence());

    annotationDAO.createOrUpdate(a);
    return Response.ok(new VideoAnnotationIO(a)).build();
  }

  @DELETE
  @Path("/{annotationAppId}")
  @Operation(
    summary = "Delete a VideoAnnotation from a VideoStreamReference (VID1b-annotation).",
    description =
      "Removes the :VideoAnnotation identified by annotationAppId from the :VideoStreamReference " +
      "identified by refAppId. Both the graph node and its has_video_annotation relationship are deleted.\n\n" +
      "Auth: Write permission on the parent DataObject."
  )
  @APIResponse(responseCode = "204", description = "Annotation node and its relationship to the reference deleted.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No VideoStreamReference with refAppId, or no annotation with annotationAppId.")
  public Response delete(
    @PathParam("dataObjectAppId") String dataObjectAppId,
    @PathParam("refAppId") String refAppId,
    @PathParam("annotationAppId") String annotationAppId,
    @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();

    Response gate = checkParentAndAccess(refAppId, dataObjectAppId, AccessType.Write, caller);
    if (gate != null) return gate;

    VideoAnnotation a = annotationDAO.findByAppId(annotationAppId);
    if (a == null) return Response.status(Response.Status.NOT_FOUND).build();
    annotationDAO.unlinkAndDelete(refAppId, a);
    return Response.noContent().build();
  }
}
