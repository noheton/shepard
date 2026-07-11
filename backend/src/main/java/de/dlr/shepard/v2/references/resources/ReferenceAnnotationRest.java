package de.dlr.shepard.v2.references.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import static de.dlr.shepard.v2.common.ProblemResponse.problem;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import de.dlr.shepard.v2.references.services.ReferencesV2Service;
import de.dlr.shepard.v2.references.services.ReferencesV2Service.ResolvedReference;
import de.dlr.shepard.v2.references.spi.ReferenceKindHandler;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * APISIMP-ANNOTATION-SUBRESOURCE-COLLISION — unified annotation sub-resource
 * for all reference kinds that support annotations.
 *
 * <p>Replaces the two colliding resources:
 * <ul>
 *   <li>{@code TimeseriesAnnotationRest} (core) — was {@code @Path("/v2/references/{appId}/annotations")}</li>
 *   <li>{@code VideoAnnotationRest} (video plugin) — was {@code @Path("/v2/references/{refAppId}/annotations")}</li>
 * </ul>
 * Both claimed the same JAX-RS path pattern, silently dropping one. This class is the single
 * authority at that path; per-kind logic is dispatched via
 * {@link ReferenceKindHandler#supportsAnnotations()} and the five annotation CRUD methods.
 *
 * <p>Auth: Read permission on the parent DataObject for GET; Write for POST/PATCH/DELETE.
 * Permission is resolved against the reference's parent DataObject appId via
 * {@link PermissionsService#isAccessAllowedForDataObjectAppId}.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/references/{appId}/annotations")
@RequestScoped
@Tag(name = "References")
public class ReferenceAnnotationRest {

  private static final String PT_UNAUTHORIZED = "/problems/reference-annotations.unauthorized";
  private static final String PT_FORBIDDEN    = "/problems/reference-annotations.forbidden";
  private static final String PT_NOT_FOUND    = "/problems/reference-annotations.not-found";
  private static final String PT_BAD_REQUEST  = "/problems/reference-annotations.bad-request";

  @Inject
  ReferencesV2Service referencesService;

  @Inject
  PermissionsService permissionsService;

  // ─── helpers ─────────────────────────────────────────────────────────────

  private String callerOrNull(SecurityContext sc) {
    return sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
  }

  /**
   * Resolves the reference + handler, checks that the kind supports annotations,
   * gates access, then calls {@code action}. Returns a short-circuit Response
   * for any auth/not-found/unsupported failure; otherwise returns the action result.
   */
  private Response gateAndDispatch(
    String appId,
    AccessType accessType,
    SecurityContext sc,
    Function<ResolvedReference, Response> action
  ) {
    String caller = callerOrNull(sc);
    if (caller == null) {
      return problem(PT_UNAUTHORIZED, "Unauthorized", Response.Status.UNAUTHORIZED, "Authentication required");
    }

    Optional<ResolvedReference> opt = referencesService.resolveByAppId(appId);
    if (opt.isEmpty()) {
      return problem(PT_NOT_FOUND, "Not Found", Response.Status.NOT_FOUND, "No reference with appId " + appId);
    }

    ResolvedReference r = opt.get();
    if (!r.handler().supportsAnnotations()) {
      return problem(PT_NOT_FOUND, "Not Found", Response.Status.NOT_FOUND,
        "Reference kind='" + r.handler().kind() + "' does not support annotations");
    }

    DataObject parent = r.reference().getDataObject();
    if (parent == null) {
      return problem(PT_NOT_FOUND, "Not Found", Response.Status.NOT_FOUND,
        "Reference parent DataObject is missing (graph inconsistency)");
    }
    String doAppId = parent.getAppId();
    boolean permitted;
    if (doAppId != null) {
      permitted = permissionsService.isAccessAllowedForDataObjectAppId(doAppId, accessType, caller);
    } else {
      permitted = permissionsService.isAccessTypeAllowedForUser(parent.getId(), accessType, caller);
    }
    if (!permitted) {
      return problem(PT_FORBIDDEN, "Forbidden", Response.Status.FORBIDDEN,
        "Caller lacks required access on the parent DataObject");
    }

    return action.apply(r);
  }

  // ─── endpoints ───────────────────────────────────────────────────────────

  @GET
  @Operation(
    operationId = "listReferenceAnnotationsV2",
    summary = "List all annotations on a reference.",
    description =
      "Returns annotations attached to the reference identified by `appId` (UUID v7). " +
      "The response shape is kind-specific: timeseries annotations carry `startNs`/`endNs` " +
      "(nanoseconds since Unix epoch); video annotations carry `startSeconds`/`endSeconds` " +
      "(seconds from the start of the video). Common fields across all kinds: `appId`, " +
      "`label`, `description`, `aiGenerated`, `confidence`.\n\n" +
      "Pagination: `?page=0&pageSize=200` (default). `pageSize` is capped at 200.\n\n" +
      "Returns 404 when no reference with that appId exists, or when the reference kind " +
      "does not support annotations. Auth: Read permission on the parent DataObject."
  )
  @APIResponse(
    responseCode = "200",
    description = "Paged envelope: items + total + page + pageSize. Response body `total` carries the count.",
    content = @Content(schema = @Schema(implementation = PagedResponseIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No reference with that appId, or kind does not support annotations.")
  public Response list(
    @PathParam("appId") String appId,
    @Parameter(description = "Zero-based page index (default 0).") @QueryParam("page") @DefaultValue("0") @PositiveOrZero int page,
    @Parameter(description = "Items per page (1–200). Default 200.") @QueryParam("pageSize") @DefaultValue("200") @Min(1) @Max(200) int pageSize,
    @Context SecurityContext sc
  ) {
    return gateAndDispatch(appId, AccessType.Read, sc, r -> {
      long total = r.handler().countAnnotations(appId);
      long skip = (long) page * pageSize;
      List<Map<String, Object>> slice = r.handler().listAnnotations(appId, skip, pageSize);
      return Response.ok(new PagedResponseIO<>(slice, total, page, pageSize))
          .build();
    });
  }

  @POST
  @Operation(
    operationId = "createReferenceAnnotationV2",
    summary = "Create an annotation on a reference.",
    description =
      "Creates an annotation on the reference identified by `appId`. The body is kind-specific:\n\n" +
      "**Timeseries** (`kind=timeseries`): `startNs` (long, required), `endNs` (long, optional — " +
      "omit for a point annotation), `label` (string, required, non-blank), `description` (optional), " +
      "`aiGenerated` (boolean, default false), `confidence` (double [0.0, 1.0], optional).\n\n" +
      "**Video** (`kind=video`): `startSeconds` (double, required), `endSeconds` (double, optional), " +
      "`label` (string, required, non-blank), `description` (optional), `aiGenerated`, `confidence`.\n\n" +
      "Auth: Write permission on the parent DataObject."
  )
  @APIResponse(responseCode = "201", description = "Annotation created; body is the new annotation map.")
  @APIResponse(responseCode = "400", description = "Required fields missing or invalid.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No reference with that appId, or kind does not support annotations.")
  public Response create(@PathParam("appId") String appId, Map<String, Object> body, @Context SecurityContext sc) {
    return gateAndDispatch(appId, AccessType.Write, sc, r -> {
      try {
        Map<String, Object> created = r.handler().createAnnotation(appId, body);
        return Response.status(Response.Status.CREATED).entity(created).build();
      } catch (BadRequestException e) {
        return problem(PT_BAD_REQUEST, "Bad Request", Response.Status.BAD_REQUEST, e.getMessage());
      }
    });
  }

  @GET
  @Path("/{annotationAppId}")
  @Operation(
    operationId = "getReferenceAnnotationV2",
    summary = "Get a single annotation by appId.",
    description =
      "Returns the annotation identified by `annotationAppId` within the reference `appId`. " +
      "Auth: Read permission on the parent DataObject."
  )
  @APIResponse(responseCode = "200", description = "Annotation map.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No reference or annotation with those appIds.")
  public Response get(
    @PathParam("appId") String appId,
    @PathParam("annotationAppId") String annotationAppId,
    @Context SecurityContext sc
  ) {
    return gateAndDispatch(appId, AccessType.Read, sc, r -> {
      try {
        return Response.ok(r.handler().getAnnotation(appId, annotationAppId)).build();
      } catch (NotFoundException e) {
        return problem(PT_NOT_FOUND, "Not Found", Response.Status.NOT_FOUND, e.getMessage());
      }
    });
  }

  @PATCH
  @Path("/{annotationAppId}")
  @Operation(
    operationId = "patchReferenceAnnotationV2",
    summary = "Partially update an annotation (merge-patch).",
    description =
      "Applies a merge-patch to the annotation. Only non-null fields in the body are applied; " +
      "absent keys leave the stored values unchanged. `aiGenerated` cannot be patched — it is " +
      "set at creation time only. Auth: Write permission on the parent DataObject."
  )
  @APIResponse(responseCode = "200", description = "Post-patch annotation map.")
  @APIResponse(responseCode = "400", description = "Invalid field value (e.g. blank label).")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No reference or annotation with those appIds.")
  public Response patch(
    @PathParam("appId") String appId,
    @PathParam("annotationAppId") String annotationAppId,
    Map<String, Object> body,
    @Context SecurityContext sc
  ) {
    return gateAndDispatch(appId, AccessType.Write, sc, r -> {
      try {
        return Response.ok(r.handler().patchAnnotation(appId, annotationAppId, body)).build();
      } catch (BadRequestException e) {
        return problem(PT_BAD_REQUEST, "Bad Request", Response.Status.BAD_REQUEST, e.getMessage());
      } catch (NotFoundException e) {
        return problem(PT_NOT_FOUND, "Not Found", Response.Status.NOT_FOUND, e.getMessage());
      }
    });
  }

  @DELETE
  @Path("/{annotationAppId}")
  @Operation(
    operationId = "deleteReferenceAnnotationV2",
    summary = "Delete an annotation from a reference.",
    description =
      "Removes the annotation identified by `annotationAppId` from the reference `appId`. " +
      "Auth: Write permission on the parent DataObject."
  )
  @APIResponse(responseCode = "204", description = "Annotation deleted.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No reference or annotation with those appIds.")
  public Response delete(
    @PathParam("appId") String appId,
    @PathParam("annotationAppId") String annotationAppId,
    @Context SecurityContext sc
  ) {
    return gateAndDispatch(appId, AccessType.Write, sc, r -> {
      try {
        r.handler().deleteAnnotation(appId, annotationAppId);
        return Response.noContent().build();
      } catch (NotFoundException e) {
        return problem(PT_NOT_FOUND, "Not Found", Response.Status.NOT_FOUND, e.getMessage());
      }
    });
  }
}
