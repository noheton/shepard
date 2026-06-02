package de.dlr.shepard.v2.thermography.services;

import java.util.Arrays;

/**
 * MFFD-NDT-QUALITY-1 — pure deterministic thermography frame statistics.
 *
 * <p>This class is intentionally I/O-free so the math can be exercised
 * directly in JUnit without any TIFF / file backend. The orchestrating
 * service ({@link ThermographyAnalysisService}) streams pixels in, calls
 * the helpers here per-frame, accumulates the result, and writes the
 * derived semantic annotations.
 *
 * <h2>Metric definitions (the "peak-delta-C math")</h2>
 *
 * <p>For each frame's pixel grid (post-calibration, in degrees Celsius):
 *
 * <ul>
 *   <li><b>{@code peakDeltaC}</b> — {@code max(pixel) − median(pixel)}.
 *       Hot-spot signal isolated from the frame's bulk temperature.
 *       A bulk-uniform frame yields {@code peakDeltaC ≈ 0}; a frame
 *       with a localised overheat region yields a large positive
 *       value regardless of the absolute camera offset.</li>
 *   <li><b>{@code meanDeltaC}</b> — {@code mean(pixel) − median(pixel)}.
 *       Skew indicator; negative on bulk-cold with hot tail, near zero
 *       on symmetric distributions.</li>
 *   <li><b>{@code maxC}</b> — absolute maximum temperature.</li>
 *   <li><b>{@code medianC}</b> — exact median (sorted; O(N log N)
 *       per frame). The advisor flagged the speed/accuracy trade-off;
 *       v1 picks exact median over a histogram approximation because
 *       reviewers can sanity-check with a hand-calculator and the
 *       accuracy matters at the bundle's worst-frame ranking.</li>
 *   <li><b>{@code hotspotIx} / {@code hotspotIy}</b> — pixel coordinates
 *       of the hottest pixel (first occurrence wins on ties).</li>
 * </ul>
 *
 * <p>The plate heatmap is a separate accumulator: a {@code int}-mapped
 * {@code float[gridH][gridW]} where each cell is the maximum temperature
 * observed at that grid bucket across all frames in the bundle. Each
 * pixel maps to one cell via integer division
 * ({@code cx = px * gridW / frameW}, etc.). The advisor's "never
 * accumulate a frame stack" rule is honoured — only the running max
 * grid + the per-frame stats list survive across frames.
 *
 * <p>The DataObject-level {@code qualityScore} aggregates per-bundle
 * {@code peakDeltaC} across all bundles (advisor guidance: pick
 * {@code max} across bundles — most conservative, surfaces the worst
 * region — and write one DO-level annotation).
 */
public final class ThermographyMetrics {

  private ThermographyMetrics() {
    // Pure helper class — no instantiation.
  }

  /**
   * Per-frame statistics. Immutable value type; one of these is produced
   * per processed TIFF and accumulated into a {@link BundleStats}.
   *
   * @param frameIndex zero-based ordinal in the bundle's frame list
   * @param frameName  the original filename (preserved for tooltip + audit)
   * @param maxC       absolute hottest pixel value, degrees Celsius
   * @param medianC    median over all pixels in this frame
   * @param meanC      mean over all pixels in this frame
   * @param peakDeltaC {@code maxC − medianC} — primary hot-spot signal
   * @param meanDeltaC {@code meanC − medianC} — distribution skew
   * @param hotspotIx  x-coord of the hottest pixel in frame coordinates
   * @param hotspotIy  y-coord of the hottest pixel in frame coordinates
   * @param frameWidth pixel width of this frame (for centroid weighting)
   * @param frameHeight pixel height of this frame
   */
  public record FrameStats(
    int frameIndex,
    String frameName,
    double maxC,
    double medianC,
    double meanC,
    double peakDeltaC,
    double meanDeltaC,
    int hotspotIx,
    int hotspotIy,
    int frameWidth,
    int frameHeight
  ) {}

  /**
   * Per-bundle accumulator and final summary.
   * The plate heatmap grid + the per-frame stats list co-live here.
   */
  public static final class BundleStats {
    private final float[][] plateGrid;          // [gridH][gridW] running max-C
    private final int gridW;
    private final int gridH;
    private final java.util.List<FrameStats> frames = new java.util.ArrayList<>();
    // Hotspot centroid is a max-weighted (x,y) accumulator across frames.
    private double centroidXSum = 0.0;
    private double centroidYSum = 0.0;
    private double centroidWeight = 0.0;
    private double minC = Double.POSITIVE_INFINITY;
    private double maxC = Double.NEGATIVE_INFINITY;
    private int frameCount = 0;

    public BundleStats(int gridW, int gridH) {
      if (gridW <= 0 || gridH <= 0) {
        throw new IllegalArgumentException("grid dimensions must be positive, got "
          + gridW + "x" + gridH);
      }
      this.gridW = gridW;
      this.gridH = gridH;
      this.plateGrid = new float[gridH][gridW];
      // Initialise to NEGATIVE_INFINITY so the first observation always wins.
      for (float[] row : plateGrid) {
        Arrays.fill(row, Float.NEGATIVE_INFINITY);
      }
    }

    public int gridW() { return gridW; }
    public int gridH() { return gridH; }
    public int frameCount() { return frameCount; }
    public double minC() { return minC == Double.POSITIVE_INFINITY ? 0.0 : minC; }
    public double maxC() { return maxC == Double.NEGATIVE_INFINITY ? 0.0 : maxC; }
    public java.util.List<FrameStats> frames() { return java.util.Collections.unmodifiableList(frames); }

    /**
     * The plate heatmap, replacing any unwritten cell with {@code minC()}
     * so consumers don't see {@code -Infinity}.
     */
    public float[][] plateGridSafe() {
      float fill = (float) minC();
      float[][] out = new float[gridH][gridW];
      for (int y = 0; y < gridH; y++) {
        for (int x = 0; x < gridW; x++) {
          float v = plateGrid[y][x];
          out[y][x] = (v == Float.NEGATIVE_INFINITY) ? fill : v;
        }
      }
      return out;
    }

    /** Max {@code peakDeltaC} observed in this bundle, 0 when empty. */
    public double maxPeakDeltaC() {
      double best = 0.0;
      for (FrameStats f : frames) {
        if (f.peakDeltaC > best) best = f.peakDeltaC;
      }
      return best;
    }

    /**
     * Centroid x-coordinate weighted by max-temperature across frames.
     * Returns -1 when no frames contributed (empty bundle).
     */
    public double hotspotCentroidX() {
      return centroidWeight == 0.0 ? -1.0 : centroidXSum / centroidWeight;
    }

    /** Centroid y-coordinate, parallel semantics. */
    public double hotspotCentroidY() {
      return centroidWeight == 0.0 ? -1.0 : centroidYSum / centroidWeight;
    }

    /**
     * Ingest one frame's float-per-pixel array (row-major, length
     * {@code width * height}). Computes the stats, updates the plate
     * heatmap accumulator, and stores the FrameStats record.
     */
    public FrameStats addFrame(int frameIndex, String frameName,
                               float[] pixels, int width, int height) {
      if (pixels == null || pixels.length != width * height) {
        throw new IllegalArgumentException("pixel array length must equal width*height; got "
          + (pixels == null ? -1 : pixels.length) + " for " + width + "x" + height);
      }
      FrameStats stats = computeFrameStats(frameIndex, frameName, pixels, width, height);
      frames.add(stats);

      // Update plate-grid max accumulator.
      for (int py = 0; py < height; py++) {
        int cy = (int) Math.min(gridH - 1L, (long) py * gridH / height);
        for (int px = 0; px < width; px++) {
          int cx = (int) Math.min(gridW - 1L, (long) px * gridW / width);
          float v = pixels[py * width + px];
          if (v > plateGrid[cy][cx]) plateGrid[cy][cx] = v;
        }
      }

      // Centroid: weight (px,py) of the hotspot by its peak-delta-c.
      double weight = Math.max(0.0, stats.peakDeltaC);
      centroidXSum += weight * stats.hotspotIx;
      centroidYSum += weight * stats.hotspotIy;
      centroidWeight += weight;

      if (stats.maxC > maxC) maxC = stats.maxC;
      // Min frame-min, tracked so missing plate cells render as cold-not-NaN.
      double frameMin = Double.POSITIVE_INFINITY;
      for (float p : pixels) if (p < frameMin) frameMin = p;
      if (frameMin < minC) minC = frameMin;

      frameCount++;
      return stats;
    }
  }

  /**
   * Compute the per-frame stats for one frame. Pure function — input
   * pixels are left untouched; a defensive copy is made before sorting
   * for the median, because in-place sort would corrupt the caller's
   * buffer when it's a recyclable scratch array.
   */
  public static FrameStats computeFrameStats(
    int frameIndex,
    String frameName,
    float[] pixels,
    int width,
    int height
  ) {
    if (pixels.length == 0) {
      return new FrameStats(frameIndex, frameName, 0, 0, 0, 0, 0, 0, 0, width, height);
    }
    int n = pixels.length;

    // Pass 1: max + sum + argmax (one allocation-free sweep).
    double sum = 0.0;
    float max = Float.NEGATIVE_INFINITY;
    int argmax = 0;
    for (int i = 0; i < n; i++) {
      float v = pixels[i];
      sum += v;
      if (v > max) { max = v; argmax = i; }
    }
    double mean = sum / n;

    // Pass 2: exact median via defensive sort.
    float[] sorted = pixels.clone();
    Arrays.sort(sorted);
    double median = (n % 2 == 1)
      ? sorted[n / 2]
      : 0.5 * (sorted[n / 2 - 1] + sorted[n / 2]);

    int hx = argmax % width;
    int hy = argmax / width;
    double peakDelta = max - median;
    double meanDelta = mean - median;
    return new FrameStats(
      frameIndex, frameName,
      max, median, mean,
      peakDelta, meanDelta,
      hx, hy, width, height
    );
  }

  /**
   * DataObject-level quality score in [0.0, 1.0]. Defined as
   * {@code 1 - clip(maxPeakDeltaC / thresholdC, 0, 1)}.
   * Returns 1.0 for a perfectly uniform bundle, 0.0 once the worst
   * frame's peak-delta-c crosses the threshold.
   */
  public static double qualityScore(double maxPeakDeltaC, double thresholdC) {
    if (thresholdC <= 0) {
      throw new IllegalArgumentException("thresholdC must be positive, got " + thresholdC);
    }
    if (maxPeakDeltaC <= 0) return 1.0;
    double ratio = maxPeakDeltaC / thresholdC;
    if (ratio >= 1.0) return 0.0;
    return 1.0 - ratio;
  }
}
