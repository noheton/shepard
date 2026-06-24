package de.dlr.shepard.v2.shapes.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.template.daos.ShepardTemplateDAO;
import de.dlr.shepard.template.entities.ShepardTemplate;
import de.dlr.shepard.template.services.TemplateInheritanceResolver;
import de.dlr.shepard.v2.shapes.io.ShapesApplicableResponseIO;
import de.dlr.shepard.v2.shapes.io.ShapesApplicableResponseIO.ApplicableShapeItemIO;
import de.dlr.shepard.v2.shapes.repositories.FocusEntityRepository;
import de.dlr.shepard.v2.shapes.repositories.FocusEntityRepository.FocusEntity;
import de.dlr.shepard.v2.template.services.FormDescriptorCompiler;
import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * {@code GET /v2/shapes/applicable?focusAppId=…} — the unified
 * applicable-shapes discovery endpoint (SHAPES-APPLICABLE-FORMS, doc 125
 * §5.3 / D4; pairs with the UX audit's SHAPES-APPLICABLE-1 — landed as ONE
 * endpoint, not two).
 *
 * <p>One discovery for both directions of the shapes UX:
 *
 * <ul>
 *   <li><b>{@code mode = "VIEW"}</b> — renderable views. Determination
 *       reuses the existing template-attachment seam: the focus entity's
 *       stamped {@code attachedTemplateAppId} (server-set at create time,
 *       surfaced on {@code DataObjectIO}) resolves to a non-retired
 *       {@code VIEW_RECIPE} template — the same gate
 *       {@link ShapesRenderRest} enforces (422 on any other kind). VIEW
 *       entries point at {@code POST /v2/shapes/render}.</li>
 *   <li><b>{@code mode = "FORM"}</b> — fillable forms: the write-direction
 *       projection of data-kind shapes (doc 125 D1). Candidates are
 *       non-retired data-kind templates
 *       ({@link FormDescriptorCompiler#FORM_RENDERABLE_KINDS}) whose
 *       flattened body carries a {@code shapeGraph}, scoped to the owning
 *       Collection's {@code :ALLOWS_TEMPLATE} allow-list when that list is
 *       non-empty (doc 125 §5.3). FORM entries point at
 *       {@code GET /v2/templates/{appId}/form}.</li>
 * </ul>
 *
 * <p><b>Fail-soft.</b> An empty {@code items} list is a valid response —
 * the discovery never 500s on a determination failure: each leg is wrapped
 * so a template-walk error degrades to "that leg contributes nothing"
 * (logged WARN), per the fail-soft registry rule.
 *
 * <p><b>Status codes.</b> 200 (possibly empty items) · 400 missing
 * {@code focusAppId} · 401 unauthenticated · 404 unknown focus appId
 * (problem-JSON).
 *
 * <p>Consumed by {@code ActionMenuButton.vue} ("View as…" / "Record a…"
 * groups on entity detail pages — FORM-UX-ACTIONBUTTON).
 */
@Produces(MediaType.APPLICATION_JSON)
@Path("/v2/shapes")
@RequestScoped
@Tag(name = "Shapes")
public class ShapesApplicableRest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String PT_BAD_REQUEST = "/problems/shapes.bad-request";
  private static final String PT_NOT_FOUND = "/problems/shapes.focus-not-found";

  @Inject
  FocusEntityRepository focusRepository;

  @Inject
  ShepardTemplateDAO templateDAO;

  @Inject
  TemplateInheritanceResolver inheritanceResolver;

  @GET
  @Path("/applicable")
  @RolesAllowed("authenticated")
  @Operation(
    operationId = "listApplicableShapes",
    summary = "List the renderable views (mode=VIEW) and fillable forms (mode=FORM) applicable to a focus entity.",
    description = "One discovery for both directions of the shapes UX (doc 125 §5.3). VIEW entries are " +
    "non-retired VIEW_RECIPE templates attached to the focus entity, consumed via POST /v2/shapes/render. " +
    "FORM entries are non-retired data-kind templates carrying a shapeGraph (scoped to the owning " +
    "Collection's allow-list when non-empty), consumed via GET /v2/templates/{appId}/form. " +
    "An empty items list is a valid response — never an error."
  )
  @APIResponse(
    responseCode = "200",
    description = "Applicable shapes for the focus entity (items may be empty).",
    content = @Content(schema = @Schema(implementation = ShapesApplicableResponseIO.class))
  )
  @APIResponse(responseCode = "400", description = "focusAppId query parameter missing.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "404", description = "No entity carries the focus appId.")
  public Response listApplicable(
    @Parameter(
      description = "AppId of the entity to match shapes against (DataObject, Collection, etc.). Required.",
      required = true
    )
    @QueryParam("focusAppId") String focusAppId
  ) {
    if (focusAppId == null || focusAppId.isBlank()) {
      return problem(PT_BAD_REQUEST, "focusAppId required", 400, "Query parameter focusAppId is required");
    }

    Optional<FocusEntity> focusOpt = focusRepository.findByAppId(focusAppId);
    if (focusOpt.isEmpty()) {
      return problem(PT_NOT_FOUND, "Focus entity not found", 404, "No entity with appId " + focusAppId);
    }
    FocusEntity focus = focusOpt.get();

    List<ApplicableShapeItemIO> items = new ArrayList<>();
    items.addAll(viewItems(focus));
    items.addAll(formItems(focus));

    return Response.ok(new ShapesApplicableResponseIO(focusAppId, items)).build();
  }

  // ─── VIEW determination ──────────────────────────────────────────────────

  /**
   * VIEW = the focus entity's attached template, when it is a live
   * {@code VIEW_RECIPE} — the exact precondition under which
   * {@code POST /v2/shapes/render} succeeds (anything else 422s there).
   * Fail-soft: errors contribute an empty leg, never a 500.
   */
  private List<ApplicableShapeItemIO> viewItems(FocusEntity focus) {
    try {
      if (focus.attachedTemplateAppId() == null || focus.attachedTemplateAppId().isBlank()) {
        return List.of();
      }
      Optional<ShepardTemplate> templateOpt = templateDAO.findByAppId(focus.attachedTemplateAppId());
      if (templateOpt.isEmpty()) {
        return List.of();
      }
      ShepardTemplate template = templateOpt.get();
      if (template.isRetired() || !"VIEW_RECIPE".equals(template.getTemplateKind())) {
        return List.of();
      }
      return List.of(
        ApplicableShapeItemIO.view(
          template.getAppId(),
          template.getName(),
          parseViewRecipeShape(template.getBody()),
          "VIEW_RECIPE template attached to this entity"
        )
      );
    } catch (RuntimeException e) {
      Log.warnf(e, "shapes/applicable: VIEW determination failed for focus — returning empty VIEW leg");
      return List.of();
    }
  }

  // ─── FORM determination ──────────────────────────────────────────────────

  /**
   * FORM = non-retired data-kind templates whose flattened body carries a
   * {@code shapeGraph} (the precondition for the form-descriptor endpoint),
   * scoped to the owning Collection's allow-list when non-empty (doc 125
   * §5.3). Fail-soft per template and per leg.
   */
  private List<ApplicableShapeItemIO> formItems(FocusEntity focus) {
    List<ApplicableShapeItemIO> out = new ArrayList<>();
    try {
      List<ShepardTemplate> candidates = formCandidates(focus);
      for (ShepardTemplate template : candidates) {
        try {
          if (
            template.getTemplateKind() == null ||
            !FormDescriptorCompiler.FORM_RENDERABLE_KINDS.contains(template.getTemplateKind()) ||
            template.isRetired()
          ) {
            continue;
          }
          String flattened = inheritanceResolver.flattenBody(template);
          if (!hasShapeGraph(flattened)) {
            continue;
          }
          out.add(
            ApplicableShapeItemIO.form(
              template.getAppId(),
              template.getName(),
              "data-kind shape instantiable in this context"
            )
          );
        } catch (RuntimeException e) {
          Log.warnf(
            e,
            "shapes/applicable: skipping template %s — FORM evaluation failed",
            template.getAppId()
          );
        }
      }
    } catch (RuntimeException e) {
      Log.warnf(e, "shapes/applicable: FORM determination failed — returning partial/empty FORM leg");
    }
    return out;
  }

  /**
   * Candidate set: the owning Collection's {@code :ALLOWS_TEMPLATE} list
   * when non-empty, else all non-retired templates.
   */
  private List<ShepardTemplate> formCandidates(FocusEntity focus) {
    if (focus.collectionAppId() != null && !focus.collectionAppId().isBlank()) {
      List<ShepardTemplate> allowed = templateDAO.listAllowedForCollection(focus.collectionAppId());
      if (allowed != null && !allowed.isEmpty()) {
        return allowed;
      }
    }
    return templateDAO.list(null, false);
  }

  // ─── body parsers ────────────────────────────────────────────────────────

  /** Read the template body's {@code viewRecipeShape} IRI; null when absent. */
  private static String parseViewRecipeShape(String bodyJson) {
    if (bodyJson == null) {
      return null;
    }
    try {
      JsonNode root = MAPPER.readTree(bodyJson);
      JsonNode s = root.path("viewRecipeShape");
      return s.isTextual() && !s.asText().isBlank() ? s.asText() : null;
    } catch (JsonProcessingException e) {
      return null;
    }
  }

  /** True when the (flattened) body carries a non-blank {@code shapeGraph} Turtle string. */
  static boolean hasShapeGraph(String bodyJson) {
    if (bodyJson == null || bodyJson.isBlank()) {
      return false;
    }
    try {
      JsonNode root = MAPPER.readTree(bodyJson);
      JsonNode sg = root.path("shapeGraph");
      return sg.isTextual() && !sg.textValue().isBlank();
    } catch (JsonProcessingException e) {
      return false;
    }
  }

  private static Response problem(String type, String title, int status, String detail) {
    return Response.status(status)
      .type("application/problem+json")
      .entity(new ProblemJson(type, title, status, detail, null))
      .build();
  }
}
