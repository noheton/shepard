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
    // DataObjects have no own :Permissions node — walk up to the parent Collection.
    if (!permissionsService.isAccessAllowedForDataObjectAppId(dataObject.getAppId(), type, caller)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    return null;
  }

  // ─── endpoints ───────────────────────────────────────────────────────────

  @GET
  @Operation(
    summary = "List all annotations on a TimeseriesReference.",
    description =
      "Returns all `:TimeseriesAnnotation` nodes attached to the `:TimeseriesReference` " +
      "identified by `refAppId` (UUID v7). Each `TimeseriesAnnotationIO` record includes " +
      "`appId`, `startNs` (start of the annotated window in nanoseconds since the " +
      "timeseries epoch), `endNs` (end of the window, nullable for point annotations), " +
      "`label` (short non-blank string), `description` (optional long-form text), " +
      "`aiGenerated` (boolean flag set when the annotation was created by an AI detector), " +
      "and `confidence` (nullable float 0.0–1.0).\n\n" +
      "The list is unordered; callers should sort client-side by `startNs` to render " +
      "annotations in timeline order.\n\n" +
      "Auth: Read permission on the parent DataObject (inherited from its Collection). " +
      "Returns 404 when the `refAppId` does not resolve to a known `:TimeseriesReference`.\n\n" +
      "Next step: `POST /v2/timeseries-references/{refAppId}/annotations` to add " +
      "an annotation, or `GET /v2/timeseries-references/{refAppId}/annotations/{annotationAppId}` " +
      "to fetch a single one."
  )
  @APIResponse(
    responseCode = "200",
    description = "JSON array of TimeseriesAnnotationIO records attached to the reference; may be empty.",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = TimeseriesAnnotationIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required (no JWT or X-API-KEY).")
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
  @Operation(
    summary = "Create an annotation on a TimeseriesReference.",
    description =
      "Creates a `:TimeseriesAnnotation` node and links it to the `:TimeseriesReference` " +
      "identified by `refAppId` (UUID v7). The server mints `appId` (UUID v7) for the " +
      "new annotation.\n\n" +
      "Body fields: `startNs` (long, required — start of the annotated window in " +
      "nanoseconds relative to the timeseries epoch), `endNs` (long, optional — end of " +
      "the window; omit for a point annotation), `label` (string, required, non-blank — " +
      "short display name), `description` (string, optional — long-form text), " +
      "`aiGenerated` (boolean, optional, default `false` — set to `true` when the " +
      "annotation was produced by an AI detector such as the MAD anomaly endpoint), " +
      "`confidence` (float 0.0–1.0, optional — only meaningful when `aiGenerated=true`).\n\n" +
      "Example body: `{\"startNs\": 1700000000000000000, \"endNs\": 1700000001000000000, " +
      "\"label\": \"spike\", \"description\": \"unexpected pressure spike\", " +
      "\"aiGenerated\": false}`.\n\n" +
      "Auth: Write permission on the parent DataObject (inherited from its Collection).\n\n" +
      "Side effects: the annotation is linked to the reference in Neo4j via a " +
      "`HAS_ANNOTATION` relationship. No provenance Activity is recorded for " +
      "individual annotations in TA1a."
  )
  @APIResponse(
    responseCode = "201",
    description = "Annotation created; body contains the new TimeseriesAnnotationIO with its minted appId.",
    content = @Content(schema = @Schema(implementation = TimeseriesAnnotationIO.class))
  )
  @APIResponse(responseCode = "400", description = "Required fields missing: `startNs` is null, or `label` is null or blank.")
  @APIResponse(responseCode = "401", description = "Authentication required (no JWT or X-API-KEY).")
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
  @Operation(
    summary = "Read a single annotation by appId.",
    description =
      "Returns the `TimeseriesAnnotationIO` record for the `:TimeseriesAnnotation` " +
      "identified by `annotationAppId` (UUID v7) within the `:TimeseriesReference` " +
      "identified by `refAppId`. Both path params are required; 404 is returned if " +
      "either the reference or the annotation is unknown.\n\n" +
      "Auth: Read permission on the parent DataObject (inherited from its Collection). " +
      "The access check is performed against the `refAppId` parent reference; the " +
      "annotation lookup is not independently permission-gated.\n\n" +
      "Next step: `PATCH /v2/timeseries-references/{refAppId}/annotations/{annotationAppId}` " +
      "to update, or `DELETE ...` to remove."
  )
  @APIResponse(
    responseCode = "200",
    description = "TimeseriesAnnotationIO record for the requested annotation.",
    content = @Content(schema = @Schema(implementation = TimeseriesAnnotationIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required (no JWT or X-API-KEY).")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No TimeseriesReference with `refAppId`, or no annotation with `annotationAppId`.")
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
  @Operation(
    summary = "Update an annotation (merge-patch: only non-null provided fields are changed).",
    description =
      "Applies a partial update to the `:TimeseriesAnnotation` identified by " +
      "`annotationAppId` within the reference `refAppId`. Only fields present and " +
      "non-null in the request body are applied; absent or null fields are left unchanged " +
      "on the stored entity.\n\n" +
      "Patchable fields: `startNs` (long), `endNs` (long, set to null to convert a " +
      "range annotation to a point), `label` (string, non-blank required if provided), " +
      "`description` (string), `confidence` (float). `aiGenerated` cannot be updated via " +
      "PATCH — it is set at creation time only.\n\n" +
      "Example: widen the annotated window — " +
      "`{\"startNs\": 1700000000000000000, \"endNs\": 1700000005000000000}`.\n" +
      "Example: relabel an existing annotation — `{\"label\": \"confirmed-spike\"}`.\n\n" +
      "Auth: Write permission on the parent DataObject (inherited from its Collection)."
  )
  @APIResponse(
    responseCode = "200",
    description = "TimeseriesAnnotationIO reflecting the state after the patch was applied.",
    content = @Content(schema = @Schema(implementation = TimeseriesAnnotationIO.class))
  )
  @APIResponse(responseCode = "400", description = "`label` is provided but is blank or whitespace-only.")
  @APIResponse(responseCode = "401", description = "Authentication required (no JWT or X-API-KEY).")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No TimeseriesReference with `refAppId`, or no annotation with `annotationAppId`.")
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
  @Operation(
    summary = "Delete an annotation from a TimeseriesReference.",
    description =
      "Removes the `:TimeseriesAnnotation` identified by `annotationAppId` (UUID v7) " +
      "from the `:TimeseriesReference` identified by `refAppId`. Both the graph node " +
      "and its `HAS_ANNOTATION` relationship to the reference are deleted.\n\n" +
      "Auth: Write permission on the parent DataObject (inherited from its Collection). " +
      "Returns 404 when either `refAppId` does not resolve to a known reference or " +
      "`annotationAppId` does not resolve to a known annotation.\n\n" +
      "Idempotency: the call returns 404 (not 204) if the annotation is already gone."
  )
  @APIResponse(responseCode = "204", description = "Annotation node and its relationship to the reference deleted.")
  @APIResponse(responseCode = "401", description = "Authentication required (no JWT or X-API-KEY).")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No TimeseriesReference with `refAppId`, or no annotation with `annotationAppId`.")
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
