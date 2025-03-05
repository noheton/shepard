package de.dlr.shepard.data.spatialdata.model.geometryFilter;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class KNearestNeighbor extends AbstractGeometryFilter {

  private int k;

  private double x;
  private double y;
  private double z;

  public KNearestNeighbor() {
    super(GeometryFilterType.K_NEAREST_NEIGHBOR);
  }

  public KNearestNeighbor(int k, double x, double y, double z) {
    super(GeometryFilterType.K_NEAREST_NEIGHBOR);
    this.k = k;
    this.x = x;
    this.y = y;
    this.z = z;
  }

  @Override
  public boolean isValid() {
    return k >= 0;
  }
}
