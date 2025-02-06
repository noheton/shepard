package de.dlr.shepard.data.spatialdata.model;

import lombok.Data;

@Data
public class AxisAlignedBoundingBox extends AbstractGeometryFilter {

  private double minX;
  private double minY;
  private double minZ;

  private double maxX;
  private double maxY;
  private double maxZ;

  public AxisAlignedBoundingBox() {
    super(GeometryFilterType.AXIS_ALIGNED_BOUNDING_BOX);
  }
}
