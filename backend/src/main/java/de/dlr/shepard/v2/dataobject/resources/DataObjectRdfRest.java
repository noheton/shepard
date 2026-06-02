package de.dlr.shepard.v2.dataobject.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.v2.annotations.daos.SemanticAnnotationV2DAO;
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
 * SHAPES-V-PREFILL-2 — {@code GET /v2/data-objects/{appId}/rdf}.
 *
 * <p>Returns all {@link SemanticAnnotation} triples for a given DataObject
 * as an OA-framed Turtle document. This is the backend half consumed by
 * the {@code validate.vue} frontend page when {@code ?focusAppId=} is present.
 *
 * <p>The Turtle shape follows §3.3 of
 * {@code aidocs/semantics/100-consistent-semantic-annotation-design.md}:
 * one flat triple and one OA {@code oa:Annotation} node per annotation.
 *
 * <p>When no annotations exist for the DataObject the response is still
 * HTTP 200 with a valid (prefix-only) Turtle document — the empty set is a
 * legitimate state, not an error.
 *
 * <p><b>Auth.</b> Read permission on the parent Collection (DataObjects
 * inherit Collection permissions; there is no per-DataObject permission node).
 * Returns 404 when the DataObject appId is unknown, 401 when the caller is
 * unauthenticated, 403 when they lack Read access.
 *
 * <p><b>API-version policy.</b> This endpoint lives at {@code /v2/} — it is
 * part of the fork's development surface and must never be added to the
 * frozen {@code /shepard/api/...} compat surface.
 */
@Path("/v2/data-objects")
@RequestScoped
@Authenticated
@Tag(name = "DataObjects (v2)")
public class DataObjectRdfRest {

  @Inject
  SemanticAnnotationV2DAO annotationDAO;

  @Inject
  PermissionsService permissionsService;

  @Inject
  EntityIdResolver entityIdResolver;

  // ─── endpoint ─────────────────────────────────────────────────────────────

  @GET
  @Path("/{appId}/rdf")
  @Produces("text/turtle")
  @Operation(
    summary = "Export all semantic annotations for a DataObject as Turtle RDF.",
    description =
      "Returns all `:SemanticAnnotation` triples attached to the DataObject " +
      "identified by `appId` as an OA-framed Turtle document.\n\n" +
      "The shape follows §3.3 of " +
      "`aidocs/semantics/100-consistent-semantic-annotation-design.md`: " +
      "one flat triple and one W3C Open Annotations ({@code oa:Annotation}) " +
      "node per annotation. Prefix declarations ({@code oa:}, {@code prov:}, " +
      "{@code rdf:}, {@code sh:}, {@code shepard:}) are always emitted.\n\n" +
      "When the DataObject has no annotations the response is still HTTP 200 " +
      "with a valid (prefix-only) Turtle document.\n\n" +
      "This endpoint is consumed by the {@code validate.vue} frontend page " +
      "when {@code ?focusAppId=} is present (SHAPES-V-PREFILL-2).\n\n" +
      "Auth: Read permission on the parent Collection (DataObjects inherit " +
      "Collection permissions). Returns 404 for unknown appId."
  )
  @APIResponse(
    responseCode = "200",
    description = "Turtle document containing all SemanticAnnotation triples for the DataObject. " +
      "Valid Turtle even when the annotation set is empty (prefix-only document).",
    content = @Content(mediaType = "text/turtle")
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the parent Collection.")
  @APIResponse(responseCode = "404", description = "No DataObject with that appId.")
  public Response getDataObjectRdf(
      @PathParam("appId") String appId,
      @Context SecurityContext sc) {

    // ── auth gate ──────────────────────────────────────────────────────────
    String caller = sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
    if (caller == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    // ── existence check ────────────────────────────────────────────────────
    // EntityIdResolver throws NotFoundException when the appId is unknown.
    try {
      entityIdResolver.resolveLong(appId);
    } catch (NotFoundException nfe) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    // ── permission check ───────────────────────────────────────────────────
    // DataObjects have no own :Permissions node; access is inherited from the
    // parent Collection. The helper walks DO → Collection via Cypher.
    if (!permissionsService.isAccessAllowedForDataObjectAppId(appId, AccessType.Read, caller)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    // ── fetch annotations & build Turtle ──────────────────────────────────
    List<SemanticAnnotation> annotations = annotationDAO.findBySubjectAppId(appId);
    String turtle = buildTurtleDocument(appId, annotations);

    return Response.ok(turtle, "text/turtle").build();
  }

  // ─── Turtle serialisation helpers ────────────────────────────────────────

  /**
   * Builds a complete Turtle document for all annotations on the given DataObject.
   *
   * <p>Emits shared prefix declarations once, then one block per annotation.
   * An empty annotation list yields a valid prefix-only document.
   *
   * <p>Uses the same §3.3 OA-frame shape as
   * {@link de.dlr.shepard.v2.annotations.resources.SemanticAnnotationV2Rest#buildTurtle}.
   */
  static String buildTurtleDocument(String dataObjectAppId, List<SemanticAnnotation> annotations) {
    StringBuilder sb = new StringBuilder();

    // Shared prefix declarations
    sb.append("@prefix oa: <http://www.w3.org/ns/oa#> .\n");
    sb.append("@prefix prov: <http://www.w3.org/ns/prov#> .\n");
    sb.append("@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n");
    sb.append("@prefix sh: <http://www.w3.org/ns/shacl#> .\n");
    sb.append("@prefix shepard: <https://shepard.dlr.de/v2/> .\n");

    if (annotations == null || annotations.isEmpty()) {
      return sb.toString();
    }

    sb.append("\n");

    for (SemanticAnnotation a : annotations) {
      sb.append(buildAnnotationBlock(a));
      sb.append("\n");
    }

    return sb.toString();
  }

  /**
   * Renders one annotation as a pair of Turtle stanzas (flat triple + OA frame).
   *
   * <p>Mirrors the private {@code buildTurtle(SemanticAnnotation)} helper in
   * {@link de.dlr.shepard.v2.annotations.resources.SemanticAnnotationV2Rest},
   * but without the per-annotation prefix block (those are emitted once by
   * {@link #buildTurtleDocument}).
   */
  static String buildAnnotationBlock(SemanticAnnotation a) {
    String subjectIri = shepardIri(a.getSubjectKind(), a.getSubjectAppId());
    String predicateIri = nvl(a.getPropertyIRI(), "http://www.w3.org/1999/02/22-rdf-syntax-ns#predicate");
    String annotationIri = "shepard:Annotation/" + nvl(a.getAppId(), "unknown");
    String activityIri = blank(a.getSourceActivityAppId())
        ? null
        : "shepard:Activity/" + a.getSourceActivityAppId();

    // object value — IRI or literal
    String objectValue = a.getValueIRI() != null
        ? "<" + a.getValueIRI() + ">"
        : "\"" + escapeTurtleLiteral(nvl(a.getValueName(), "")) + "\"";

    StringBuilder sb = new StringBuilder();

    // Flat triple (§3.3 line 1)
    sb.append("<").append(subjectIri).append("> <").append(predicateIri).append("> ")
        .append(objectValue).append(" .\n");
    sb.append("\n");

    // OA-shaped annotation (§3.3 lines 2-5)
    sb.append("<").append(annotationIri).append("> a oa:Annotation ;\n");
    sb.append("    oa:hasTarget <").append(subjectIri).append("> ;\n");
    sb.append("    oa:hasBody [ rdf:value ").append(objectValue)
        .append(" ; sh:path <").append(predicateIri).append("> ]");

    if (activityIri != null) {
      sb.append(" ;\n    prov:wasGeneratedBy <").append(activityIri).append(">");
    }
    sb.append(" .\n");

    return sb.toString();
  }

  // ── tiny helpers — package-private so tests can reach them ────────────────

  static String shepardIri(String kind, String appId) {
    if (blank(kind) || blank(appId)) {
      return "https://shepard.dlr.de/v2/entities/" + nvl(appId, "unknown");
    }
    return "https://shepard.dlr.de/v2/" + kind.toLowerCase() + "s/" + appId;
  }

  static String escapeTurtleLiteral(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
  }

  private static boolean blank(String s) {
    return s == null || s.isBlank();
  }

  private static String nvl(String s, String fallback) {
    return s == null || s.isBlank() ? fallback : s;
  }
}
