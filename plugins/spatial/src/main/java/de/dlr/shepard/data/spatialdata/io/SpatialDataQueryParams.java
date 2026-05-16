package de.dlr.shepard.data.spatialdata.io;

import de.dlr.shepard.data.spatialdata.model.geometryFilter.AbstractGeometryFilter;
import de.dlr.shepard.data.spatialdata.model.geometryFilter.KNearestNeighbor;
import java.util.ArrayList;
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

  /**
   * @return List of validation errors
   */
  public List<String> validate() {
    List<String> errors = new ArrayList<>();

    if (geometryFilter != null) {
      if (!geometryFilter.isValid()) errors.add("Invalid geometryFilter param");
      if (geometryFilter instanceof KNearestNeighbor) {
        if (skip != null) errors.add("Skip parameter is not accepted with KNN");
        if (limit != null) errors.add("Limit parameter is not accepted with KNN");
      }
    }
    if (skip != null && skip <= 0 && !(geometryFilter instanceof KNearestNeighbor)) errors.add(
      "Skip parameter must be greater than 0"
    );
    if (startTime != null && endTime != null && startTime > endTime) errors.add(
      "startTime should be less than or equals endTime"
    );
    return errors;
  }
}
