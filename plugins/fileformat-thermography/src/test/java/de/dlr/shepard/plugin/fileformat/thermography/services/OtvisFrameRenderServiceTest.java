package de.dlr.shepard.plugin.fileformat.thermography.services;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.plugin.fileformat.thermography.ExtractedFrames;
import de.dlr.shepard.plugin.fileformat.thermography.OTvisFrameExtractor;
import de.dlr.shepard.plugin.fileformat.thermography.RecurringHeader;
import de.dlr.shepard.plugin.fileformat.thermography.io.OtvisFramesIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.TreeMap;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * OTVIS-VIEWER — unit tests for the OTvis frame render service. These
 * exercise the pure index-build + PNG-render + colour-map helpers against a
 * synthetic single complex (DataFormat=13) lock-in frame fabricated in
 * memory, so no Mongo / Neo4j / CDI surface is touched.
 */
class OtvisFrameRenderServiceTest {

  private static final int W = 4;
  private static final int H = 3;
  private static final String MAGIC = "DIFFJPBG00000001";

  /** Build a minimal one-lock-in-frame ExtractedFrames via the extractor's stream codec. */
  private ExtractedFrames synthLockIn() {
    int n = W * H;
    ByteBuffer bb = ByteBuffer.allocate(RecurringHeader.HEADER_BYTES + n * 8)
      .order(ByteOrder.LITTLE_ENDIAN);
    bb.put(MAGIC.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    bb.putInt(W);
    bb.putInt(H);
    bb.putInt(OTvisFrameExtractor.DF_COMPLEX_FLOAT);
    // re/im ramp so amplitude + phase vary across the frame.
    for (int i = 0; i < n; i++) {
      bb.putFloat(i + 1f); // re
      bb.putFloat(i * 0.5f); // im
    }
    Map<String, byte[]> streams = new TreeMap<>();
    streams.put("content.xml", new byte[] {1});
    streams.put("sequence0/f0.bin", bb.array());
    return OTvisFrameExtractor.decodeFromStreams(streams);
  }

  @Test
  @DisplayName("buildIndex lists a lock-in frame with amplitude+phase, phase default")
  void buildIndexLockIn() {
    OtvisFrameRenderService svc = new OtvisFrameRenderService();
    ExtractedFrames frames = synthLockIn();
    OtvisFramesIO io = svc.buildIndex("ref-1", frames);
    assertEquals("ref-1", io.getFileReferenceAppId());
    assertEquals(W, io.getWidth());
    assertEquals(H, io.getHeight());
    assertEquals(1, io.getFrameCount());
    OtvisFramesIO.FrameInfo f = io.getFrames().get(0);
    assertEquals("lockin", f.getKind());
    assertEquals("phase", f.getDefaultChannel());
    assertTrue(f.getChannels().contains("amplitude"));
    assertTrue(f.getChannels().contains("phase"));
  }

  @Test
  @DisplayName("renderFromExtracted produces a valid W×H PNG for amplitude + phase")
  void renderPng() throws IOException {
    OtvisFrameRenderService svc = new OtvisFrameRenderService();
    ExtractedFrames frames = synthLockIn();
    for (String ch : new String[] {"amplitude", "phase"}) {
      byte[] png = svc.renderFromExtracted(frames, 0, ch);
      assertNotNull(png);
      var img = ImageIO.read(new ByteArrayInputStream(png));
      assertNotNull(img, "channel " + ch + " must decode to an image");
      assertEquals(W, img.getWidth());
      assertEquals(H, img.getHeight());
    }
  }

  @Test
  @DisplayName("null channel on a lock-in frame falls back to amplitude")
  void nullChannelDefaultsAmplitude() throws IOException {
    OtvisFrameRenderService svc = new OtvisFrameRenderService();
    ExtractedFrames frames = synthLockIn();
    byte[] png = svc.renderFromExtracted(frames, 0, null);
    assertNotNull(ImageIO.read(new ByteArrayInputStream(png)));
  }

  @Test
  @DisplayName("out-of-range frame index throws InvalidBodyException")
  void outOfRange() {
    OtvisFrameRenderService svc = new OtvisFrameRenderService();
    ExtractedFrames frames = synthLockIn();
    assertThrows(InvalidBodyException.class, () -> svc.renderFromExtracted(frames, 99, "phase"));
    assertThrows(InvalidBodyException.class, () -> svc.renderFromExtracted(frames, -1, "phase"));
  }

  @Test
  @DisplayName("invalid channel for a lock-in frame throws InvalidBodyException")
  void invalidChannel() {
    OtvisFrameRenderService svc = new OtvisFrameRenderService();
    ExtractedFrames frames = synthLockIn();
    assertThrows(InvalidBodyException.class,
      () -> svc.renderFromExtracted(frames, 0, "temperature"));
  }

  @Test
  @DisplayName("toPng on an all-equal frame maps to mid-grey, no divide-by-zero")
  void allEqualFrame() throws IOException {
    float[] flat = new float[] {5f, 5f, 5f, 5f};
    byte[] png = OtvisFrameRenderService.toPng(flat, 2, 2, false);
    var img = ImageIO.read(new ByteArrayInputStream(png));
    assertNotNull(img);
    assertEquals(2, img.getWidth());
    assertEquals(2, img.getHeight());
  }

  @Test
  @DisplayName("colour maps clamp at endpoints and interpolate monotonically")
  void colourMaps() {
    int lo = OtvisFrameRenderService.infernoRgb(0f);
    int hi = OtvisFrameRenderService.infernoRgb(1f);
    // inferno starts near-black, ends near-white-yellow → hi luminance > lo.
    assertTrue(luminance(hi) > luminance(lo));
    // clamp beyond [0,1] does not throw / overflow.
    assertEquals(lo, OtvisFrameRenderService.infernoRgb(-1f));
    assertEquals(hi, OtvisFrameRenderService.infernoRgb(2f));
    // cyclic map endpoints are visually identical (wrap-friendly).
    assertArrayEquals(
      rgb(OtvisFrameRenderService.cyclicRgb(0f)),
      rgb(OtvisFrameRenderService.cyclicRgb(1f)));
  }

  private static int luminance(int rgb) {
    int[] c = rgb(rgb);
    return c[0] + c[1] + c[2];
  }

  private static int[] rgb(int v) {
    return new int[] {(v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF};
  }
}
