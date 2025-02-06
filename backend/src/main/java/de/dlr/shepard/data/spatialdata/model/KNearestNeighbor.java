package de.dlr.shepard.data.spatialdata.model;

import lombok.Data;

@Data
public class KNearestNeighbor extends AbstractGeometryFilter {

  private int k;

  public KNearestNeighbor() {
    super(GeometryFilterType.K_NEAREST_NEIGHBOR);
  }
}
