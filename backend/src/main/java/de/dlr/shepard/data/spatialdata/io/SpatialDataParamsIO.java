package de.dlr.shepard.data.spatialdata.io;

import de.dlr.shepard.data.spatialdata.model.geometryFilter.AbstractGeometryFilter;
import java.util.Map;
import java.util.Optional;
import lombok.Data;

@Data
public class SpatialDataParamsIO {

  private AbstractGeometryFilter geometryFilter;
  private Map<String, Object> metadata;
  private Long startTime;
  private Long endTime;
  private Optional<Integer> limit;
  private Integer offset;
  private Integer skip;

  public SpatialDataParamsIO(
    AbstractGeometryFilter geometryFilter,
    Map<String, Object> metadata,
    Long startTime,
    Long endTime,
    Optional<Integer> limit,
    Integer offset,
    Integer skip
  ) {
    this.geometryFilter = geometryFilter;
    this.metadata = metadata;
    this.startTime = startTime;
    this.endTime = endTime;
    this.limit = limit;
    this.offset = offset;
    this.skip = skip;
  }
}
