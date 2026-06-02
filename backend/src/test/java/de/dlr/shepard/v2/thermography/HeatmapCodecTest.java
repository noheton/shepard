package de.dlr.shepard.v2.thermography;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import de.dlr.shepard.v2.thermography.io.PlateHeatmapIO;
import de.dlr.shepard.v2.thermography.services.ThermographyAnalysisService;
import de.dlr.shepard.v2.thermography.services.ThermographyMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MFFD-NDT-QUALITY-1 — codec round-trip tests for the plate-heatmap
 * persistence shape. The codec is hand-rolled (not Jackson) to keep the
 * hot persistence path free of allocations; this test guards against
 * a regression that flips encode/decode shapes.
 */
class HeatmapCodecTest {

  @Test
  @DisplayName("encode → decode round-trips the grid + min/max")
  void encodeDecodeRoundTrip() {
    ThermographyMetrics.BundleStats bundle = new ThermographyMetrics.BundleStats(3, 2);
    float[] pix = new float[] {1f, 2f, 3f, 4f, 5f, 6f};
    bundle.addFrame(0, "x.tif", pix, 3, 2);
    ThermographyAnalysisService svc = new ThermographyAnalysisService();
    String encoded = svc.encodeHeatmapJson(bundle);
    assertNotNull(encoded);
    PlateHeatmapIO decoded = svc.decodeHeatmapJson("bundle-1", encoded, 80.0, 1);
    assertNotNull(decoded);
    assertEquals(3, decoded.getWidth());
    assertEquals(2, decoded.getHeight());
    assertEquals(80.0, decoded.getThresholdTemp(), 1e-9);
    assertEquals(1, decoded.getFrameCount());
    // The pixel grid is 3x2 → grid is also 3x2; row 0 = (1,2,3), row 1 = (4,5,6).
    assertEquals(1.0f, decoded.getCells()[0][0], 1e-3f);
    assertEquals(2.0f, decoded.getCells()[0][1], 1e-3f);
    assertEquals(3.0f, decoded.getCells()[0][2], 1e-3f);
    assertEquals(4.0f, decoded.getCells()[1][0], 1e-3f);
    assertEquals(5.0f, decoded.getCells()[1][1], 1e-3f);
    assertEquals(6.0f, decoded.getCells()[1][2], 1e-3f);
  }

  @Test
  @DisplayName("decoder returns null on malformed input (no crash)")
  void decoderTolerantToMalformedInput() {
    ThermographyAnalysisService svc = new ThermographyAnalysisService();
    assertNull(svc.decodeHeatmapJson("b", "garbage", 80.0, 0));
    assertNull(svc.decodeHeatmapJson("b", "", 80.0, 0));
    // Wrong version header — still a string with not enough colons.
    assertNull(svc.decodeHeatmapJson("b", "v2:1x1:0:0", 80.0, 0));
  }
}
