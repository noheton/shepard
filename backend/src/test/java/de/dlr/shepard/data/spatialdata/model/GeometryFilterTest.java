package de.dlr.shepard.data.spatialdata.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.data.spatialdata.model.geometryFilter.AxisAlignedBoundingBox;
import de.dlr.shepard.data.spatialdata.model.geometryFilter.BoundingSphere;
import de.dlr.shepard.data.spatialdata.model.geometryFilter.KNearestNeighbor;
import org.junit.jupiter.api.Test;

public class GeometryFilterTest {

  @Test
  public void isValidAxisAlignedBoundingBoxFilter_valid() {
    AxisAlignedBoundingBox axisAlignedBoundingBox = new AxisAlignedBoundingBox();
    axisAlignedBoundingBox.set(1, 2, 3, 4, 5, 6);
    assertTrue(axisAlignedBoundingBox.isValid());
  }

  @Test
  public void isValidAxisAlignedBoundingBoxFilter_notValid() {
    AxisAlignedBoundingBox axisAlignedBoundingBox = new AxisAlignedBoundingBox();
    axisAlignedBoundingBox.set(1, 2, 3, -1, 5, 6);
    assertFalse(axisAlignedBoundingBox.isValid());
    axisAlignedBoundingBox = new AxisAlignedBoundingBox();
    axisAlignedBoundingBox.set(1, 2, 3, 4, -1, 6);
    assertFalse(axisAlignedBoundingBox.isValid());
    axisAlignedBoundingBox = new AxisAlignedBoundingBox();
    axisAlignedBoundingBox.set(1, 2, 3, 4, 5, -1);
    assertFalse(axisAlignedBoundingBox.isValid());
  }

  @Test
  public void isValidBoundingSphereFilter_valid() {
    BoundingSphere boundingSphere = new BoundingSphere();
    boundingSphere.set(1, 2, 3, 4);
    assertTrue(boundingSphere.isValid());
  }

  @Test
  public void isValidBoundingSphereFilter_notValid() {
    BoundingSphere boundingSphere = new BoundingSphere();
    boundingSphere.set(-1, 2, 3, 4);
    assertFalse(boundingSphere.isValid());
  }

  @Test
  public void isValidKNearestNeighborFilter_valid() {
    KNearestNeighbor nearestNeighbor = new KNearestNeighbor();
    nearestNeighbor.set(1, 2, 3, 4);
    assertTrue(nearestNeighbor.isValid());
  }

  @Test
  public void isValidKNearestNeighborFilter_notValid() {
    KNearestNeighbor nearestNeighbor = new KNearestNeighbor();
    nearestNeighbor.set(-1, 2, 3, 4);
    assertFalse(nearestNeighbor.isValid());
  }

  @Test
  public void setAxisAlignedBounding_setsValues() {
    AxisAlignedBoundingBox axisAlignedBoundingBox = new AxisAlignedBoundingBox();
    axisAlignedBoundingBox.set(1, 2, 3, 4, 5, 6);
    assertEquals(axisAlignedBoundingBox.getMinX(), 1);
    assertEquals(axisAlignedBoundingBox.getMinY(), 2);
    assertEquals(axisAlignedBoundingBox.getMinZ(), 3);
    assertEquals(axisAlignedBoundingBox.getMaxX(), 4);
    assertEquals(axisAlignedBoundingBox.getMaxY(), 5);
    assertEquals(axisAlignedBoundingBox.getMaxZ(), 6);
  }

  @Test
  public void setBoundingSphere_setValues() {
    BoundingSphere boundingSphere = new BoundingSphere();
    boundingSphere.set(1, 2, 3, 4);
    assertEquals(boundingSphere.getRadius(), 1);
    assertEquals(boundingSphere.getCenterX(), 2);
    assertEquals(boundingSphere.getCenterY(), 3);
    assertEquals(boundingSphere.getCenterZ(), 4);
  }

  @Test
  public void setKNN_setValues() {
    KNearestNeighbor kNearestNeighbor = new KNearestNeighbor();
    kNearestNeighbor.set(1, 2, 3, 4);
    assertEquals(kNearestNeighbor.getK(), 1);
    assertEquals(kNearestNeighbor.getX(), 2);
    assertEquals(kNearestNeighbor.getY(), 3);
    assertEquals(kNearestNeighbor.getZ(), 4);
  }
}
