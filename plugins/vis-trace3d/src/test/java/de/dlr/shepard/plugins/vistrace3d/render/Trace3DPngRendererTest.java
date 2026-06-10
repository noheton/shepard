package de.dlr.shepard.plugins.vistrace3d.render;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.spi.view.RenderRequest;
import de.dlr.shepard.spi.view.RenderResponse;
import de.dlr.shepard.spi.view.RenderedMedia;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * RESEED-FIND-RENDER-PNG — unit tests for {@link Trace3DPngRenderer}.
 *
 * <p>The headline assertion: rendering the Trace3DViewShape with
 * {@code Accept: image/png} yields non-empty bytes carrying a valid PNG signature
 * — the contract that closes the "render always falls back to JSON" gap.
 */
class Trace3DPngRendererTest {

  /** The 8-byte PNG file signature (\x89 P N G \r \n \x1a \n). */
  private static final byte[] PNG_MAGIC = {
    (byte) 0x89, 'P', 'N', 'G', (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A,
  };

  private final Trace3DPngRenderer renderer = new Trace3DPngRenderer();

  private static final String BODY =
    "{\"templateKind\":\"VIEW_RECIPE\",\"title\":\"AFP TCP thermal trail\",\"renderer\":\"tresjs\","
      + "\"viewRecipeShape\":\"" + Trace3DPngRenderer.TRACE3D_VIEW_SHAPE_IRI + "\","
      + "\"trace3d:colorMap\":\"inferno\","
      + "\"channelBindings\":["
      + "{\"role\":\"x\",\"channelSelector\":\"{\\\"field\\\":\\\"tcp_x\\\"}\",\"required\":true},"
      + "{\"role\":\"y\",\"channelSelector\":\"{\\\"field\\\":\\\"tcp_y\\\"}\",\"required\":true},"
      + "{\"role\":\"z\",\"channelSelector\":\"{\\\"field\\\":\\\"tcp_z\\\"}\",\"required\":true},"
      + "{\"role\":\"color\",\"channelSelector\":\"{\\\"field\\\":\\\"tcp_temp\\\"}\",\"required\":false}"
      + "]}";

  @Test
  void claimsTheTrace3DViewShapeIri() {
    assertThat(renderer.supportedShapeIris())
      .containsExactly(Trace3DPngRenderer.TRACE3D_VIEW_SHAPE_IRI);
  }

  @Test
  void declaresPngInProducibleMedia() {
    assertThat(renderer.producibleMedia()).containsExactly("image/png");
  }

  @Test
  void renderMediaWithPngProducesValidPngBytes() {
    var req = new RenderRequest("tmpl-1", "focus-1", Trace3DPngRenderer.TRACE3D_VIEW_SHAPE_IRI, BODY);
    Optional<RenderedMedia> out = renderer.renderMedia(req, "image/png");

    assertThat(out).isPresent();
    assertThat(out.get().mediaType()).isEqualTo("image/png");
    byte[] bytes = out.get().bytes();
    assertThat(bytes).isNotNull();
    assertThat(bytes.length).isGreaterThan(PNG_MAGIC.length);
    // Valid PNG signature in the first 8 bytes.
    for (int i = 0; i < PNG_MAGIC.length; i++) {
      assertThat(bytes[i]).as("PNG signature byte %d", i).isEqualTo(PNG_MAGIC[i]);
    }
  }

  @Test
  void renderMediaWithEmptyBodyStillProducesPng() {
    var req = new RenderRequest("tmpl-1", "focus-1", Trace3DPngRenderer.TRACE3D_VIEW_SHAPE_IRI, "{}");
    Optional<RenderedMedia> out = renderer.renderMedia(req, "image/png");
    assertThat(out).isPresent();
    assertThat(out.get().bytes().length).isGreaterThan(PNG_MAGIC.length);
  }

  @Test
  void renderMediaDeclinesNonPngMedia() {
    var req = new RenderRequest("tmpl-1", "focus-1", Trace3DPngRenderer.TRACE3D_VIEW_SHAPE_IRI, BODY);
    assertThat(renderer.renderMedia(req, "model/gltf+json")).isEmpty();
  }

  @Test
  void jsonViewModelEchoesDeclaredBindings() {
    var req = new RenderRequest("tmpl-1", "focus-1", Trace3DPngRenderer.TRACE3D_VIEW_SHAPE_IRI, BODY);
    RenderResponse resp = renderer.render(req);
    assertThat(resp.renderer()).isEqualTo("tresjs");
    assertThat(resp.channelBindings()).hasSize(4);
    assertThat(resp.channelBindings()).allMatch(b -> "DECLARED".equals(b.status()));
  }
}
