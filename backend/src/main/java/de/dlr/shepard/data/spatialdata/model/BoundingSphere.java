package de.dlr.shepard.data.spatialdata.model;

import lombok.Data;

@Data
public class BoundingSphere extends AbstractGeometryFilter {

  private int r;
  private int centerX;
  private int centerY;
  private int centerZ;

  public BoundingSphere() {
    super(GeometryFilterType.BOUNDING_SPHERE);
  }
}
