package de.dlr.shepard.data.timeseries.util;

import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import java.util.ArrayList;
import java.util.List;

/**
 * Largest-Triangle-Three-Buckets timeseries downsampling.
 *
 * <p>Reduces an ordered list of {@link TimeseriesDataPoint} to at most
 * {@code targetPoints} entries while preserving the curve's visual
 * shape — peaks and troughs survive aggressive compression, which is
 * the property charts and anomaly-detection workflows care about and
 * which uniform time-bucket means (the only other shepard-native
 * downsampler today) destroy.
 *
 * <p>Algorithm: Sveinn Steinarsson, "Downsampling Time Series for
 * Visual Representation" (2013, U. Iceland). For each of the
 * {@code targetPoints-2} interior buckets, choose the point that
 * forms the largest triangle with the previously-kept point and the
 * average of the next bucket. The first and last samples are always
 * preserved as anchors.
 *
 * <p>Used by:
 * <ul>
 *   <li>the v1 {@code GET /shepard/api/timeseriesContainers/{id}/payload}
 *       endpoint when called with {@code ?downsample=lttb&maxPoints=N}
 *       (default behaviour is unchanged — no downsampling unless asked);</li>
 *   <li>the MCP {@code get_channel_data} tool (via
 *       {@code TimeseriesMcpTools}).</li>
 * </ul>
 *
 * <p>Non-numeric values (booleans, strings) coerce to {@code 0.0} for
 * the area calculation; the algorithm then picks bucket representatives
 * driven by the timestamp axis alone, which is the right behaviour for
 * a boolean / categorical channel that happens to share the timeseries
 * surface.
 *
 * <p>O(n) time, no external dependencies. Pure function — caller's
 * input list is not mutated.
 */
public final class Lttb {

  private Lttb() {}

  /**
   * Downsample {@code points} to at most {@code targetPoints}.
   *
   * <ul>
   *   <li>If the input is already at-or-below the target, returns it
   *       verbatim (identity, no copy).</li>
   *   <li>If {@code targetPoints < 3} the algorithm degenerates to
   *       "keep just the endpoints" — caller should validate upstream
   *       if they need at least one interior sample.</li>
   * </ul>
   *
   * @param points       chronologically-ordered, non-null input series
   * @param targetPoints maximum point budget for the output
   * @return downsampled list of length {@code ≤ targetPoints}
   */
  public static List<TimeseriesDataPoint> downsample(List<TimeseriesDataPoint> points, int targetPoints) {
    int n = points.size();
    if (n <= targetPoints) return points;
    if (targetPoints < 3) {
      List<TimeseriesDataPoint> out = new ArrayList<>(Math.max(targetPoints, 1));
      out.add(points.get(0));
      if (targetPoints >= 2) out.add(points.get(n - 1));
      return out;
    }

    double bucketSize = (n - 2.0) / (targetPoints - 2.0);
    List<TimeseriesDataPoint> sampled = new ArrayList<>(targetPoints);
    sampled.add(points.get(0));
    int a = 0;

    for (int i = 0; i < targetPoints - 2; i++) {
      int avgStart = (int) ((i + 1) * bucketSize) + 1;
      int avgEnd = Math.min((int) ((i + 2) * bucketSize) + 1, n);
      int avgLen = Math.max(avgEnd - avgStart, 1);

      double avgX = 0.0;
      double avgY = 0.0;
      for (int j = avgStart; j < avgEnd; j++) {
        avgX += j;
        avgY += numeric(points.get(j).getValue());
      }
      avgX /= avgLen;
      avgY /= avgLen;

      int rangeStart = (int) (i * bucketSize) + 1;
      int rangeEnd = (int) ((i + 1) * bucketSize) + 1;

      double maxArea = -1.0;
      int best = rangeStart;
      double ax = a;
      double ay = numeric(points.get(a).getValue());

      for (int j = rangeStart; j < rangeEnd; j++) {
        double bx = j;
        double by = numeric(points.get(j).getValue());
        double area = Math.abs((ax - avgX) * (by - ay) - (ax - bx) * (avgY - ay)) * 0.5;
        if (area > maxArea) {
          maxArea = area;
          best = j;
        }
      }
      sampled.add(points.get(best));
      a = best;
    }
    sampled.add(points.get(n - 1));
    return sampled;
  }

  private static double numeric(Object v) {
    if (v instanceof Number n) return n.doubleValue();
    if (v instanceof Boolean b) return b ? 1.0 : 0.0;
    return 0.0;
  }
}
