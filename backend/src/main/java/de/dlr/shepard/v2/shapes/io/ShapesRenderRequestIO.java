package de.dlr.shepard.v2.shapes.io;

import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request body for {@code POST /v2/shapes/render}.
 *
 * <p>Two dispatch shapes (V2CONV-A1b):
 * <ul>
 *   <li><b>Template-rooted</b> (default) — supply {@code templateAppId}
 *       of a VIEW_RECIPE {@link de.dlr.shepard.template.entities.ShepardTemplate};
 *       all view state is stored on the template.</li>
 *   <li><b>File-rooted</b> — supply {@code shapeIri} + {@code focusFileRefAppId}
 *       directly (no stored template). The dispatcher resolves the renderer
 *       by {@code shapeIri} and hands it the focus FileReference's bytes.
 *       This is the path the thermography OTvis viewer + heatmap take —
 *       the {@code .OTvis} / TIFF-bundle reference IS the data, no
 *       per-(file × frame × channel) template needs minting.</li>
 * </ul>
 *
 * <p>The {@code focusShepardId} names the DataObject (or other individual)
 * to project. {@code params} carries per-call render knobs ({@code frame},
 * {@code channel}, {@code mode}, …) that vary per request and cannot live
 * in a stored template body.
 *
 * <p>{@code mediaType} is informational — the concrete response media is
 * driven by the {@code Accept} header (content negotiation, V2CONV-A1).
 */
@Schema(description = "Request body for POST /v2/shapes/render.")
public record ShapesRenderRequestIO(
  @Schema(
    description = "appId (UUID v7) of the VIEW_RECIPE :ShepardTemplate to render (template-rooted dispatch). " +
    "Optional: omit it and supply shapeIri + focusFileRefAppId for a file-rooted render. " +
    "404 when supplied but not found; 422 when found but templateKind != VIEW_RECIPE.",
    nullable = true
  )
  String templateAppId,

  @Schema(
    description = "appId (UUID v7) of the focus DataObject whose timeseries channels are projected through the recipe's channel bindings.",
    nullable = true
  )
  String focusShepardId,

  @Schema(
    description = "Desired response media type. Informational — the concrete media is driven by the Accept header.",
    nullable = true,
    defaultValue = "application/json"
  )
  String mediaType,

  @Schema(
    description = "SHACL shape IRI naming the renderer to dispatch to on a file-rooted render (e.g. " +
    "http://semantics.dlr.de/shepard-ui/thermography/transform#OtvisFrameShape). Required when templateAppId is absent.",
    nullable = true
  )
  String shapeIri,

  @Schema(
    description = "appId (UUID v7) of the focus FileReference (e.g. the .OTvis archive or TIFF bundle) for a file-rooted render. " +
    "The backend resolves its bytes — the UI never sends a path/URL.",
    nullable = true
  )
  String focusFileRefAppId,

  @Schema(
    description = "Per-call render knobs that vary per request and cannot live in a stored template body, e.g. " +
    "{\"frame\":\"3\",\"channel\":\"phase\"} or {\"mode\":\"index\"}.",
    nullable = true
  )
  Map<String, String> params
) {
  /**
   * V2CONV-A1b — legacy 3-arg template-rooted constructor. Preserves the
   * pre-A1b wire-shape so existing callers + tests compile unchanged.
   *
   * @param templateAppId  the VIEW_RECIPE template appId
   * @param focusShepardId the focus DataObject appId
   * @param mediaType      informational desired media type
   */
  public ShapesRenderRequestIO(String templateAppId, String focusShepardId, String mediaType) {
    this(templateAppId, focusShepardId, mediaType, null, null, null);
  }
}
