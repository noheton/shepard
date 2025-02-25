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
}
