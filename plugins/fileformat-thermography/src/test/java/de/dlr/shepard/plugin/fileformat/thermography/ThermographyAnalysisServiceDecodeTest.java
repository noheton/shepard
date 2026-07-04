package de.dlr.shepard.plugin.fileformat.thermography;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.plugin.fileformat.thermography.services.ThermographyAnalysisService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MFFD-NDT-QUALITY-1 — TIFF decoder pathway tests.
 *
 * <p>These exercise {@link ThermographyAnalysisService#decodeTiffFrame(InputStream)}
 * with synthetic in-memory TIFFs (TwelveMonkeys SPI on the test classpath).
 * No DAO / Quarkus context — the decoder is a static method.
 */
class ThermographyAnalysisServiceDecodeTest {

  // ── helpers ────────────────────────────────────────────────────────────

  /** Encode a tiny grayscale BufferedImage as a TIFF byte stream. */
  private static byte[] writeTiff(BufferedImage img) throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    boolean wrote = ImageIO.write(img, "TIFF", baos);
    if (!wrote) {
      // TwelveMonkeys uses "TIFF" (uppercase) — the assertion guards against
      // a classpath regression where the SPI provider goes missing.
      throw new IllegalStateException("ImageIO has no TIFF writer on the classpath");
    }
    return baos.toByteArray();
  }

  // ── tests ──────────────────────────────────────────────────────────────

  @Test
  @DisplayName("decodeTiffFrame round-trips a synthetic 8-bit gray TIFF")
  void decodeRoundTrips8BitGray() throws Exception {
    int w = 4;
    int h = 3;
    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
    // Set pixel (2,1) = 200 (high temp); everything else 30.
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        img.getRaster().setSample(x, y, 0, 30);
      }
    }
    img.getRaster().setSample(2, 1, 0, 200);
    byte[] bytes = writeTiff(img);

    ThermographyAnalysisService.FrameDecode out =
      ThermographyAnalysisService.decodeTiffFrame(new ByteArrayInputStream(bytes));
    assertNotNull(out, "TwelveMonkeys must produce a non-null FrameDecode");
    assertEquals(w, out.width());
    assertEquals(h, out.height());
    assertEquals(w * h, out.pixels().length);
    assertEquals(200f, out.pixels()[1 * w + 2], 1.5f, "hot pixel survived encoding");
    assertEquals(30f, out.pixels()[0], 1.5f);
  }

  @Test
  @DisplayName("decodeTiffFrame returns null on garbage input (no crash)")
  void decodeNullOnGarbage() {
    byte[] garbage = "not a TIFF, just text".getBytes();
    ThermographyAnalysisService.FrameDecode out =
      ThermographyAnalysisService.decodeTiffFrame(new ByteArrayInputStream(garbage));
    assertNull(out, "garbage input must decode to null, not throw");
  }

  @Test
  @DisplayName("decodeTiffFrame returns null on empty stream")
  void decodeNullOnEmpty() {
    ThermographyAnalysisService.FrameDecode out =
      ThermographyAnalysisService.decodeTiffFrame(new ByteArrayInputStream(new byte[0]));
    assertNull(out);
  }

  @Test
  @DisplayName("looksLikeTiff is case-insensitive and tolerant")
  void looksLikeTiffMatcher() {
    assertTrue(ThermographyAnalysisService.looksLikeTiff("foo.tif"));
    assertTrue(ThermographyAnalysisService.looksLikeTiff("foo.TIFF"));
    assertTrue(ThermographyAnalysisService.looksLikeTiff("S4_M13_L18_F4.Tif"));
    assertTrue(!ThermographyAnalysisService.looksLikeTiff("foo.png"));
    assertTrue(!ThermographyAnalysisService.looksLikeTiff(null));
    assertTrue(!ThermographyAnalysisService.looksLikeTiff(""));
  }

  @Test
  @DisplayName("decode → addFrame → max-delta yields the expected hot-pixel signal")
  void endToEndPureMath() throws Exception {
    int w = 4;
    int h = 4;
    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        img.getRaster().setSample(x, y, 0, 50);
      }
    }
    img.getRaster().setSample(0, 0, 0, 250);
    byte[] bytes = writeTiff(img);
    ThermographyAnalysisService.FrameDecode out =
      ThermographyAnalysisService.decodeTiffFrame(new ByteArrayInputStream(bytes));
    assertNotNull(out);
    var bundle = new de.dlr.shepard.plugin.fileformat.thermography.services.ThermographyMetrics.BundleStats(2, 2);
    bundle.addFrame(0, "hot.tif", out.pixels(), out.width(), out.height());
    // 1 hot pixel + 15 background → median ≈ 50, max ≈ 250, peak-delta ≈ 200.
    assertEquals(200.0, bundle.maxPeakDeltaC(), 5.0,
      "1 hot pixel + 15 background → peak-delta-c ≈ 200 (±5 for encoding noise)");
  }
}
