package de.dlr.shepard.v2.mappings.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.provenance.filters.ProvenanceCaptureFilter;
import de.dlr.shepard.provenance.services.ProvenanceService;
import de.dlr.shepard.spi.transform.TransformException;
import de.dlr.shepard.spi.transform.TransformExecutor;
import de.dlr.shepard.spi.transform.TransformExecutorRegistry;
import de.dlr.shepard.spi.transform.TransformRequest;
import de.dlr.shepard.spi.transform.TransformResult;
import de.dlr.shepard.template.daos.ShepardTemplateDAO;
import de.dlr.shepard.template.entities.ShepardTemplate;
import de.dlr.shepard.v2.mappings.io.MaterializeRequestIO;
import de.dlr.shepard.v2.mappings.io.MaterializeResponseIO;
import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * V2CONV-B3 — {@code POST /v2/mappings/{templateAppId}/materialize} — the
 * generic MAPPING_RECIPE materialization endpoint.
 *
 * <p><b>Endpoint-shape decision.</b> A dedicated {@code /v2/mappings/*} resource
 * (not folded into {@code /v2/shapes/render}) because "materialize" is a
 * genuinely different operation from "render": render returns a read-only
 * view-model projection of a focus DataObject's channels; materialize
 * <em>produces a derived output</em> (a new reference appId, or a played view)
 * from bound input references. Same SPI dispatch mechanism as
 * {@code /v2/shapes/render} (shape-IRI → executor via a fail-soft registry), but
 * a distinct, discoverable verb-path. This is the most reversible default per
 * the V2CONV-B3 brief; see {@code aidocs/platform/191 §4} +
 * {@code aidocs/agent-findings/v2conv-b3.md}.
 *
 * <p><b>What it does.</b> Given a MAPPING_RECIPE
 * {@link ShepardTemplate} (by path appId) and a body of input reference appId
 * bindings, it:
 * <ol>
 *   <li>loads the template (404 if absent);</li>
 *   <li>422s when {@code templateKind != MAPPING_RECIPE};</li>
 *   <li>reads the body's {@code mappingRecipeShape} IRI (422 when absent);</li>
 *   <li>resolves that IRI → a registered {@link TransformExecutor} via the
 *       fail-soft {@link TransformExecutorRegistry} (404 when none claims it —
 *       graceful degradation, never 500);</li>
 *   <li>runs the executor and returns the derived output, mapping
 *       {@link TransformException} typed codes to 404/422 and unexpected
 *       failures to 500.</li>
 * </ol>
 *
 * <p>The built-in {@link de.dlr.shepard.spi.transform.NoOpTransformExecutor}
 * claims the identity shape, so this path is exercisable end-to-end without any
 * plugin.
 *
 * <p><b>Provenance</b> is captured here as a typed {@code EXECUTE}
 * {@link de.dlr.shepard.provenance.entities.Activity} (best-effort,
 * fire-and-forget — never breaks the primary op), and the
 * {@link ProvenanceCaptureFilter} is told to step back via
 * {@code PROP_SKIP_CAPTURE} so the mutation produces exactly one Activity.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/mappings")
@RequestScoped
@org.eclipse.microprofile.openapi.annotations.tags.Tag(name = "Mappings")
public class MappingsMaterializeRest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  static final String MAPPING_RECIPE_KIND = "MAPPING_RECIPE";

  @Inject
  ShepardTemplateDAO templateDAO;

  @Inject
  TransformExecutorRegistry executorRegistry;

  @Inject
  ProvenanceService provenanceService;

  @POST
  @Path("/{templateAppId}/materialize")
  @RolesAllowed("authenticated")
  @org.eclipse.microprofile.openapi.annotations.Operation(
    summary = "Materialize a MAPPING_RECIPE template into a derived output.",
    description = "Binds the supplied input reference appIds through the recipe's shape and runs the " +
    "registered TransformExecutor, returning a derived reference appId or a played view-model. " +
    "404 when the template/executor is absent; 422 when templateKind != MAPPING_RECIPE or the body " +
    "declares no mappingRecipeShape."
  )
  @org.eclipse.microprofile.openapi.annotations.responses.APIResponse(responseCode = "200", description = "Materialization succeeded.")
  @org.eclipse.microprofile.openapi.annotations.responses.APIResponse(responseCode = "401", description = "Authentication required.")
  @org.eclipse.microprofile.openapi.annotations.responses.APIResponse(
    responseCode = "404",
    description = "Template not found, or no TransformExecutor is registered for the recipe's shape IRI."
  )
  @org.eclipse.microprofile.openapi.annotations.responses.APIResponse(
    responseCode = "422",
    description = "Template found but not a MAPPING_RECIPE, or the body declares no mappingRecipeShape."
  )
  public Response materialize(
    @PathParam("templateAppId") String templateAppId,
    MaterializeRequestIO body,
    @Context SecurityContext securityContext,
    @Context ContainerRequestContext requestContext
  ) {
    if (templateAppId == null || templateAppId.isBlank()) {
      return badRequest("templateAppId path parameter is required");
    }

    ShepardTemplate template = templateDAO.findByAppId(templateAppId).orElse(null);
    if (template == null) {
      return problem(Response.Status.NOT_FOUND, "template not found: " + templateAppId, null);
    }

    if (!MAPPING_RECIPE_KIND.equals(template.getTemplateKind())) {
      return problem(
        422,
        "materialize requires a MAPPING_RECIPE template; templateKind=" + template.getTemplateKind(),
        null
      );
    }

    String shapeIri = parseMappingRecipeShape(template.getBody());
    if (shapeIri == null) {
      return problem(
        422,
        "MAPPING_RECIPE body declares no `mappingRecipeShape` IRI — cannot resolve a TransformExecutor",
        null
      );
    }

    Optional<TransformExecutor> match = executorRegistry == null
      ? Optional.empty()
      : executorRegistry.resolve(shapeIri);
    if (match.isEmpty()) {
      // Fail-soft registry → graceful 404, never 500: the recipe's plugin
      // isn't installed (or no built-in claims this shape).
      Log.debugf("V2CONV-B3: no TransformExecutor registered for shape <%s>", shapeIri);
      return problem(
        Response.Status.NOT_FOUND,
        "no TransformExecutor is registered for shape <" + shapeIri + ">; the recipe's plugin may not be installed",
        "transform.executor.not-registered"
      );
    }

    Map<String, String> inputs = body == null || body.inputReferenceAppIds() == null
      ? Map.of()
      : new HashMap<>(body.inputReferenceAppIds());

    String username = securityContext == null || securityContext.getUserPrincipal() == null
      ? null
      : securityContext.getUserPrincipal().getName();

    TransformExecutor executor = match.get();
    TransformRequest req = new TransformRequest(templateAppId, shapeIri, inputs, username, template.getBody());

    try {
      TransformResult result = executor.materialize(req);
      if (result == null) {
        Log.warnf("V2CONV-B3: TransformExecutor '%s' returned null for shape <%s> — surfacing 500", executor.name(), shapeIri);
        return problem(Response.Status.INTERNAL_SERVER_ERROR, "executor '" + executor.name() + "' returned null", "transform.internal-error");
      }
      recordMaterializeActivity(template, executor, result, username, requestContext);
      return Response.ok(MaterializeResponseIO.from(templateAppId, result)).build();
    } catch (TransformException ex) {
      Log.debugf(ex, "V2CONV-B3: executor '%s' threw TransformException %s for shape <%s>", executor.name(), ex.code(), shapeIri);
      int status = statusForCode(ex.code());
      return problem(status, ex.getMessage(), ex.code() == null ? "transform.unknown-error" : ex.code());
    } catch (RuntimeException ex) {
      Log.warnf(ex, "V2CONV-B3: executor '%s' threw for shape <%s> — surfacing 500", executor.name(), shapeIri);
      return problem(
        Response.Status.INTERNAL_SERVER_ERROR,
        ex.getClass().getSimpleName() + ": " + ex.getMessage(),
        "transform.internal-error"
      );
    }
  }

  // ─── helpers ───────────────────────────────────────────────────────────────

  /**
   * Best-effort, fire-and-forget provenance of the materialize run. Never
   * propagates — {@link ProvenanceService#record} already swallows write
   * failures, and this wrapper guards the skip-capture handoff too. Records a
   * typed {@code EXECUTE} Activity, then tells the {@link ProvenanceCaptureFilter}
   * to step back so the mutation yields exactly one Activity.
   */
  private void recordMaterializeActivity(
    ShepardTemplate template,
    TransformExecutor executor,
    TransformResult result,
    String username,
    ContainerRequestContext requestContext
  ) {
    try {
      long now = System.currentTimeMillis();
      String targetKind = result.kind() == TransformResult.Kind.REFERENCE ? "Reference" : "ShepardTemplate";
      String targetAppId = result.kind() == TransformResult.Kind.REFERENCE
        ? result.derivedReferenceAppId()
        : template.getAppId();
      provenanceService.record(
        "EXECUTE",
        targetKind,
        targetAppId,
        username,
        "materialize MAPPING_RECIPE '" + template.getName() + "' via " + executor.name() + " → " + result.kind(),
        "POST",
        "/v2/mappings/" + template.getAppId() + "/materialize",
        200,
        now,
        now
      );
      if (requestContext != null) {
        requestContext.setProperty(ProvenanceCaptureFilter.PROP_SKIP_CAPTURE, Boolean.TRUE);
      }
    } catch (RuntimeException ex) {
      // Secondary write — never break the primary op.
      Log.warnf(ex, "V2CONV-B3: provenance capture for materialize failed (non-fatal)");
    }
  }

  /** Read the body's {@code mappingRecipeShape} IRI; null when absent/unparseable/blank. */
  private String parseMappingRecipeShape(String bodyJson) {
    if (bodyJson == null) return null;
    try {
      JsonNode root = MAPPER.readTree(bodyJson);
      JsonNode s = root.path("mappingRecipeShape");
      if (!s.isTextual()) return null;
      String iri = s.asText();
      return (iri == null || iri.isBlank()) ? null : iri;
    } catch (JsonProcessingException e) {
      return null;
    }
  }

  /** Map a TransformException code to an HTTP status code (422 is not a JAX-RS enum value). */
  private int statusForCode(String code) {
    if (code == null) return 422;
    return switch (code) {
      case "transform.input.not-found", "transform.focus.not-found" -> 404;
      default -> 422; // body.invalid, input.missing, unknown
    };
  }

  private Response badRequest(String message) {
    return problem(Response.Status.BAD_REQUEST, message, null);
  }

  private Response problem(Response.StatusType status, String error, String code) {
    Map<String, Object> entity = new HashMap<>();
    entity.put("error", error);
    if (code != null) entity.put("code", code);
    return Response.status(status.getStatusCode()).entity(entity).build();
  }

  private Response problem(int status, String error, String code) {
    Map<String, Object> entity = new HashMap<>();
    entity.put("error", error);
    if (code != null) entity.put("code", code);
    return Response.status(status).entity(entity).build();
  }
}
