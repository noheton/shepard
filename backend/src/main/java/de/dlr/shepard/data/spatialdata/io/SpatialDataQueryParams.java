package de.dlr.shepard.data.spatialdata.io;

import de.dlr.shepard.data.spatialdata.model.geometryFilter.AbstractGeometryFilter;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class SpatialDataQueryParams {

  private AbstractGeometryFilter geometryFilter;
  private List<FilterCondition> measurementsFilters;
  private Map<String, Object> metadata;
  private Long startTime;
  private Long endTime;
  private Integer limit;
  private Integer skip;

  public SpatialDataQueryParams(
    AbstractGeometryFilter geometryFilter,
    Map<String, Object> metadata,
    List<FilterCondition> measurementsFilters,
    Long startTime,
    Long endTime,
    Integer limit,
    Integer skip
  ) {
    this.geometryFilter = geometryFilter;
    this.metadata = metadata;
    this.measurementsFilters = measurementsFilters;
    this.startTime = startTime;
    this.endTime = endTime;
    this.limit = limit;
    this.skip = skip;
  }
}
