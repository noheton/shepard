package de.dlr.shepard.v2.dataobject.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.semantic.daos.SemanticAnnotationDAO;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.time.Instant;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * SHAPES-V-PREFILL-2-RDF-ENDPOINT — flat {@code GET /v2/data-objects/{appId}/rdf}
 * resource that returns a tight Turtle subgraph for a DataObject. The
 * subgraph is shaped for the SHACL validation playground
 * ({@code /shapes/validate}): focus node + first-level neighbors, not
 * the full universe.
 *
 * <p><b>Why a separate flat resource.</b> The companion collection-scoped
 * {@link DataObjectV2Rest} resource lives at
 * {@code /v2/collections/{cid}/data-objects/{doid}} and accepts the
 * Collection appId as part of the path. The SHACL playground reaches a
 * DataObject by appId only (the URL query carries
 * {@code ?focusAppId=<doAppId>} — no Collection context). Rather than
 * forcing the frontend to look up the Collection appId first, the flat
 * shape resolves the Collection back-edge server-side.
 *
 * <p><b>Subgraph emitted.</b>
 * <ul>
 *   <li>The DataObject node — typed {@code m4i:InvestigatedObject},
 *       {@code prov:Entity}; carries {@code dcterms:identifier},
 *       {@code dcterms:title}, {@code schema:dateCreated},
 *       {@code dcterms:description}.</li>
 *   <li>Every {@code :SemanticAnnotation} on the DataObject — flat
 *       triple ({@code <subject> <predicate> <object>}).</li>
 *   <li>Every direct predecessor / successor as
 *       {@code obo:RO_0002233} / {@code obo:RO_0002234} edges (target
 *       IRIs only — no nested neighbor bodies).</li>
 *   <li>The attached {@code :ShepardTemplate} via a
 *       {@code shepard:hasTemplate} edge when present.</li>
 * </ul>
 *
 * <p><b>What's not in scope.</b> No PROV-O Activity projection, no m4i
 * unit-aware NumericalVariable promotion, no channel-level
 * AnnotatableTimeseries walk. The shape validation playground is the
 * primary consumer; richer projections live on the
 * {@code Accept: application/ld+json; profile="metadata4ing"} branch of
 * the collection-scoped DataObject GET (M4I-c, see
 * {@code aidocs/semantics/94 §4.3}).
 *
 * <p><b>Auth.</b> Read on the parent Collection (inherited via
 * {@code PermissionsService#isAccessAllowedForDataObjectAppId}).
 *
 * <p><b>Cross-references.</b>
 * <ul>
 *   <li>Brief — {@code SHAPES-V-PREFILL-2-RDF-ENDPOINT} in {@code aidocs/16}.</li>
 *   <li>Sibling shape — {@code M4iDataObjectRenderer} (JSON-LD flavour).</li>
 *   <li>Hand-rolled Turtle precedent —
 *       {@code SemanticAnnotationV2Rest#buildTurtle}.</li>
 * </ul>
 */
@Path("/v2/data-objects")
@RequestScoped
@Authenticated
@Tag(name = "DataObjects (v2)")
public class DataObjectRdfRest {

  // ─── namespace constants (mirrored from M4iDataObjectRenderer to keep
  //     the Turtle prefix set identical to the JSON-LD flavour) ─────────
  static final String M4I_NS = "http://w3id.org/nfdi4ing/metadata4ing#";
  static final String OBO_NS = "http://purl.obolibrary.org/obo/";
  static final String PROV_NS = "http://www.w3.org/ns/prov#";
  static final String DCTERMS_NS = "http://purl.org/dc/terms/";
  static final String SCHEMA_NS = "http://schema.org/";
  static final String XSD_NS = "http://www.w3.org/2001/XMLSchema#";
  static final String SHEPARD_NS = "https://shepard.dlr.de/v2/";

  @Inject
  DataObjectDAO dataObjectDAO;

  @Inject
  SemanticAnnotationDAO semanticAnnotationDAO;

  @Inject
  PermissionsService permissionsService;

  @GET
  @Path("/{appId}/rdf")
  @Produces("text/turtle")
  @Operation(
    summary = "Return a Turtle subgraph for the DataObject (SHACL focus node).",
    description =
      "Serialises the DataObject + first-level neighbors + direct semantic " +
      "annotations + attached-template edge as Turtle. Designed to seed " +
      "the data-graph textarea of the SHACL validation playground " +
      "(`/shapes/validate?focusAppId=<doAppId>`).\n\n" +
      "The body is tight on purpose — focus node + edges to first-level " +
      "neighbors only; no nested neighbor bodies. Callers wanting the full " +
      "m4i flavour should request `Accept: application/ld+json; " +
      "profile=\"metadata4ing\"` on the collection-scoped GET.\n\n" +
      "Auth: Read on the parent Collection. Returns 404 when no such " +
      "DataObject exists, 403 when the caller lacks Read."
  )
  @APIResponse(responseCode = "200", description = "Turtle document.", content = @Content(mediaType = "text/turtle"))
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the parent Collection.")
  @APIResponse(responseCode = "404", description = "No DataObject with that appId.")
  public Response getRdf(
    @PathParam("appId") @NotBlank String appId,
    @Context SecurityContext sc
  ) {
    String caller = sc != null && sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
    if (caller == null || caller.isBlank()) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    DataObject d = dataObjectDAO.findByAppId(appId);
    if (d == null || d.isDeleted()) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    if (!permissionsService.isAccessAllowedForDataObjectAppId(appId, AccessType.Read, caller)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    // Annotation lookup is a separate DAO hop (see M4iDataObjectRenderer
    // for the precedent — direct annotations on the subject, not the
    // multi-hop AnnotatableTimeseries walk).
    List<SemanticAnnotation> annotations;
    try {
      annotations = semanticAnnotationDAO.findBySubjectAppId(appId);
    } catch (RuntimeException ex) {
      // Fail-soft per the CLAUDE.md "secondary reads must not 500" rule
      // — an unreachable annotation index shouldn't block the focus
      // subgraph from rendering for validation.
      annotations = List.of();
    }

    String turtle = buildTurtle(d, annotations);
    return Response.ok(turtle, "text/turtle")
      .header("Cache-Control", "max-age=60, must-revalidate")
      .build();
  }

  // ─── pure Turtle builder (unit-testable) ────────────────────────────

  /**
   * Build the focus subgraph Turtle for a DataObject. Pure function —
   * no DAO, no permissions, no I/O. Visible-for-testing.
   */
  static String buildTurtle(DataObject d, List<SemanticAnnotation> annotations) {
    StringBuilder sb = new StringBuilder(512);
    sb.append("@prefix dcterms: <").append(DCTERMS_NS).append("> .\n");
    sb.append("@prefix m4i: <").append(M4I_NS).append("> .\n");
    sb.append("@prefix obo: <").append(OBO_NS).append("> .\n");
    sb.append("@prefix prov: <").append(PROV_NS).append("> .\n");
    sb.append("@prefix schema: <").append(SCHEMA_NS).append("> .\n");
    sb.append("@prefix shepard: <").append(SHEPARD_NS).append("> .\n");
    sb.append("@prefix xsd: <").append(XSD_NS).append("> .\n");
    sb.append("\n");

    String doIri = doIri(d.getAppId());
    sb.append("<").append(doIri).append(">\n");
    sb.append("    a m4i:InvestigatedObject, prov:Entity");

    if (d.getAppId() != null) {
      sb.append(" ;\n    dcterms:identifier \"").append(escape(d.getAppId())).append("\"");
    }
    if (d.getName() != null && !d.getName().isBlank()) {
      sb.append(" ;\n    dcterms:title \"").append(escape(d.getName())).append("\"");
    }
    if (d.getDescription() != null && !d.getDescription().isBlank()) {
      sb.append(" ;\n    dcterms:description \"").append(escape(d.getDescription())).append("\"");
    }
    if (d.getCreatedAt() != null) {
      sb.append(" ;\n    schema:dateCreated \"")
        .append(Instant.ofEpochMilli(d.getCreatedAt().getTime()).toString())
        .append("\"^^xsd:dateTime");
    }

    // First-level predecessors / successors → target IRIs only.
    List<DataObject> preds = d.getPredecessors();
    if (preds != null) {
      for (DataObject p : preds) {
        if (p == null || p.isDeleted() || p.getAppId() == null) continue;
        sb.append(" ;\n    obo:RO_0002233 <").append(doIri(p.getAppId())).append(">");
      }
    }
    List<DataObject> succs = d.getSuccessors();
    if (succs != null) {
      for (DataObject s : succs) {
        if (s == null || s.isDeleted() || s.getAppId() == null) continue;
        sb.append(" ;\n    obo:RO_0002234 <").append(doIri(s.getAppId())).append(">");
      }
    }

    // Attached :ShepardTemplate — TOOLS-CONTEXT-DO-TEMPLATE-DETECT-1.
    String templateAppId = d.getAttachedTemplateAppId();
    if (templateAppId != null && !templateAppId.isBlank()) {
      sb.append(" ;\n    shepard:hasTemplate <")
        .append(SHEPARD_NS).append("templates/").append(escape(templateAppId)).append(">");
    }

    sb.append(" .\n");

    // SemanticAnnotations — flat triple form, one per annotation.
    if (annotations != null && !annotations.isEmpty()) {
      sb.append("\n");
      for (SemanticAnnotation a : annotations) {
        if (a == null) continue;
        String predicate = a.getPropertyIRI();
        if (predicate == null || predicate.isBlank()) continue;
        String object = annotationObject(a);
        if (object == null) continue;
        sb.append("<").append(doIri).append("> <").append(predicate).append("> ")
          .append(object).append(" .\n");
      }
    }

    return sb.toString();
  }

  // ─── tiny helpers ────────────────────────────────────────────────────

  private static String doIri(String appId) {
    return SHEPARD_NS + "dataobjects/" + (appId == null ? "anon" : appId);
  }

  /**
   * Render a {@link SemanticAnnotation}'s object position. IRI value
   * wins; numeric literal next; plain string literal last. Null when
   * the annotation has no resolvable object.
   */
  private static String annotationObject(SemanticAnnotation a) {
    String iri = a.getValueIRI();
    if (iri != null && !iri.isBlank()) {
      return "<" + iri + ">";
    }
    Double num = a.getNumericValue();
    if (num != null) {
      return "\"" + num + "\"^^xsd:double";
    }
    String literal = a.getValueName();
    if (literal != null) {
      return "\"" + escape(literal) + "\"";
    }
    return null;
  }

  /** Turtle literal escape — matches {@code SemanticAnnotationV2Rest}. */
  private static String escape(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r");
  }
}
