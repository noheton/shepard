package de.dlr.shepard.data.spatialdata.services;

import de.dlr.shepard.data.spatialdata.io.SpatialDataParamsIO;
import de.dlr.shepard.data.spatialdata.io.SpatialDataPointIO;
import jakarta.enterprise.context.RequestScoped;
import java.util.List;

@RequestScoped
public class SpatialDataService {

  // TODO: inject repositories

  public void getSpatialDataPoints(long containerId, SpatialDataParamsIO spatialDataParams) {}

  public void createSpatialDataPoints(long containerId, List<SpatialDataPointIO> dataPoints) {}
}
