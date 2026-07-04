package de.dlr.shepard.v2.shapes.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.context.references.file.services.SingletonFileReferenceService;
import de.dlr.shepard.spi.view.FocusPayloadResolver;
import de.dlr.shepard.spi.view.RenderException;
import de.dlr.shepard.spi.view.RenderRequest;
import de.dlr.shepard.spi.view.RenderResponse;
import de.dlr.shepard.spi.view.RenderedMedia;
import de.dlr.shepard.spi.view.ViewRecipeRenderer;
import de.dlr.shepard.spi.view.ViewRecipeRendererRegistry;
import de.dlr.shepard.template.daos.ShepardTemplateDAO;
import de.dlr.shepard.template.entities.ShepardTemplate;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.v2.shapes.io.ShapesRenderRequestIO;
import de.dlr.shepard.v2.shapes.io.ShapesRenderResponseIO;
import de.dlr.shepard.v2.shapes.io.ShapesRenderResponseIO.ChannelBindingProjectionIO;
import de.dlr.shepard.v2.shapes.io.ShapesRenderResponseIO.ResolvedChannelIO;
import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
@Tag(name = "Shapes")
public class ShapesRenderRest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String STATUS_DECLARED = "DECLARED";

  private static final String PT_NOT_FOUND = "/problems/shapes-render.template-not-found";
  private static final String PT_WRONG_KIND = "/problems/shapes-render.wrong-template-kind";
  private static final String PT_BAD_REQUEST = "/problems/shapes-render.bad-request";
  private static final String PT_RENDER_FAILED = "/problems/shapes-render.render-failed";
  private static final String PT_INTERNAL = "/problems/shapes-render.internal-error";

  @Inject
  ShepardTemplateDAO templateDAO;

  /**
   * VIS-S1 — dispatcher that ServiceLoader-loads
   * {@link ViewRecipeRenderer} impls at startup. When a VIEW_RECIPE
   * template body declares a {@code viewRecipeShape} IRI claimed by a
   * registered renderer, this endpoint delegates the projection to
   * that renderer (typically returning live OK / MISSING /
   * UNIT_MISMATCH status codes). When no renderer claims the IRI (or
   * the body declares no IRI at all), the endpoint falls back to the
   * in-tree {@code DECLARED}-status projection it shipped in TPL2b —
   * preserving the wire shape for templates whose plugin isn't
   * installed.
   */
  @Inject
  ViewRecipeRendererRegistry rendererRegistry;

  /**
   * V2CONV-A1b (E3) — focus-byte resolution. The dispatcher wraps this CDI
   * service in a {@link FocusPayloadResolver} and hands it to the renderer so
   * a byte-rooted renderer (thermography OTvis/heatmap) can read the focus
   * FileReference's content without itself being a CDI bean. Field-injected so
   * unit tests can leave it null and exercise the template-rooted path.
   */
  @Inject
  SingletonFileReferenceService singletonFileReferenceService;

  @POST
  @Path("/render")
  @RolesAllowed("authenticated")
  // V2CONV-A1 — wildcard so a non-JSON Accept (image/png, model/gltf+json) reaches
  // the method instead of 406-ing at JAX-RS negotiation; the concrete content-type
  // is set on the Response. JSON stays the default view-model.
  @Produces({ MediaType.APPLICATION_JSON, MediaType.WILDCARD })
  @Operation(
    operationId = "renderShape",
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
    @Context HttpHeaders headers,
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

    boolean hasTemplate = body.templateAppId() != null && !body.templateAppId().isBlank();
    boolean hasFileRoot = body.shapeIri() != null && !body.shapeIri().isBlank();

    // V2CONV-A1b (E2) — file-rooted dispatch: a caller may name a shapeIri
    // (+ focusFileRefAppId) directly with no stored VIEW_RECIPE template. The
    // template-rooted path stays the default. 400 only when neither is usable.
    if (!hasTemplate && hasFileRoot) {
      return renderFileRooted(headers, body);
    }

    if (!hasTemplate) {
      return badRequest(
        "either templateAppId (template-rooted) or shapeIri (+focusFileRefAppId, file-rooted) is required"
      );
    }
    if (body.focusShepardId() == null || body.focusShepardId().isBlank()) {
      return badRequest("focusShepardId is required");
    }

    ShepardTemplate template = templateDAO.findByAppId(body.templateAppId()).orElse(null);
    if (template == null) {
      return Response.status(Response.Status.NOT_FOUND)
        .entity(
          new ProblemJson(PT_NOT_FOUND, "Template Not Found", 404,
            "template not found: " + body.templateAppId(), null)
        )
        .type("application/problem+json")
        .build();
    }

    if (!"VIEW_RECIPE".equals(template.getTemplateKind())) {
      return Response.status(422)
        .entity(
          new ProblemJson(PT_WRONG_KIND, "Wrong Template Kind", 422,
            "render not yet supported for templateKind=" +
            template.getTemplateKind() +
            "; only VIEW_RECIPE is supported in this release. " +
            "Use GET /v2/templates?kind=view to discover VIEW_RECIPE templates.",
            null)
        )
        .type("application/problem+json")
        .build();
    }

    // VIS-S1 — try the renderer SPI dispatch first. When the template
    // body declares a `viewRecipeShape` IRI claimed by an installed
    // renderer (e.g. shepard-plugin-vis-trace3d), delegate; otherwise
    // fall through to the TPL2b DECLARED-status in-tree path so the
    // wire shape is preserved for templates whose plugin isn't
    // installed.
    String shapeIri = parseViewRecipeShape(template.getBody());
    if (shapeIri != null && rendererRegistry != null) {
      Optional<ViewRecipeRenderer> match = rendererRegistry.resolve(shapeIri);
      if (match.isPresent()) {
        // V2CONV-A1 — content negotiation: if the caller's Accept asks for a
        // non-JSON media type this renderer produces (e.g. image/png heatmap,
        // model/gltf+json), return the rendered bytes. Otherwise fall through
        // to the JSON view-model — the default, byte-compatible with today.
        Response media = negotiateMedia(match.get(), headers, body, template, shapeIri);
        if (media != null) {
          return media;
        }
        return dispatchToRenderer(match.get(), body, template, shapeIri);
      }
      Log.debugf(
        "VIS-S1: no ViewRecipeRenderer registered for shape <%s> — falling back to in-tree DECLARED projection",
        shapeIri
      );
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

  // ─── V2CONV-A1b (E2) file-rooted dispatch ────────────────────────────────

  /**
   * V2CONV-A1b (E2/E3) — render a file-rooted request: the caller names a
   * {@code shapeIri} + (optionally) a {@code focusFileRefAppId} with no stored
   * VIEW_RECIPE template. Resolves the renderer by shape IRI and hands it the
   * per-call {@code params} + a {@link FocusPayloadResolver} (E3). Content
   * negotiation honours {@code Accept} just like the template-rooted path; the
   * default is the JSON view-model ({@code params.mode=index} returns a
   * describe view-model — the frames catalogue, E4).
   */
  private Response renderFileRooted(HttpHeaders headers, ShapesRenderRequestIO body) {
    String shapeIri = body.shapeIri();
    if (rendererRegistry == null) {
      return Response.status(422)
        .entity(
          new ProblemJson(PT_RENDER_FAILED, "Renderer Unavailable", 422,
            "no renderer registry available for shape: " + shapeIri, null)
        )
        .type("application/problem+json")
        .build();
    }
    Optional<ViewRecipeRenderer> match = rendererRegistry.resolve(shapeIri);
    if (match.isEmpty()) {
      return Response.status(422)
        .entity(
          new ProblemJson(PT_RENDER_FAILED, "No Renderer Registered", 422,
            "no renderer registered for shape: " + shapeIri, null)
        )
        .type("application/problem+json")
        .build();
    }
    RenderRequest req = buildFileRootedRequest(body, shapeIri);

    // Content negotiation — a non-JSON Accept that the renderer produces returns
    // bytes (e.g. image/png frame); otherwise the JSON view-model.
    Response media = negotiateMediaForRequest(match.get(), headers, req);
    if (media != null) {
      return media;
    }
    return dispatchRequestToRenderer(match.get(), req, body.templateAppId(), body.focusShepardId());
  }

  private RenderRequest buildFileRootedRequest(ShapesRenderRequestIO body, String shapeIri) {
    return new RenderRequest(
      null,
      body.focusShepardId(),
      shapeIri,
      null,
      body.params() == null ? Map.of() : body.params(),
      body.focusFileRefAppId(),
      focusPayloadResolver()
    );
  }

  /**
   * V2CONV-A1b (E3) — adapt the CDI {@link SingletonFileReferenceService} to the
   * SPI {@link FocusPayloadResolver} handle. Null when the service isn't wired
   * (unit tests) so the renderer can detect the absent-resolver case.
   */
  private FocusPayloadResolver focusPayloadResolver() {
    if (singletonFileReferenceService == null) {
      return null;
    }
    return appId -> singletonFileReferenceService.getPayload(appId).getInputStream();
  }

  // ─── VIS-S1 SPI dispatch ─────────────────────────────────────────────────

  /**
   * VIS-S1 — read the template body's {@code viewRecipeShape} field.
   * Returns null when the body is null, unparseable, or has no
   * {@code viewRecipeShape} entry — the dispatcher then falls back
   * to the in-tree projection path.
   */
  private String parseViewRecipeShape(String bodyJson) {
    if (bodyJson == null) return null;
    try {
      JsonNode root = MAPPER.readTree(bodyJson);
      JsonNode s = root.path("viewRecipeShape");
      if (!s.isTextual()) return null;
      String iri = s.asText();
      return (iri == null || iri.isBlank()) ? null : iri;
    } catch (JsonProcessingException e) {
      return null;
    }
  }

  /**
   * VIS-S1 — invoke the resolved renderer + translate the
   * {@link RenderResponse} envelope onto the wire-shape
   * {@link ShapesRenderResponseIO}. Catches:
   *
   * <ul>
   *   <li>{@link RenderException} — typed renderer failures map to
   *       HTTP 422 (RFC 7807-shaped body carrying {@code code} +
   *       {@code title}).</li>
   *   <li>{@link RuntimeException} — unexpected; logged at WARN,
   *       surfaced as HTTP 500.</li>
   * </ul>
   */
  /**
   * V2CONV-A1 — content negotiation for the renderer SPI. Returns a media
   * {@link Response} when the caller's {@code Accept} asks for a non-JSON media
   * type the renderer declares in {@link ViewRecipeRenderer#producibleMedia()}
   * and actually produces; otherwise null → fall back to the JSON view-model
   * (the default, byte-compatible with the pre-A1 behaviour).
   */
  private Response negotiateMedia(
    ViewRecipeRenderer renderer,
    HttpHeaders headers,
    ShapesRenderRequestIO body,
    ShepardTemplate template,
    String shapeIri
  ) {
    // V2CONV-A1b — build the request once (now carries params + the focus
    // payload resolver) and delegate to the request-based negotiator so the
    // template-rooted and file-rooted paths share one code path.
    return negotiateMediaForRequest(renderer, headers, buildTemplateRequest(body, template, shapeIri));
  }

  /**
   * V2CONV-A1b — content negotiation for a fully-built {@link RenderRequest}.
   * Returns a media {@link Response} when the caller's {@code Accept} asks for a
   * non-JSON media type the renderer produces; otherwise null → JSON fallback.
   */
  private Response negotiateMediaForRequest(ViewRecipeRenderer renderer, HttpHeaders headers, RenderRequest req) {
    java.util.Set<String> producible = renderer.producibleMedia();
    if (producible == null || producible.isEmpty()) {
      return null;
    }
    // V2CONV-A7-THERMO — a renderer may declare application/json in
    // producibleMedia() to emit its OWN JSON view-model (e.g. the thermography
    // plate-heatmap grid that thermographyHeatmap.ts consumes) instead of the
    // generic channel-bindings envelope. Additive: a renderer that does NOT
    // declare application/json (every renderer today, incl. Trace3D) keeps the
    // default view-model path unchanged.
    boolean rendererOwnsJson = producible.contains(MediaType.APPLICATION_JSON);
    List<MediaType> accepted = headers == null ? List.of() : headers.getAcceptableMediaTypes();
    for (MediaType mt : accepted) {
      if (mt.isWildcardType() || MediaType.APPLICATION_JSON_TYPE.isCompatible(mt)) {
        if (rendererOwnsJson) {
          Response json = emitRendererMedia(renderer, req, MediaType.APPLICATION_JSON);
          if (json != null) {
            return json;
          }
        }
        return null; // caller accepts JSON / */* — use the default view-model
      }
      String concrete = mt.getType() + "/" + mt.getSubtype();
      if (producible.contains(concrete)) {
        Response media = emitRendererMedia(renderer, req, concrete);
        // null → renderer declined this media → JSON view-model fallback.
        return media;
      }
    }
    return null;
  }

  /**
   * V2CONV-A1/A7 — call {@link ViewRecipeRenderer#renderMedia} for one concrete
   * media type. Returns the bytes {@link Response}, a 422 on a renderer throw,
   * or null when the renderer declined (the caller falls back to the JSON
   * view-model).
   */
  private Response emitRendererMedia(ViewRecipeRenderer renderer, RenderRequest req, String concrete) {
    try {
      Optional<RenderedMedia> rendered = renderer.renderMedia(req, concrete);
      if (rendered.isPresent() && rendered.get().bytes() != null) {
        return Response.ok(rendered.get().bytes(), rendered.get().mediaType()).build();
      }
    } catch (RenderException ex) {
      Log.debugf(
        ex,
        "V2CONV-A1: renderer '%s' threw RenderException %s producing %s for shape <%s>",
        renderer.name(),
        ex.code(),
        concrete,
        req.shapeIri()
      );
      return Response
        .status(422)
        .entity(
          new ProblemJson(PT_RENDER_FAILED, "Media Render Failed", 422,
            ex.getMessage() == null ? "media render failed for " + concrete : ex.getMessage(),
            null,
            Map.of("code", ex.code() == null ? "render.unknown-error" : ex.code(),
                   "renderer", renderer.name()))
        )
        .type("application/problem+json")
        .build();
    } catch (RuntimeException ex) {
      Log.warnf(
        ex,
        "V2CONV-A1: renderer '%s' threw producing %s for shape <%s> — surfacing 422",
        renderer.name(),
        concrete,
        req.shapeIri()
      );
      return Response
        .status(422)
        .entity(
          new ProblemJson(PT_RENDER_FAILED, "Media Render Failed", 422,
            "media render failed for " + concrete, null,
            Map.of("renderer", renderer.name()))
        )
        .type("application/problem+json")
        .build();
    }
    return null; // renderer declined this media — JSON fallback
  }

  /**
   * V2CONV-A1b — build the renderer request for a template-rooted dispatch.
   * Carries the per-call {@code params} from the body + the focus payload
   * resolver so a renderer that also wants bytes can reach them.
   */
  private RenderRequest buildTemplateRequest(ShapesRenderRequestIO body, ShepardTemplate template, String shapeIri) {
    return new RenderRequest(
      body.templateAppId(),
      body.focusShepardId(),
      shapeIri,
      template.getBody(),
      body.params() == null ? Map.of() : body.params(),
      body.focusFileRefAppId(),
      focusPayloadResolver()
    );
  }

  private Response dispatchToRenderer(
    ViewRecipeRenderer renderer,
    ShapesRenderRequestIO body,
    ShepardTemplate template,
    String shapeIri
  ) {
    return dispatchRequestToRenderer(
      renderer,
      buildTemplateRequest(body, template, shapeIri),
      body.templateAppId(),
      body.focusShepardId()
    );
  }

  /**
   * V2CONV-A1b — invoke a renderer for a fully-built {@link RenderRequest} and
   * translate the envelope onto the wire IO. Echoes the supplied
   * {@code templateAppId}/{@code focusShepardId} (which may be null on a
   * file-rooted render) onto the response — never trusts the renderer's echo.
   */
  private Response dispatchRequestToRenderer(
    ViewRecipeRenderer renderer,
    RenderRequest req,
    String echoTemplateAppId,
    String echoFocusShepardId
  ) {
    String shapeIri = req.shapeIri();
    try {
      RenderResponse out = renderer.render(req);
      if (out == null) {
        Log.warnf(
          "VIS-S1: ViewRecipeRenderer '%s' returned null for shape <%s> — surfacing 500",
          renderer.name(),
          shapeIri
        );
        return Response
          .serverError()
          .entity(
            new ProblemJson(PT_INTERNAL, "Renderer Internal Error", 500,
              "renderer '" + renderer.name() + "' returned null", null,
              Map.of("renderer", renderer.name()))
          )
          .type("application/problem+json")
          .build();
      }
      return Response.ok(toWire(out, echoTemplateAppId, echoFocusShepardId)).build();
    } catch (RenderException ex) {
      Log.debugf(
        ex,
        "VIS-S1: renderer '%s' threw RenderException %s for shape <%s>",
        renderer.name(),
        ex.code(),
        shapeIri
      );
      return Response
        .status(422)
        .entity(
          new ProblemJson(PT_RENDER_FAILED, "Render Failed", 422,
            ex.getMessage(), null,
            Map.of("code", ex.code() == null ? "render.unknown-error" : ex.code(),
                   "renderer", renderer.name()))
        )
        .type("application/problem+json")
        .build();
    } catch (RuntimeException ex) {
      Log.warnf(
        ex,
        "VIS-S1: ViewRecipeRenderer '%s' threw for shape <%s> — surfacing 500",
        renderer.name(),
        shapeIri
      );
      return Response
        .serverError()
        .entity(
          new ProblemJson(PT_INTERNAL, "Renderer Internal Error", 500,
            ex.getClass().getSimpleName() + ": " + ex.getMessage(), null,
            Map.of("renderer", renderer.name()))
        )
        .type("application/problem+json")
        .build();
    }
  }

  /**
   * VIS-S1 — map the SPI envelope onto the wire IO. The
   * {@code DECLARED / OK / MISSING / UNIT_MISMATCH} status vocabulary
   * is preserved byte-for-byte; clients can keep their existing
   * branch logic.
   */
  private ShapesRenderResponseIO toWire(RenderResponse out, String echoTemplateAppId, String echoFocusShepardId) {
    List<ChannelBindingProjectionIO> wireBindings = new ArrayList<>();
    List<RenderResponse.ChannelBindingProjection> in = out.channelBindings();
    if (in != null) {
      for (RenderResponse.ChannelBindingProjection cbp : in) {
        if (cbp == null) continue;
        ResolvedChannelIO resolved = null;
        if (cbp.resolved() != null) {
          resolved = new ResolvedChannelIO(cbp.resolved().channelRef());
        }
        wireBindings.add(
          new ChannelBindingProjectionIO(
            cbp.role(),
            cbp.channelSelector(),
            cbp.unit(),
            cbp.required(),
            cbp.status(),
            resolved
          )
        );
      }
    }
    // Echo the request's appIds — never trust the renderer to fill
    // these correctly; this is dispatcher-level housekeeping.
    return new ShapesRenderResponseIO(
      echoTemplateAppId,
      echoFocusShepardId,
      out.renderer(),
      wireBindings
    );
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
    return Response.status(Response.Status.BAD_REQUEST)
      .entity(new ProblemJson(PT_BAD_REQUEST, "Bad Request", 400, message, null))
      .type("application/problem+json")
      .build();
  }
}
