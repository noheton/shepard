package de.dlr.shepard.data.spatialdata.model;

import lombok.Data;

@Data
public class OrientedBoundingBox extends AbstractGeometryFilter {

  private double centerX;
  private double centerY;
  private double centerZ;

  private double roll;
  private double pitch;
  private double yaw;

  private double width;
  private double height;
  private double depth;

  public OrientedBoundingBox() {
    super(GeometryFilterType.ORIENTED_BOUNDING_BOX);
  }
}
