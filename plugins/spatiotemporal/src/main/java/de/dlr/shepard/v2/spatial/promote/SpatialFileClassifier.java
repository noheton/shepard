package de.dlr.shepard.v2.spatial.promote;

import java.util.Locale;

/**
 * SPATIAL-UNIFY-004 — classify a FileReference name/filename as an eligible
 * spatial payload (pointcloud / trajectory) for in-context promotion.
 *
 * <p>Reuses the design's classifier (aidocs/integrations/124 §6): the standard
 * pointcloud / scan formats (.las, .laz, .ply, .e57, .pcd, .xyz, .pts) plus the
 * MFFD ASCII pointcloud / trajectory shapes recognised by the Python
 * spatial-importer ("TPS 3D pointclouds", "FSD course 3D pointclouds",
 * "TPS raw data"). A {@code .csv} only qualifies when its name signals a
 * trajectory/pointcloud — a bare {@code .csv} is NOT auto-eligible (a CSV that
 * merely looks like a pointcloud must not silently spawn a hypertable; §11).
 */
public final class SpatialFileClassifier {

  private SpatialFileClassifier() {}

  /** Standard binary/ASCII pointcloud + laser-scan extensions. */
  private static final String[] POINTCLOUD_EXTENSIONS = {
    ".las",
    ".laz",
    ".ply",
    ".e57",
    ".pcd",
    ".xyz",
    ".pts",
  };

  /** MFFD importer name signals (case-insensitive substring match). */
  private static final String[] MFFD_NAME_SIGNALS = {
    "tps 3d pointclouds",
    "fsd course 3d pointclouds",
    "tps raw data",
    "pointcloud",
    "trajectory",
  };

  /**
   * @param name the FileReference name (or attached filename) to classify
   * @return {@code true} when the file is an eligible spatial payload
   */
  public static boolean isEligible(String name) {
    if (name == null || name.isBlank()) return false;
    String lower = name.toLowerCase(Locale.ROOT);
    for (String ext : POINTCLOUD_EXTENSIONS) {
      if (lower.endsWith(ext)) return true;
    }
    for (String signal : MFFD_NAME_SIGNALS) {
      if (lower.contains(signal)) return true;
    }
    return false;
  }
}
