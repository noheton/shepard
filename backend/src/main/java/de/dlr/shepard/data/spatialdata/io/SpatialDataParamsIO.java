package de.dlr.shepard.data.spatialdata.io;

import de.dlr.shepard.data.spatialdata.model.AbstractGeometryFilter;
import java.util.Map;
import lombok.Data;

@Data
public class SpatialDataParamsIO {

  private AbstractGeometryFilter geometryFilter;
  private Map<String, Object> metadata;
  private long timestamp;
}
