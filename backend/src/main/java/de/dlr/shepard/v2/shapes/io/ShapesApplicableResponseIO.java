package de.dlr.shepard.v2.shapes.io;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Response body for {@code GET /v2/shapes/applicable?focusAppId=…}
 * (SHAPES-APPLICABLE-FORMS + FORM-UX-ACTIONBUTTON; doc 125 §5.3 / D4).
 *
 * <p>One discovery for both directions of the shapes UX: renderable
 * <b>views</b> ({@code mode = "VIEW"} — VIEW_RECIPE templates attached to
 * the focus entity, consumed via {@code POST /v2/shapes/render}) and
 * fillable <b>forms</b> ({@code mode = "FORM"} — data-kind templates with a
 * {@code shapeGraph}, consumed via {@code GET /v2/templates/{appId}/form}).
 * The single {@code ActionMenuButton} renders both groups — "View as…" /
 * "Record a…" — on entity detail pages.
 *
 * <p>An empty {@code items} list is a valid response (nothing applicable),
 * never an error.
 */
@Schema(description = "Applicable views (mode=VIEW) and forms (mode=FORM) for one focus entity.")
public record ShapesApplicableResponseIO(
  @Schema(description = "Echoes the focusAppId from the request.") String focusAppId,

  @Schema(description = "Applicable shapes; empty when nothing applies (valid, not an error).")
  List<ApplicableShapeItemIO> items
) {
  /** One applicable shape — a renderable view or a fillable form. */
  @Schema(description = "One applicable shape entry.")
  public record ApplicableShapeItemIO(
    @Schema(description = "Discriminator: VIEW (renderable) or FORM (fillable).", enumeration = { "VIEW", "FORM" })
    String mode,

    @Schema(description = "The ShepardTemplate the entry points at.") String templateAppId,

    @Schema(description = "Human-readable title (the template's name).") String title,

    @Schema(description = "The template body's viewRecipeShape IRI (VIEW entries; nullable).")
    String shapeIri,

    @Schema(description = "Render target for VIEW entries: POST /v2/shapes/render (nullable).")
    String renderHref,

    @Schema(description = "Form-descriptor target for FORM entries: GET /v2/templates/{appId}/form (nullable).")
    String formHref,

    @Schema(description = "Optional applicability note (e.g. why the entry is offered).") String reason
  ) {
    /** Factory for a VIEW entry. */
    public static ApplicableShapeItemIO view(String templateAppId, String title, String shapeIri, String reason) {
      return new ApplicableShapeItemIO("VIEW", templateAppId, title, shapeIri, "/v2/shapes/render", null, reason);
    }

    /** Factory for a FORM entry. */
    public static ApplicableShapeItemIO form(String templateAppId, String title, String reason) {
      return new ApplicableShapeItemIO(
        "FORM",
        templateAppId,
        title,
        null,
        null,
        "/v2/templates/" + templateAppId + "/form",
        reason
      );
    }
  }
}
