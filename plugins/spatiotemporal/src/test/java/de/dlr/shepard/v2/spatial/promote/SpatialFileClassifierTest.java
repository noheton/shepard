package de.dlr.shepard.v2.spatial.promote;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** SPATIAL-UNIFY-004 — unit tests for {@link SpatialFileClassifier}. */
class SpatialFileClassifierTest {

  @Test
  void recognisesStandardPointcloudExtensions() {
    assertTrue(SpatialFileClassifier.isEligible("scan.las"));
    assertTrue(SpatialFileClassifier.isEligible("scan.LAZ"));
    assertTrue(SpatialFileClassifier.isEligible("mesh.ply"));
    assertTrue(SpatialFileClassifier.isEligible("cloud.e57"));
    assertTrue(SpatialFileClassifier.isEligible("points.pcd"));
    assertTrue(SpatialFileClassifier.isEligible("raw.xyz"));
    assertTrue(SpatialFileClassifier.isEligible("raw.pts"));
  }

  @Test
  void recognisesMffdNameSignals() {
    assertTrue(SpatialFileClassifier.isEligible("Track_66__Run_23133_/files/TPS 3D pointclouds.0"));
    assertTrue(SpatialFileClassifier.isEligible("FSD course 3D pointclouds"));
    assertTrue(SpatialFileClassifier.isEligible("TPS raw data"));
    assertTrue(SpatialFileClassifier.isEligible("afp-trajectory.dat"));
  }

  @Test
  void rejectsBareCsvAndUnrelatedFiles() {
    assertFalse(SpatialFileClassifier.isEligible("results.csv"));
    assertFalse(SpatialFileClassifier.isEligible("report.pdf"));
    assertFalse(SpatialFileClassifier.isEligible("notes.txt"));
  }

  @Test
  void rejectsNullAndBlank() {
    assertFalse(SpatialFileClassifier.isEligible(null));
    assertFalse(SpatialFileClassifier.isEligible(""));
    assertFalse(SpatialFileClassifier.isEligible("   "));
  }
}
