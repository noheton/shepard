package de.dlr.shepard.data.spatialdata.model.geometryFilter;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class BoundingSphere extends AbstractGeometryFilter {

  private double radius;

  private double centerX;
  private double centerY;
  private double centerZ;

  public BoundingSphere() {
    super(GeometryFilterType.BOUNDING_SPHERE);
  }

  public void set(double radius, double centerX, double centerY, double centerZ) {
    this.radius = radius;
    this.centerX = centerX;
    this.centerY = centerY;
    this.centerZ = centerZ;
  }

  public boolean isValid() {
    return radius >= 0;
  }
}
