package de.dlr.shepard.data.spatialdata.model.geometryFilter;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
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
