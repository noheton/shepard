package de.dlr.shepard.data.spatialdata.io;

import de.dlr.shepard.data.spatialdata.model.geometryFilter.AbstractGeometryFilter;
import jakarta.ws.rs.BadRequestException;
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
  private Integer offset;
  private Integer skip;

  public SpatialDataQueryParams(
    AbstractGeometryFilter geometryFilter,
    Map<String, Object> metadata,
    List<FilterCondition> measurementsFilters,
    Long startTime,
    Long endTime,
    Integer limit,
    Integer offset,
    Integer skip
  ) {
    if (!geometryFilter.isValid()) throw new BadRequestException("Invalid geometryFilter param");
    if (startTime != null && endTime != null && startTime > endTime) throw new BadRequestException(
      "startTime should be less than or equals endTime"
    );
    this.geometryFilter = geometryFilter;
    this.metadata = metadata;
    this.measurementsFilters = measurementsFilters;
    this.startTime = startTime;
    this.endTime = endTime;
    this.limit = limit;
    this.offset = offset;
    this.skip = skip;
  }
}
