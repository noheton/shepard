package de.dlr.shepard.spi.view;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * V2CONV-A1 — covers the additive media-output contract on {@link ViewRecipeRenderer}:
 * defaults keep JSON-only renderers unchanged, and an opt-in renderer can declare +
 * emit a binary media type.
 */
class ViewRecipeRendererMediaTest {

  /** A renderer that only does the JSON view-model — must inherit empty media defaults. */
  private static final class JsonOnlyRenderer implements ViewRecipeRenderer {
    @Override
    public Set<String> supportedShapeIris() {
      return Set.of("urn:test:json-shape");
    }

    @Override
    public RenderResponse render(RenderRequest req) {
      return new RenderResponse(req.templateAppId(), req.focusShepardId(), "echarts", java.util.List.of());
    }
  }

  /** A renderer that opts into a PNG media output. */
  private static final class PngRenderer implements ViewRecipeRenderer {
    @Override
    public Set<String> supportedShapeIris() {
      return Set.of("urn:test:png-shape");
    }

    @Override
    public RenderResponse render(RenderRequest req) {
      return new RenderResponse(req.templateAppId(), req.focusShepardId(), "canvas", java.util.List.of());
    }

    @Override
    public Set<String> producibleMedia() {
      return Set.of("image/png");
    }

    @Override
    public Optional<RenderedMedia> renderMedia(RenderRequest req, String acceptMediaType) {
      if ("image/png".equals(acceptMediaType)) {
        return Optional.of(new RenderedMedia("image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G'}));
      }
      return Optional.empty();
    }
  }

  @Test
  void jsonOnlyRenderer_inheritsEmptyMediaDefaults() {
    var r = new JsonOnlyRenderer();
    assertTrue(r.producibleMedia().isEmpty(), "default producibleMedia must be empty");
    assertTrue(
      r.renderMedia(new RenderRequest("t", "f", "urn:test:json-shape", "{}"), "image/png").isEmpty(),
      "default renderMedia must be empty so the dispatcher falls back to JSON"
    );
  }

  @Test
  void pngRenderer_declaresAndEmitsBinary() {
    var r = new PngRenderer();
    assertEquals(Set.of("image/png"), r.producibleMedia());

    var req = new RenderRequest("t", "f", "urn:test:png-shape", "{}");
    Optional<RenderedMedia> out = r.renderMedia(req, "image/png");
    assertTrue(out.isPresent());
    assertEquals("image/png", out.get().mediaType());
    assertArrayEquals(new byte[] {(byte) 0x89, 'P', 'N', 'G'}, out.get().bytes());

    assertTrue(r.renderMedia(req, "model/gltf+json").isEmpty(), "unsupported media → empty → JSON fallback");
  }
}
