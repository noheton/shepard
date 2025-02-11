package de.dlr.shepard.data.spatialdata.io;

import de.dlr.shepard.data.spatialdata.model.AbstractGeometryFilter;
import java.util.Map;
import lombok.Data;

@Data
public class SpatialDataParamsIO {

  private AbstractGeometryFilter geometryFilter;
  private Map<String, Object> metadata;
  private Long startTime;
  private Long endTime;
  private Integer limit;
  private Integer offset;
  private Integer skip;
}
