package de.dlr.shepard.plugin.fileformat.thermography.render;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.plugin.fileformat.thermography.io.OtvisFramesIO;
import de.dlr.shepard.plugin.fileformat.thermography.services.OtvisFrameRenderService;
import de.dlr.shepard.spi.view.FocusPayloadResolver;
import de.dlr.shepard.spi.view.RenderException;
import de.dlr.shepard.spi.view.RenderRequest;
import de.dlr.shepard.spi.view.RenderResponse;
import de.dlr.shepard.spi.view.RenderedMedia;
import de.dlr.shepard.spi.view.ViewRecipeRenderer;
import io.quarkus.logging.Log;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * V2CONV-A7-THERMO — {@link ViewRecipeRenderer} that dissolves the bespoke
 * {@code GET /v2/thermography/otvis/{ref}/frames[/{n}]} REST onto the generic
 * {@code POST /v2/shapes/render}.
 *
 * <p>Claims the {@code OtvisFrameShape} IRI. A <i>file-rooted</i> render names
 * the shape IRI + the {@code .OTvis} FileReference appId (no stored template);
 * the dispatcher resolves the bytes through the V2CONV-A1b (E3)
 * {@link FocusPayloadResolver} on the request and hands them to this renderer,
 * which delegates to the existing decode/render logic in
 * {@link OtvisFrameRenderService} (no decode logic is rewritten here).
 *
 * <h2>Two output modes</h2>
 *
 * <ul>
 *   <li><b>JSON view-model</b> ({@link #render(RenderRequest)}) — when
 *       {@code params.mode=index} (E4) returns the decoded frame catalogue
 *       (the former {@code GET .../frames}); otherwise a minimal descriptor.</li>
 *   <li><b>{@code image/png}</b> ({@link #renderMedia}) — decodes
 *       {@code params.frame} (default 0) + {@code params.channel} to a
 *       colour-mapped heatmap PNG (the former
 *       {@code GET .../frames/{n}?channel=}).</li>
 * </ul>
 *
 * <p>Permission gating is upstream of the renderer: the dispatcher only builds
 * + supplies the {@link FocusPayloadResolver} after the request passed the
 * reference-rooted Read check, so this renderer cannot escalate.
 *
 * <p>Registered through
 * {@code META-INF/services/de.dlr.shepard.spi.view.ViewRecipeRenderer}.
 */
public final class OtvisFrameRenderer implements ViewRecipeRenderer {

  /** The OTvis frame shape IRI this renderer claims (the dispatch key). */
  public static final String OTVIS_FRAME_SHAPE_IRI =
    "http://semantics.dlr.de/shepard-ui/thermography/transform#OtvisFrameShape";

  /** PNG media type this renderer emits. */
  public static final String MEDIA_PNG = "image/png";

  static final String PARAM_MODE = "mode";
  static final String PARAM_FRAME = "frame";
  static final String PARAM_CHANNEL = "channel";
  static final String MODE_INDEX = "index";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /**
   * Stateless decode façade — the stream-decode methods touch no CDI-injected
   * state, so a plain instance is safe for a ServiceLoader POJO renderer.
   */
  private final OtvisFrameRenderService renderService = new OtvisFrameRenderService();

  @Override
  public Set<String> supportedShapeIris() {
    return Set.of(OTVIS_FRAME_SHAPE_IRI);
  }

  @Override
  public String name() {
    return "OtvisFrameRenderer";
  }

  @Override
  public Set<String> producibleMedia() {
    return Set.of(MEDIA_PNG);
  }

  // ── JSON view-model (describe / index — E4) ──────────────────────────────

  @Override
  public RenderResponse render(RenderRequest req) {
    String mode = req.param(PARAM_MODE);
    if (MODE_INDEX.equalsIgnoreCase(mode)) {
      OtvisFramesIO index = withBytes(req, in ->
        renderService.listFramesFromStream(req.focusFileRefAppId(), in));
      // Encode the frame catalogue as the JSON view-model: one binding per
      // decoded frame, role=frame index, selector=kind, unit=defaultChannel.
      List<RenderResponse.ChannelBindingProjection> bindings =
        index.getFrames() == null
          ? List.of()
          : index.getFrames().stream()
            .map(f -> new RenderResponse.ChannelBindingProjection(
              String.valueOf(f.getIndex()),
              f.getKind(),
              f.getDefaultChannel(),
              false,
              "OK",
              null))
            .toList();
      return new RenderResponse(null, req.focusShepardId(), "otvis-frames-index", bindings);
    }
    // Non-index JSON: a minimal descriptor pointing the caller at image/png.
    return new RenderResponse(
      null,
      req.focusShepardId(),
      "otvis-frame",
      List.of(new RenderResponse.ChannelBindingProjection(
        PARAM_CHANNEL, req.param(PARAM_CHANNEL), null, false, "DECLARED", null))
    );
  }

  // ── image/png frame heatmap ──────────────────────────────────────────────

  @Override
  public Optional<RenderedMedia> renderMedia(RenderRequest req, String acceptMediaType) {
    if (!MEDIA_PNG.equals(acceptMediaType)) {
      return Optional.empty();
    }
    int frame = parseFrame(req.param(PARAM_FRAME));
    String channel = req.param(PARAM_CHANNEL);
    byte[] png = withBytes(req, in -> renderService.renderPngFromStream(in, frame, channel));
    if (png == null || png.length == 0) {
      return Optional.empty();
    }
    return Optional.of(new RenderedMedia(MEDIA_PNG, png));
  }

  // ── E3 byte resolution ───────────────────────────────────────────────────

  @FunctionalInterface
  private interface StreamFn<T> {
    T apply(InputStream in) throws IOException;
  }

  /**
   * Resolve the focus FileReference bytes via the V2CONV-A1b (E3)
   * {@link FocusPayloadResolver} on the request, run {@code fn}, and translate
   * decode failures into the typed render {@link RenderException} the dispatcher
   * maps to 422.
   */
  private <T> T withBytes(RenderRequest req, StreamFn<T> fn) {
    String appId = req.focusFileRefAppId();
    if (appId == null || appId.isBlank()) {
      throw new RenderException(
        "render.input.missing",
        "OTvis frame render requires a focusFileRefAppId (the .OTvis FileReference)");
    }
    FocusPayloadResolver resolver = req.payloadResolver();
    if (resolver == null) {
      throw new RenderException(
        "render.internal-error",
        "no focus payload resolver wired — cannot read OTvis bytes for " + appId);
    }
    try (InputStream in = resolver.open(appId)) {
      if (in == null) {
        throw new RenderException(
          "render.input.not-found", "FileReference " + appId + " has no resolvable bytes");
      }
      return fn.apply(in);
    } catch (InvalidBodyException ex) {
      // Bad archive / frame / channel → 422 with the decode message.
      throw new RenderException("render.body.invalid", ex.getMessage());
    } catch (IOException ex) {
      Log.warnf(ex, "V2CONV-A7-THERMO: failed reading OTvis bytes for %s", appId);
      throw new RenderException("render.input.not-found",
        "could not read FileReference " + appId + ": " + ex.getMessage());
    }
  }

  private static int parseFrame(String raw) {
    if (raw == null || raw.isBlank()) {
      return 0;
    }
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException ex) {
      throw new RenderException("render.body.invalid", "frame param is not an integer: " + raw);
    }
  }
}
