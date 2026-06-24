package de.dlr.shepard.v2.thermography;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.v2.thermography.services.ThermographyMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MFFD-NDT-QUALITY-1 — pure-math tests for {@link ThermographyMetrics}.
 * No backend / Quarkus context — the math layer is intentionally I/O-free.
 */
class ThermographyMetricsTest {

  // ── computeFrameStats ───────────────────────────────────────────────────

  @Test
  @DisplayName("peak-delta-c happy path — single hot pixel above uniform background")
  void peakDeltaC_happyPath() {
    int w = 4;
    int h = 4;
    float[] pix = new float[w * h];
    java.util.Arrays.fill(pix, 25.0f);   // uniform background = 25 degC
    pix[5] = 95.0f;                       // single hotspot in row 1 col 1
    ThermographyMetrics.FrameStats fs = ThermographyMetrics.computeFrameStats(7, "p5.tif", pix, w, h);
    assertEquals(95.0, fs.maxC(), 0.001);
    assertEquals(25.0, fs.medianC(), 0.001);
    // mean = (95 + 15*25) / 16 = (95 + 375)/16 = 29.375
    assertEquals(29.375, fs.meanC(), 0.001);
    assertEquals(70.0, fs.peakDeltaC(), 0.001);
    assertEquals(4.375, fs.meanDeltaC(), 0.001);
    assertEquals(1, fs.hotspotIx());
    assertEquals(1, fs.hotspotIy());
    assertEquals(7, fs.frameIndex());
    assertEquals("p5.tif", fs.frameName());
  }

  @Test
  @DisplayName("uniform frame yields peak-delta-c = 0 (no hot-spot signal)")
  void peakDeltaC_uniformFrame() {
    float[] pix = new float[100];
    java.util.Arrays.fill(pix, 42.0f);
    ThermographyMetrics.FrameStats fs = ThermographyMetrics.computeFrameStats(0, "u.tif", pix, 10, 10);
    assertEquals(0.0, fs.peakDeltaC(), 1e-6);
    assertEquals(0.0, fs.meanDeltaC(), 1e-6);
    assertEquals(42.0, fs.maxC(), 1e-6);
    assertEquals(42.0, fs.medianC(), 1e-6);
  }

  @Test
  @DisplayName("median is exact for both even and odd pixel counts")
  void medianCorrectness() {
    // odd: 1,2,3,4,5 → median = 3
    float[] odd = new float[] {1f, 2f, 3f, 4f, 5f};
    ThermographyMetrics.FrameStats fs1 = ThermographyMetrics.computeFrameStats(0, "o", odd, 5, 1);
    assertEquals(3.0, fs1.medianC(), 1e-6);
    // even: 1,2,3,4 → median = 2.5
    float[] even = new float[] {1f, 2f, 3f, 4f};
    ThermographyMetrics.FrameStats fs2 = ThermographyMetrics.computeFrameStats(0, "e", even, 4, 1);
    assertEquals(2.5, fs2.medianC(), 1e-6);
  }

  @Test
  @DisplayName("argmax — first occurrence wins on ties")
  void argmaxFirstWinsOnTies() {
    // 5x1 frame with two equal maxima → first index wins
    float[] pix = new float[] {1f, 9f, 9f, 9f, 1f};
    ThermographyMetrics.FrameStats fs = ThermographyMetrics.computeFrameStats(0, "t", pix, 5, 1);
    assertEquals(9.0, fs.maxC(), 1e-6);
    assertEquals(1, fs.hotspotIx());
    assertEquals(0, fs.hotspotIy());
  }

  @Test
  @DisplayName("empty pixel array → zero stats, no crash")
  void emptyFrame() {
    float[] pix = new float[0];
    ThermographyMetrics.FrameStats fs = ThermographyMetrics.computeFrameStats(0, "zero", pix, 0, 0);
    assertEquals(0.0, fs.maxC(), 1e-6);
    assertEquals(0.0, fs.peakDeltaC(), 1e-6);
  }

  @Test
  @DisplayName("computeFrameStats does NOT mutate the caller's pixel buffer")
  void doesNotMutateCallerBuffer() {
    float[] pix = new float[] {3f, 1f, 4f, 1f, 5f, 9f, 2f, 6f, 5f};
    float[] copy = pix.clone();
    ThermographyMetrics.computeFrameStats(0, "c", pix, 3, 3);
    for (int i = 0; i < pix.length; i++) {
      assertEquals(copy[i], pix[i], 1e-9, "pixel " + i + " was mutated");
    }
  }

  // ── BundleStats ─────────────────────────────────────────────────────────

  @Test
  @DisplayName("BundleStats accumulates per-frame stats + plate grid")
  void bundleStatsAccumulates() {
    ThermographyMetrics.BundleStats bundle = new ThermographyMetrics.BundleStats(4, 4);
    // Frame A — cold uniform
    float[] cold = new float[16];
    java.util.Arrays.fill(cold, 20.0f);
    bundle.addFrame(0, "cold.tif", cold, 4, 4);
    // Frame B — single hot pixel
    float[] hot = new float[16];
    java.util.Arrays.fill(hot, 22.0f);
    hot[10] = 88.0f;  // (2, 2)
    bundle.addFrame(1, "hot.tif", hot, 4, 4);
    assertEquals(2, bundle.frameCount());
    assertEquals(88.0, bundle.maxC(), 1e-6);
    assertEquals(20.0, bundle.minC(), 1e-6);
    assertEquals(66.0, bundle.maxPeakDeltaC(), 1e-6);
    float[][] grid = bundle.plateGridSafe();
    // The hot pixel must surface in cell (2,2) of the plate grid.
    assertEquals(88.0, grid[2][2], 1e-6);
    // Cell (0,0) was covered by both frames; max = 22 (from hot frame).
    assertEquals(22.0, grid[0][0], 1e-6);
    // Centroid weighted by peak-delta-c — only frame B contributes weight.
    assertEquals(2.0, bundle.hotspotCentroidX(), 1e-6);
    assertEquals(2.0, bundle.hotspotCentroidY(), 1e-6);
  }

  @Test
  @DisplayName("BundleStats handles empty bundle (no frames) without crashing")
  void bundleStatsEmpty() {
    ThermographyMetrics.BundleStats bundle = new ThermographyMetrics.BundleStats(8, 8);
    assertEquals(0, bundle.frameCount());
    assertEquals(0.0, bundle.maxPeakDeltaC(), 1e-9);
    assertEquals(-1.0, bundle.hotspotCentroidX(), 1e-9);
    assertEquals(-1.0, bundle.hotspotCentroidY(), 1e-9);
    // Plate grid replaces uncovered cells with minC (zero on empty bundle).
    float[][] grid = bundle.plateGridSafe();
    assertEquals(0.0, grid[0][0], 1e-6);
  }

  @Test
  @DisplayName("BundleStats rejects bad grid dimensions")
  void bundleStatsRejectsBadGrid() {
    assertThrows(IllegalArgumentException.class,
      () -> new ThermographyMetrics.BundleStats(0, 64));
    assertThrows(IllegalArgumentException.class,
      () -> new ThermographyMetrics.BundleStats(64, -1));
  }

  @Test
  @DisplayName("BundleStats rejects mismatched pixel-array length")
  void bundleStatsRejectsBadPixelArrayLength() {
    ThermographyMetrics.BundleStats bundle = new ThermographyMetrics.BundleStats(4, 4);
    float[] wrongSize = new float[7];
    assertThrows(IllegalArgumentException.class,
      () -> bundle.addFrame(0, "bad.tif", wrongSize, 4, 4));
  }

  @Test
  @DisplayName("plate-grid downsampling — large frame buckets into smaller grid correctly")
  void plateGridDownsampleMath() {
    // 8x8 frame down to 4x4 grid: each grid cell covers a 2x2 pixel block.
    ThermographyMetrics.BundleStats bundle = new ThermographyMetrics.BundleStats(4, 4);
    float[] pix = new float[64];
    // Set pixel (5,5) = 100 — should land in grid cell (5*4/8, 5*4/8) = (2,2).
    pix[5 * 8 + 5] = 100.0f;
    bundle.addFrame(0, "f.tif", pix, 8, 8);
    float[][] grid = bundle.plateGridSafe();
    assertEquals(100.0, grid[2][2], 1e-6);
    // Other cells stay at their default fill (0.0 — minC was 0 here).
    assertEquals(0.0, grid[0][0], 1e-6);
  }

  @Test
  @DisplayName("plate-grid stores running MAX across frames at each cell")
  void plateGridMaxAcrossFrames() {
    ThermographyMetrics.BundleStats bundle = new ThermographyMetrics.BundleStats(2, 2);
    float[] f1 = new float[] {10f, 20f, 30f, 40f};
    bundle.addFrame(0, "a", f1, 2, 2);
    float[] f2 = new float[] {99f, 5f, 5f, 5f};   // top-left wins; others stay.
    bundle.addFrame(1, "b", f2, 2, 2);
    float[][] grid = bundle.plateGridSafe();
    assertEquals(99.0, grid[0][0], 1e-6, "frame B's 99 beats frame A's 10");
    assertEquals(20.0, grid[0][1], 1e-6, "frame A's 20 beats frame B's 5");
    assertEquals(30.0, grid[1][0], 1e-6);
    assertEquals(40.0, grid[1][1], 1e-6);
  }

  // ── qualityScore ────────────────────────────────────────────────────────

  @Test
  @DisplayName("qualityScore is 1.0 for a uniform bundle (peak-delta-c = 0)")
  void qualityScoreUniform() {
    assertEquals(1.0, ThermographyMetrics.qualityScore(0.0, 80.0), 1e-9);
  }

  @Test
  @DisplayName("qualityScore drops linearly with peak-delta-c up to the threshold")
  void qualityScoreLinearDrop() {
    assertEquals(0.5, ThermographyMetrics.qualityScore(40.0, 80.0), 1e-9);
    assertEquals(0.25, ThermographyMetrics.qualityScore(60.0, 80.0), 1e-9);
  }

  @Test
  @DisplayName("qualityScore clamps to 0 at and above the threshold")
  void qualityScoreClampsAtThreshold() {
    assertEquals(0.0, ThermographyMetrics.qualityScore(80.0, 80.0), 1e-9);
    assertEquals(0.0, ThermographyMetrics.qualityScore(200.0, 80.0), 1e-9);
  }

  @Test
  @DisplayName("qualityScore is configurable via the threshold parameter")
  void qualityScoreThresholdConfigurable() {
    // Same delta, two thresholds, two scores.
    assertEquals(0.5, ThermographyMetrics.qualityScore(20.0, 40.0), 1e-9);
    assertEquals(0.75, ThermographyMetrics.qualityScore(20.0, 80.0), 1e-9);
  }

  @Test
  @DisplayName("qualityScore rejects non-positive thresholds")
  void qualityScoreRejectsBadThreshold() {
    assertThrows(IllegalArgumentException.class,
      () -> ThermographyMetrics.qualityScore(10.0, 0.0));
    assertThrows(IllegalArgumentException.class,
      () -> ThermographyMetrics.qualityScore(10.0, -5.0));
  }

  @Test
  @DisplayName("real-world synthetic — 5 frames, one anomaly, quality score reflects worst frame")
  void realisticSyntheticBundle() {
    ThermographyMetrics.BundleStats bundle = new ThermographyMetrics.BundleStats(8, 8);
    int w = 32;
    int h = 32;
    int sz = w * h;
    // 4 normal frames @ 30 degC uniform.
    for (int i = 0; i < 4; i++) {
      float[] normal = new float[sz];
      java.util.Arrays.fill(normal, 30.0f);
      bundle.addFrame(i, "ok_" + i + ".tif", normal, w, h);
    }
    // 5th frame: 30 degC background with a hot patch at center reaching 110 degC.
    float[] anomaly = new float[sz];
    java.util.Arrays.fill(anomaly, 30.0f);
    for (int y = 15; y < 17; y++) {
      for (int x = 15; x < 17; x++) anomaly[y * w + x] = 110.0f;
    }
    bundle.addFrame(4, "anomaly.tif", anomaly, w, h);

    assertEquals(5, bundle.frameCount());
    assertEquals(80.0, bundle.maxPeakDeltaC(), 0.5,
      "anomaly frame: max 110 − median 30 = 80 (matches default threshold)");
    // quality_score with threshold 80 should now be ~0.0 (worst frame matches threshold).
    double q = ThermographyMetrics.qualityScore(bundle.maxPeakDeltaC(), 80.0);
    assertTrue(q < 0.05, "expected near-zero quality with delta near threshold, got " + q);
  }

  @Test
  @DisplayName("BundleStats.frames() returns an unmodifiable view")
  void bundleStatsFramesIsImmutable() {
    ThermographyMetrics.BundleStats bundle = new ThermographyMetrics.BundleStats(4, 4);
    float[] pix = new float[16];
    bundle.addFrame(0, "x.tif", pix, 4, 4);
    var frames = bundle.frames();
    assertNotNull(frames);
    assertEquals(1, frames.size());
    assertThrows(UnsupportedOperationException.class, () -> frames.clear());
  }
}
