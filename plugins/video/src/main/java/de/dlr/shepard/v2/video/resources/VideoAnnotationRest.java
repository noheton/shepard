package de.dlr.shepard.v2.video.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.ProblemJson;
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
 * <p>Auth: resolves the parent DataObject from the reference itself and
 * delegates to {@link PermissionsService#isAccessAllowedForDataObjectAppId}
 * — same pattern as {@code TimeseriesAnnotationRest}.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/references/{refAppId}/annotations")
@RequestScoped
@Tag(name = "Video annotations")
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
   * Resolve the reference and check that {@code caller} has the requested access level
   * on the parent DataObject (resolved from the reference itself).
   *
   * @return {@code null} when access is granted; a short-circuit {@link Response} otherwise.
   */
  private Response checkAccess(String refAppId, AccessType accessType, String caller) {
    VideoStreamReference ref = videoStreamReferenceDAO.findByAppId(refAppId);
    if (ref == null) return problem(Response.Status.NOT_FOUND, "VideoStreamReference not found");
    if (ref.getDataObject() == null) return problem(Response.Status.NOT_FOUND, "VideoStreamReference not found");

    String parentAppId = ref.getDataObject().getAppId();
    if (!permissionsService.isAccessAllowedForDataObjectAppId(parentAppId, accessType, caller)) {
      return problem(Response.Status.FORBIDDEN, "Insufficient permissions");
    }
    return null;
  }

  private static Response problem(Response.Status status, String detail) {
    String type = switch (status) {
      case UNAUTHORIZED -> "urn:shepard:error:unauthorized";
      case FORBIDDEN -> "urn:shepard:error:forbidden";
      case BAD_REQUEST -> "urn:shepard:error:validation";
      case NOT_FOUND -> "urn:shepard:error:not-found";
      case CONFLICT -> "urn:shepard:error:conflict";
      case SERVICE_UNAVAILABLE -> "urn:shepard:error:service-unavailable";
      default -> "urn:shepard:error:internal";
    };
    return Response.status(status)
      .type("application/problem+json")
      .entity(new ProblemJson(type, status.getReasonPhrase(), status.getStatusCode(), detail, null))
      .build();
  }

  // ─── endpoints ───────────────────────────────────────────────────────────

  @GET
  @Operation(
    operationId = "listVideoAnnotations",
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
    @PathParam("refAppId") String refAppId,
    @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) return problem(Response.Status.UNAUTHORIZED, "Authentication required");

    Response gate = checkAccess(refAppId, AccessType.Read, caller);
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
    operationId = "createVideoAnnotation",
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
    @PathParam("refAppId") String refAppId,
    VideoAnnotationIO body,
    @Context SecurityContext sc
  ) {
    if (body == null || body.getStartSeconds() == null) {
      return problem(Response.Status.BAD_REQUEST, "startSeconds is required");
    }
    if (body.getLabel() == null || body.getLabel().isBlank()) {
      return problem(Response.Status.BAD_REQUEST, "label is required and must be non-blank");
    }

    String caller = callerOrNull(sc);
    if (caller == null) return problem(Response.Status.UNAUTHORIZED, "Authentication required");

    Response gate = checkAccess(refAppId, AccessType.Write, caller);
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
    operationId = "getVideoAnnotation",
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
    @PathParam("refAppId") String refAppId,
    @PathParam("annotationAppId") String annotationAppId,
    @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) return problem(Response.Status.UNAUTHORIZED, "Authentication required");

    Response gate = checkAccess(refAppId, AccessType.Read, caller);
    if (gate != null) return gate;

    VideoAnnotation a = annotationDAO.findByAppId(annotationAppId);
    if (a == null) return problem(Response.Status.NOT_FOUND, "VideoAnnotation not found");
    return Response.ok(new VideoAnnotationIO(a)).build();
  }

  @PATCH
  @Path("/{annotationAppId}")
  @Operation(
    operationId = "updateVideoAnnotation",
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
    @PathParam("refAppId") String refAppId,
    @PathParam("annotationAppId") String annotationAppId,
    VideoAnnotationIO body,
    @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) return problem(Response.Status.UNAUTHORIZED, "Authentication required");

    Response gate = checkAccess(refAppId, AccessType.Write, caller);
    if (gate != null) return gate;

    VideoAnnotation a = annotationDAO.findByAppId(annotationAppId);
    if (a == null) return problem(Response.Status.NOT_FOUND, "VideoAnnotation not found");

    if (body.getStartSeconds() != null) a.setStartSeconds(body.getStartSeconds());
    if (body.getEndSeconds() != null) a.setEndSeconds(body.getEndSeconds());
    if (body.getLabel() != null) {
      if (body.getLabel().isBlank()) {
        return problem(Response.Status.BAD_REQUEST, "label must be non-blank");
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
    operationId = "deleteVideoAnnotation",
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
    @PathParam("refAppId") String refAppId,
    @PathParam("annotationAppId") String annotationAppId,
    @Context SecurityContext sc
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) return problem(Response.Status.UNAUTHORIZED, "Authentication required");

    Response gate = checkAccess(refAppId, AccessType.Write, caller);
    if (gate != null) return gate;

    VideoAnnotation a = annotationDAO.findByAppId(annotationAppId);
    if (a == null) return problem(Response.Status.NOT_FOUND, "VideoAnnotation not found");
    annotationDAO.unlinkAndDelete(refAppId, a);
    return Response.noContent().build();
  }
}
