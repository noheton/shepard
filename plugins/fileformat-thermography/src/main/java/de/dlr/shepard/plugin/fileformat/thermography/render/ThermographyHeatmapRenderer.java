package de.dlr.shepard.plugin.fileformat.thermography.render;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.plugin.fileformat.thermography.io.PlateHeatmapIO;
import de.dlr.shepard.plugin.fileformat.thermography.services.ThermographyAnalysisService;
import de.dlr.shepard.spi.view.RenderException;
import de.dlr.shepard.spi.view.RenderRequest;
import de.dlr.shepard.spi.view.RenderResponse;
import de.dlr.shepard.spi.view.RenderedMedia;
import de.dlr.shepard.spi.view.ViewRecipeRenderer;
import io.quarkus.logging.Log;
import jakarta.enterprise.inject.spi.CDI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * V2CONV-A7-THERMO — {@link ViewRecipeRenderer} that dissolves the bespoke
 * {@code GET /v2/thermography/{bundle}/plate-heatmap} REST onto the generic
 * {@code POST /v2/shapes/render}.
 *
 * <p>Claims the {@code ThermographyHeatmapShape} IRI. A <i>file-rooted</i>
 * render names the shape IRI + the TIFF {@code FileBundleReference} appId via
 * {@code focusFileRefAppId}; the renderer returns the cached composite
 * plate-heatmap grid ({@link PlateHeatmapIO}) as its OWN {@code application/json}
 * view-model — the exact shape {@code frontend/utils/thermographyHeatmap.ts}
 * already consumes, so the canvas pane + hover-temp tooltip keep working
 * unchanged.
 *
 * <h2>Why this renderer declares {@code application/json} as producible media</h2>
 *
 * <p>The heatmap view-model is the {@link PlateHeatmapIO} grid, not the generic
 * channel-bindings envelope. The dispatcher (V2CONV-A7-THERMO) routes a JSON
 * {@code Accept} to {@link #renderMedia(RenderRequest, String)} when the
 * renderer declares {@code application/json} in {@link #producibleMedia()} — so
 * this renderer can emit its domain JSON. Renderers that don't declare it keep
 * the default channel-bindings view-model.
 *
 * <h2>Why a CDI lookup rather than the E3 byte resolver</h2>
 *
 * <p>Unlike the OTvis frame renderer, the plate-heatmap is <b>cached
 * annotation data</b> (written at analyze time as {@code urn:shepard:ndt:*}
 * {@code :SemanticAnnotation} rows), not bytes to decode. It is read via
 * {@link ThermographyAnalysisService#readPlateHeatmap(String)}, which needs the
 * request-scoped DAOs. The renderer is a ServiceLoader POJO, so it looks the
 * service up lazily via {@link CDI#current()} inside the request scope — the
 * same pattern the sibling {@code SceneGraphPlayTransformExecutor} uses.
 *
 * <p>Registered through
 * {@code META-INF/services/de.dlr.shepard.spi.view.ViewRecipeRenderer}.
 */
public final class ThermographyHeatmapRenderer implements ViewRecipeRenderer {

  /** The plate-heatmap shape IRI this renderer claims (the dispatch key). */
  public static final String THERMOGRAPHY_HEATMAP_SHAPE_IRI =
    "http://semantics.dlr.de/shepard-ui/thermography/transform#ThermographyHeatmapShape";

  static final String MEDIA_JSON = "application/json";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public Set<String> supportedShapeIris() {
    return Set.of(THERMOGRAPHY_HEATMAP_SHAPE_IRI);
  }

  @Override
  public String name() {
    return "ThermographyHeatmapRenderer";
  }

  @Override
  public Set<String> producibleMedia() {
    // Declares application/json so the dispatcher routes a JSON Accept to
    // renderMedia() — letting this renderer emit the PlateHeatmapIO grid
    // instead of the generic channel-bindings envelope.
    return Set.of(MEDIA_JSON);
  }

  @Override
  public Optional<RenderedMedia> renderMedia(RenderRequest req, String acceptMediaType) {
    if (!MEDIA_JSON.equals(acceptMediaType)) {
      return Optional.empty();
    }
    PlateHeatmapIO heatmap = resolveHeatmap(req);
    if (heatmap == null) {
      // No cached heatmap → bundle never analyzed. Mirror the old 404 with a
      // typed render error (dispatcher → 422 with the explanatory message).
      throw new RenderException(
        "render.not-analyzed",
        "no cached plate-heatmap for " + req.focusFileRefAppId()
          + " — run the thermography analysis (FileFormatPlugin.parse on upload, or re-parse) first");
    }
    try {
      byte[] json = MAPPER.writeValueAsBytes(heatmap);
      return Optional.of(new RenderedMedia(MEDIA_JSON, json));
    } catch (JsonProcessingException ex) {
      throw new RenderException("render.internal-error", "failed to serialise plate-heatmap: " + ex.getMessage());
    }
  }

  /**
   * The default channel-bindings view-model is never used for this shape (the
   * JSON path is intercepted by {@link #renderMedia}); supply a minimal,
   * non-null envelope for defensive callers.
   */
  @Override
  public RenderResponse render(RenderRequest req) {
    return new RenderResponse(null, req.focusShepardId(), "thermography-heatmap",
      List.<RenderResponse.ChannelBindingProjection>of());
  }

  private PlateHeatmapIO resolveHeatmap(RenderRequest req) {
    String bundleAppId = req.focusFileRefAppId();
    if (bundleAppId == null || bundleAppId.isBlank()) {
      throw new RenderException(
        "render.input.missing",
        "thermography heatmap render requires a focusFileRefAppId (the TIFF FileBundleReference)");
    }
    ThermographyAnalysisService service;
    try {
      service = CDI.current().select(ThermographyAnalysisService.class).get();
    } catch (RuntimeException ex) {
      Log.warnf(ex, "V2CONV-A7-THERMO: could not resolve ThermographyAnalysisService for %s", bundleAppId);
      throw new RenderException(
        "render.internal-error",
        "thermography heatmap renderer could not resolve the analysis service: " + ex.getMessage());
    }
    return service.readPlateHeatmap(bundleAppId);
  }
}
