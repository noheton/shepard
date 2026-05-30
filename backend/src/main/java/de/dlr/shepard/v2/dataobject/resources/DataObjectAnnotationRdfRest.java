package de.dlr.shepard.v2.dataobject.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.v2.annotations.daos.SemanticAnnotationV2DAO;
import de.dlr.shepard.v2.annotations.util.TurtleAnnotationSerializer;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * SHAPES-V-PREFILL-2-RDF-ENDPOINT — exposes all semantic annotations on a
 * DataObject as a Turtle document so that the SHACL validator UI can
 * auto-fill its data-graph textarea from a DataObject's appId.
 *
 * <p>Path: {@code GET /v2/data-objects/{appId}/rdf}
 * <br>Produces: {@code text/turtle}
 * <br>Auth: caller must be able to Read the DataObject's parent Collection
 * (inherited permissions — same gate as other DataObject endpoints).
 *
 * <p>Aggregates all {@code :SemanticAnnotation} nodes whose
 * {@code subjectAppId} equals {@code {appId}}. If the DataObject has no
 * annotations, a valid Turtle document containing only the standard prefix
 * declarations is returned (still HTTP 200 — empty is not an error).
 *
 * <p>Turtle shape follows §3.3 of
 * {@code aidocs/semantics/100-consistent-semantic-annotation-design.md}:
 * one flat triple + one OA annotation block per annotation, with the
 * prefix block emitted once at the document head.
 *
 * <p>API-version policy (CLAUDE.md): new endpoint under {@code /v2/}
 * — does not affect the frozen {@code /shepard/api/...} surface.
 */
@Path("/v2/data-objects")
@RequestScoped
@Authenticated
@Tag(name = "DataObjects (v2)")
public class DataObjectAnnotationRdfRest {

  static final String PROBLEM_TYPE_NOT_FOUND = "/problems/dataobject.not-found";
  static final String PROBLEM_TYPE_FORBIDDEN  = "/problems/dataobject.forbidden";

  @Inject
  SemanticAnnotationV2DAO annotationDAO;

  @Inject
  PermissionsService permissionsService;

  @Inject
  EntityIdResolver entityIdResolver;

  /**
   * Return all semantic annotations for the DataObject as a Turtle document.
   *
   * <p>Empty DataObject (0 annotations) → 200 with prefix-only Turtle.
   * Unknown appId → 404. No Read permission → 403.
   */
  @GET
  @Path("/{appId}/rdf")
  @Produces("text/turtle")
  @Operation(
    summary = "Export a DataObject's semantic annotations as Turtle.",
    description =
      "Returns a Turtle document aggregating all `:SemanticAnnotation` nodes " +
      "whose `subjectAppId` equals `{appId}`. The document follows the §3.3 " +
      "shape from `aidocs/semantics/100`: one flat triple (`<subjectIri> " +
      "<predicateIri> <objectValue> .`) plus an OA-framed `oa:Annotation` block " +
      "per annotation, with the standard prefix declarations emitted once.\n\n" +
      "Use this endpoint to auto-fill the data-graph textarea of the SHACL " +
      "validator at `/shapes/validate?focusAppId={appId}`.\n\n" +
      "When the DataObject has no annotations, a valid Turtle document containing " +
      "only the prefix declarations is returned (HTTP 200, not 404).\n\n" +
      "Auth: the caller must have Read permission on the DataObject's parent " +
      "Collection (DataObjects inherit Collection permissions)."
  )
  @APIResponse(responseCode = "200", description = "Turtle document (may be prefix-only when 0 annotations).",
    content = @Content(mediaType = "text/turtle"))
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the parent Collection.")
  @APIResponse(responseCode = "404", description = "No DataObject with that appId.")
  public Response getRdf(
    @PathParam("appId") String appId,
    @Context SecurityContext sc
  ) {
    String caller = callerName(sc);
    if (caller == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    // Resolve appId → verify the entity exists
    try {
      entityIdResolver.resolveLong(appId);
    } catch (NotFoundException nfe) {
      return problem(PROBLEM_TYPE_NOT_FOUND, "Not found",
        Response.Status.NOT_FOUND,
        "DataObject with appId '" + appId + "' not found.");
    }

    // Permission gate: walk DataObject → parent Collection
    if (!permissionsService.isAccessAllowedForDataObjectAppId(appId, AccessType.Read, caller)) {
      return problem(PROBLEM_TYPE_FORBIDDEN, "Access denied",
        Response.Status.FORBIDDEN,
        "Caller '" + caller + "' lacks Read permission on the DataObject's parent Collection.");
    }

    // Fetch all annotations where subjectAppId = appId.
    // Practical upper cap of 10 000: a DataObject with more annotations
    // than this is an extreme edge case; the Turtle document would be
    // unwieldy in the UI anyway. Cap keeps the Cypher LIMIT sane.
    List<SemanticAnnotation> annotations = annotationDAO.findFiltered(
      appId, null, null, null, 0, 10_000
    );

    String turtle = TurtleAnnotationSerializer.toAggregatedTurtle(annotations);
    return Response.ok(turtle, "text/turtle").build();
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  private static String callerName(SecurityContext sc) {
    if (sc == null || sc.getUserPrincipal() == null) return null;
    String n = sc.getUserPrincipal().getName();
    return n == null || n.isBlank() ? null : n;
  }

  private static Response problem(String type, String title, Response.Status status, String detail) {
    ProblemJson body = new ProblemJson(type, title, status.getStatusCode(), detail, null);
    return Response.status(status).type("application/problem+json").entity(body).build();
  }
}
