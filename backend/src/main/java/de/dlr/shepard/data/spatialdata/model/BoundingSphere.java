package de.dlr.shepard.data.spatialdata.model;

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
}
