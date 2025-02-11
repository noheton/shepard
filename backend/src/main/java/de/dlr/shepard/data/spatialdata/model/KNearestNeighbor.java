package de.dlr.shepard.data.spatialdata.model;

import lombok.Data;

@Data
public class KNearestNeighbor extends AbstractGeometryFilter {

  private int k;

  private double x;
  private double y;
  private double z;

  public KNearestNeighbor() {
    super(GeometryFilterType.K_NEAREST_NEIGHBOR);
  }
}
