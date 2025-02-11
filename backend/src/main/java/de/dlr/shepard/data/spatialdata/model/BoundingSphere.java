package de.dlr.shepard.data.spatialdata.model;

import lombok.Data;

@Data
public class BoundingSphere extends AbstractGeometryFilter {

  private double r;
  private double centerX;
  private double centerY;
  private double centerZ;

  public BoundingSphere() {
    super(GeometryFilterType.BOUNDING_SPHERE);
  }
}
