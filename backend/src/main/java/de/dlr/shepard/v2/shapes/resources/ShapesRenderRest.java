package de.dlr.shepard.v2.shapes.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.template.daos.ShepardTemplateDAO;
import de.dlr.shepard.template.entities.ShepardTemplate;
import de.dlr.shepard.v2.shapes.io.ShapesRenderRequestIO;
import de.dlr.shepard.v2.shapes.io.ShapesRenderResponseIO;
import de.dlr.shepard.v2.shapes.io.ShapesRenderResponseIO.ChannelBindingProjectionIO;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * {@code POST /v2/shapes/render} — stateless VIEW_RECIPE projection
 * endpoint (TPL2b). Sibling to {@link ShapesValidateRest}.
 *
 * <p><b>What it does.</b> Given a VIEW_RECIPE
 * {@link de.dlr.shepard.template.entities.ShepardTemplate} and a focus
 * DataObject appId, it returns the template's channel bindings as a
 * structured wire payload the frontend can use to drive a timeseries
 * renderer (e.g. Trace3D / TresJS).
 *
 * <p><b>Beta scope (TPL2b).</b> Only {@code VIEW_RECIPE} templates are
 * supported. All other {@code templateKind} values return 422. Channel
 * bindings are returned with {@code status = "DECLARED"} — live
 * resolution against the focus DataObject's TS references ships in
 * TPL2c once the TS-ID migration (aidocs/platform/87) lands a stable
 * single-key channel identity.
 *
 * <p><b>Template body structure expected.</b> A VIEW_RECIPE body must
 * satisfy {@link de.dlr.shepard.template.services.TemplateBodyValidator}
 * (at least one of {@code view}, {@code shape}, {@code renderer} present).
 * For the render endpoint to project channel bindings the body must also
 * contain a {@code channelBindings} array with entries of the shape:
 * <pre>
 * {
 *   "renderer": "tresjs",
 *   "channelBindings": [
 *     {
 *       "role":            "x",
 *       "channelSelector": "{\"measurement\":\"AFP\",\"device\":\"tcp\",...}",
 *       "unit":            "http://qudt.org/vocab/unit/MilliM",
 *       "required":        true
 *     }
 *   ]
 * }
 * </pre>
 * Templates without a {@code channelBindings} array return a 200 with an
 * empty {@code channelBindings} list — the endpoint does not reject them.
 *
 * <p><b>Status codes.</b>
 * <ul>
 *   <li>{@code 200} — projection succeeded. Inspect
 *       {@code channelBindings[].status} per binding.</li>
 *   <li>{@code 400} — request body missing or required fields absent.</li>
 *   <li>{@code 401} — not authenticated.</li>
 *   <li>{@code 404} — template not found.</li>
 *   <li>{@code 422} — template found but its
 *       {@code templateKind != VIEW_RECIPE}. Body explains which kind
 *       was found; upgrade path: use {@code GET /v2/templates?kind=view}
 *       to discover VIEW_RECIPE templates.</li>
 * </ul>
 *
 * <p><b>Cross-references.</b>
 * <ul>
 *   <li>{@code aidocs/semantics/98 §1.2} — design contract + response shape</li>
 *   <li>{@code backend/src/main/resources/shapes/view-recipe-meta.shacl.ttl}
 *       — the meta-shape the body should satisfy</li>
 *   <li>{@link ShapesValidateRest} — sibling validate endpoint</li>
 * </ul>
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/shapes")
@RequestScoped
@Tag(name = "Shapes (v2)")
public class ShapesRenderRest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String STATUS_DECLARED = "DECLARED";

  @Inject
  ShepardTemplateDAO templateDAO;

  @POST
  @Path("/render")
  @RolesAllowed("authenticated")
  @Operation(
    summary = "Project a VIEW_RECIPE template's channel bindings onto a focus DataObject.",
    description = "Stateless, read-only. Returns the template's channel binding declarations. " +
    "Beta (TPL2b): all bindings have status=DECLARED — live channel resolution " +
    "(OK / MISSING / UNIT_MISMATCH) ships in TPL2c. 422 when templateKind != VIEW_RECIPE."
  )
  @APIResponse(
    responseCode = "200",
    description = "Projection succeeded. Inspect channelBindings[].status per binding.",
    content = @Content(schema = @Schema(implementation = ShapesRenderResponseIO.class))
  )
  @APIResponse(responseCode = "400", description = "Request body missing or required fields absent.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "404", description = "Template not found.")
  @APIResponse(
    responseCode = "422",
    description = "Template found but templateKind != VIEW_RECIPE. Use GET /v2/templates?kind=view to discover VIEW_RECIPE templates."
  )
  public Response render(
    @RequestBody(
      required = true,
      description = "VIEW_RECIPE template appId + focus DataObject appId.",
      content = @Content(schema = @Schema(implementation = ShapesRenderRequestIO.class))
    )
    ShapesRenderRequestIO body
  ) {
    if (body == null) {
      return badRequest("request body required");
    }
    if (body.templateAppId() == null || body.templateAppId().isBlank()) {
      return badRequest("templateAppId is required");
    }
    if (body.focusShepardId() == null || body.focusShepardId().isBlank()) {
      return badRequest("focusShepardId is required");
    }

    ShepardTemplate template = templateDAO.findByAppId(body.templateAppId()).orElse(null);
    if (template == null) {
      return Response.status(Response.Status.NOT_FOUND)
        .entity(Map.of("error", "template not found: " + body.templateAppId()))
        .build();
    }

    if (!"VIEW_RECIPE".equals(template.getTemplateKind())) {
      return Response.status(422)
        .entity(
          Map.of(
            "error",
            "render not yet supported for templateKind=" +
            template.getTemplateKind() +
            "; only VIEW_RECIPE is supported in this release. " +
            "Use GET /v2/templates?kind=view to discover VIEW_RECIPE templates."
          )
        )
        .build();
    }

    List<ChannelBindingProjectionIO> bindings = parseChannelBindings(template.getBody());
    String renderer = parseRenderer(template.getBody());

    ShapesRenderResponseIO response = new ShapesRenderResponseIO(
      body.templateAppId(),
      body.focusShepardId(),
      renderer,
      bindings
    );

    return Response.ok(response).build();
  }

  // ─── body parsers ────────────────────────────────────────────────────────

  private String parseRenderer(String bodyJson) {
    if (bodyJson == null) return null;
    try {
      JsonNode root = MAPPER.readTree(bodyJson);
      JsonNode r = root.path("renderer");
      return r.isTextual() ? r.asText() : null;
    } catch (JsonProcessingException e) {
      return null;
    }
  }

  private List<ChannelBindingProjectionIO> parseChannelBindings(String bodyJson) {
    List<ChannelBindingProjectionIO> out = new ArrayList<>();
    if (bodyJson == null) return out;
    try {
      JsonNode root = MAPPER.readTree(bodyJson);
      JsonNode bindingsNode = root.path("channelBindings");
      if (!bindingsNode.isArray()) return out;
      for (JsonNode b : bindingsNode) {
        String role = b.path("role").asText(null);
        String selector = b.path("channelSelector").asText(null);
        JsonNode unitNode = b.path("unit");
        String unit = unitNode.isTextual() ? unitNode.asText() : null;
        boolean required = b.path("required").asBoolean(true);
        out.add(new ChannelBindingProjectionIO(role, selector, unit, required, STATUS_DECLARED, null));
      }
    } catch (JsonProcessingException e) {
      // malformed body — return what we parsed so far
    }
    return out;
  }

  private Response badRequest(String message) {
    return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", message)).build();
  }
}
