package de.dlr.shepard.data.spatialdata.model;

import lombok.Data;

@Data
public class KNearestNeighbor extends AbstractGeometryFilter {

  private int k;

  private Double x;
  private Double y;
  private Double z;

  public KNearestNeighbor() {
    super(GeometryFilterType.K_NEAREST_NEIGHBOR);
  }
}
