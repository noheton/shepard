package de.dlr.shepard.v2.template.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.template.daos.ShepardTemplateDAO;
import de.dlr.shepard.template.entities.ShepardTemplate;
import de.dlr.shepard.template.services.TemplateInheritanceResolver;
import de.dlr.shepard.v2.template.io.TemplateFormDescriptorIO;
import de.dlr.shepard.v2.template.services.FormDescriptorCompiler;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * {@code GET /v2/templates/{templateAppId}/form} — the form-descriptor
 * endpoint (FORM-DESCRIPTOR-1, doc 125 §5.1 / BTKVS-B2).
 *
 * <p>Read-only projection: flattens the template's inheritance chain
 * ({@link TemplateInheritanceResolver}), extracts the {@code shapeGraph}
 * Turtle, and compiles it via {@link FormDescriptorCompiler} into a
 * renderer-friendly descriptor. No new entity, no new store, no write
 * surface — the entire feature is a GET projection over existing state
 * (doc 125 §3.3, the minimalist's win).
 *
 * <p><b>Status codes</b> (doc 125 §5.1): 401 unauthenticated · 404 unknown
 * template · 409 retired template · 422 when the flattened body carries no
 * {@code shapeGraph}, the Turtle is unparseable, or the template is not a
 * data kind (a legacy attribute-bag template / a VIEW_RECIPE is not
 * form-renderable; the message points at the shape builder).
 *
 * <p><b>Caching.</b> Weak ETag over template appId + flattened-body hash;
 * {@code If-None-Match} → 304.
 *
 * <p><b>Deviation note (written justification per BTKVS-B2):</b> the optional
 * {@code ?focusAppId=} edit-form prefill from doc 125 §5.1 is deferred — it
 * needs the entity-resolution seam the SHAPES-APPLICABLE-FORMS row brings.
 * The FORM-DESCRIPTOR-1 backlog row tracks the residue.
 */
@Produces(MediaType.APPLICATION_JSON)
@Path("/v2/templates/{templateAppId}/form")
@RequestScoped
@Tag(name = "Templates")
public class TemplateFormRest {

  private static final String PT_NOT_FOUND    = "/problems/templates.not-found";
  private static final String PT_CONFLICT     = "/problems/templates.conflict";
  private static final String PT_UNPROCESSABLE = "/problems/templates.unprocessable";
  private static final String PT_UNAUTHORIZED = "/problems/templates.unauthorized";

  @Inject
  ShepardTemplateDAO templateDAO;

  @Inject
  TemplateInheritanceResolver inheritanceResolver;

  @Inject
  FormDescriptorCompiler compiler;

  @Inject
  ObjectMapper objectMapper;

  @GET
  @Operation(
    operationId = "getTemplateForm",
    summary = "Compile a data-kind template's SHACL shape into a form descriptor.",
    description = "A form is the write-direction projection of a data-kind shape (doc 125 D1): the same " +
    "flattened shapeGraph the instantiation endpoint validates server-side is compiled here into " +
    "groups + fields (with DASH editor hints and constraint-scoring defaults) + a server-computed " +
    "submit block. fields[].path is byte-identical to the violations[].path entries the submit " +
    "leg's 422 returns, so mapping a violation to a field is a dictionary lookup."
  )
  @APIResponse(
    responseCode = "200",
    description = "The compiled form descriptor.",
    content = @Content(schema = @Schema(implementation = TemplateFormDescriptorIO.class))
  )
  @APIResponse(responseCode = "304", description = "Not modified (If-None-Match matched the ETag).")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "404", description = "No template with that appId.")
  @APIResponse(responseCode = "409", description = "Template is retired.")
  @APIResponse(
    responseCode = "422",
    description = "Template is not form-renderable: no shapeGraph in the flattened body, unparseable Turtle, or a non-data templateKind."
  )
  public Response getForm(
    @PathParam("templateAppId") String templateAppId,
    @HeaderParam("If-None-Match") String ifNoneMatch,
    @Context SecurityContext securityContext
  ) {
    if (securityContext.getUserPrincipal() == null) {
      return problem(PT_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED.getStatusCode(), null);
    }

    Optional<ShepardTemplate> templateOpt = templateDAO.findByAppId(templateAppId);
    if (templateOpt.isEmpty()) {
      return problem(PT_NOT_FOUND, "Template not found", Response.Status.NOT_FOUND.getStatusCode(),
        "No template with appId " + templateAppId);
    }
    ShepardTemplate template = templateOpt.get();

    if (template.isRetired()) {
      return problem(PT_CONFLICT, "Template retired", Response.Status.CONFLICT.getStatusCode(),
        "Template " + templateAppId + " is retired; pick a non-retired version");
    }

    if (template.getTemplateKind() == null ||
        !FormDescriptorCompiler.FORM_RENDERABLE_KINDS.contains(template.getTemplateKind())) {
      return problem(PT_UNPROCESSABLE, "Template not form-renderable", 422,
        "templateKind " + template.getTemplateKind() + " is not a data kind — forms are the " +
        "write-direction projection of DATAOBJECT_RECIPE / COLLECTION_RECIPE / STRUCTURED_RECIPE " +
        "shapes (doc 125 D1)");
    }

    String effectiveBody = inheritanceResolver.flattenBody(template);
    String shapeGraph = extractShapeGraph(effectiveBody);
    if (shapeGraph == null || shapeGraph.isBlank()) {
      return problem(PT_UNPROCESSABLE, "Template carries no shapeGraph", 422,
        "The flattened body of template " + templateAppId + " has no shapeGraph Turtle — author one " +
        "via POST /v2/shapes/build (the JSON-DSL shape builder) and store it on the template body");
    }

    String etag = etagFor(templateAppId, effectiveBody);
    if (etag != null && etag.equals(ifNoneMatch)) {
      return Response.status(Response.Status.NOT_MODIFIED).header("ETag", etag).build();
    }

    TemplateFormDescriptorIO descriptor;
    try {
      descriptor = compiler.compile(template, shapeGraph);
    } catch (IllegalArgumentException ex) {
      return problem(PT_UNPROCESSABLE, "Shape graph not compilable", 422,
        "Template " + templateAppId + ": " + ex.getMessage());
    }

    Response.ResponseBuilder rb = Response.ok(descriptor);
    if (etag != null) {
      rb.header("ETag", etag);
    }
    return rb.build();
  }

  // ─── helpers ───────────────────────────────────────────────────────

  /** Extract the top-level {@code shapeGraph} Turtle string from the flattened body. */
  String extractShapeGraph(String body) {
    if (body == null || body.isBlank()) return null;
    try {
      JsonNode root = objectMapper.readTree(body);
      JsonNode sg = root.path("shapeGraph");
      if (!sg.isMissingNode() && !sg.isNull() && sg.isTextual()) {
        return sg.textValue();
      }
    } catch (JsonProcessingException e) {
      Log.warnf("TemplateFormRest: could not parse template body when extracting shapeGraph: %s", e.getMessage());
    }
    return null;
  }

  /** Weak ETag over appId + flattened-body SHA-256. Null when hashing is unavailable. */
  static String etagFor(String appId, String flattenedBody) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      md.update((appId == null ? "" : appId).getBytes(StandardCharsets.UTF_8));
      md.update((flattenedBody == null ? "" : flattenedBody).getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte b : md.digest()) {
        hex.append(String.format("%02x", b));
      }
      return "W/\"" + hex + "\"";
    } catch (NoSuchAlgorithmException e) {
      return null;
    }
  }

  private static Response problem(String type, String title, int status, String detail) {
    return Response.status(status).type("application/problem+json")
      .entity(new ProblemJson(type, title, status, detail, null)).build();
  }
}
