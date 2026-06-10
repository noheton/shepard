package de.dlr.shepard.plugin.fileformat.thermography;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.plugin.fileformat.thermography.render.OtvisFrameRenderer;
import de.dlr.shepard.spi.view.FocusPayloadResolver;
import de.dlr.shepard.spi.view.RenderException;
import de.dlr.shepard.spi.view.RenderRequest;
import de.dlr.shepard.spi.view.RenderResponse;
import de.dlr.shepard.spi.view.RenderedMedia;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * V2CONV-A7-THERMO — unit tests for {@link OtvisFrameRenderer}, the
 * file-rooted {@link de.dlr.shepard.spi.view.ViewRecipeRenderer} that dissolves
 * {@code GET /v2/thermography/otvis/*} onto {@code POST /v2/shapes/render}. The
 * renderer obtains the {@code .OTvis} bytes through the V2CONV-A1b (E3)
 * {@link FocusPayloadResolver}; here that resolver serves a synthetic one-frame
 * archive built in-memory (no Mongo / Neo4j / CDI surface).
 */
class OtvisFrameRendererTest {

  private static final int W = 4;
  private static final int H = 3;
  private static final String MAGIC = "DIFFJPBG00000001";
  private static final String IRI = OtvisFrameRenderer.OTVIS_FRAME_SHAPE_IRI;

  /** Build a synthetic single lock-in-frame .OTvis tar byte array. */
  private static byte[] synthOtvisTar() throws Exception {
    int n = W * H;
    ByteBuffer bb = ByteBuffer.allocate(RecurringHeader.HEADER_BYTES + n * 8).order(ByteOrder.LITTLE_ENDIAN);
    bb.put(MAGIC.getBytes(StandardCharsets.US_ASCII));
    bb.putInt(W);
    bb.putInt(H);
    bb.putInt(OTvisFrameExtractor.DF_COMPLEX_FLOAT);
    for (int i = 0; i < n; i++) {
      bb.putFloat(i + 1f);
      bb.putFloat(i * 0.5f);
    }
    Map<String, byte[]> streams = new TreeMap<>();
    streams.put("content.xml", new byte[] { 1 });
    streams.put("sequence0/f0.bin", bb.array());
    return OTvisFrameExtractor.repackTar(streams);
  }

  /** A FocusPayloadResolver that serves the synthetic tar for the given appId. */
  private static FocusPayloadResolver resolverFor(byte[] tar) {
    return appId -> new ByteArrayInputStream(tar);
  }

  private static RenderRequest fileRooted(String fileRefAppId, FocusPayloadResolver resolver, Map<String, String> params) {
    return new RenderRequest(null, "do-1", IRI, null, params, fileRefAppId, resolver);
  }

  @Test
  @DisplayName("renderMedia(image/png) decodes the resolved OTvis bytes to a PNG frame")
  void rendersFramePng() throws Exception {
    byte[] tar = synthOtvisTar();
    OtvisFrameRenderer r = new OtvisFrameRenderer();
    RenderRequest req = fileRooted("otvis-ref", resolverFor(tar), Map.of("frame", "0", "channel", "phase"));

    Optional<RenderedMedia> media = r.renderMedia(req, "image/png");
    assertTrue(media.isPresent());
    assertEquals("image/png", media.get().mediaType());
    var img = ImageIO.read(new ByteArrayInputStream(media.get().bytes()));
    assertNotNull(img);
    assertEquals(W, img.getWidth());
    assertEquals(H, img.getHeight());
  }

  @Test
  @DisplayName("frame param defaults to 0 when absent")
  void defaultsFrameZero() throws Exception {
    byte[] tar = synthOtvisTar();
    OtvisFrameRenderer r = new OtvisFrameRenderer();
    RenderRequest req = fileRooted("otvis-ref", resolverFor(tar), Map.of("channel", "amplitude"));
    assertTrue(r.renderMedia(req, "image/png").isPresent());
  }

  @Test
  @DisplayName("render(params.mode=index) returns the frame catalogue as a JSON view-model (E4)")
  void describeModeReturnsFrameIndex() throws Exception {
    byte[] tar = synthOtvisTar();
    OtvisFrameRenderer r = new OtvisFrameRenderer();
    RenderRequest req = fileRooted("otvis-ref", resolverFor(tar), Map.of("mode", "index"));

    RenderResponse out = r.render(req);
    assertEquals("otvis-frames-index", out.renderer());
    assertEquals(1, out.channelBindings().size());
    var binding = out.channelBindings().get(0);
    assertEquals("0", binding.role()); // frame index
    assertEquals("lockin", binding.channelSelector()); // frame kind
    assertEquals("phase", binding.unit()); // default channel
  }

  @Test
  @DisplayName("missing focusFileRefAppId throws a typed RenderException")
  void missingFocusFileRef() {
    OtvisFrameRenderer r = new OtvisFrameRenderer();
    RenderRequest req = fileRooted(null, resolverFor(new byte[0]), Map.of("frame", "0"));
    RenderException ex = assertThrows(RenderException.class, () -> r.renderMedia(req, "image/png"));
    assertEquals("render.input.missing", ex.code());
  }

  @Test
  @DisplayName("absent payload resolver throws a typed RenderException")
  void missingResolver() {
    OtvisFrameRenderer r = new OtvisFrameRenderer();
    RenderRequest req = fileRooted("otvis-ref", null, Map.of("frame", "0"));
    RenderException ex = assertThrows(RenderException.class, () -> r.renderMedia(req, "image/png"));
    assertEquals("render.internal-error", ex.code());
  }

  @Test
  @DisplayName("non-integer frame param throws a typed RenderException")
  void badFrameParam() throws Exception {
    byte[] tar = synthOtvisTar();
    OtvisFrameRenderer r = new OtvisFrameRenderer();
    RenderRequest req = fileRooted("otvis-ref", resolverFor(tar), Map.of("frame", "abc"));
    RenderException ex = assertThrows(RenderException.class, () -> r.renderMedia(req, "image/png"));
    assertEquals("render.body.invalid", ex.code());
  }

  @Test
  @DisplayName("the renderer claims the OtvisFrameShape IRI and produces image/png")
  void claimsShapeAndMedia() {
    OtvisFrameRenderer r = new OtvisFrameRenderer();
    assertTrue(r.supportedShapeIris().contains(IRI));
    assertTrue(r.producibleMedia().contains("image/png"));
  }
}
